package com.mudhut.software.kasisira.security

import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component
import javax.crypto.SecretKey
import java.util.*

@Component
class JwtTokenProvider {

    private val logger = LoggerFactory.getLogger(JwtTokenProvider::class.java)

    @Value("\${app.jwt.secret}")
    private lateinit var jwtSecret: String

    @Value("\${app.jwt.access-token-expiration-ms}")
    private var accessTokenExpirationMs: Long = 0

    @Value("\${app.jwt.refresh-token-expiration-ms}")
    private var refreshTokenExpirationMs: Long = 0

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

        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .claim("type", "access")
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(getSigningKey())
            .compact()
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
        } catch (ex: SecurityException) {
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
