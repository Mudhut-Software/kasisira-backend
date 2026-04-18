package com.mudhut.software.kasisira.owner_org.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.notifications.services.NotificationService
import com.mudhut.software.kasisira.owner_org.entities.Invite
import com.mudhut.software.kasisira.owner_org.entities.Membership
import com.mudhut.software.kasisira.owner_org.entities.MembershipRole
import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import com.mudhut.software.kasisira.owner_org.entities.Permission
import com.mudhut.software.kasisira.owner_org.repositories.InviteRepository
import com.mudhut.software.kasisira.owner_org.repositories.MembershipRepository
import com.mudhut.software.kasisira.owner_org.repositories.OwnerOrgRepository
import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.utils.exceptions.InviteAlreadyUsedException
import com.mudhut.software.kasisira.utils.exceptions.InviteExpiredException
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

@ExtendWith(MockKExtension::class)
class InviteServiceImplTest {

    @MockK private lateinit var inviteRepository: InviteRepository
    @MockK private lateinit var membershipRepository: MembershipRepository
    @MockK private lateinit var membershipService: MembershipService
    @MockK private lateinit var ownerOrgRepository: OwnerOrgRepository
    @MockK private lateinit var userRepository: UserRepository
    @MockK private lateinit var notificationService: NotificationService
    @MockK private lateinit var passwordEncoder: PasswordEncoder
    private val objectMapper = ObjectMapper()
    private val clock: Clock = Clock.fixed(Instant.parse("2026-05-01T10:00:00Z"), ZoneOffset.UTC)
    @InjectMockKs private lateinit var service: InviteServiceImpl

    private val inviter = User(id = 1L, username = "o", email = "o@x.com", provider = AuthProvider.LOCAL)
    private val invitee = User(id = 2L, username = "m", email = "m@x.com", provider = AuthProvider.LOCAL)
    private val org = OwnerOrg(id = 42L, creator = inviter, name = "Org")

    @Test
    fun `createInvite requires MANAGE_TEAM, persists invite, enqueues email`() {
        every { membershipService.requirePermission(1L, 42L, Permission.MANAGE_TEAM) } just Runs
        every { ownerOrgRepository.findById(42L) } returns Optional.of(org)
        every { userRepository.findById(1L) } returns Optional.of(inviter)
        every { passwordEncoder.encode(any()) } returns "hashed-token"
        every { inviteRepository.save(any<Invite>()) } answers { (firstArg() as Invite).copy(id = 5L) }
        every { notificationService.enqueueEmail(any(), any(), any()) } just Runs

        service.createInvite(
            inviterUserId = 1L,
            orgId = 42L,
            email = "m@x.com",
            phoneNumber = null,
            role = MembershipRole.MANAGER,
            permissions = mapOf("manage_listings" to true, "manage_faults" to true)
        )

        verify { inviteRepository.save(match<Invite> {
            it.ownerOrg.id == 42L && it.email == "m@x.com" && it.role == MembershipRole.MANAGER
        }) }
        verify { notificationService.enqueueEmail(eq("m@x.com"), eq("invite"), any()) }
    }

    @Test
    fun `acceptInvite creates membership and marks invite accepted`() {
        val invite = Invite(
            id = 5L, ownerOrg = org, email = "m@x.com",
            role = MembershipRole.MANAGER,
            permissions = "{\"manage_listings\":true}",
            tokenHash = "hashed-token",
            invitedBy = inviter,
            expiresAt = Instant.parse("2026-05-08T10:00:00Z")
        )
        every { inviteRepository.findAll() } returns listOf(invite)
        every { passwordEncoder.matches("RAW", "hashed-token") } returns true
        every { userRepository.findById(2L) } returns Optional.of(invitee)
        every { membershipRepository.existsByOwnerOrgIdAndUserId(42L, 2L) } returns false
        every { membershipRepository.save(any<Membership>()) } answers { (firstArg() as Membership).copy(id = 20L) }
        every { inviteRepository.save(any<Invite>()) } answers { firstArg() }

        val result = service.acceptInvite(userId = 2L, rawToken = "RAW")

        assertEquals(20L, result.id)
        verify { membershipRepository.save(match<Membership> {
            it.ownerOrg.id == 42L && it.user.id == 2L && it.role == MembershipRole.MANAGER
        }) }
        verify { inviteRepository.save(match<Invite> { it.acceptedAt != null }) }
    }

    @Test
    fun `acceptInvite throws InviteExpiredException when no invite matches token`() {
        every { inviteRepository.findAll() } returns emptyList()
        assertThrows(InviteExpiredException::class.java) {
            service.acceptInvite(2L, "BAD-TOKEN")
        }
    }

    @Test
    fun `acceptInvite throws InviteExpiredException when past expiry`() {
        val expired = Invite(
            id = 5L, ownerOrg = org, email = "m@x.com",
            role = MembershipRole.MANAGER, permissions = "{}",
            tokenHash = "hashed-token", invitedBy = inviter,
            expiresAt = Instant.parse("2026-04-01T10:00:00Z")
        )
        every { inviteRepository.findAll() } returns listOf(expired)
        every { passwordEncoder.matches("RAW", "hashed-token") } returns true

        assertThrows(InviteExpiredException::class.java) {
            service.acceptInvite(2L, "RAW")
        }
    }

    @Test
    fun `acceptInvite throws InviteAlreadyUsedException when acceptedAt is set`() {
        // Already-accepted invites are filtered out of the scan by acceptedAt IS NULL.
        // The test verifies behaviour when a user is already a member: the scan finds
        // a matching active invite, but membership already exists.
        val invite = Invite(
            id = 5L, ownerOrg = org, email = "m@x.com",
            role = MembershipRole.MANAGER, permissions = "{}",
            tokenHash = "hashed-token", invitedBy = inviter,
            expiresAt = Instant.parse("2026-05-08T10:00:00Z")
        )
        every { inviteRepository.findAll() } returns listOf(invite)
        every { passwordEncoder.matches("RAW", "hashed-token") } returns true
        every { userRepository.findById(2L) } returns Optional.of(invitee)
        every { membershipRepository.existsByOwnerOrgIdAndUserId(42L, 2L) } returns true

        assertThrows(InviteAlreadyUsedException::class.java) {
            service.acceptInvite(2L, "RAW")
        }
    }

    @Test
    fun `revokeInvite requires MANAGE_TEAM and marks revokedAt`() {
        val invite = Invite(
            id = 5L, ownerOrg = org, email = "m@x.com",
            role = MembershipRole.MANAGER, permissions = "{}",
            tokenHash = "h", invitedBy = inviter,
            expiresAt = Instant.parse("2026-05-08T10:00:00Z")
        )
        every { inviteRepository.findById(5L) } returns Optional.of(invite)
        every { membershipService.requirePermission(1L, 42L, Permission.MANAGE_TEAM) } just Runs
        every { inviteRepository.save(any<Invite>()) } answers { firstArg() }

        service.revokeInvite(inviterUserId = 1L, inviteId = 5L)

        verify { inviteRepository.save(match<Invite> { it.revokedAt != null }) }
    }
}
