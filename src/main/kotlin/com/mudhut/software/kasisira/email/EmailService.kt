package com.mudhut.software.kasisira.email

interface EmailService {

    fun sendVerificationEmail(to: String, username: String, token: String)

    fun sendPasswordResetEmail(to: String, username: String, token: String)

    fun sendWelcomeEmail(to: String, username: String)

    /**
     * Renders the Thymeleaf template resolved under `email/{template}` with the
     * provided variables and sends the resulting HTML email to [to].
     *
     * Used by the notifications outbox dispatcher so email sends can be enqueued
     * as generic (template, variables) pairs rather than calls to channel-specific
     * helpers.
     */
    fun sendTemplate(to: String, template: String, variables: Map<String, Any>)
}
