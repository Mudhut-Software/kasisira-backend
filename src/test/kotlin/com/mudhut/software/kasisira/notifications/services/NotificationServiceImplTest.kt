package com.mudhut.software.kasisira.notifications.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.notifications.entities.Outbox
import com.mudhut.software.kasisira.notifications.entities.OutboxChannel
import com.mudhut.software.kasisira.notifications.entities.OutboxStatus
import com.mudhut.software.kasisira.notifications.repositories.OutboxRepository
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class NotificationServiceImplTest {

    @MockK
    private lateinit var outboxRepository: OutboxRepository

    @MockK
    private lateinit var objectMapper: ObjectMapper

    private lateinit var notificationService: NotificationServiceImpl

    @BeforeEach
    fun setUp() {
        notificationService = NotificationServiceImpl(
            outboxRepository = outboxRepository,
            objectMapper = objectMapper
        )
    }

    @Test
    fun `enqueueSms writes a PENDING SMS outbox row with the body in the payload`() {
        // Given
        val phone = "+256700123456"
        val body = "Your code is 123456"
        val serialized = """{"body":"Your code is 123456"}"""
        val outboxSlot = slot<Outbox>()
        every { objectMapper.writeValueAsString(any()) } returns serialized
        every { outboxRepository.save(capture(outboxSlot)) } answers { firstArg() }

        // When
        notificationService.enqueueSms(phone, body)

        // Then
        val saved = outboxSlot.captured
        Assertions.assertEquals(OutboxChannel.SMS, saved.channel)
        Assertions.assertEquals(phone, saved.recipient)
        Assertions.assertEquals(OutboxStatus.PENDING, saved.status)
        Assertions.assertEquals(0, saved.attempts)
        Assertions.assertEquals(serialized, saved.payload)

        verify(exactly = 1) { outboxRepository.save(any()) }
        verify(exactly = 1) { objectMapper.writeValueAsString(any()) }
    }

    @Test
    fun `enqueueEmail writes a PENDING EMAIL outbox row with template and variables`() {
        // Given
        val email = "user@example.com"
        val template = "welcome"
        val variables = mapOf<String, Any>("name" to "Phillip", "code" to 123)
        val serialized = """{"template":"welcome","variables":{"name":"Phillip","code":123}}"""
        val outboxSlot = slot<Outbox>()
        every { objectMapper.writeValueAsString(any()) } returns serialized
        every { outboxRepository.save(capture(outboxSlot)) } answers { firstArg() }

        // When
        notificationService.enqueueEmail(email, template, variables)

        // Then
        val saved = outboxSlot.captured
        Assertions.assertEquals(OutboxChannel.EMAIL, saved.channel)
        Assertions.assertEquals(email, saved.recipient)
        Assertions.assertEquals(OutboxStatus.PENDING, saved.status)
        Assertions.assertEquals(0, saved.attempts)
        Assertions.assertEquals(serialized, saved.payload)

        verify(exactly = 1) { outboxRepository.save(any()) }
        verify(exactly = 1) { objectMapper.writeValueAsString(any()) }
    }

    @Test
    fun `enqueueInApp writes a PENDING INAPP outbox row, recipient = userId as string`() {
        // Given
        val userId = 42L
        val template = "property_liked"
        val payload = mapOf<String, Any>("propertyId" to 99L, "by" to "Alex")
        val serialized = """{"template":"property_liked","payload":{"propertyId":99,"by":"Alex"}}"""
        val outboxSlot = slot<Outbox>()
        every { objectMapper.writeValueAsString(any()) } returns serialized
        every { outboxRepository.save(capture(outboxSlot)) } answers { firstArg() }

        // When
        notificationService.enqueueInApp(userId, template, payload)

        // Then
        val saved = outboxSlot.captured
        Assertions.assertEquals(OutboxChannel.INAPP, saved.channel)
        Assertions.assertEquals("42", saved.recipient)
        Assertions.assertEquals(OutboxStatus.PENDING, saved.status)
        Assertions.assertEquals(0, saved.attempts)
        Assertions.assertEquals(serialized, saved.payload)

        verify(exactly = 1) { outboxRepository.save(any()) }
        verify(exactly = 1) { objectMapper.writeValueAsString(any()) }
    }
}
