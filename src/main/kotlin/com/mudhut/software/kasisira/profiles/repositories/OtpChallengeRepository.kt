package com.mudhut.software.kasisira.profiles.repositories

import com.mudhut.software.kasisira.profiles.entities.OtpChallenge
import com.mudhut.software.kasisira.profiles.entities.OtpPurpose
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface OtpChallengeRepository : JpaRepository<OtpChallenge, Long> {

    @Query("""
        SELECT o FROM OtpChallenge o
        WHERE o.phoneNumber = :phone AND o.purpose = :purpose
          AND o.consumedAt IS NULL AND o.expiresAt > :now
        ORDER BY o.createdAt DESC
    """)
    fun findLatestActive(
        @Param("phone") phone: String,
        @Param("purpose") purpose: OtpPurpose,
        @Param("now") now: LocalDateTime
    ): List<OtpChallenge>

    @Query("""
        SELECT COUNT(o) FROM OtpChallenge o
        WHERE o.phoneNumber = :phone AND o.createdAt > :since
    """)
    fun countRecent(
        @Param("phone") phone: String,
        @Param("since") since: LocalDateTime
    ): Long
}
