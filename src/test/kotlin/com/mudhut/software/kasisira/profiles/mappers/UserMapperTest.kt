package com.mudhut.software.kasisira.profiles.mappers

import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.Contact
import com.mudhut.software.kasisira.profiles.entities.RoleName
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.entities.UserRole
import com.mudhut.software.kasisira.profiles.models.request.RegisterRequest
import com.mudhut.software.kasisira.profiles.models.response.ContactResponse
import com.mudhut.software.kasisira.profiles.repositories.UserRoleRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.time.LocalDateTime

class UserMapperTest {

    private lateinit var contactMapper: ContactMapper
    private lateinit var userRoleRepository: UserRoleRepository
    private lateinit var userMapper: UserMapper

    private lateinit var testUser: User
    private lateinit var testContact: Contact
    private lateinit var testContactResponse: ContactResponse

    @BeforeEach
    fun setUp() {
        contactMapper = mockk()
        userRoleRepository = mockk()
        userMapper = UserMapper(contactMapper, userRoleRepository)

        // Default: no roles. Individual tests override to assert role mapping.
        every { userRoleRepository.findAllByUserId(any()) } returns emptyList()

        testContact = Contact(
            id = 1L,
            phoneNumber = "+256701234567",
            isPrimary = true,
            isVerified = false,
            label = "Primary",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        testContactResponse = ContactResponse(
            id = 1L,
            phoneNumber = "+256701234567",
            isPrimary = true,
            isVerified = false,
            label = "Primary",
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        testUser = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            passwordHash = "hashedPassword",
            provider = AuthProvider.LOCAL,
            providerId = null,
            imageUrl = "http://example.com/image.jpg",
            emailVerified = true,
            isActive = true,
            isEnabled = true,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            lastLogin = LocalDateTime.now()
        )
    }

    @Nested
    @DisplayName("toResponse tests")
    inner class ToResponseTests {

        @Test
        fun `should map user to response correctly`() {
            // Given
            every { contactMapper.toResponse(any()) } returns testContactResponse

            // When
            val response = userMapper.toResponse(testUser)

            // Then
            assertEquals(testUser.id, response.id)
            assertEquals(testUser.username, response.username)
            assertEquals(testUser.email, response.email)
            assertEquals(testUser.provider, response.provider)
            assertEquals(testUser.imageUrl, response.imageUrl)
            assertEquals(testUser.emailVerified, response.emailVerified)
            assertEquals(testUser.isActive, response.isActive)
            assertEquals(testUser.isEnabled, response.isEnabled)
            assertEquals(testUser.createdAt, response.createdAt)
            assertEquals(testUser.updatedAt, response.updatedAt)
            assertEquals(testUser.lastLogin, response.lastLogin)
        }

        @Test
        fun `should map user with contacts`() {
            // Given
            testUser.contacts.add(testContact)
            every { contactMapper.toResponse(testContact) } returns testContactResponse

            // When
            val response = userMapper.toResponse(testUser)

            // Then
            assertEquals(1, response.contacts.size)
            assertEquals(testContactResponse, response.contacts[0])
        }

        @Test
        fun `should map user without contacts`() {
            // Given - user has no contacts

            // When
            val response = userMapper.toResponse(testUser)

            // Then
            assertTrue(response.contacts.isEmpty())
        }

        @Test
        fun `should handle null optional fields`() {
            // Given
            val userWithNulls = testUser.copy(
                imageUrl = null,
                lastLogin = null
            )

            // When
            val response = userMapper.toResponse(userWithNulls)

            // Then
            assertNull(response.imageUrl)
            assertNull(response.lastLogin)
        }

        @Test
        fun `should populate roles from UserRoleRepository`() {
            // Given
            val tenantRole = UserRole(id = 10L, user = testUser, roleName = RoleName.TENANT)
            val ownerRole = UserRole(id = 11L, user = testUser, roleName = RoleName.OWNER)
            every { userRoleRepository.findAllByUserId(testUser.id) } returns listOf(tenantRole, ownerRole)

            // When
            val response = userMapper.toResponse(testUser)

            // Then
            assertEquals(setOf("TENANT", "OWNER"), response.roles)
        }

        @Test
        fun `should return empty roles set when user has no roles`() {
            // Given
            every { userRoleRepository.findAllByUserId(testUser.id) } returns emptyList()

            // When
            val response = userMapper.toResponse(testUser)

            // Then
            assertTrue(response.roles.isEmpty())
        }
    }

    @Nested
    @DisplayName("toResponseList tests")
    inner class ToResponseListTests {

        @Test
        fun `should map list of users to responses`() {
            // Given
            val user2 = testUser.copy(id = 2L, username = "user2", email = "user2@example.com")
            val users = listOf(testUser, user2)

            // When
            val responses = userMapper.toResponseList(users)

            // Then
            assertEquals(2, responses.size)
            assertEquals(testUser.id, responses[0].id)
            assertEquals(user2.id, responses[1].id)
        }

        @Test
        fun `should return empty list for empty input`() {
            // Given
            val users = emptyList<User>()

            // When
            val responses = userMapper.toResponseList(users)

            // Then
            assertTrue(responses.isEmpty())
        }
    }

    @Nested
    @DisplayName("fromRegisterRequest tests")
    inner class FromRegisterRequestTests {

        @Test
        fun `should map register request to user`() {
            // Given
            val request = RegisterRequest(
                username = "newuser",
                email = "new@example.com",
                password = "Password123!",
                provider = AuthProvider.LOCAL,
                providerId = null,
                imageUrl = "http://example.com/avatar.jpg",
                phoneNumber = null,
                phoneLabel = null
            )
            val encodedPassword = "encodedPassword123"

            // When
            val user = userMapper.fromRegisterRequest(request, encodedPassword)

            // Then
            assertEquals(0, user.id) // ID should be 0 for new entity
            assertEquals(request.username, user.username)
            assertEquals(request.email, user.email)
            assertEquals(encodedPassword, user.passwordHash)
            assertEquals(request.provider, user.provider)
            assertEquals(request.providerId, user.providerId)
            assertEquals(request.imageUrl, user.imageUrl)
            assertFalse(user.emailVerified)
            assertFalse(user.isActive)
            assertTrue(user.isEnabled)
        }

        @Test
        fun `should set default values for new user`() {
            // Given
            val request = RegisterRequest(
                username = "newuser",
                email = "new@example.com",
                password = "Password123!"
            )

            // When
            val user = userMapper.fromRegisterRequest(request, "encoded")

            // Then
            assertFalse(user.emailVerified)
            assertFalse(user.isActive)
            assertTrue(user.isEnabled)
            assertEquals(AuthProvider.LOCAL, user.provider)
        }
    }

    @Nested
    @DisplayName("fromRegisterRequestWithContact tests")
    inner class FromRegisterRequestWithContactTests {

        @Test
        fun `should create user with contact`() {
            // Given
            val request = RegisterRequest(
                username = "newuser",
                email = "new@example.com",
                password = "Password123!",
                phoneNumber = "+256701234567",
                phoneLabel = "Mobile"
            )

            // When
            val user = userMapper.fromRegisterRequestWithContact(request, "encoded")

            // Then
            assertEquals(1, user.contacts.size)
            assertEquals("+256701234567", user.contacts[0].phoneNumber)
            assertEquals("Mobile", user.contacts[0].label)
            assertTrue(user.contacts[0].isPrimary)
            assertFalse(user.contacts[0].isVerified)
        }

        @Test
        fun `should use default label when not provided`() {
            // Given
            val request = RegisterRequest(
                username = "newuser",
                email = "new@example.com",
                password = "Password123!",
                phoneNumber = "+256701234567",
                phoneLabel = null
            )

            // When
            val user = userMapper.fromRegisterRequestWithContact(request, "encoded")

            // Then
            assertEquals("Primary", user.contacts[0].label)
        }

        @Test
        fun `should not add contact when phone is blank`() {
            // Given
            val request = RegisterRequest(
                username = "newuser",
                email = "new@example.com",
                password = "Password123!",
                phoneNumber = "",
                phoneLabel = null
            )

            // When
            val user = userMapper.fromRegisterRequestWithContact(request, "encoded")

            // Then
            assertTrue(user.contacts.isEmpty())
        }

        @Test
        fun `should not add contact when phone is null`() {
            // Given
            val request = RegisterRequest(
                username = "newuser",
                email = "new@example.com",
                password = "Password123!",
                phoneNumber = null,
                phoneLabel = null
            )

            // When
            val user = userMapper.fromRegisterRequestWithContact(request, "encoded")

            // Then
            assertTrue(user.contacts.isEmpty())
        }

        @Test
        fun `should set contact user reference`() {
            // Given
            val request = RegisterRequest(
                username = "newuser",
                email = "new@example.com",
                password = "Password123!",
                phoneNumber = "+256701234567"
            )

            // When
            val user = userMapper.fromRegisterRequestWithContact(request, "encoded")

            // Then
            assertEquals(user, user.contacts[0].user)
        }
    }
}
