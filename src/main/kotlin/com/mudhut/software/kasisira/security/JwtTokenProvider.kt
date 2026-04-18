package com.mudhut.software.kasisira.security

import com.mudhut.software.kasisira.profiles.entities.RefreshToken
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.repositories.RefreshTokenRepository
import com.mudhut.software.kasisira.profiles.repositories.UserRoleRepository
import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import javax.crypto.SecretKey
import java.time.Instant
import java.util.*

/**
 * Simple value carrier for the access + refresh token pair returned by
 * [JwtTokenProvider.issueTokens]. Keeps the non-HTTP-aware phone OTP flow
 * from having to juggle two return values.
 */
data class TokenPair(val accessToken: String, val refreshToken: String)

@Component
class JwtTokenProvider {

    private val logger = LoggerFactory.getLogger(JwtTokenProvider::class.java)

    @Value("\${app.jwt.secret}")
    private lateinit var jwtSecret: String

    @Value("\${app.jwt.access-token-expiration-ms}")
    private var accessTokenExpirationMs: Long = 0

    @Value("\${app.jwt.refresh-token-expiration-ms}")
    private var refreshTokenExpirationMs: Long = 0

    // Queried once at token issue time only (login / refresh / OAuth success).
    // JwtAuthenticationFilter does NOT call generateAccessToken — it only validates/parses
    // incoming tokens — so this DB hit stays off the request-validation hot path.
    @Autowired
    private lateinit var userRoleRepository: UserRoleRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    private fun getSigningKey(): SecretKey {
        return Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }

    fun generateAccessToken(authentication: Authentication): String {
        val userPrincipal = authentication.principal as UserPrincipal
        return generateAccessToken(userPrincipal.id, userPrincipal.email)
    }

    fun generateAccessToken(userId: Long, email: String): String {
        val now = Date()
        val expiryDate = Date(now.time + accessTokenExpirationMs)

        val roles = userRoleRepository.findAllByUserId(userId)
            .map { it.roleName.name }

        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("type", "access")
            .claim("roles", roles)
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(getSigningKey())
            .compact()
    }

    /**
     * Issues an access + refresh token pair for [user] and persists the refresh token in
     * [RefreshTokenRepository] so it can later be redeemed by the refresh flow. This helper
     * is for non-HTTP-aware callers (e.g. the phone OTP flow) that don't have access to
     * device info or IP address — those fields are persisted as null. HTTP-aware flows
     * (email/password login, OAuth success handler) continue to manage persistence
     * themselves so they can record the User-Agent / remote address.
     */
    fun issueTokens(user: User): TokenPair {
        val accessToken = generateAccessToken(user.id, user.email)
        val refreshToken = generateRefreshToken(user.id)

        val expiresAt = Instant.now().plusSeconds(refreshTokenExpirationMs / 1000)
        refreshTokenRepository.save(
            RefreshToken(
                token = refreshToken,
                user = user,
                expiresAt = expiresAt,
                deviceInfo = null,
                ipAddress = null
            )
        )

        return TokenPair(accessToken = accessToken, refreshToken = refreshToken)
    }

    fun generateRefreshToken(userId: Long): String {
        val now = Date()
        val expiryDate = Date(now.time + refreshTokenExpirationMs)

        return Jwts.builder()
            .subject(userId.toString())
            .claim("type", "refresh")
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(getSigningKey())
            .compact()
    }

    fun getUserIdFromToken(token: String): Long {
        val claims = Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .payload

        return claims.subject.toLong()
    }

    fun validateToken(token: String): Boolean {
        try {
            Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
            return true
        } catch (ex: SignatureException) {
            logger.error("Invalid JWT signature")
        } catch (ex: MalformedJwtException) {
            logger.error("Invalid JWT token")
        } catch (ex: ExpiredJwtException) {
            logger.error("Expired JWT token")
        } catch (ex: UnsupportedJwtException) {
            logger.error("Unsupported JWT token")
        } catch (ex: IllegalArgumentException) {
            logger.error("JWT claims string is empty")
        }
        return false
    }

    fun getTokenType(token: String): String? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .payload

            claims["type"] as? String
        } catch (ex: Exception) {
            null
        }
    }

    fun getRolesFromToken(token: String): List<String> {
        return try {
            val claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .payload

            @Suppress("UNCHECKED_CAST")
            (claims["roles"] as? List<String>) ?: emptyList()
        } catch (ex: Exception) {
            emptyList()
        }
    }

    fun getExpirationFromToken(token: String): Date? {
        return try {
            val claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .payload

            claims.expiration
        } catch (ex: Exception) {
            null
        }
    }
}
