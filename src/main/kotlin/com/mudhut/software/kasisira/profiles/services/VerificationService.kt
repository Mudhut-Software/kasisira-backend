package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.entities.TokenType
import com.mudhut.software.kasisira.profiles.entities.User

interface VerificationService {

    fun createVerificationToken(user: User, tokenType: TokenType = TokenType.EMAIL_VERIFICATION): String

    fun verifyToken(token: String, tokenType: TokenType = TokenType.EMAIL_VERIFICATION): User

    fun resendVerificationEmail(email: String)

    fun deleteExpiredTokens()

    fun invalidateUserTokens(userId: Long, tokenType: TokenType)
}
