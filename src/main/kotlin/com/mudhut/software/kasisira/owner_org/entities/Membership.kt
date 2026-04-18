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
    name = "membership",
    uniqueConstraints = [UniqueConstraint(name = "uk_membership_user_org", columnNames = ["owner_org_id", "user_id"])],
    indexes = [
        Index(name = "idx_membership_user", columnList = "user_id"),
        Index(name = "idx_membership_org",  columnList = "owner_org_id")
    ]
)
data class Membership(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "membership_seq_gen")
    @SequenceGenerator(name = "membership_seq_gen", sequenceName = "membership_seq", allocationSize = 50)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_org_id", nullable = false)
    val ownerOrg: OwnerOrg,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val role: MembershipRole,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var permissions: String,   // serialized Map<String, Boolean>

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by")
    val invitedBy: User? = null,

    @Column(name = "accepted_at", nullable = false)
    val acceptedAt: Instant = Instant.now(),

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    val createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    val updatedAt: Instant? = null
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is Membership && id != 0L && id == other.id)
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "Membership(id=$id, orgId=${ownerOrg.id}, userId=${user.id}, role=$role)"
}
