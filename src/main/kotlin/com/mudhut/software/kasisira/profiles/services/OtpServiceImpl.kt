package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.notifications.sms.SmsSender
import com.mudhut.software.kasisira.profiles.entities.OtpChallenge
import com.mudhut.software.kasisira.profiles.entities.OtpPurpose
import com.mudhut.software.kasisira.profiles.repositories.OtpChallengeRepository
import com.mudhut.software.kasisira.utils.exceptions.InvalidOtpException
import com.mudhut.software.kasisira.utils.exceptions.RateLimitedException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

/**
 * Default [OtpService] implementation.
 *
 * Task 11 wires SMS delivery directly through [SmsSender]. Task 15 refactors the
 * send path to go through the notifications outbox once it lands.
 */
@Service
class OtpServiceImpl(
    private val otpChallengeRepository: OtpChallengeRepository,
    private val smsSender: SmsSender,
    private val passwordEncoder: PasswordEncoder,
    private val clock: Clock
) : OtpService {

    private val secureRandom = SecureRandom()

    companion object {
        private const val CODE_LENGTH = 6
        private val EXPIRY: Duration = Duration.ofMinutes(10)
        private val RATE_WINDOW: Duration = Duration.ofHours(1)
        private const val RATE_MAX = 3
        private const val MAX_ATTEMPTS = 5
    }

    @Transactional
    override fun requestOtp(phoneNumberE164: String, purpose: OtpPurpose) {
        val now = LocalDateTime.now(clock)
        val windowStart = now.minus(RATE_WINDOW)

        val recentCount = otpChallengeRepository.countRecent(phoneNumberE164, windowStart)
        if (recentCount >= RATE_MAX) {
            throw RateLimitedException(
                "Too many OTP requests for this phone number. Try again later."
            )
        }

        val code = generateCode()
        val codeHash = passwordEncoder.encode(code)
            ?: throw IllegalStateException("PasswordEncoder returned null hash")
        val challenge = OtpChallenge(
            phoneNumber = phoneNumberE164,
            codeHash = codeHash,
            purpose = purpose,
            expiresAt = now.plus(EXPIRY),
            attempts = 0
        )
        otpChallengeRepository.save(challenge)

        smsSender.send(
            phoneNumberE164,
            "Your Kasisira verification code is $code. It expires in 10 minutes."
        )
    }

    @Transactional
    override fun verifyOtp(phoneNumberE164: String, code: String, purpose: OtpPurpose): Boolean {
        val now = LocalDateTime.now(clock)

        val challenge = otpChallengeRepository
            .findLatestActive(phoneNumberE164, purpose, now)
            .firstOrNull()
            ?: throw InvalidOtpException("No active OTP for this phone number and purpose.")

        if (challenge.attempts >= MAX_ATTEMPTS) {
            throw InvalidOtpException("OTP locked after too many attempts.")
        }

        if (!passwordEncoder.matches(code, challenge.codeHash)) {
            challenge.attempts += 1
            otpChallengeRepository.save(challenge)
            throw InvalidOtpException("Incorrect code.")
        }

        challenge.consumedAt = now
        otpChallengeRepository.save(challenge)
        return true
    }

    private fun generateCode(): String {
        val sb = StringBuilder(CODE_LENGTH)
        repeat(CODE_LENGTH) {
            sb.append(secureRandom.nextInt(10))
        }
        return sb.toString()
    }
}
