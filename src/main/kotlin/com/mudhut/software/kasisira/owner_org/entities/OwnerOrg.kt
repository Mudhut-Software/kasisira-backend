package com.mudhut.software.kasisira.owner_org.entities

import com.mudhut.software.kasisira.profiles.entities.User
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(
    name = "owner_org",
    indexes = [Index(name = "idx_owner_org_creator", columnList = "creator_user_id")]
)
data class OwnerOrg(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "owner_org_seq_gen")
    @SequenceGenerator(name = "owner_org_seq_gen", sequenceName = "owner_org_seq", allocationSize = 50)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_user_id", nullable = false)
    val creator: User,

    @Column(nullable = false, length = 120)
    val name: String,

    @Column(name = "verified_at")
    var verifiedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    val createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    val updatedAt: Instant? = null
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is OwnerOrg && id != 0L && id == other.id)
    override fun hashCode(): Int = id.hashCode()
    override fun toString(): String = "OwnerOrg(id=$id, name='$name')"
}
