package com.mudhut.software.kasisira.notifications.repositories

import com.mudhut.software.kasisira.notifications.entities.Outbox
import com.mudhut.software.kasisira.notifications.entities.OutboxStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface OutboxRepository : JpaRepository<Outbox, Long> {
    @Query("""
        SELECT o FROM Outbox o
        WHERE o.status = :status AND o.notBefore <= :now
        ORDER BY o.notBefore ASC
    """)
    fun findDue(@Param("status") status: OutboxStatus, @Param("now") now: Instant, pageable: Pageable): List<Outbox>
}
