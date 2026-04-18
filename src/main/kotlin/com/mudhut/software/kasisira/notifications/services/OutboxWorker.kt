package com.mudhut.software.kasisira.notifications.services

import com.mudhut.software.kasisira.notifications.entities.OutboxStatus
import com.mudhut.software.kasisira.notifications.repositories.OutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant

/**
 * Scheduled drainer for the notifications outbox.
 *
 * Each tick picks up to [BATCH_SIZE] PENDING rows whose notBefore has elapsed
 * and hands each one to [OutboxDispatcher] (which runs in its own
 * REQUIRES_NEW transaction). The fixed delay is configurable via
 * `notifications.outbox.fixed-delay-ms` (default 5000ms).
 */
@Component
class OutboxWorker(
    private val outboxRepository: OutboxRepository,
    private val dispatcher: OutboxDispatcher,
    private val clock: Clock
) {

    companion object {
        private const val BATCH_SIZE = 20
    }

    private val log = LoggerFactory.getLogger(OutboxWorker::class.java)

    @Scheduled(fixedDelayString = "\${notifications.outbox.fixed-delay-ms:5000}")
    fun drain() {
        val now = Instant.now(clock)
        val due = outboxRepository.findDue(OutboxStatus.PENDING, now, PageRequest.of(0, BATCH_SIZE))
        if (due.isEmpty()) return
        log.debug("Outbox draining {} due rows", due.size)
        for (row in due) {
            dispatcher.dispatch(row)
        }
    }
}
