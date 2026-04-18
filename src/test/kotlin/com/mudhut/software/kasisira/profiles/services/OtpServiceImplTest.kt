package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.notifications.sms.SmsSender
import com.mudhut.software.kasisira.profiles.entities.OtpChallenge
import com.mudhut.software.kasisira.profiles.entities.OtpPurpose
import com.mudhut.software.kasisira.profiles.repositories.OtpChallengeRepository
import com.mudhut.software.kasisira.utils.exceptions.InvalidOtpException
import com.mudhut.software.kasisira.utils.exceptions.RateLimitedException
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@ExtendWith(MockKExtension::class)
class OtpServiceImplTest {

    @MockK
    private lateinit var otpChallengeRepository: OtpChallengeRepository

    @MockK
    private lateinit var smsSender: SmsSender

    @MockK
    private lateinit var passwordEncoder: PasswordEncoder

    private lateinit var clock: Clock
    private lateinit var otpService: OtpServiceImpl

    private val fixedInstant: Instant = Instant.parse("2026-04-17T10:00:00Z")
    private val phone = "+256700123456"

    @BeforeEach
    fun setUp() {
        clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
        otpService = OtpServiceImpl(
            otpChallengeRepository = otpChallengeRepository,
            smsSender = smsSender,
            passwordEncoder = passwordEncoder,
            clock = clock
        )
    }

    @Test
    fun `requestOtp creates a challenge, hashes code, sends SMS`() {
        // Given
        val challengeSlot = slot<OtpChallenge>()
        val smsBodySlot = slot<String>()
        every { otpChallengeRepository.countRecent(eq(phone), any()) } returns 0L
        every { passwordEncoder.encode(any()) } answers { "hashed::${firstArg<String>()}" }
        every { otpChallengeRepository.save(capture(challengeSlot)) } answers { firstArg() }
        every { smsSender.send(eq(phone), capture(smsBodySlot)) } just runs

        // When
        otpService.requestOtp(phone, OtpPurpose.LOGIN)

        // Then
        val saved = challengeSlot.captured
        Assertions.assertEquals(phone, saved.phoneNumber)
        Assertions.assertEquals(OtpPurpose.LOGIN, saved.purpose)
        Assertions.assertTrue(saved.codeHash.startsWith("hashed::"))
        // Code extracted from the hashed prefix
        val code = saved.codeHash.removePrefix("hashed::")
        Assertions.assertEquals(6, code.length)
        Assertions.assertTrue(code.all { it.isDigit() }, "Code must be all digits, got: $code")
        // Expiry is 10 minutes from fixed now
        val expectedExpiry = LocalDateTime.ofInstant(fixedInstant, ZoneOffset.UTC).plusMinutes(10)
        Assertions.assertEquals(expectedExpiry, saved.expiresAt)
        Assertions.assertEquals(0, saved.attempts)
        Assertions.assertNull(saved.consumedAt)

        // SMS body contains code and expiry
        val body = smsBodySlot.captured
        Assertions.assertTrue(body.contains(code), "SMS body should contain the code: $body")
        Assertions.assertTrue(body.contains("10 minutes"), "SMS body should mention expiry window: $body")

        verify(exactly = 1) { smsSender.send(phone, any()) }
        verify(exactly = 1) { otpChallengeRepository.save(any()) }
        verify(exactly = 1) { passwordEncoder.encode(any()) }
    }

    @Test
    fun `requestOtp throws RateLimitedException when more than 3 requests in the last hour`() {
        // Given — 4 recent requests exceeds the max of 3
        every { otpChallengeRepository.countRecent(eq(phone), any()) } returns 4L

        // When/Then
        assertThrows<RateLimitedException> {
            otpService.requestOtp(phone, OtpPurpose.LOGIN)
        }

        verify(exactly = 0) { otpChallengeRepository.save(any()) }
        verify(exactly = 0) { smsSender.send(any(), any()) }
    }

    @Test
    fun `verifyOtp returns true when code matches latest active challenge and marks consumed`() {
        // Given
        val now = LocalDateTime.ofInstant(fixedInstant, ZoneOffset.UTC)
        val challenge = OtpChallenge(
            id = 42L,
            phoneNumber = phone,
            codeHash = "bcrypt-hash",
            purpose = OtpPurpose.LOGIN,
            expiresAt = now.plusMinutes(5),
            consumedAt = null,
            attempts = 0
        )
        every { otpChallengeRepository.findLatestActive(phone, OtpPurpose.LOGIN, any()) } returns listOf(challenge)
        every { passwordEncoder.matches("123456", "bcrypt-hash") } returns true
        val saveSlot = slot<OtpChallenge>()
        every { otpChallengeRepository.save(capture(saveSlot)) } answers { firstArg() }

        // When
        val result = otpService.verifyOtp(phone, "123456", OtpPurpose.LOGIN)

        // Then
        Assertions.assertTrue(result)
        Assertions.assertNotNull(saveSlot.captured.consumedAt)
        Assertions.assertEquals(now, saveSlot.captured.consumedAt)
    }

    @Test
    fun `verifyOtp throws InvalidOtpException when no active challenge exists`() {
        // Given
        every {
            otpChallengeRepository.findLatestActive(phone, OtpPurpose.LOGIN, any())
        } returns emptyList()

        // When/Then
        assertThrows<InvalidOtpException> {
            otpService.verifyOtp(phone, "123456", OtpPurpose.LOGIN)
        }
    }

    @Test
    fun `verifyOtp increments attempts and throws on wrong code`() {
        // Given
        val now = LocalDateTime.ofInstant(fixedInstant, ZoneOffset.UTC)
        val challenge = OtpChallenge(
            id = 42L,
            phoneNumber = phone,
            codeHash = "bcrypt-hash",
            purpose = OtpPurpose.LOGIN,
            expiresAt = now.plusMinutes(5),
            consumedAt = null,
            attempts = 1
        )
        every { otpChallengeRepository.findLatestActive(phone, OtpPurpose.LOGIN, any()) } returns listOf(challenge)
        every { passwordEncoder.matches("000000", "bcrypt-hash") } returns false
        val saveSlot = slot<OtpChallenge>()
        every { otpChallengeRepository.save(capture(saveSlot)) } answers { firstArg() }

        // When/Then
        assertThrows<InvalidOtpException> {
            otpService.verifyOtp(phone, "000000", OtpPurpose.LOGIN)
        }

        Assertions.assertEquals(2, saveSlot.captured.attempts)
        Assertions.assertNull(saveSlot.captured.consumedAt)
    }

    @Test
    fun `verifyOtp throws locked exception when attempts already maxed out`() {
        // Given — already at max attempts, should NOT increment further
        val now = LocalDateTime.ofInstant(fixedInstant, ZoneOffset.UTC)
        val challenge = OtpChallenge(
            id = 42L,
            phoneNumber = phone,
            codeHash = "bcrypt-hash",
            purpose = OtpPurpose.LOGIN,
            expiresAt = now.plusMinutes(5),
            consumedAt = null,
            attempts = 5
        )
        every { otpChallengeRepository.findLatestActive(phone, OtpPurpose.LOGIN, any()) } returns listOf(challenge)

        // When/Then
        val ex = assertThrows<InvalidOtpException> {
            otpService.verifyOtp(phone, "111111", OtpPurpose.LOGIN)
        }
        Assertions.assertTrue(
            ex.message!!.contains("locked", ignoreCase = true),
            "Expected lockout message, got: ${ex.message}"
        )
        verify(exactly = 0) { otpChallengeRepository.save(any()) }
        verify(exactly = 0) { passwordEncoder.matches(any(), any()) }
    }
}
