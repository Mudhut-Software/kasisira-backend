package com.mudhut.software.kasisira.owner_org.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.owner_org.entities.Membership
import com.mudhut.software.kasisira.owner_org.entities.MembershipRole
import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import com.mudhut.software.kasisira.owner_org.repositories.MembershipRepository
import com.mudhut.software.kasisira.owner_org.repositories.OwnerOrgRepository
import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.RoleName
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.profiles.services.RoleService
import com.mudhut.software.kasisira.utils.exceptions.OrgNotFoundException
import com.mudhut.software.kasisira.utils.exceptions.UserNotFoundException
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
import java.util.Optional

@ExtendWith(MockKExtension::class)
class OwnerOrgServiceImplTest {

    @MockK private lateinit var ownerOrgRepository: OwnerOrgRepository
    @MockK private lateinit var membershipRepository: MembershipRepository
    @MockK private lateinit var userRepository: UserRepository
    @MockK private lateinit var roleService: RoleService
    private val objectMapper = ObjectMapper()
    @InjectMockKs private lateinit var service: OwnerOrgServiceImpl

    private val creator = User(id = 1L, username = "creator", email = "c@example.com", provider = AuthProvider.LOCAL)

    @Test
    fun `createOrg saves org, creates OWNER membership with all perms, grants global OWNER role`() {
        every { userRepository.findById(1L) } returns Optional.of(creator)
        every { ownerOrgRepository.save(any<OwnerOrg>()) } answers { (firstArg() as OwnerOrg).copy(id = 10L) }
        every { membershipRepository.save(any<Membership>()) } answers { (firstArg() as Membership).copy(id = 99L) }
        every { roleService.grant(creator, RoleName.OWNER) } just Runs

        val result = service.createOrg(creatorUserId = 1L, name = "My Org")

        assertEquals(10L, result.id)
        verify { ownerOrgRepository.save(match<OwnerOrg> { it.creator.id == 1L && it.name == "My Org" }) }
        verify { membershipRepository.save(match<Membership> {
            it.ownerOrg.id == 10L && it.user.id == 1L && it.role == MembershipRole.OWNER &&
            it.permissions.contains("\"manage_listings\":true") &&
            it.permissions.contains("\"manage_tenancies\":true") &&
            it.permissions.contains("\"manage_faults\":true") &&
            it.permissions.contains("\"manage_team\":true") &&
            it.permissions.contains("\"manage_billing\":true")
        }) }
        verify { roleService.grant(creator, RoleName.OWNER) }
    }

    @Test
    fun `createOrg throws UserNotFoundException if creator missing`() {
        every { userRepository.findById(999L) } returns Optional.empty()
        assertThrows(UserNotFoundException::class.java) { service.createOrg(999L, "Name") }
    }

    @Test
    fun `getOrgById returns org when it exists`() {
        val org = OwnerOrg(id = 5L, creator = creator, name = "X")
        every { ownerOrgRepository.findById(5L) } returns Optional.of(org)
        assertEquals("X", service.getOrgById(5L).name)
    }

    @Test
    fun `getOrgById throws OrgNotFoundException when missing`() {
        every { ownerOrgRepository.findById(99L) } returns Optional.empty()
        assertThrows(OrgNotFoundException::class.java) { service.getOrgById(99L) }
    }
}
