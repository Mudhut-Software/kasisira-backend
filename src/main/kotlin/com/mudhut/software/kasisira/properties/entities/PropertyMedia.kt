package com.mudhut.software.kasisira.properties.entities

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Entity
@Table(
    name = "property_media",
    indexes = [
        Index(name = "idx_media_property", columnList = "property_id"),
        Index(name = "idx_media_type", columnList = "media_type"),
        Index(name = "idx_media_order", columnList = "property_id,display_order")
    ]
)
data class PropertyMedia(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    var property: Property? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 20)
    val mediaType: MediaType,

    @Column(nullable = false, length = 500)
    @field:NotBlank(message = "URL is required")
    val url: String,

    @Column(name = "thumbnail_url", length = 500)
    val thumbnailUrl: String? = null,

    @Column(length = 500)
    val description: String? = null,

    @Column(name = "is_primary", nullable = false)
    val isPrimary: Boolean = false,

    @Column(name = "display_order", nullable = false)
    val displayOrder: Int = 0,

    @Column(name = "file_size")
    val fileSize: Long? = null,

    @Column(name = "mime_type", length = 100)
    val mimeType: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    val createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    val updatedAt: Instant? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PropertyMedia) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "PropertyMedia(id=$id, mediaType=$mediaType, url='$url')"
    }
}
