package com.mudhut.software.kasisira.profiles.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.models.request.LoginRequest
import com.mudhut.software.kasisira.profiles.models.request.RefreshTokenRequest
import com.mudhut.software.kasisira.profiles.models.request.RegisterRequest
import com.mudhut.software.kasisira.profiles.models.response.TokenResponse
import com.mudhut.software.kasisira.profiles.models.response.UserResponse
import com.mudhut.software.kasisira.profiles.services.AuthService
import com.mudhut.software.kasisira.profiles.services.UserService
import com.mudhut.software.kasisira.profiles.services.VerificationService
import com.mudhut.software.kasisira.security.JwtAuthenticationFilter
import com.mudhut.software.kasisira.utils.exceptions.UserAlreadyExistsException
import com.mudhut.software.kasisira.utils.exceptions.UserNotFoundException
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDateTime

@WebMvcTest(
    controllers = [AuthController::class],
    excludeFilters = [
        ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = [JwtAuthenticationFilter::class])
    ]
)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var userService: UserService

    @MockkBean
    private lateinit var authService: AuthService

    @MockkBean
    private lateinit var verificationService: VerificationService

    private lateinit var testUserResponse: UserResponse
    private lateinit var testTokenResponse: TokenResponse

    @BeforeEach
    fun setUp() {
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
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now(),
            lastLogin = null
        )

        testTokenResponse = TokenResponse(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresIn = 3600,
            user = testUserResponse.copy(emailVerified = true, isActive = true)
        )
    }

    @Nested
    @DisplayName("POST /v1/auth/register")
    inner class RegisterTests {

        @Test
        fun `should register user successfully`() {
            // Given
            val request = RegisterRequest(
                username = "newuser",
                email = "newuser@example.com",
                password = "Password123!",
                phoneNumber = "+256701234567"
            )
            every { userService.registerUser(any()) } returns testUserResponse

            // When/Then
            mockMvc.perform(
                post("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.user").exists())
        }

        @Test
        fun `should return 409 when user already exists`() {
            // Given
            val request = RegisterRequest(
                username = "existinguser",
                email = "existing@example.com",
                password = "Password123!",
                phoneNumber = "+256701234567"
            )
            every { userService.registerUser(any()) } throws UserAlreadyExistsException("Email already registered")

            // When/Then
            mockMvc.perform(
                post("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
        }

        @Test
        fun `should return 400 when username is blank`() {
            // Given
            val request = mapOf(
                "username" to "",
                "email" to "test@example.com",
                "password" to "Password123!",
                "phoneNumber" to "+256701234567"
            )

            // When/Then
            mockMvc.perform(
                post("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when email is invalid`() {
            // Given
            val request = mapOf(
                "username" to "testuser",
                "email" to "invalid-email",
                "password" to "Password123!",
                "phoneNumber" to "+256701234567"
            )

            // When/Then
            mockMvc.perform(
                post("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when password is too short`() {
            // Given
            val request = mapOf(
                "username" to "testuser",
                "email" to "test@example.com",
                "password" to "short",
                "phoneNumber" to "+256701234567"
            )

            // When/Then
            mockMvc.perform(
                post("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `should return 400 when phone number is invalid`() {
            // Given
            val request = mapOf(
                "username" to "testuser",
                "email" to "test@example.com",
                "password" to "Password123!",
                "phoneNumber" to "123" // Invalid - no + prefix
            )

            // When/Then
            mockMvc.perform(
                post("/v1/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("POST /v1/auth/login")
    inner class LoginTests {

        @Test
        fun `should login successfully`() {
            // Given
            val request = LoginRequest(
                email = "test@example.com",
                password = "Password123!"
            )
            every { authService.login(any(), any()) } returns testTokenResponse

            // When/Then
            mockMvc.perform(
                post("/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.user").exists())
        }

        @Test
        fun `should return 404 when user not found`() {
            // Given
            val request = LoginRequest(
                email = "notfound@example.com",
                password = "Password123!"
            )
            every { authService.login(any(), any()) } throws UserNotFoundException("User not found")

            // When/Then
            mockMvc.perform(
                post("/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun `should return 400 when email is blank`() {
            // Given
            val request = mapOf(
                "email" to "",
                "password" to "Password123!"
            )

            // When/Then
            mockMvc.perform(
                post("/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("POST /v1/auth/refresh")
    inner class RefreshTokenTests {

        @Test
        fun `should refresh token successfully`() {
            // Given
            val request = RefreshTokenRequest(refreshToken = "valid-refresh-token")
            every { authService.refreshToken(any(), any()) } returns testTokenResponse

            // When/Then
            mockMvc.perform(
                post("/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
        }
    }

    @Nested
    @DisplayName("POST /v1/auth/logout")
    inner class LogoutTests {

        @Test
        fun `should logout successfully`() {
            // Given
            val request = RefreshTokenRequest(refreshToken = "valid-refresh-token")
            every { authService.logout(any()) } just runs

            // When/Then
            mockMvc.perform(
                post("/v1/auth/logout")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
        }
    }

    @Nested
    @DisplayName("GET /v1/auth/check-email-verified")
    inner class CheckEmailVerifiedTests {

        @Test
        fun `should return email verification status`() {
            // Given
            every { userService.findByEmail("test@example.com") } returns testUserResponse

            // When/Then
            mockMvc.perform(
                get("/v1/auth/check-email-verified")
                    .param("email", "test@example.com")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.emailVerified").exists())
                .andExpect(jsonPath("$.isActive").exists())
        }

        @Test
        fun `should return 404 when user not found`() {
            // Given
            every { userService.findByEmail("notfound@example.com") } returns null

            // When/Then
            mockMvc.perform(
                get("/v1/auth/check-email-verified")
                    .param("email", "notfound@example.com")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("POST /v1/auth/resend-verification")
    inner class ResendVerificationTests {

        @Test
        fun `should resend verification email successfully`() {
            // Given
            every { verificationService.resendVerificationEmail(any()) } just runs

            // When/Then
            mockMvc.perform(
                post("/v1/auth/resend-verification")
                    .param("email", "test@example.com")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
        }

        @Test
        fun `should return 404 when user not found for resend`() {
            // Given
            every { verificationService.resendVerificationEmail(any()) } throws NoSuchElementException("User not found")

            // When/Then
            mockMvc.perform(
                post("/v1/auth/resend-verification")
                    .param("email", "notfound@example.com")
            )
                .andExpect(status().isNotFound)
        }
    }
}
