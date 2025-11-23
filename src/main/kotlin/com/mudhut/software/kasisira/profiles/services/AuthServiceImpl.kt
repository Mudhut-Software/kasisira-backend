package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.entities.RefreshToken
import com.mudhut.software.kasisira.profiles.mappers.UserMapper
import com.mudhut.software.kasisira.profiles.models.request.LoginRequest
import com.mudhut.software.kasisira.profiles.models.response.TokenResponse
import com.mudhut.software.kasisira.profiles.repositories.RefreshTokenRepository
import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.security.JwtTokenProvider
import com.mudhut.software.kasisira.utils.exceptions.*
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class AuthServiceImpl : AuthService {

    @Autowired
    private lateinit var authenticationManager: AuthenticationManager

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Autowired
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @Autowired
    private lateinit var userMapper: UserMapper

    @Value("\${app.jwt.access-token-expiration-ms}")
    private var accessTokenExpirationMs: Long = 0

    @Value("\${app.jwt.refresh-token-expiration-ms}")
    private var refreshTokenExpirationMs: Long = 0

    override fun login(request: LoginRequest, httpRequest: HttpServletRequest): TokenResponse {
        // Find user by email or username
        val user = userRepository.findByEmail(request.email)
            .orElseGet {
                userRepository.findByUsername(request.email)
                    .orElseThrow { UserNotFoundException("User not found with email or username: ${request.email}") }
            }

        // Check if user is active
        if (!user.isActive) {
            throw UserNotActiveException("User account is not active. Please verify your email.")
        }

        // Check if user is enabled
        if (!user.isEnabled) {
            throw UserNotActiveException("User account is disabled")
        }

        // Check if user has email verified (for local auth)
        if (user.isLocalAuth && !user.emailVerified) {
            throw EmailNotVerifiedException("Please verify your email address before logging in")
        }

        // Authenticate user
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(
                user.email,
                request.password
            )
        )

        SecurityContextHolder.getContext().authentication = authentication

        // Generate tokens
        val accessToken = jwtTokenProvider.generateAccessToken(user.id, user.email)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)

        // Save refresh token
        saveRefreshToken(user.id, refreshToken, httpRequest)

        // Update last login
        val updatedUser = user.copy(lastLogin = LocalDateTime.now())
        userRepository.save(updatedUser)

        return TokenResponse(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresIn = accessTokenExpirationMs / 1000,
            user = userMapper.toResponse(updatedUser)
        )
    }

    override fun refreshToken(refreshToken: String, httpRequest: HttpServletRequest): TokenResponse {
        // Validate refresh token
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw InvalidTokenException("Invalid refresh token")
        }

        // Check token type
        val tokenType = jwtTokenProvider.getTokenType(refreshToken)
        if (tokenType != "refresh") {
            throw InvalidTokenException("Invalid token type. Expected refresh token")
        }

        // Find refresh token in database
        val storedToken = refreshTokenRepository.findByToken(refreshToken)
            .orElseThrow { InvalidTokenException("Refresh token not found") }

        // Check if token is valid
        if (!storedToken.isValid()) {
            if (storedToken.revoked) {
                throw TokenAlreadyUsedException("Refresh token has been revoked")
            }
            if (storedToken.isExpired()) {
                throw TokenExpiredException("Refresh token has expired")
            }
        }

        // Get user from token
        val userId = jwtTokenProvider.getUserIdFromToken(refreshToken)
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found") }

        // Check if user is still active
        if (!user.isActive || !user.isEnabled) {
            throw UserNotActiveException("User account is not active")
        }

        // Generate new tokens
        val newAccessToken = jwtTokenProvider.generateAccessToken(user.id, user.email)
        val newRefreshToken = jwtTokenProvider.generateRefreshToken(user.id)

        // Revoke old refresh token
        storedToken.revoked = true
        storedToken.revokedAt = LocalDateTime.now()
        refreshTokenRepository.save(storedToken)

        // Save new refresh token
        saveRefreshToken(user.id, newRefreshToken, httpRequest)

        return TokenResponse(
            accessToken = newAccessToken,
            refreshToken = newRefreshToken,
            expiresIn = accessTokenExpirationMs / 1000,
            user = userMapper.toResponse(user)
        )
    }

    override fun logout(refreshToken: String) {
        val storedToken = refreshTokenRepository.findByToken(refreshToken)
            .orElseThrow { InvalidTokenException("Refresh token not found") }

        storedToken.revoked = true
        storedToken.revokedAt = LocalDateTime.now()
        refreshTokenRepository.save(storedToken)
    }

    override fun logoutAll(userId: Long) {
        refreshTokenRepository.revokeAllUserTokens(userId, LocalDateTime.now())
    }

    private fun saveRefreshToken(userId: Long, token: String, request: HttpServletRequest) {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User not found") }

        val expiresAt = LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000)

        val refreshToken = RefreshToken(
            token = token,
            user = user,
            expiresAt = expiresAt,
            deviceInfo = request.getHeader("User-Agent"),
            ipAddress = request.remoteAddr
        )

        refreshTokenRepository.save(refreshToken)
    }
}
