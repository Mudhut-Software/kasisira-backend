package com.mudhut.software.kasisira.owner_org.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.notifications.services.NotificationService
import com.mudhut.software.kasisira.owner_org.entities.Invite
import com.mudhut.software.kasisira.owner_org.entities.Membership
import com.mudhut.software.kasisira.owner_org.entities.MembershipRole
import com.mudhut.software.kasisira.owner_org.entities.Permission
import com.mudhut.software.kasisira.owner_org.repositories.InviteRepository
import com.mudhut.software.kasisira.owner_org.repositories.MembershipRepository
import com.mudhut.software.kasisira.owner_org.repositories.OwnerOrgRepository
import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.utils.exceptions.InviteAlreadyUsedException
import com.mudhut.software.kasisira.utils.exceptions.InviteExpiredException
import com.mudhut.software.kasisira.utils.exceptions.InviteRevokedException
import com.mudhut.software.kasisira.utils.exceptions.OrgNotFoundException
import com.mudhut.software.kasisira.utils.exceptions.UserNotFoundException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.util.Base64

@Service
class InviteServiceImpl(
    private val inviteRepository: InviteRepository,
    private val membershipRepository: MembershipRepository,
    private val membershipService: MembershipService,
    private val ownerOrgRepository: OwnerOrgRepository,
    private val userRepository: UserRepository,
    private val notificationService: NotificationService,
    private val passwordEncoder: PasswordEncoder,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) : InviteService {

    private val rng = SecureRandom()
    private val expiry: Duration = Duration.ofDays(7)

    @Transactional
    override fun createInvite(
        inviterUserId: Long,
        orgId: Long,
        email: String?,
        phoneNumber: String?,
        role: MembershipRole,
        permissions: Map<String, Boolean>
    ): Invite {
        require(email != null || phoneNumber != null) { "Email or phoneNumber required" }
        membershipService.requirePermission(inviterUserId, orgId, Permission.MANAGE_TEAM)

        val org = ownerOrgRepository.findById(orgId)
            .orElseThrow { OrgNotFoundException("Org $orgId not found") }
        val inviter = userRepository.findById(inviterUserId)
            .orElseThrow { UserNotFoundException("User $inviterUserId not found") }

        val rawToken = generateToken()
        val invite = inviteRepository.save(Invite(
            ownerOrg = org,
            email = email,
            phoneNumber = phoneNumber,
            role = role,
            permissions = objectMapper.writeValueAsString(permissions),
            tokenHash = passwordEncoder.encode(rawToken)!!,
            invitedBy = inviter,
            expiresAt = clock.instant().plus(expiry)
        ))

        val acceptUrl = "/accept-invite?token=$rawToken"
        val payload = mapOf<String, Any>(
            "orgName"   to org.name,
            "acceptUrl" to acceptUrl,
            "rawToken"  to rawToken
        )
        if (email != null) notificationService.enqueueEmail(email, "invite", payload)
        if (phoneNumber != null) notificationService.enqueueSms(
            phoneNumber,
            "You're invited to ${org.name} on Kasisira. Accept: $acceptUrl"
        )
        return invite
    }

    @Transactional
    override fun acceptInvite(userId: Long, rawToken: String): Membership {
        // Token lookup is by HASH; bcrypt is one-way so we scan open invites and
        // find one whose hash matches `rawToken`. O(n) in open invites globally;
        // acceptable at MVP scale. Revisit via HMAC-indexed lookup if needed.
        val candidates = inviteRepository.findAll()
            .filter { it.acceptedAt == null && it.revokedAt == null }
        val invite = candidates.firstOrNull { passwordEncoder.matches(rawToken, it.tokenHash) }
            ?: throw InviteExpiredException("Invite token invalid or expired")

        if (invite.revokedAt != null) throw InviteRevokedException("Invite has been revoked")
        if (invite.acceptedAt != null) throw InviteAlreadyUsedException("Invite already accepted")
        if (invite.expiresAt.isBefore(clock.instant())) throw InviteExpiredException("Invite expired")

        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User $userId not found") }

        if (membershipRepository.existsByOwnerOrgIdAndUserId(invite.ownerOrg.id, userId)) {
            throw InviteAlreadyUsedException("User is already a member of this org")
        }

        val membership = membershipRepository.save(Membership(
            ownerOrg = invite.ownerOrg,
            user = user,
            role = invite.role,
            permissions = invite.permissions,
            invitedBy = invite.invitedBy
        ))

        invite.acceptedAt = clock.instant()
        inviteRepository.save(invite)
        return membership
    }

    @Transactional
    override fun revokeInvite(inviterUserId: Long, inviteId: Long) {
        val invite = inviteRepository.findById(inviteId)
            .orElseThrow { InviteExpiredException("Invite $inviteId not found") }
        membershipService.requirePermission(inviterUserId, invite.ownerOrg.id, Permission.MANAGE_TEAM)
        invite.revokedAt = clock.instant()
        inviteRepository.save(invite)
    }

    private fun generateToken(): String {
        val bytes = ByteArray(32)
        rng.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
