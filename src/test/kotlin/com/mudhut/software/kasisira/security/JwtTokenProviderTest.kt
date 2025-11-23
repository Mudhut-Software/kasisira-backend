package com.mudhut.software.kasisira.security

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.springframework.test.util.ReflectionTestUtils
import java.util.*

class JwtTokenProviderTest {

    private lateinit var jwtTokenProvider: JwtTokenProvider

    private val testSecret = "myTestSecretKeyThatIsAtLeast256BitsLongForHS256Algorithm!"
    private val accessTokenExpirationMs = 3600000L // 1 hour
    private val refreshTokenExpirationMs = 604800000L // 7 days

    @BeforeEach
    fun setUp() {
        jwtTokenProvider = JwtTokenProvider()

        // Set private fields using reflection
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", testSecret)
        ReflectionTestUtils.setField(jwtTokenProvider, "accessTokenExpirationMs", accessTokenExpirationMs)
        ReflectionTestUtils.setField(jwtTokenProvider, "refreshTokenExpirationMs", refreshTokenExpirationMs)
    }

    @Nested
    @DisplayName("generateAccessToken tests")
    inner class GenerateAccessTokenTests {

        @Test
        fun `should generate valid access token`() {
            // When
            val token = jwtTokenProvider.generateAccessToken(1L, "test@example.com")

            // Then
            assertNotNull(token)
            assertTrue(token.isNotEmpty())
        }

        @Test
        fun `should include user id in token`() {
            // Given
            val userId = 123L
            val email = "test@example.com"

            // When
            val token = jwtTokenProvider.generateAccessToken(userId, email)
            val extractedUserId = jwtTokenProvider.getUserIdFromToken(token)

            // Then
            assertEquals(userId, extractedUserId)
        }

        @Test
        fun `should set token type as access`() {
            // When
            val token = jwtTokenProvider.generateAccessToken(1L, "test@example.com")
            val tokenType = jwtTokenProvider.getTokenType(token)

            // Then
            assertEquals("access", tokenType)
        }

        @Test
        fun `should set correct expiration time`() {
            // Given
            val beforeGeneration = Date()

            // When
            val token = jwtTokenProvider.generateAccessToken(1L, "test@example.com")
            val expiration = jwtTokenProvider.getExpirationFromToken(token)

            // Then
            assertNotNull(expiration)
            val expectedExpiration = Date(beforeGeneration.time + accessTokenExpirationMs)
            // Allow 5 second tolerance
            assertTrue(kotlin.math.abs(expiration!!.time - expectedExpiration.time) < 5000)
        }
    }

    @Nested
    @DisplayName("generateRefreshToken tests")
    inner class GenerateRefreshTokenTests {

        @Test
        fun `should generate valid refresh token`() {
            // When
            val token = jwtTokenProvider.generateRefreshToken(1L)

            // Then
            assertNotNull(token)
            assertTrue(token.isNotEmpty())
        }

        @Test
        fun `should set token type as refresh`() {
            // When
            val token = jwtTokenProvider.generateRefreshToken(1L)
            val tokenType = jwtTokenProvider.getTokenType(token)

            // Then
            assertEquals("refresh", tokenType)
        }

        @Test
        fun `should include user id in refresh token`() {
            // Given
            val userId = 456L

            // When
            val token = jwtTokenProvider.generateRefreshToken(userId)
            val extractedUserId = jwtTokenProvider.getUserIdFromToken(token)

            // Then
            assertEquals(userId, extractedUserId)
        }
    }

    @Nested
    @DisplayName("validateToken tests")
    inner class ValidateTokenTests {

        @Test
        fun `should return true for valid access token`() {
            // Given
            val token = jwtTokenProvider.generateAccessToken(1L, "test@example.com")

            // When
            val isValid = jwtTokenProvider.validateToken(token)

            // Then
            assertTrue(isValid)
        }

        @Test
        fun `should return true for valid refresh token`() {
            // Given
            val token = jwtTokenProvider.generateRefreshToken(1L)

            // When
            val isValid = jwtTokenProvider.validateToken(token)

            // Then
            assertTrue(isValid)
        }

        @Test
        fun `should return false for malformed token`() {
            // Given
            val malformedToken = "not.a.valid.jwt.token"

            // When
            val isValid = jwtTokenProvider.validateToken(malformedToken)

            // Then
            assertFalse(isValid)
        }

        @Test
        fun `should return false for empty token`() {
            // When
            val isValid = jwtTokenProvider.validateToken("")

            // Then
            assertFalse(isValid)
        }

        @Test
        fun `should return false for token signed with different key`() {
            // Given - create a token with different secret
            val differentSecret = "aDifferentSecretKeyThatIsAtLeast256BitsLongForHS256Algo!"
            val differentKey = Keys.hmacShaKeyFor(differentSecret.toByteArray())

            val token = Jwts.builder()
                .subject("1")
                .claim("type", "access")
                .issuedAt(Date())
                .expiration(Date(System.currentTimeMillis() + 3600000))
                .signWith(differentKey)
                .compact()

            // When
            val isValid = jwtTokenProvider.validateToken(token)

            // Then
            assertFalse(isValid)
        }

        @Test
        fun `should return false for expired token`() {
            // Given - create an expired token
            val signingKey = Keys.hmacShaKeyFor(testSecret.toByteArray())
            val expiredToken = Jwts.builder()
                .subject("1")
                .claim("type", "access")
                .issuedAt(Date(System.currentTimeMillis() - 7200000)) // 2 hours ago
                .expiration(Date(System.currentTimeMillis() - 3600000)) // 1 hour ago
                .signWith(signingKey)
                .compact()

            // When
            val isValid = jwtTokenProvider.validateToken(expiredToken)

            // Then
            assertFalse(isValid)
        }
    }

    @Nested
    @DisplayName("getUserIdFromToken tests")
    inner class GetUserIdFromTokenTests {

        @Test
        fun `should extract user id from access token`() {
            // Given
            val userId = 789L
            val token = jwtTokenProvider.generateAccessToken(userId, "test@example.com")

            // When
            val extractedId = jwtTokenProvider.getUserIdFromToken(token)

            // Then
            assertEquals(userId, extractedId)
        }

        @Test
        fun `should extract user id from refresh token`() {
            // Given
            val userId = 101112L
            val token = jwtTokenProvider.generateRefreshToken(userId)

            // When
            val extractedId = jwtTokenProvider.getUserIdFromToken(token)

            // Then
            assertEquals(userId, extractedId)
        }
    }

    @Nested
    @DisplayName("getTokenType tests")
    inner class GetTokenTypeTests {

        @Test
        fun `should return access for access token`() {
            // Given
            val token = jwtTokenProvider.generateAccessToken(1L, "test@example.com")

            // When
            val tokenType = jwtTokenProvider.getTokenType(token)

            // Then
            assertEquals("access", tokenType)
        }

        @Test
        fun `should return refresh for refresh token`() {
            // Given
            val token = jwtTokenProvider.generateRefreshToken(1L)

            // When
            val tokenType = jwtTokenProvider.getTokenType(token)

            // Then
            assertEquals("refresh", tokenType)
        }

        @Test
        fun `should return null for invalid token`() {
            // When
            val tokenType = jwtTokenProvider.getTokenType("invalid-token")

            // Then
            assertNull(tokenType)
        }
    }

    @Nested
    @DisplayName("getExpirationFromToken tests")
    inner class GetExpirationFromTokenTests {

        @Test
        fun `should return expiration date for valid token`() {
            // Given
            val token = jwtTokenProvider.generateAccessToken(1L, "test@example.com")

            // When
            val expiration = jwtTokenProvider.getExpirationFromToken(token)

            // Then
            assertNotNull(expiration)
            assertTrue(expiration!!.after(Date()))
        }

        @Test
        fun `should return null for invalid token`() {
            // When
            val expiration = jwtTokenProvider.getExpirationFromToken("invalid-token")

            // Then
            assertNull(expiration)
        }
    }
}
