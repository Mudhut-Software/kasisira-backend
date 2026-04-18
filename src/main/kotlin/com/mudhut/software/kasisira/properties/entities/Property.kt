package com.mudhut.software.kasisira.properties.entities

import com.mudhut.software.kasisira.profiles.entities.User
import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "properties",
    indexes = [
        Index(name = "idx_property_owner", columnList = "owner_id"),
        Index(name = "idx_property_type", columnList = "property_type"),
        Index(name = "idx_property_listing_type", columnList = "listing_type"),
        Index(name = "idx_property_status", columnList = "status"),
        Index(name = "idx_property_price", columnList = "price"),
        Index(name = "idx_property_location", columnList = "city,district"),
        Index(name = "idx_property_created", columnList = "created_at")
    ]
)
data class Property(
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    var owner: User? = null,

    @Column(nullable = false, length = 200)
    @field:NotBlank(message = "Title is required")
    @field:Size(min = 5, max = 200, message = "Title must be between 5 and 200 characters")
    val title: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    @field:NotBlank(message = "Description is required")
    val description: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "property_type", nullable = false, length = 20)
    val propertyType: PropertyType,

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_type", nullable = false, length = 20)
    val listingType: ListingType,

    @Enumerated(EnumType.STRING)
    @Column(name = "rental_duration", length = 20)
    val rentalDuration: RentalDuration? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "furnishing_status", length = 20)
    val furnishingStatus: FurnishingStatus? = null,

    @Column(nullable = false, precision = 15, scale = 2)
    @field:NotNull(message = "Price is required")
    @field:Positive(message = "Price must be positive")
    val price: BigDecimal,

    @Column(length = 10)
    val currency: String = "UGX",

    @Column(nullable = false, length = 100)
    @field:NotBlank(message = "City is required")
    val city: String,

    @Column(length = 100)
    val district: String? = null,

    @Column(length = 255)
    val address: String? = null,

    @Column(precision = 10, scale = 7)
    val latitude: BigDecimal? = null,

    @Column(precision = 10, scale = 7)
    val longitude: BigDecimal? = null,

    val bedrooms: Int? = null,

    val bathrooms: Int? = null,

    @Column(name = "land_size", precision = 15, scale = 2)
    val landSize: BigDecimal? = null,

    @Column(name = "land_size_unit", length = 20)
    val landSizeUnit: String? = "sqm",

    @Column(name = "built_area", precision = 15, scale = 2)
    val builtArea: BigDecimal? = null,

    @Column(name = "year_built")
    val yearBuilt: Int? = null,

    @Column(columnDefinition = "TEXT")
    val features: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val status: PropertyStatus = PropertyStatus.DRAFT,

    @Column(name = "view_count", nullable = false)
    val viewCount: Long = 0,

    @OneToMany(
        mappedBy = "property",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
        fetch = FetchType.LAZY
    )
    val media: MutableList<PropertyMedia> = mutableListOf(),

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    val createdAt: Instant? = null,

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    val updatedAt: Instant? = null
) {
    fun addMedia(propertyMedia: PropertyMedia) {
        media.add(propertyMedia)
        propertyMedia.property = this
    }

    fun removeMedia(propertyMedia: PropertyMedia) {
        media.remove(propertyMedia)
        propertyMedia.property = null
    }

    fun clearMedia() {
        media.forEach { it.property = null }
        media.clear()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Property) return false
        return id == other.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun toString(): String {
        return "Property(id=$id, title='$title', propertyType=$propertyType, listingType=$listingType)"
    }
}
