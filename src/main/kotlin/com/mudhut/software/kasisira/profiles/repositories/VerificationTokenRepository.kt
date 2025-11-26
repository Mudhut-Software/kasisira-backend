package com.mudhut.software.kasisira.profiles.repositories

import com.mudhut.software.kasisira.profiles.entities.TokenType
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.entities.VerificationToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface VerificationTokenRepository : JpaRepository<VerificationToken, Long> {

    fun findByToken(token: String): Optional<VerificationToken>

    fun findByUserAndTokenType(user: User, tokenType: TokenType): List<VerificationToken>

    fun findByUserIdAndTokenType(userId: Long, tokenType: TokenType): List<VerificationToken>

    fun deleteByExpiresAtBeforeAndIsUsedTrue(expiresAt: LocalDateTime)

    fun deleteByUserAndTokenType(user: User, tokenType: TokenType)
}
