package com.mudhut.software.kasisira.properties.repositories

import com.mudhut.software.kasisira.properties.entities.ListingType
import com.mudhut.software.kasisira.properties.entities.Property
import com.mudhut.software.kasisira.properties.entities.PropertyStatus
import com.mudhut.software.kasisira.properties.entities.PropertyType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.math.BigDecimal

@Repository
interface PropertyRepository : JpaRepository<Property, Long>, JpaSpecificationExecutor<Property> {

    fun findByOwnerId(ownerId: Long, pageable: Pageable): Page<Property>

    fun findByStatus(status: PropertyStatus, pageable: Pageable): Page<Property>

    fun findByPropertyType(propertyType: PropertyType, pageable: Pageable): Page<Property>

    fun findByListingType(listingType: ListingType, pageable: Pageable): Page<Property>

    fun findByPropertyTypeAndListingType(
        propertyType: PropertyType,
        listingType: ListingType,
        pageable: Pageable
    ): Page<Property>

    fun findByCity(city: String, pageable: Pageable): Page<Property>

    @Query(
        """
        SELECT p FROM Property p
        WHERE p.status = :status
        AND (:propertyType IS NULL OR p.propertyType = :propertyType)
        AND (:listingType IS NULL OR p.listingType = :listingType)
        AND (:city IS NULL OR LOWER(p.city) LIKE LOWER(CONCAT('%', :city, '%')))
        AND (:minPrice IS NULL OR p.price >= :minPrice)
        AND (:maxPrice IS NULL OR p.price <= :maxPrice)
        AND (:bedrooms IS NULL OR p.bedrooms >= :bedrooms)
        ORDER BY p.createdAt DESC
    """
    )
    fun searchProperties(
        @Param("status") status: PropertyStatus,
        @Param("propertyType") propertyType: PropertyType?,
        @Param("listingType") listingType: ListingType?,
        @Param("city") city: String?,
        @Param("minPrice") minPrice: BigDecimal?,
        @Param("maxPrice") maxPrice: BigDecimal?,
        @Param("bedrooms") bedrooms: Int?,
        pageable: Pageable
    ): Page<Property>

    @Modifying
    @Query("UPDATE Property p SET p.viewCount = p.viewCount + 1 WHERE p.id = :id")
    fun incrementViewCount(@Param("id") id: Long)

    fun countByOwnerId(ownerId: Long): Long

    fun countByOwnerIdAndStatus(ownerId: Long, status: PropertyStatus): Long
}
