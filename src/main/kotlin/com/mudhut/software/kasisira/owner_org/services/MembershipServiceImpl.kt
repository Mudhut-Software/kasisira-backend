package com.mudhut.software.kasisira.owner_org.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.owner_org.entities.Membership
import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import com.mudhut.software.kasisira.owner_org.entities.Permission
import com.mudhut.software.kasisira.owner_org.repositories.MembershipRepository
import com.mudhut.software.kasisira.utils.exceptions.NotOrgMemberException
import com.mudhut.software.kasisira.utils.exceptions.PermissionDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MembershipServiceImpl(
    private val membershipRepository: MembershipRepository,
    private val objectMapper: ObjectMapper
) : MembershipService {

    @Transactional(readOnly = true)
    override fun requirePermission(userId: Long, orgId: Long, permission: Permission) {
        val membership = membershipRepository.findByOwnerOrgIdAndUserId(orgId, userId)
            ?: throw NotOrgMemberException("User $userId is not a member of org $orgId")
        val granted = parsePermissions(membership.permissions)[permission.key] ?: false
        if (!granted) throw PermissionDeniedException("User $userId lacks ${permission.key} in org $orgId")
    }

    @Transactional(readOnly = true)
    override fun hasPermission(userId: Long, orgId: Long, permission: Permission): Boolean {
        val membership = membershipRepository.findByOwnerOrgIdAndUserId(orgId, userId) ?: return false
        return parsePermissions(membership.permissions)[permission.key] ?: false
    }

    @Transactional(readOnly = true)
    override fun listMembersOfOrg(orgId: Long): List<Membership> =
        membershipRepository.findAllByOwnerOrgId(orgId)

    @Transactional(readOnly = true)
    override fun listOrgsForUser(userId: Long): List<OwnerOrg> =
        membershipRepository.findAllByUserId(userId).map { it.ownerOrg }

    private fun parsePermissions(json: String): Map<String, Boolean> {
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(json, Map::class.java) as Map<String, Boolean>
    }
}
