package com.mudhut.software.kasisira.notifications.sms

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Dev/test SMS sender that logs instead of sending. Selected by
 * notifications.sms.provider=noop (also the default when the property is absent).
 */
@Component
@ConditionalOnProperty(name = ["notifications.sms.provider"], havingValue = "noop", matchIfMissing = true)
class NoopSmsSender : SmsSender {
    private val log = LoggerFactory.getLogger(NoopSmsSender::class.java)

    override fun send(toE164: String, body: String) {
        log.info("[noop-sms] -> {}: {}", toE164, body)
    }
}
