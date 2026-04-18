package com.mudhut.software.kasisira.notifications.sms

/**
 * Sends an SMS to a phone number in E.164 format. Throws on failure; the caller
 * (typically the outbox dispatcher, added in Task 14) is responsible for retry
 * policy.
 */
interface SmsSender {
    fun send(toE164: String, body: String)
}
