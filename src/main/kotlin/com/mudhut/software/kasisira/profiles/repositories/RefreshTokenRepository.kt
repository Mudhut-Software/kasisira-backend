package com.mudhut.software.kasisira.profiles.repositories

import com.mudhut.software.kasisira.profiles.entities.RefreshToken
import com.mudhut.software.kasisira.profiles.entities.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.*

@Repository
interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    fun findByToken(token: String): Optional<RefreshToken>

    fun findByUser(user: User): List<RefreshToken>

    fun findByUserId(userId: Long): List<RefreshToken>

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true, rt.revokedAt = :revokedAt WHERE rt.user.id = :userId")
    fun revokeAllUserTokens(userId: Long, revokedAt: Instant)

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :now OR rt.revoked = true")
    fun deleteExpiredAndRevokedTokens(now: Instant)

    fun deleteByUser(user: User)
}
