package com.mudhut.software.kasisira.notifications.sms

import com.africastalking.AfricasTalking
import com.africastalking.SmsService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Production SMS sender using Africa's Talking.
 * Selected by notifications.sms.provider=africas-talking.
 *
 * Required environment variables:
 * - AT_USERNAME
 * - AT_API_KEY
 * - AT_SENDER_ID (optional; empty string falls back to AT's shared shortcode)
 */
@Component
@ConditionalOnProperty(name = ["notifications.sms.provider"], havingValue = "africas-talking")
class AfricasTalkingSmsSender(
    @Value("\${AT_USERNAME}") username: String,
    @Value("\${AT_API_KEY}") apiKey: String,
    @Value("\${AT_SENDER_ID:}") private val senderId: String
) : SmsSender {

    private val log = LoggerFactory.getLogger(AfricasTalkingSmsSender::class.java)
    private val sms: SmsService

    init {
        AfricasTalking.initialize(username, apiKey)
        sms = AfricasTalking.getService(AfricasTalking.SERVICE_SMS)
    }

    override fun send(toE164: String, body: String) {
        val from = senderId.takeIf { it.isNotBlank() }
        val recipients = try {
            sms.send(body, from, arrayOf(toE164), false)
        } catch (e: Exception) {
            log.warn("AT send threw for {}: {}", toE164, e.message)
            throw SmsDeliveryException("Africa's Talking send failed: ${e.message}")
        }
        val failed = recipients.filter { it.status != "Success" }
        if (failed.isNotEmpty()) {
            val reasons = failed.joinToString { "${it.number}:${it.status}" }
            log.warn("AT rejected {}: {}", toE164, reasons)
            throw SmsDeliveryException("Africa's Talking rejected: $reasons")
        }
    }
}

class SmsDeliveryException(message: String) : RuntimeException(message)
