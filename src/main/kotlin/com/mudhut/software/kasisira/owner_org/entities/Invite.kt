package com.mudhut.software.kasisira.owner_org.entities

import com.mudhut.software.kasisira.profiles.entities.User
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.annotations.UpdateTimestamp
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(
    name = "invite",
    indexes = [
        Index(name = "idx_invite_token_hash", columnList = "token_hash", unique = true),
        Index(name = "idx_invite_org", columnList = "owner_org_id")
    ]
)
data class Invite(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "invite_seq_gen")
    @SequenceGenerator(name = "invite_seq_gen", sequenceName = "invite_seq", allocationSize = 50)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_org_id", nullable = false)
    val ownerOrg: OwnerOrg,

    @Column(length = 150)
    val email: String? = null,

    @Column(name = "phone_number", length = 20)
    val phoneNumber: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val role: MembershipRole,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val permissions: String,

    @Column(name = "token_hash", nullable = false, length = 255)
    val tokenHash: String,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invited_by", nullable = false)
    val invitedBy: User,

    @Column(name = "expires_at", nullable = false)
    val expiresAt: Instant,

    @Column(name = "accepted_at")
    var acceptedAt: Instant? = null,

    @Column(name = "revoked_at")
    var revokedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    val createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    val updatedAt: Instant? = null
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is Invite && id != 0L && id == other.id)
    override fun hashCode(): Int = id.hashCode()
}
