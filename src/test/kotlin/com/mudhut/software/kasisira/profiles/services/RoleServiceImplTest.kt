package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.RoleName
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.entities.UserRole
import com.mudhut.software.kasisira.profiles.repositories.UserRoleRepository
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Instant

@ExtendWith(MockKExtension::class)
class RoleServiceImplTest {

    @MockK
    private lateinit var userRoleRepository: UserRoleRepository

    @InjectMockKs
    private lateinit var roleService: RoleServiceImpl

    private lateinit var testUser: User

    @BeforeEach
    fun setUp() {
        testUser = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            passwordHash = "hashedPassword",
            provider = AuthProvider.LOCAL,
            emailVerified = true,
            isActive = true,
            isEnabled = true,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }

    @Test
    fun `grant inserts a UserRole when not already granted`() {
        // Given
        every { userRoleRepository.existsByUserIdAndRoleName(1L, RoleName.TENANT) } returns false
        every { userRoleRepository.save(any<UserRole>()) } answers { firstArg() }

        // When
        roleService.grant(testUser, RoleName.TENANT)

        // Then
        verify {
            userRoleRepository.save(
                match<UserRole> { it.user.id == 1L && it.roleName == RoleName.TENANT }
            )
        }
    }

    @Test
    fun `grant is idempotent — no save if role already exists`() {
        // Given
        every { userRoleRepository.existsByUserIdAndRoleName(1L, RoleName.TENANT) } returns true

        // When
        roleService.grant(testUser, RoleName.TENANT)

        // Then
        verify(exactly = 0) { userRoleRepository.save(any<UserRole>()) }
    }

    @Test
    fun `rolesOf returns all role names for the user`() {
        // Given
        val tenantRole = UserRole(id = 10L, user = testUser, roleName = RoleName.TENANT)
        val ownerRole = UserRole(id = 11L, user = testUser, roleName = RoleName.OWNER)
        every { userRoleRepository.findAllByUserId(1L) } returns listOf(tenantRole, ownerRole)

        // When
        val result = roleService.rolesOf(1L)

        // Then
        Assertions.assertEquals(setOf(RoleName.TENANT, RoleName.OWNER), result)
    }

    @Test
    fun `has returns true when user has the role`() {
        // Given
        every { userRoleRepository.existsByUserIdAndRoleName(1L, RoleName.ADMIN) } returns true

        // When
        val result = roleService.has(1L, RoleName.ADMIN)

        // Then
        Assertions.assertTrue(result)
    }

    @Test
    fun `revoke calls repo delete`() {
        // Given
        every { userRoleRepository.deleteByUserIdAndRoleName(1L, RoleName.TENANT) } returns 1L

        // When
        roleService.revoke(1L, RoleName.TENANT)

        // Then
        verify { userRoleRepository.deleteByUserIdAndRoleName(1L, RoleName.TENANT) }
    }
}
