package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.notifications.services.NotificationService
import com.mudhut.software.kasisira.profiles.entities.TokenType
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.entities.VerificationToken
import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.profiles.repositories.VerificationTokenRepository
import com.mudhut.software.kasisira.utils.exceptions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.*

@Service
@Transactional
class VerificationServiceImpl : VerificationService {

    @Autowired
    private lateinit var verificationTokenRepository: VerificationTokenRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var notificationService: NotificationService

    companion object {
        private const val VERIFICATION_TOKEN_EXPIRY_HOURS = 24L
        private const val PASSWORD_RESET_TOKEN_EXPIRY_HOURS = 24L
    }

    override fun createVerificationToken(user: User, tokenType: TokenType): String {
        // Delete any existing tokens of this type for the user
        verificationTokenRepository.deleteByUserAndTokenType(user, tokenType)

        // Generate a unique token
        val token = generateToken()

        // Calculate expiry time based on token type
        val expiryHours = when (tokenType) {
            TokenType.EMAIL_VERIFICATION -> VERIFICATION_TOKEN_EXPIRY_HOURS
            TokenType.PASSWORD_RESET -> PASSWORD_RESET_TOKEN_EXPIRY_HOURS
        }

        // Create and save the token
        val verificationToken = VerificationToken(
            token = token,
            user = user,
            tokenType = tokenType,
            expiresAt = Instant.now().plus(Duration.ofHours(expiryHours))
        )

        verificationTokenRepository.save(verificationToken)

        return token
    }

    override fun verifyToken(token: String, tokenType: TokenType): User {
        val verificationToken = verificationTokenRepository.findByToken(token)
            .orElseThrow { InvalidTokenException("Invalid or expired token") }

        // Verify token type matches
        if (verificationToken.tokenType != tokenType) {
            throw InvalidTokenException("Invalid token type")
        }

        // Check if token is valid
        if (!verificationToken.isValid()) {
            if (verificationToken.isUsed) {
                throw TokenAlreadyUsedException("Token has already been used")
            }
            if (verificationToken.isExpired()) {
                throw TokenExpiredException("Token has expired")
            }
        }

        // Mark token as used
        verificationToken.isUsed = true
        verificationToken.usedAt = Instant.now()
        verificationTokenRepository.save(verificationToken)

        return verificationToken.user
    }

    override fun resendVerificationEmail(email: String) {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UserNotFoundException("User with email $email not found") }

        // Check if user is already verified
        if (user.emailVerified) {
            throw EmailNotVerifiedException("Email is already verified")
        }

        // Create new verification token
        val token = createVerificationToken(user, TokenType.EMAIL_VERIFICATION)

        // Enqueue verification email via the outbox so delivery happens
        // asynchronously with retry/backoff. Runs inside this @Transactional
        // method so the outbox row commits atomically with the new token row.
        notificationService.enqueueEmail(
            toEmail = user.email,
            template = "verification",
            variables = mapOf(
                "username" to user.username,
                "token" to token
            )
        )
    }

    override fun deleteExpiredTokens() {
        verificationTokenRepository.deleteByExpiresAtBeforeAndIsUsedTrue(Instant.now())
    }

    override fun invalidateUserTokens(userId: Long, tokenType: TokenType) {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User with id $userId not found") }

        verificationTokenRepository.deleteByUserAndTokenType(user, tokenType)
    }

    private fun generateToken(): String {
        return UUID.randomUUID().toString()
    }
}
