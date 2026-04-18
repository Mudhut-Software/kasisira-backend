package com.mudhut.software.kasisira.notifications.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.email.EmailService
import com.mudhut.software.kasisira.notifications.entities.Outbox
import com.mudhut.software.kasisira.notifications.entities.OutboxChannel
import com.mudhut.software.kasisira.notifications.entities.OutboxStatus
import com.mudhut.software.kasisira.notifications.repositories.OutboxRepository
import com.mudhut.software.kasisira.notifications.sms.SmsSender
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@ExtendWith(MockKExtension::class)
class OutboxDispatcherTest {

    @MockK
    private lateinit var outboxRepository: OutboxRepository

    @MockK
    private lateinit var smsSender: SmsSender

    @MockK
    private lateinit var emailService: EmailService

    private val fixedInstant: Instant = Instant.parse("2026-04-17T10:00:00Z")
    private val clock: Clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)
    private val objectMapper = ObjectMapper()

    private lateinit var dispatcher: OutboxDispatcher

    @BeforeEach
    fun setUp() {
        dispatcher = OutboxDispatcher(
            outboxRepository = outboxRepository,
            smsSender = smsSender,
            emailService = emailService,
            objectMapper = objectMapper,
            clock = clock
        )
    }

    @Test
    fun `dispatch SMS row calls SmsSender and marks SENT`() {
        // Given
        val row = Outbox(
            id = 1L,
            channel = OutboxChannel.SMS,
            recipient = "+256700123456",
            payload = """{"body":"Your code is 123456"}""",
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        every { smsSender.send(any(), any()) } returns Unit
        val savedSlot = slot<Outbox>()
        every { outboxRepository.save(capture(savedSlot)) } answers { firstArg() }

        // When
        dispatcher.dispatch(row)

        // Then
        verify(exactly = 1) { smsSender.send("+256700123456", "Your code is 123456") }
        val saved = savedSlot.captured
        Assertions.assertEquals(OutboxStatus.SENT, saved.status)
        Assertions.assertEquals(LocalDateTime.now(clock), saved.processedAt)
        Assertions.assertNull(saved.lastError)
    }

    @Test
    fun `dispatch EMAIL row calls EmailService sendTemplate with template and variables`() {
        // Given
        val row = Outbox(
            id = 2L,
            channel = OutboxChannel.EMAIL,
            recipient = "user@example.com",
            payload = """{"template":"welcome","variables":{"username":"Phillip","appName":"Kasisira"}}""",
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        val varsSlot = slot<Map<String, Any>>()
        every { emailService.sendTemplate(any(), any(), capture(varsSlot)) } returns Unit
        val savedSlot = slot<Outbox>()
        every { outboxRepository.save(capture(savedSlot)) } answers { firstArg() }

        // When
        dispatcher.dispatch(row)

        // Then
        verify(exactly = 1) { emailService.sendTemplate("user@example.com", "welcome", any()) }
        Assertions.assertEquals("Phillip", varsSlot.captured["username"])
        Assertions.assertEquals("Kasisira", varsSlot.captured["appName"])
        val saved = savedSlot.captured
        Assertions.assertEquals(OutboxStatus.SENT, saved.status)
        Assertions.assertEquals(LocalDateTime.now(clock), saved.processedAt)
        Assertions.assertNull(saved.lastError)
    }

    @Test
    fun `dispatch INAPP row marks SENT without calling SMS or email`() {
        // Given
        val row = Outbox(
            id = 3L,
            channel = OutboxChannel.INAPP,
            recipient = "42",
            payload = """{"template":"property_liked","payload":{}}""",
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        val savedSlot = slot<Outbox>()
        every { outboxRepository.save(capture(savedSlot)) } answers { firstArg() }

        // When
        dispatcher.dispatch(row)

        // Then
        verify(exactly = 0) { smsSender.send(any(), any()) }
        verify(exactly = 0) { emailService.sendTemplate(any(), any(), any()) }
        val saved = savedSlot.captured
        Assertions.assertEquals(OutboxStatus.SENT, saved.status)
        Assertions.assertEquals(LocalDateTime.now(clock), saved.processedAt)
    }

    @Test
    fun `dispatch failure increments attempts sets notBefore to backoff keeps PENDING`() {
        // Given
        val row = Outbox(
            id = 4L,
            channel = OutboxChannel.SMS,
            recipient = "+256700123456",
            payload = """{"body":"hi"}""",
            status = OutboxStatus.PENDING,
            attempts = 0,
            notBefore = LocalDateTime.now(clock)
        )
        every { smsSender.send(any(), any()) } throws RuntimeException("upstream boom")
        val savedSlot = slot<Outbox>()
        every { outboxRepository.save(capture(savedSlot)) } answers { firstArg() }

        // When
        dispatcher.dispatch(row)

        // Then
        val saved = savedSlot.captured
        Assertions.assertEquals(1, saved.attempts)
        Assertions.assertEquals(OutboxStatus.PENDING, saved.status)
        Assertions.assertEquals("upstream boom", saved.lastError)
        // backoff = 30 seconds after 1st failure
        Assertions.assertEquals(LocalDateTime.now(clock).plusSeconds(30), saved.notBefore)
    }

    @Test
    fun `second failure applies 2 minute backoff`() {
        // Given — row already attempted once
        val row = Outbox(
            id = 5L,
            channel = OutboxChannel.SMS,
            recipient = "+256700123456",
            payload = """{"body":"hi"}""",
            status = OutboxStatus.PENDING,
            attempts = 1,
            notBefore = LocalDateTime.now(clock)
        )
        every { smsSender.send(any(), any()) } throws RuntimeException("still broken")
        val savedSlot = slot<Outbox>()
        every { outboxRepository.save(capture(savedSlot)) } answers { firstArg() }

        // When
        dispatcher.dispatch(row)

        // Then
        val saved = savedSlot.captured
        Assertions.assertEquals(2, saved.attempts)
        Assertions.assertEquals(OutboxStatus.PENDING, saved.status)
        // backoff = 2 minutes after 2nd failure
        Assertions.assertEquals(LocalDateTime.now(clock).plusMinutes(2), saved.notBefore)
    }

    @Test
    fun `third failure marks row FAILED`() {
        // Given — row already attempted twice
        val row = Outbox(
            id = 6L,
            channel = OutboxChannel.SMS,
            recipient = "+256700123456",
            payload = """{"body":"hi"}""",
            status = OutboxStatus.PENDING,
            attempts = 2,
            notBefore = LocalDateTime.now(clock)
        )
        every { smsSender.send(any(), any()) } throws RuntimeException("dead")
        val savedSlot = slot<Outbox>()
        every { outboxRepository.save(capture(savedSlot)) } answers { firstArg() }

        // When
        dispatcher.dispatch(row)

        // Then
        val saved = savedSlot.captured
        Assertions.assertEquals(3, saved.attempts)
        Assertions.assertEquals(OutboxStatus.FAILED, saved.status)
        Assertions.assertEquals("dead", saved.lastError)
    }

    @Test
    fun `failure with long exception message truncates lastError to 1000 chars`() {
        // Given
        val longMessage = "x".repeat(2000)
        val row = Outbox(
            id = 7L,
            channel = OutboxChannel.SMS,
            recipient = "+256700123456",
            payload = """{"body":"hi"}""",
            status = OutboxStatus.PENDING,
            attempts = 0
        )
        every { smsSender.send(any(), any()) } throws RuntimeException(longMessage)
        val savedSlot = slot<Outbox>()
        every { outboxRepository.save(capture(savedSlot)) } answers { firstArg() }

        // When
        dispatcher.dispatch(row)

        // Then
        val saved = savedSlot.captured
        Assertions.assertEquals(1000, saved.lastError?.length)
    }
}
