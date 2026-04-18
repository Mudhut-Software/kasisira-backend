package com.mudhut.software.kasisira.notifications.services

interface NotificationService {
    fun enqueueSms(toE164: String, body: String)
    fun enqueueEmail(toEmail: String, template: String, variables: Map<String, Any>)
    fun enqueueInApp(userId: Long, template: String, payload: Map<String, Any>)
}
