package com.mudhut.software.kasisira.profiles.entities

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(
    name = "contacts",
    uniqueConstraints = [UniqueConstraint(name = "uk_contact_phone_number", columnNames = ["phone_number"])],
    indexes = [Index(name = "idx_contact_user", columnList = "user_id")]
)
data class Contact(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    val id: Long,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    var user: User? = null, // var needed for bidirectional relationship management

    @Column(name = "phone_number", nullable = false, length = 20)
    @field:NotBlank(message = "Phone number is required")
    @field:Size(max = 20, message = "Phone number must not exceed 20 characters")
    val phoneNumber: String,

    @Column(name = "is_primary", nullable = false)
    val isPrimary: Boolean = false,

    @Column(name = "is_verified", nullable = false)
    val isVerified: Boolean = false,

    @Column(length = 100)
    val label: String? = null, // e.g., "Home", "Work", "Mobile"

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    val createdAt: LocalDateTime? = null,

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    val updatedAt: LocalDateTime? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Contact) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "Contact(id=$id, phoneNumber='$phoneNumber', label='$label', isPrimary=$isPrimary)"
    }
}
