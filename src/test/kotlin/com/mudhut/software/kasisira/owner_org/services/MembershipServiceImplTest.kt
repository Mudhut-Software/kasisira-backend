package com.mudhut.software.kasisira.owner_org.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.owner_org.entities.Membership
import com.mudhut.software.kasisira.owner_org.entities.MembershipRole
import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import com.mudhut.software.kasisira.owner_org.entities.Permission
import com.mudhut.software.kasisira.owner_org.repositories.MembershipRepository
import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.utils.exceptions.NotOrgMemberException
import com.mudhut.software.kasisira.utils.exceptions.PermissionDeniedException
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class MembershipServiceImplTest {

    @MockK private lateinit var membershipRepository: MembershipRepository

    private val objectMapper = ObjectMapper()

    @InjectMockKs
    private lateinit var service: MembershipServiceImpl

    private val user = User(id = 7L, username = "u", email = "u@example.com", provider = AuthProvider.LOCAL)
    private val creator = User(id = 1L, username = "o", email = "o@example.com", provider = AuthProvider.LOCAL)
    private val org = OwnerOrg(id = 42L, creator = creator, name = "Test Org")

    private fun perms(map: Map<String, Boolean>): String = objectMapper.writeValueAsString(map)

    @Test
    fun `requirePermission passes when user has the permission`() {
        val membership = Membership(
            id = 1, ownerOrg = org, user = user, role = MembershipRole.OWNER,
            permissions = perms(mapOf("manage_listings" to true))
        )
        every { membershipRepository.findByOwnerOrgIdAndUserId(42L, 7L) } returns membership

        service.requirePermission(userId = 7L, orgId = 42L, permission = Permission.MANAGE_LISTINGS)
        // No exception = pass.
    }

    @Test
    fun `requirePermission throws NotOrgMemberException when no membership exists`() {
        every { membershipRepository.findByOwnerOrgIdAndUserId(42L, 7L) } returns null

        assertThrows(NotOrgMemberException::class.java) {
            service.requirePermission(7L, 42L, Permission.MANAGE_LISTINGS)
        }
    }

    @Test
    fun `requirePermission throws PermissionDeniedException when permission is false`() {
        val membership = Membership(
            id = 1, ownerOrg = org, user = user, role = MembershipRole.MANAGER,
            permissions = perms(mapOf("manage_listings" to false, "manage_faults" to true))
        )
        every { membershipRepository.findByOwnerOrgIdAndUserId(42L, 7L) } returns membership

        assertThrows(PermissionDeniedException::class.java) {
            service.requirePermission(7L, 42L, Permission.MANAGE_LISTINGS)
        }
    }

    @Test
    fun `requirePermission throws PermissionDeniedException when permission key is missing`() {
        val membership = Membership(
            id = 1, ownerOrg = org, user = user, role = MembershipRole.MANAGER,
            permissions = perms(mapOf("manage_faults" to true))
        )
        every { membershipRepository.findByOwnerOrgIdAndUserId(42L, 7L) } returns membership

        assertThrows(PermissionDeniedException::class.java) {
            service.requirePermission(7L, 42L, Permission.MANAGE_LISTINGS)
        }
    }

    @Test
    fun `hasPermission returns true when user has the permission`() {
        val membership = Membership(
            id = 1, ownerOrg = org, user = user, role = MembershipRole.OWNER,
            permissions = perms(mapOf("manage_team" to true))
        )
        every { membershipRepository.findByOwnerOrgIdAndUserId(42L, 7L) } returns membership

        assertEquals(true, service.hasPermission(7L, 42L, Permission.MANAGE_TEAM))
    }

    @Test
    fun `hasPermission returns false when user is not a member`() {
        every { membershipRepository.findByOwnerOrgIdAndUserId(42L, 7L) } returns null
        assertFalse(service.hasPermission(7L, 42L, Permission.MANAGE_TEAM))
    }

    @Test
    fun `listOrgsForUser returns the user's orgs`() {
        val m1 = Membership(id = 1, ownerOrg = org, user = user, role = MembershipRole.OWNER,
            permissions = perms(mapOf("manage_listings" to true)))
        every { membershipRepository.findAllByUserId(7L) } returns listOf(m1)

        val result = service.listOrgsForUser(7L)
        assertEquals(1, result.size)
        assertEquals(42L, result[0].id)
    }

    @Test
    fun `listMembersOfOrg returns memberships`() {
        val m1 = Membership(id = 1, ownerOrg = org, user = user, role = MembershipRole.MANAGER,
            permissions = perms(mapOf("manage_faults" to true)))
        every { membershipRepository.findAllByOwnerOrgId(42L) } returns listOf(m1)

        val result = service.listMembersOfOrg(42L)
        assertEquals(1, result.size)
        assertEquals(MembershipRole.MANAGER, result[0].role)
    }
}
