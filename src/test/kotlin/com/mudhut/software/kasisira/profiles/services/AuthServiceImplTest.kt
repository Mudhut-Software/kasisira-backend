package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.RefreshToken
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.mappers.UserMapper
import com.mudhut.software.kasisira.profiles.models.request.LoginRequest
import com.mudhut.software.kasisira.profiles.models.response.UserResponse
import com.mudhut.software.kasisira.profiles.repositories.RefreshTokenRepository
import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.security.JwtTokenProvider
import com.mudhut.software.kasisira.utils.exceptions.*
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import java.time.Duration
import java.time.Instant
import java.util.*

@ExtendWith(MockKExtension::class)
class AuthServiceImplTest {

    @MockK
    private lateinit var authenticationManager: AuthenticationManager

    @MockK
    private lateinit var userRepository: UserRepository

    @MockK
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @MockK
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @MockK
    private lateinit var userMapper: UserMapper

    @MockK
    private lateinit var httpRequest: HttpServletRequest

    @InjectMockKs
    private lateinit var authService: AuthServiceImpl

    private lateinit var testUser: User
    private lateinit var testUserResponse: UserResponse
    private lateinit var testRefreshToken: RefreshToken

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

        testUserResponse = UserResponse(
            id = 1L,
            username = "testuser",
            email = "test@example.com",
            provider = AuthProvider.LOCAL,
            imageUrl = null,
            emailVerified = true,
            isActive = true,
            isEnabled = true,
            contacts = emptyList(),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
            lastLogin = null
        )

        testRefreshToken = RefreshToken(
            id = 1L,
            token = "valid-refresh-token",
            user = testUser,
            expiresAt = Instant.now().plus(Duration.ofDays(7)),
            revoked = false
        )

        // Common mock setups
        every { httpRequest.getHeader("User-Agent") } returns "Test-Agent"
        every { httpRequest.remoteAddr } returns "127.0.0.1"
    }

    @Nested
    @DisplayName("login tests")
    inner class LoginTests {

        private lateinit var loginRequest: LoginRequest

        @BeforeEach
        fun setUp() {
            loginRequest = LoginRequest(
                email = "test@example.com",
                password = "Password123!"
            )
        }

        @Test
        fun `should login successfully with email`() {
            // Given
            val authentication = mockk<Authentication>()
            every { userRepository.findByEmail("test@example.com") } returns Optional.of(testUser)
            every { authenticationManager.authenticate(any()) } returns authentication
            every { jwtTokenProvider.generateAccessToken(any(), any()) } returns "access-token"
            every { jwtTokenProvider.generateRefreshToken(any()) } returns "refresh-token"
            every { userRepository.findById(1L) } returns Optional.of(testUser)
            every { refreshTokenRepository.save(any()) } returns testRefreshToken
            every { userRepository.save(any()) } returns testUser
            every { userMapper.toResponse(any()) } returns testUserResponse

            // When
            val result = authService.login(loginRequest, httpRequest)

            // Then
            Assertions.assertNotNull(result)
            Assertions.assertEquals("access-token", result.accessToken)
            Assertions.assertEquals("refresh-token", result.refreshToken)
            verify { authenticationManager.authenticate(any()) }
        }

        @Test
        fun `should login successfully with username`() {
            // Given
            val authentication = mockk<Authentication>()
            val usernameRequest = LoginRequest(email = "testuser", password = "Password123!")

            every { userRepository.findByEmail("testuser") } returns Optional.empty()
            every { userRepository.findByUsername("testuser") } returns Optional.of(testUser)
            every { authenticationManager.authenticate(any()) } returns authentication
            every { jwtTokenProvider.generateAccessToken(any(), any()) } returns "access-token"
            every { jwtTokenProvider.generateRefreshToken(any()) } returns "refresh-token"
            every { userRepository.findById(1L) } returns Optional.of(testUser)
            every { refreshTokenRepository.save(any()) } returns testRefreshToken
            every { userRepository.save(any()) } returns testUser
            every { userMapper.toResponse(any()) } returns testUserResponse

            // When
            val result = authService.login(usernameRequest, httpRequest)

            // Then
            Assertions.assertNotNull(result)
        }

        @Test
        fun `should throw UserNotFoundException when user not found`() {
            // Given
            every { userRepository.findByEmail("notfound@example.com") } returns Optional.empty()
            every { userRepository.findByUsername("notfound@example.com") } returns Optional.empty()

            val request = LoginRequest(email = "notfound@example.com", password = "password")

            // When/Then
            assertThrows<UserNotFoundException> {
                authService.login(request, httpRequest)
            }
        }

        @Test
        fun `should throw UserNotActiveException when user is not active`() {
            // Given
            val inactiveUser = testUser.copy(isActive = false)
            every { userRepository.findByEmail("test@example.com") } returns Optional.of(inactiveUser)

            // When/Then
            assertThrows<UserNotActiveException> {
                authService.login(loginRequest, httpRequest)
            }
        }

        @Test
        fun `should throw UserNotActiveException when user is disabled`() {
            // Given
            val disabledUser = testUser.copy(isEnabled = false)
            every { userRepository.findByEmail("test@example.com") } returns Optional.of(disabledUser)

            // When/Then
            assertThrows<UserNotActiveException> {
                authService.login(loginRequest, httpRequest)
            }
        }

        @Test
        fun `should throw EmailNotVerifiedException when email is not verified`() {
            // Given
            val unverifiedUser = testUser.copy(emailVerified = false)
            every { userRepository.findByEmail("test@example.com") } returns Optional.of(unverifiedUser)

            // When/Then
            assertThrows<EmailNotVerifiedException> {
                authService.login(loginRequest, httpRequest)
            }
        }
    }

    @Nested
    @DisplayName("refreshToken tests")
    inner class RefreshTokenTests {

        @Test
        fun `should refresh token successfully`() {
            // Given
            every { jwtTokenProvider.validateToken("valid-refresh-token") } returns true
            every { jwtTokenProvider.getTokenType("valid-refresh-token") } returns "refresh"
            every { refreshTokenRepository.findByToken("valid-refresh-token") } returns Optional.of(testRefreshToken)
            every { jwtTokenProvider.getUserIdFromToken("valid-refresh-token") } returns 1L
            every { userRepository.findById(1L) } returns Optional.of(testUser)
            every { jwtTokenProvider.generateAccessToken(any(), any()) } returns "new-access-token"
            every { jwtTokenProvider.generateRefreshToken(any()) } returns "new-refresh-token"
            every { refreshTokenRepository.save(any()) } returns testRefreshToken
            every { userMapper.toResponse(any()) } returns testUserResponse

            // When
            val result = authService.refreshToken("valid-refresh-token", httpRequest)

            // Then
            Assertions.assertEquals("new-access-token", result.accessToken)
            Assertions.assertEquals("new-refresh-token", result.refreshToken)
        }

        @Test
        fun `should throw InvalidTokenException when token is invalid`() {
            // Given
            every { jwtTokenProvider.validateToken("invalid-token") } returns false

            // When/Then
            assertThrows<InvalidTokenException> {
                authService.refreshToken("invalid-token", httpRequest)
            }
        }

        @Test
        fun `should throw InvalidTokenException when token type is not refresh`() {
            // Given
            every { jwtTokenProvider.validateToken("access-token") } returns true
            every { jwtTokenProvider.getTokenType("access-token") } returns "access"

            // When/Then
            assertThrows<InvalidTokenException> {
                authService.refreshToken("access-token", httpRequest)
            }
        }

        @Test
        fun `should throw InvalidTokenException when token not found in database`() {
            // Given
            every { jwtTokenProvider.validateToken("unknown-token") } returns true
            every { jwtTokenProvider.getTokenType("unknown-token") } returns "refresh"
            every { refreshTokenRepository.findByToken("unknown-token") } returns Optional.empty()

            // When/Then
            assertThrows<InvalidTokenException> {
                authService.refreshToken("unknown-token", httpRequest)
            }
        }

        @Test
        fun `should throw TokenAlreadyUsedException when token is revoked`() {
            // Given
            val revokedToken = testRefreshToken.copy(revoked = true)
            every { jwtTokenProvider.validateToken("revoked-token") } returns true
            every { jwtTokenProvider.getTokenType("revoked-token") } returns "refresh"
            every { refreshTokenRepository.findByToken("revoked-token") } returns Optional.of(revokedToken)

            // When/Then
            assertThrows<TokenAlreadyUsedException> {
                authService.refreshToken("revoked-token", httpRequest)
            }
        }

        @Test
        fun `should throw TokenExpiredException when token is expired`() {
            // Given
            val expiredToken = RefreshToken(
                id = 1L,
                token = "expired-token",
                user = testUser,
                expiresAt = Instant.now().minus(Duration.ofDays(1)),
                revoked = false
            )
            every { jwtTokenProvider.validateToken("expired-token") } returns true
            every { jwtTokenProvider.getTokenType("expired-token") } returns "refresh"
            every { refreshTokenRepository.findByToken("expired-token") } returns Optional.of(expiredToken)

            // When/Then
            assertThrows<TokenExpiredException> {
                authService.refreshToken("expired-token", httpRequest)
            }
        }

        @Test
        fun `should throw UserNotActiveException when user is inactive during refresh`() {
            // Given
            val inactiveUser = testUser.copy(isActive = false)
            every { jwtTokenProvider.validateToken("valid-refresh-token") } returns true
            every { jwtTokenProvider.getTokenType("valid-refresh-token") } returns "refresh"
            every { refreshTokenRepository.findByToken("valid-refresh-token") } returns Optional.of(testRefreshToken)
            every { jwtTokenProvider.getUserIdFromToken("valid-refresh-token") } returns 1L
            every { userRepository.findById(1L) } returns Optional.of(inactiveUser)

            // When/Then
            assertThrows<UserNotActiveException> {
                authService.refreshToken("valid-refresh-token", httpRequest)
            }
        }
    }

    @Nested
    @DisplayName("logout tests")
    inner class LogoutTests {

        @Test
        fun `should logout successfully`() {
            // Given
            every { refreshTokenRepository.findByToken("valid-refresh-token") } returns Optional.of(testRefreshToken)
            every { refreshTokenRepository.save(any()) } returns testRefreshToken

            // When
            authService.logout("valid-refresh-token")

            // Then
            verify { refreshTokenRepository.save(match { it.revoked }) }
        }

        @Test
        fun `should throw InvalidTokenException when logout with unknown token`() {
            // Given
            every { refreshTokenRepository.findByToken("unknown-token") } returns Optional.empty()

            // When/Then
            assertThrows<InvalidTokenException> {
                authService.logout("unknown-token")
            }
        }
    }

    @Nested
    @DisplayName("logoutAll tests")
    inner class LogoutAllTests {

        @Test
        fun `should revoke all user tokens`() {
            // Given
            every { refreshTokenRepository.revokeAllUserTokens(1L, any()) } just runs

            // When
            authService.logoutAll(1L)

            // Then
            verify { refreshTokenRepository.revokeAllUserTokens(1L, any()) }
        }
    }
}
