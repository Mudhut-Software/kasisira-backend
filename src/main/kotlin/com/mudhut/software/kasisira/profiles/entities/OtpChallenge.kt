package com.mudhut.software.kasisira.profiles.entities

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.LocalDateTime

@Entity
@Table(
    name = "otp_challenges",
    indexes = [
        Index(name = "idx_otp_phone_purpose_created", columnList = "phone_number,purpose,created_at"),
        Index(name = "idx_otp_phone_active", columnList = "phone_number,expires_at")
    ]
)
data class OtpChallenge(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "otp_challenges_seq_gen")
    @SequenceGenerator(name = "otp_challenges_seq_gen", sequenceName = "otp_challenges_seq", allocationSize = 50)
    val id: Long = 0,

    @Column(name = "phone_number", nullable = false, length = 20)
    val phoneNumber: String,

    @Column(name = "code_hash", nullable = false, length = 255)
    val codeHash: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val purpose: OtpPurpose,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: LocalDateTime,

    @Column(name = "consumed_at")
    var consumedAt: LocalDateTime? = null,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    val createdAt: LocalDateTime? = null
) {
    override fun equals(other: Any?): Boolean = this === other || (other is OtpChallenge && id != 0L && id == other.id)
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "OtpChallenge(id=$id, phoneNumber='$phoneNumber', purpose=$purpose, consumedAt=$consumedAt)"
}
