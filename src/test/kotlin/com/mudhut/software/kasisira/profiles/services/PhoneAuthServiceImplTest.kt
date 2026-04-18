package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.Contact
import com.mudhut.software.kasisira.profiles.entities.OtpPurpose
import com.mudhut.software.kasisira.profiles.entities.RoleName
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.mappers.UserMapper
import com.mudhut.software.kasisira.profiles.models.request.RequestOtpRequest
import com.mudhut.software.kasisira.profiles.models.request.VerifyOtpRequest
import com.mudhut.software.kasisira.profiles.repositories.ContactRepository
import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.security.JwtTokenProvider
import com.mudhut.software.kasisira.security.TokenPair
import com.mudhut.software.kasisira.utils.exceptions.UserNotFoundException
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class PhoneAuthServiceImplTest {

    @MockK
    private lateinit var otpService: OtpService

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var contactRepository: ContactRepository

    @MockK
    private lateinit var roleService: RoleService

    @MockK
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @MockK
    private lateinit var userMapper: UserMapper

    @InjectMockKs
    private lateinit var service: PhoneAuthServiceImpl

    private val loginPhone = "+256700000001"
    private val signupPhone = "+256700000002"

    @Test
    fun `requestOtp SIGNUP delegates directly to OtpService without contact lookup`() {
        // Given
        every { otpService.requestOtp(signupPhone, OtpPurpose.SIGNUP) } just Runs

        // When
        service.requestOtp(RequestOtpRequest(signupPhone, OtpPurpose.SIGNUP))

        // Then
        verify(exactly = 1) { otpService.requestOtp(signupPhone, OtpPurpose.SIGNUP) }
        verify(exactly = 0) { contactRepository.findByPhoneNumber(any()) }
    }

    @Test
    fun `requestOtp LOGIN verifies contact exists then delegates to OtpService`() {
        // Given
        val user = User(id = 10L, username = "u10", email = "u10@example.com", provider = AuthProvider.LOCAL)
        val contact = Contact(id = 1L, user = user, phoneNumber = loginPhone)
        every { contactRepository.findByPhoneNumber(loginPhone) } returns contact
        every { otpService.requestOtp(loginPhone, OtpPurpose.LOGIN) } just Runs

        // When
        service.requestOtp(RequestOtpRequest(loginPhone, OtpPurpose.LOGIN))

        // Then
        verify(exactly = 1) { contactRepository.findByPhoneNumber(loginPhone) }
        verify(exactly = 1) { otpService.requestOtp(loginPhone, OtpPurpose.LOGIN) }
    }

    @Test
    fun `requestOtp LOGIN throws UserNotFoundException if no contact exists`() {
        // Given — defence against phone-number enumeration
        every { contactRepository.findByPhoneNumber(loginPhone) } returns null

        // When/Then
        assertThrows<UserNotFoundException> {
            service.requestOtp(RequestOtpRequest(loginPhone, OtpPurpose.LOGIN))
        }

        // OTP never requested for unknown phone on LOGIN path
        verify(exactly = 0) { otpService.requestOtp(any(), any()) }
    }

    @Test
    fun `verify LOGIN returns JWT for existing user found via contact`() {
        // Given
        val user = User(id = 10L, username = "u10", email = "u10@example.com", provider = AuthProvider.LOCAL)
        val contact = Contact(id = 1L, user = user, phoneNumber = loginPhone)
        every { otpService.verifyOtp(loginPhone, "123456", OtpPurpose.LOGIN) } returns true
        every { contactRepository.findByPhoneNumber(loginPhone) } returns contact
        every { jwtTokenProvider.issueTokens(user) } returns TokenPair("access-token", "refresh-token")
        every { userMapper.toResponse(user) } returns mockUserResponse(user)

        // When
        val result = service.verify(VerifyOtpRequest(loginPhone, "123456", OtpPurpose.LOGIN))

        // Then
        Assertions.assertEquals("access-token", result.accessToken)
        Assertions.assertEquals("refresh-token", result.refreshToken)
        Assertions.assertNotNull(result.user)
        Assertions.assertEquals(10L, result.user!!.id)

        // No user creation/role grant on the LOGIN path
        verify(exactly = 0) { userRepository.save(any()) }
        verify(exactly = 0) { contactRepository.save(any()) }
        verify(exactly = 0) { roleService.grant(any(), any()) }
    }

    @Test
    fun `verify SIGNUP creates a new user, primary contact, grants TENANT, returns JWT`() {
        // Given
        every { otpService.verifyOtp(signupPhone, "123456", OtpPurpose.SIGNUP) } returns true
        every { contactRepository.findByPhoneNumber(signupPhone) } returns null

        val savedUserSlot = slot<User>()
        val savedContactSlot = slot<Contact>()
        every { userRepository.save(capture(savedUserSlot)) } answers { firstArg<User>().copy(id = 99L) }
        every { contactRepository.save(capture(savedContactSlot)) } answers { firstArg() }
        every { roleService.grant(any(), RoleName.TENANT) } just Runs
        every { jwtTokenProvider.issueTokens(any()) } returns TokenPair("a", "r")
        every { userMapper.toResponse(any()) } answers { mockUserResponse(firstArg()) }

        // When
        val result = service.verify(VerifyOtpRequest(signupPhone, "123456", OtpPurpose.SIGNUP))

        // Then
        // User is LOCAL provider, active+enabled, placeholder username/email, emailVerified=false
        val created = savedUserSlot.captured
        Assertions.assertEquals(AuthProvider.LOCAL, created.provider)
        Assertions.assertTrue(created.isActive)
        Assertions.assertTrue(created.isEnabled)
        Assertions.assertFalse(created.emailVerified, "emailVerified must default false on phone signup")
        Assertions.assertTrue(
            created.username.startsWith("u_"),
            "Expected placeholder username prefix, got: ${created.username}"
        )
        Assertions.assertTrue(
            created.email.endsWith("@placeholder.kasisira"),
            "Expected placeholder email domain, got: ${created.email}"
        )

        // Contact saved against persisted user (id=99), primary, verified, E.164 phone
        val savedContact = savedContactSlot.captured
        Assertions.assertEquals(signupPhone, savedContact.phoneNumber)
        Assertions.assertTrue(savedContact.isPrimary)
        Assertions.assertTrue(savedContact.isVerified, "Phone was just verified via OTP — contact.isVerified=true")
        Assertions.assertEquals(99L, savedContact.user!!.id)

        verify { roleService.grant(match { it.id == 99L }, RoleName.TENANT) }
        Assertions.assertEquals("a", result.accessToken)
        Assertions.assertEquals("r", result.refreshToken)
    }

    @Test
    fun `verify SIGNUP uses signupHint email when provided`() {
        // Given
        val hintEmail = "jane@example.com"
        every { otpService.verifyOtp(signupPhone, "123456", OtpPurpose.SIGNUP) } returns true
        every { contactRepository.findByPhoneNumber(signupPhone) } returns null

        val savedUserSlot = slot<User>()
        every { userRepository.save(capture(savedUserSlot)) } answers { firstArg<User>().copy(id = 99L) }
        every { contactRepository.save(any()) } answers { firstArg() }
        every { roleService.grant(any(), RoleName.TENANT) } just Runs
        every { jwtTokenProvider.issueTokens(any()) } returns TokenPair("a", "r")
        every { userMapper.toResponse(any()) } answers { mockUserResponse(firstArg()) }

        // When
        service.verify(
            VerifyOtpRequest(
                phoneNumber = signupPhone,
                code = "123456",
                purpose = OtpPurpose.SIGNUP,
                signupHint = com.mudhut.software.kasisira.profiles.models.request.SignupHint(email = hintEmail)
            )
        )

        // Then — hint email is used for both username and email, but emailVerified stays false
        val created = savedUserSlot.captured
        Assertions.assertEquals(hintEmail, created.email)
        Assertions.assertFalse(created.emailVerified)
    }

    @Test
    fun `verify SIGNUP reuses existing user when contact already exists (idempotent)`() {
        // Given — same phone hits signup a second time; treat as login-equivalent, no new user
        val existingUser = User(id = 42L, username = "u42", email = "u42@example.com", provider = AuthProvider.LOCAL)
        val existingContact = Contact(id = 5L, user = existingUser, phoneNumber = signupPhone)
        every { otpService.verifyOtp(signupPhone, "123456", OtpPurpose.SIGNUP) } returns true
        every { contactRepository.findByPhoneNumber(signupPhone) } returns existingContact
        every { jwtTokenProvider.issueTokens(existingUser) } returns TokenPair("a", "r")
        every { userMapper.toResponse(existingUser) } returns mockUserResponse(existingUser)

        // When
        val result = service.verify(VerifyOtpRequest(signupPhone, "123456", OtpPurpose.SIGNUP))

        // Then
        Assertions.assertEquals("a", result.accessToken)
        verify(exactly = 0) { userRepository.save(any()) }
        verify(exactly = 0) { contactRepository.save(any()) }
        verify(exactly = 0) { roleService.grant(any(), any()) }
    }

    @Test
    fun `requestOtp trims whitespace on input phone number`() {
        // Given
        every { contactRepository.findByPhoneNumber(loginPhone) } returns Contact(
            id = 1L, user = User(id = 1L, username = "u", email = "u@e.com"), phoneNumber = loginPhone
        )
        every { otpService.requestOtp(loginPhone, OtpPurpose.LOGIN) } just Runs

        // When
        service.requestOtp(RequestOtpRequest("  $loginPhone  ", OtpPurpose.LOGIN))

        // Then — lookup and delegation use the trimmed value
        verify(exactly = 1) { contactRepository.findByPhoneNumber(loginPhone) }
        verify(exactly = 1) { otpService.requestOtp(loginPhone, OtpPurpose.LOGIN) }
    }

    // --- helpers ---

    private fun mockUserResponse(user: User) =
        com.mudhut.software.kasisira.profiles.models.response.UserResponse(
            id = user.id,
            username = user.username,
            email = user.email,
            provider = user.provider,
            imageUrl = null,
            emailVerified = user.emailVerified,
            isActive = user.isActive,
            isEnabled = user.isEnabled,
            contacts = emptyList(),
            createdAt = null,
            updatedAt = null,
            lastLogin = null,
            roles = emptySet()
        )
}
