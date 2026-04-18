package com.mudhut.software.kasisira.profiles.entities

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(
    name = "user_roles",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_user_role", columnNames = ["user_id", "role_name"])
    ],
    indexes = [
        Index(name = "idx_user_roles_user", columnList = "user_id")
    ]
)
data class UserRole(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_roles_seq_gen")
    @SequenceGenerator(
        name = "user_roles_seq_gen",
        sequenceName = "user_roles_seq",
        allocationSize = 50
    )
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @Enumerated(EnumType.STRING)
    @Column(name = "role_name", nullable = false, length = 20)
    val roleName: RoleName,

    @Column(name = "granted_at", nullable = false, updatable = false)
    @CreationTimestamp
    val grantedAt: Instant? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is UserRole) return false
        return id != 0L && id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "UserRole(id=$id, roleName=$roleName)"
    }
}
