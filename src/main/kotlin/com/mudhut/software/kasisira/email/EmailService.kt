package com.mudhut.software.kasisira.email

interface EmailService {

    fun sendVerificationEmail(to: String, username: String, token: String)

    fun sendPasswordResetEmail(to: String, username: String, token: String)

    fun sendWelcomeEmail(to: String, username: String)
}
