package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.notifications.services.NotificationService
import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.RoleName
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.mappers.UserMapper
import com.mudhut.software.kasisira.profiles.models.request.RegisterRequest
import com.mudhut.software.kasisira.profiles.models.request.UpdateUserRequest
import com.mudhut.software.kasisira.profiles.models.response.UserResponse
import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.utils.PasswordValidator
import com.mudhut.software.kasisira.utils.exceptions.*
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Instant
import java.util.*

@ExtendWith(MockKExtension::class)
class UserServiceImplTest {

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var userMapper: UserMapper

    @MockK
    private lateinit var passwordEncoder: PasswordEncoder

    @MockK
    private lateinit var passwordValidator: PasswordValidator

    @MockK
    private lateinit var verificationService: VerificationService

    @MockK
    private lateinit var notificationService: NotificationService

    @MockK
    private lateinit var roleService: RoleService

    @InjectMockKs
    private lateinit var userService: UserServiceImpl

    private lateinit var testUser: User
    private lateinit var testUserResponse: UserResponse

    @BeforeEach
    fun setUp() {
        testUser = User(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            passwordHash = "hashedPassword",
            provider = AuthProvider.LOCAL,
            emailVerified = false,
            isActive = false,
            isEnabled = true,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        testUserResponse = UserResponse(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            provider = AuthProvider.LOCAL,
            imageUrl = null,
            emailVerified = false,
            isActive = false,
            isEnabled = true,
            contacts = emptyList(),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            lastLogin = null
        )
    }

    @Nested
    @DisplayName("findById tests")
    inner class FindByIdTests {

        @Test
        fun `should return user when found`() {
            // Given
            every { userRepository.findById(1L) } returns Optional.of(testUser)
            every { userMapper.toResponse(testUser) } returns testUserResponse

            // When
            val result = userService.findById(1L)

            // Then
            Assertions.assertEquals(testUserResponse, result)
            verify { userRepository.findById(1L) }
            verify { userMapper.toResponse(testUser) }
        }

        @Test
        fun `should throw UserNotFoundException when user not found`() {
            // Given
            every { userRepository.findById(999L) } returns Optional.empty()

            // When/Then
            assertThrows<UserNotFoundException> {
                userService.findById(999L)
            }
        }
    }

    @Nested
    @DisplayName("findByEmail tests")
    inner class FindByEmailTests {

        @Test
        fun `should return user when found by email`() {
            // Given
            every { userRepository.findByEmail("test@example.com") } returns Optional.of(testUser)
            every { userMapper.toResponse(testUser) } returns testUserResponse

            // When
            val result = userService.findByEmail("test@example.com")

            // Then
            Assertions.assertEquals(testUserResponse, result)
        }

        @Test
        fun `should return null when email not found`() {
            // Given
            every { userRepository.findByEmail("notfound@example.com") } returns Optional.empty()

            // When
            val result = userService.findByEmail("notfound@example.com")

            // Then
            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("findByUsername tests")
    inner class FindByUsernameTests {

        @Test
        fun `should return user when found by username`() {
            // Given
            every { userRepository.findByUsername("testuser") } returns Optional.of(testUser)
            every { userMapper.toResponse(testUser) } returns testUserResponse

            // When
            val result = userService.findByUsername("testuser")

            // Then
            Assertions.assertEquals(testUserResponse, result)
        }

        @Test
        fun `should return null when username not found`() {
            // Given
            every { userRepository.findByUsername("notfound") } returns Optional.empty()

            // When
            val result = userService.findByUsername("notfound")

            // Then
            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("existsByEmail tests")
    inner class ExistsByEmailTests {

        @Test
        fun `should return true when email exists`() {
            // Given
            every { userRepository.existsByEmail("test@example.com") } returns true

            // When
            val result = userService.existsByEmail("test@example.com")

            // Then
            Assertions.assertTrue(result)
        }

        @Test
        fun `should return false when email does not exist`() {
            // Given
            every { userRepository.existsByEmail("notfound@example.com") } returns false

            // When
            val result = userService.existsByEmail("notfound@example.com")

            // Then
            Assertions.assertFalse(result)
        }
    }

    @Nested
    @DisplayName("registerUser tests")
    inner class RegisterUserTests {

        private lateinit var registerRequest: RegisterRequest

        @BeforeEach
        fun setUp() {
            registerRequest = RegisterRequest(
                username = "newuser",
                email = "newuser@example.com",
                password = "Password123!",
                phoneNumber = "+256701234567"
            )
        }

        @Test
        fun `should register user successfully`() {
            // Given
            every { userRepository.existsByEmail(any()) } returns false
            every { userRepository.existsByUsername(any()) } returns false
            every { passwordValidator.validatePassword(any()) } just runs
            every { passwordEncoder.encode(any()) } returns "encodedPassword"
            every { userMapper.fromRegisterRequestWithContact(any(), any()) } returns testUser
            every { userRepository.save(any()) } returns testUser
            every { verificationService.createVerificationToken(any(), any()) } returns "token123"
            every { notificationService.enqueueEmail(any(), any(), any()) } just runs
            every { roleService.grant(any(), RoleName.TENANT) } just runs
            every { userMapper.toResponse(any()) } returns testUserResponse

            // When
            val result = userService.registerUser(registerRequest)

            // Then
            Assertions.assertNotNull(result)
            verify { passwordValidator.validatePassword("Password123!") }
            verify { passwordEncoder.encode("Password123!") }
            verify { userRepository.save(any()) }
            verify(exactly = 1) {
                notificationService.enqueueEmail(
                    eq("test@example.com"),
                    eq("verification"),
                    match { it["token"] == "token123" && it["username"] == "testuser" }
                )
            }
        }

        @Test
        fun `registerUser grants TENANT role after creating the user`() {
            // Given
            every { userRepository.existsByEmail(any()) } returns false
            every { userRepository.existsByUsername(any()) } returns false
            every { passwordValidator.validatePassword(any()) } just runs
            every { passwordEncoder.encode(any()) } returns "encodedPassword"
            every { userMapper.fromRegisterRequestWithContact(any(), any()) } returns testUser
            every { userRepository.save(any()) } returns testUser
            every { verificationService.createVerificationToken(any(), any()) } returns "token123"
            every { notificationService.enqueueEmail(any(), any(), any()) } just runs
            every { roleService.grant(any(), RoleName.TENANT) } just runs
            every { userMapper.toResponse(any()) } returns testUserResponse

            // When
            userService.registerUser(registerRequest)

            // Then
            verify(exactly = 1) {
                roleService.grant(match { it.id == testUser.id }, RoleName.TENANT)
            }
        }

        @Test
        fun `should throw InvalidEmailFormatException for invalid email`() {
            // Given
            val invalidRequest = registerRequest.copy(email = "invalid-email")

            // When/Then
            assertThrows<InvalidEmailFormatException> {
                userService.registerUser(invalidRequest)
            }
        }

        @Test
        fun `should throw InvalidPhoneNumberException for invalid phone number`() {
            // Given
            val invalidRequest = registerRequest.copy(phoneNumber = "123") // Too short, no +

            // When/Then
            assertThrows<InvalidPhoneNumberException> {
                userService.registerUser(invalidRequest)
            }
        }

        @Test
        fun `should throw UserAlreadyExistsException when email already exists`() {
            // Given
            every { userRepository.existsByEmail("newuser@example.com") } returns true

            // When/Then
            assertThrows<UserAlreadyExistsException> {
                userService.registerUser(registerRequest)
            }
        }

        @Test
        fun `should throw UserAlreadyExistsException when username already exists`() {
            // Given
            every { userRepository.existsByEmail(any()) } returns false
            every { userRepository.existsByUsername("newuser") } returns true

            // When/Then
            assertThrows<UserAlreadyExistsException> {
                userService.registerUser(registerRequest)
            }
        }

        @Test
        fun `should register user without phone number`() {
            // Given
            val requestWithoutPhone = registerRequest.copy(phoneNumber = null)
            every { userRepository.existsByEmail(any()) } returns false
            every { userRepository.existsByUsername(any()) } returns false
            every { passwordValidator.validatePassword(any()) } just runs
            every { passwordEncoder.encode(any()) } returns "encodedPassword"
            every { userMapper.fromRegisterRequest(any(), any()) } returns testUser
            every { userRepository.save(any()) } returns testUser
            every { verificationService.createVerificationToken(any(), any()) } returns "token123"
            every { notificationService.enqueueEmail(any(), any(), any()) } just runs
            every { roleService.grant(any(), RoleName.TENANT) } just runs
            every { userMapper.toResponse(any()) } returns testUserResponse

            // When
            val result = userService.registerUser(requestWithoutPhone)

            // Then
            Assertions.assertNotNull(result)
            verify { userMapper.fromRegisterRequest(any(), any()) }
        }
    }

    @Nested
    @DisplayName("updateUser tests")
    inner class UpdateUserTests {

        @Test
        fun `should update user successfully`() {
            // Given
            val updateRequest = UpdateUserRequest(username = "updateduser")
            val updatedUser = testUser.copy(username = "updateduser")
            val updatedResponse = testUserResponse.copy(username = "updateduser")

            every { userRepository.findById(1L) } returns Optional.of(testUser)
            every { userRepository.existsByUsername("updateduser") } returns false
            every { userRepository.save(any()) } returns updatedUser
            every { userMapper.toResponse(any()) } returns updatedResponse

            // When
            val result = userService.updateUser(1L, updateRequest)

            // Then
            Assertions.assertEquals("updateduser", result.username)
        }

        @Test
        fun `should throw UserNotFoundException when updating non-existent user`() {
            // Given
            val updateRequest = UpdateUserRequest(username = "updateduser")
            every { userRepository.findById(999L) } returns Optional.empty()

            // When/Then
            assertThrows<UserNotFoundException> {
                userService.updateUser(999L, updateRequest)
            }
        }

        @Test
        fun `should throw UserAlreadyExistsException when email is taken`() {
            // Given
            val updateRequest = UpdateUserRequest(email = "taken@example.com")
            every { userRepository.findById(1L) } returns Optional.of(testUser)
            every { userRepository.existsByEmail("taken@example.com") } returns true

            // When/Then
            assertThrows<UserAlreadyExistsException> {
                userService.updateUser(1L, updateRequest)
            }
        }

        @Test
        fun `should throw UserAlreadyExistsException when username is taken`() {
            // Given
            val updateRequest = UpdateUserRequest(username = "takenuser")
            every { userRepository.findById(1L) } returns Optional.of(testUser)
            every { userRepository.existsByUsername("takenuser") } returns true

            // When/Then
            assertThrows<UserAlreadyExistsException> {
                userService.updateUser(1L, updateRequest)
            }
        }
    }

    @Nested
    @DisplayName("deleteUser tests")
    inner class DeleteUserTests {

        @Test
        fun `should delete user successfully`() {
            // Given
            every { userRepository.existsById(1L) } returns true
            every { userRepository.deleteById(1L) } just runs

            // When
            userService.deleteUser(1L)

            // Then
            verify { userRepository.deleteById(1L) }
        }

        @Test
        fun `should throw UserNotFoundException when deleting non-existent user`() {
            // Given
            every { userRepository.existsById(999L) } returns false

            // When/Then
            assertThrows<UserNotFoundException> {
                userService.deleteUser(999L)
            }
        }
    }

    @Nested
    @DisplayName("activateUser tests")
    inner class ActivateUserTests {

        @Test
        fun `should activate user successfully`() {
            // Given
            val activatedUser = testUser.copy(isActive = true)
            val activatedResponse = testUserResponse.copy(isActive = true)

            every { userRepository.findById(1L) } returns Optional.of(testUser)
            every { userRepository.save(any()) } returns activatedUser
            every { userMapper.toResponse(any()) } returns activatedResponse

            // When
            val result = userService.activateUser(1L)

            // Then
            Assertions.assertTrue(result.isActive)
        }

        @Test
        fun `should throw UserNotFoundException when activating non-existent user`() {
            // Given
            every { userRepository.findById(999L) } returns Optional.empty()

            // When/Then
            assertThrows<UserNotFoundException> {
                userService.activateUser(999L)
            }
        }
    }

    @Nested
    @DisplayName("deactivateUser tests")
    inner class DeactivateUserTests {

        @Test
        fun `should deactivate user successfully`() {
            // Given
            val activeUser = testUser.copy(isActive = true)
            val deactivatedUser = testUser.copy(isActive = false)
            val deactivatedResponse = testUserResponse.copy(isActive = false)

            every { userRepository.findById(1L) } returns Optional.of(activeUser)
            every { userRepository.save(any()) } returns deactivatedUser
            every { userMapper.toResponse(any()) } returns deactivatedResponse

            // When
            val result = userService.deactivateUser(1L)

            // Then
            Assertions.assertFalse(result.isActive)
        }
    }

    @Nested
    @DisplayName("verifyEmail tests")
    inner class VerifyEmailTests {

        @Test
        fun `should verify email successfully`() {
            // Given
            val verifiedUser = testUser.copy(emailVerified = true)
            val verifiedResponse = testUserResponse.copy(emailVerified = true)

            every { userRepository.findById(1L) } returns Optional.of(testUser)
            every { userRepository.save(any()) } returns verifiedUser
            every { userMapper.toResponse(any()) } returns verifiedResponse
            every { notificationService.enqueueEmail(any(), any(), any()) } just runs

            // When
            val result = userService.verifyEmail(1L)

            // Then
            Assertions.assertTrue(result.emailVerified)
        }

        @Test
        fun `verifyEmail enqueues welcome email via NotificationService`() {
            // Given
            val verifiedUser = testUser.copy(emailVerified = true)
            val verifiedResponse = testUserResponse.copy(emailVerified = true)

            every { userRepository.findById(1L) } returns Optional.of(testUser)
            every { userRepository.save(any()) } returns verifiedUser
            every { userMapper.toResponse(any()) } returns verifiedResponse
            every { notificationService.enqueueEmail(any(), any(), any()) } just runs

            // When
            userService.verifyEmail(1L)

            // Then
            verify(exactly = 1) {
                notificationService.enqueueEmail(
                    eq("test@example.com"),
                    eq("welcome"),
                    match { it["username"] == "testuser" }
                )
            }
        }
    }

    @Nested
    @DisplayName("findAllUsers tests")
    inner class FindAllUsersTests {

        @Test
        fun `should return all users`() {
            // Given
            val users = listOf(testUser)
            val responses = listOf(testUserResponse)

            every { userRepository.findAll() } returns users
            every { userMapper.toResponseList(users) } returns responses

            // When
            val result = userService.findAllUsers()

            // Then
            Assertions.assertEquals(1, result.size)
        }

        @Test
        fun `should return empty list when no users`() {
            // Given
            every { userRepository.findAll() } returns emptyList()
            every { userMapper.toResponseList(emptyList()) } returns emptyList()

            // When
            val result = userService.findAllUsers()

            // Then
            Assertions.assertTrue(result.isEmpty())
        }
    }
}
