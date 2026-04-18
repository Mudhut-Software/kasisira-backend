package com.mudhut.software.kasisira.profiles.entities

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(name = "verification_tokens", indexes = [
    Index(name = "idx_token", columnList = "token", unique = true),
    Index(name = "idx_user_id", columnList = "user_id")
])
data class VerificationToken(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    val id: Long = 0,

    @Column(nullable = false, unique = true, length = 255)
    val token: String,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val tokenType: TokenType = TokenType.EMAIL_VERIFICATION,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    val createdAt: Instant? = null,

    @Column(name = "used_at")
    var usedAt: Instant? = null,

    @Column(name = "is_used", nullable = false)
    var isUsed: Boolean = false
) {
    fun isExpired(): Boolean {
        return Instant.now().isAfter(expiresAt)
    }

    fun isValid(): Boolean {
        return !isUsed && !isExpired()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is VerificationToken) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "VerificationToken(id=$id, tokenType=$tokenType, isUsed=$isUsed, expiresAt=$expiresAt)"
    }
}

enum class TokenType {
    EMAIL_VERIFICATION,
    PASSWORD_RESET
}
