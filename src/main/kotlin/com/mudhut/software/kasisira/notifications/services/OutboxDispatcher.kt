package com.mudhut.software.kasisira.notifications.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.email.EmailService
import com.mudhut.software.kasisira.notifications.entities.Outbox
import com.mudhut.software.kasisira.notifications.entities.OutboxChannel
import com.mudhut.software.kasisira.notifications.entities.OutboxStatus
import com.mudhut.software.kasisira.notifications.repositories.OutboxRepository
import com.mudhut.software.kasisira.notifications.sms.SmsSender
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime

/**
 * Consumes a single [Outbox] row: deserializes the payload, calls the matching
 * channel sender, and writes the terminal state back to the row.
 *
 * Each dispatch runs in its own [Propagation.REQUIRES_NEW] transaction so a
 * failure for one row doesn't sink the whole batch the [OutboxWorker] is draining.
 *
 * Retry policy:
 *  - On success: status = SENT, processedAt = now, lastError cleared.
 *  - On failure: attempts incremented; after the 3rd failure the row is marked
 *    FAILED (no further dispatch attempts). Before that the row stays PENDING
 *    with notBefore pushed out by a fixed backoff schedule.
 */
@Service
class OutboxDispatcher(
    private val outboxRepository: OutboxRepository,
    private val smsSender: SmsSender,
    private val emailService: EmailService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock
) {

    companion object {
        private const val MAX_ATTEMPTS = 3
        private val BACKOFF: List<Duration> = listOf(
            Duration.ofSeconds(30),
            Duration.ofMinutes(2)
        )
        private const val MAX_ERROR_LENGTH = 1000
    }

    private val log = LoggerFactory.getLogger(OutboxDispatcher::class.java)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun dispatch(row: Outbox) {
        try {
            when (row.channel) {
                OutboxChannel.SMS -> {
                    val payload = deserialize(row.payload)
                    val body = payload["body"] as? String
                        ?: throw IllegalStateException("SMS payload missing 'body' for outbox id=${row.id}")
                    smsSender.send(row.recipient, body)
                }
                OutboxChannel.EMAIL -> {
                    val payload = deserialize(row.payload)
                    val template = payload["template"] as? String
                        ?: throw IllegalStateException("EMAIL payload missing 'template' for outbox id=${row.id}")
                    @Suppress("UNCHECKED_CAST")
                    val variables = (payload["variables"] as? Map<String, Any>) ?: emptyMap()
                    emailService.sendTemplate(row.recipient, template, variables)
                }
                OutboxChannel.INAPP -> {
                    // No real side effect in MVP — the presence of the row IS the notification.
                }
            }
            markSent(row)
        } catch (e: Exception) {
            markFailure(row, e)
        }
    }

    private fun markSent(row: Outbox) {
        row.status = OutboxStatus.SENT
        row.processedAt = LocalDateTime.now(clock)
        row.lastError = null
        outboxRepository.save(row)
    }

    private fun markFailure(row: Outbox, e: Exception) {
        val nextAttempts = row.attempts + 1
        row.attempts = nextAttempts
        row.lastError = e.message?.take(MAX_ERROR_LENGTH)

        if (nextAttempts >= MAX_ATTEMPTS) {
            row.status = OutboxStatus.FAILED
            log.warn(
                "Outbox row id={} channel={} FAILED after {} attempts: {}",
                row.id, row.channel, nextAttempts, e.message
            )
        } else {
            row.status = OutboxStatus.PENDING
            val backoff = BACKOFF[nextAttempts - 1]
            row.notBefore = LocalDateTime.now(clock).plus(backoff)
            log.info(
                "Outbox row id={} channel={} transient failure (attempt {}): {}. Retry at {}",
                row.id, row.channel, nextAttempts, e.message, row.notBefore
            )
        }
        outboxRepository.save(row)
    }

    private fun deserialize(payload: String): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(payload, Map::class.java) as Map<String, Any>
    }
}
