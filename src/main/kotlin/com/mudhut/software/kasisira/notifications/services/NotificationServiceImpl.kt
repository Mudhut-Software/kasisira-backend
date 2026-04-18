package com.mudhut.software.kasisira.notifications.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.notifications.entities.Outbox
import com.mudhut.software.kasisira.notifications.entities.OutboxChannel
import com.mudhut.software.kasisira.notifications.repositories.OutboxRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * NotificationService implementation using the transactional outbox pattern.
 *
 * Every method uses [Propagation.MANDATORY]: the outbox row MUST be written inside
 * the caller's transaction so that the business write and the notification enqueue
 * commit atomically. If a caller forgot to open a transaction, MANDATORY fails
 * loudly — preferable to a silent split where the notification fires even though
 * the business state did not persist.
 */
@Service
class NotificationServiceImpl(
    private val outboxRepository: OutboxRepository,
    private val objectMapper: ObjectMapper
) : NotificationService {

    @Transactional(propagation = Propagation.MANDATORY)
    override fun enqueueSms(toE164: String, body: String) {
        val payload = objectMapper.writeValueAsString(mapOf("body" to body))
        outboxRepository.save(
            Outbox(
                channel = OutboxChannel.SMS,
                recipient = toE164,
                payload = payload
            )
        )
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun enqueueEmail(toEmail: String, template: String, variables: Map<String, Any>) {
        val payload = objectMapper.writeValueAsString(
            mapOf(
                "template" to template,
                "variables" to variables
            )
        )
        outboxRepository.save(
            Outbox(
                channel = OutboxChannel.EMAIL,
                recipient = toEmail,
                payload = payload
            )
        )
    }

    @Transactional(propagation = Propagation.MANDATORY)
    override fun enqueueInApp(userId: Long, template: String, payload: Map<String, Any>) {
        val serialized = objectMapper.writeValueAsString(
            mapOf(
                "template" to template,
                "payload" to payload
            )
        )
        outboxRepository.save(
            Outbox(
                channel = OutboxChannel.INAPP,
                recipient = userId.toString(),
                payload = serialized
            )
        )
    }
}
