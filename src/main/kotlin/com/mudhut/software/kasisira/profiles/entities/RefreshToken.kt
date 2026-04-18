package com.mudhut.software.kasisira.profiles.entities

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "refresh_tokens", indexes = [
    Index(name = "idx_refresh_token", columnList = "token", unique = true),
    Index(name = "idx_refresh_token_user_id", columnList = "user_id")
])
data class RefreshToken(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 500)
    val token: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    val createdAt: Instant? = null,

    @Column(name = "revoked", nullable = false)
    var revoked: Boolean = false,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    @Column(name = "device_info", length = 500)
    val deviceInfo: String? = null,

    @Column(name = "ip_address", length = 50)
    val ipAddress: String? = null
) {
    fun isExpired(): Boolean {
        return Instant.now().isAfter(expiresAt)
    }

    fun isValid(): Boolean {
        return !revoked && !isExpired()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RefreshToken) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "RefreshToken(id=$id, revoked=$revoked, expiresAt=$expiresAt)"
    }
}
