package com.mudhut.software.kasisira.profiles.entities

import jakarta.persistence.*
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(
    name = "users", indexes = [
        Index(name = "idx_user_email", columnList = "email", unique = true),
        Index(name = "idx_user_username", columnList = "username", unique = true),
        Index(name = "idx_user_provider_id", columnList = "provider,providerId")
    ]
)
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    val id: Long,

    @Column(nullable = false, unique = true, length = 50)
    @field:NotBlank(message = "Username is required")
    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    val username: String,

    @Column(nullable = false, unique = true, length = 150)
    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Email must be valid")
    val email: String,

    @Column(name = "password_hash", nullable = true)
    val passwordHash: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val provider: AuthProvider = AuthProvider.LOCAL,

    @Column(name = "provider_id", length = 255)
    val providerId: String? = null,

    @Column(name = "image_url", length = 500)
    val imageUrl: String? = null,

    @Column(name = "email_verified", nullable = false)
    val emailVerified: Boolean = false,

    @Column(name = "is_active", nullable = false)
    val isActive: Boolean = false,

    @Column(name = "is_enabled", nullable = false)
    val isEnabled: Boolean = false,

    @OneToMany(
        mappedBy = "user",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    val contacts: MutableList<Contact> = mutableListOf(),

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    val createdAt: LocalDateTime? = null,

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    val updatedAt: LocalDateTime? = null,

    @Column(name = "last_login")
    val lastLogin: LocalDateTime? = null
) {
    // Check if user is using local authentication
    val isLocalAuth: Boolean
        get() = provider == AuthProvider.LOCAL

    // Check if user is using social login
    val isSocialAuth: Boolean
        get() = provider != AuthProvider.LOCAL

    // Helper methods to manage bidirectional relationship with contacts
    // Note: contacts is MutableList so these operations are still possible
    fun addContact(contact: Contact) {
        contacts.add(contact)
        contact.user = this
    }

    fun removeContact(contact: Contact) {
        contacts.remove(contact)
        contact.user = null
    }

    fun clearContacts() {
        contacts.forEach { it.user = null }
        contacts.clear()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is User) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "User(id=$id, username='$username', email='$email', provider=$provider)"
    }
}
