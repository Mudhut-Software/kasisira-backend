package com.mudhut.software.kasisira.notifications.entities

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "outbox", indexes = [Index(name = "idx_outbox_due", columnList = "not_before")])
data class Outbox(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "outbox_seq_gen")
    @SequenceGenerator(name = "outbox_seq_gen", sequenceName = "outbox_seq", allocationSize = 50)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val channel: OutboxChannel,

    @Column(nullable = false, length = 255)
    val recipient: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val payload: String,  // serialized JSON — worker deserializes per channel

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: OutboxStatus = OutboxStatus.PENDING,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "not_before", nullable = false)
    var notBefore: Instant = Instant.now(),

    @Column(name = "last_error", columnDefinition = "text")
    var lastError: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "processed_at")
    var processedAt: Instant? = null
) {
    override fun equals(other: Any?): Boolean = this === other || (other is Outbox && id != 0L && id == other.id)
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "Outbox(id=$id, channel=$channel, status=$status, attempts=$attempts)"
}
