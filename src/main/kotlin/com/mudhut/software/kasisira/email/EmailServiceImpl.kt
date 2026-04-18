package com.mudhut.software.kasisira.email

import jakarta.mail.MessagingException
import jakarta.mail.internet.MimeMessage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.mail.javamail.MimeMessageHelper
import org.springframework.stereotype.Service
import org.thymeleaf.TemplateEngine
import org.thymeleaf.context.Context

@Service
class EmailServiceImpl : EmailService {

    @Autowired
    private lateinit var mailSender: JavaMailSender

    @Autowired
    private lateinit var templateEngine: TemplateEngine

    @Value("\${app.frontend.url}")
    private lateinit var frontendUrl: String

    @Value("\${app.backend.url}")
    private lateinit var backendUrl: String

    @Value("\${spring.mail.username}")
    private lateinit var fromEmail: String

    @Value("\${app.name}")
    private lateinit var appName: String

    override fun sendVerificationEmail(to: String, username: String, token: String) {
        try {
            val context = Context()
            context.setVariable("username", username)
            context.setVariable("appName", appName)
            context.setVariable("verificationLink", "$backendUrl/api/v1/auth/verify-email?token=$token")
            context.setVariable("loginLink", "$frontendUrl/login")
            context.setVariable("mobileDeepLink", "kasisira://verify?token=$token")

            val htmlContent = templateEngine.process("email/verification", context)

            sendHtmlEmail(to, "Verify Your Email - $appName", htmlContent)
        } catch (e: Exception) {
            throw RuntimeException("Failed to send verification email", e)
        }
    }

    override fun sendPasswordResetEmail(to: String, username: String, token: String) {
        try {
            val context = Context()
            context.setVariable("username", username)
            context.setVariable("appName", appName)
            context.setVariable("resetLink", "$frontendUrl/reset-password?token=$token")
            context.setVariable("expiryHours", 24)

            val htmlContent = templateEngine.process("email/password-reset", context)

            sendHtmlEmail(to, "Reset Your Password - $appName", htmlContent)
        } catch (e: Exception) {
            throw RuntimeException("Failed to send password reset email", e)
        }
    }

    override fun sendWelcomeEmail(to: String, username: String) {
        try {
            val context = Context()
            context.setVariable("username", username)
            context.setVariable("appName", appName)
            context.setVariable("loginLink", "$frontendUrl/login")
            context.setVariable("supportEmail", fromEmail)

            val htmlContent = templateEngine.process("email/welcome", context)

            sendHtmlEmail(to, "Welcome to $appName!", htmlContent)
        } catch (e: Exception) {
            throw RuntimeException("Failed to send welcome email", e)
        }
    }

    override fun sendTemplate(to: String, template: String, variables: Map<String, Any>) {
        try {
            val context = Context()
            // Always expose appName — most templates reference it.
            context.setVariable("appName", appName)
            variables.forEach { (key, value) -> context.setVariable(key, value) }

            val htmlContent = templateEngine.process("email/$template", context)
            val subject = (variables["subject"] as? String) ?: defaultSubjectFor(template)
            sendHtmlEmail(to, subject, htmlContent)
        } catch (e: Exception) {
            throw RuntimeException("Failed to send templated email '$template' to $to", e)
        }
    }

    private fun defaultSubjectFor(template: String): String = when (template) {
        "verification" -> "Verify Your Email - $appName"
        "password-reset" -> "Reset Your Password - $appName"
        "welcome" -> "Welcome to $appName!"
        else -> appName
    }

    private fun sendHtmlEmail(to: String, subject: String, htmlContent: String) {
        try {
            val message: MimeMessage = mailSender.createMimeMessage()
            val helper = MimeMessageHelper(message, true, "UTF-8")

            helper.setFrom(fromEmail)
            helper.setTo(to)
            helper.setSubject(subject)
            helper.setText(htmlContent, true)

            mailSender.send(message)
        } catch (e: MessagingException) {
            throw RuntimeException("Failed to send email to $to", e)
        }
    }
}
