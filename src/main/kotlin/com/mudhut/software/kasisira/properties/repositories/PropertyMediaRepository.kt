package com.mudhut.software.kasisira.properties.repositories

import com.mudhut.software.kasisira.properties.entities.MediaType
import com.mudhut.software.kasisira.properties.entities.PropertyMedia
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository

@Repository
interface PropertyMediaRepository : JpaRepository<PropertyMedia, Long> {

    fun findByPropertyIdOrderByDisplayOrder(propertyId: Long): List<PropertyMedia>

    fun findByPropertyIdAndMediaType(propertyId: Long, mediaType: MediaType): List<PropertyMedia>

    fun findByPropertyIdAndIsPrimary(propertyId: Long, isPrimary: Boolean): PropertyMedia?

    fun countByPropertyId(propertyId: Long): Long

    fun countByPropertyIdAndMediaType(propertyId: Long, mediaType: MediaType): Long

    @Modifying
    @Query("DELETE FROM PropertyMedia pm WHERE pm.property.id = :propertyId")
    fun deleteAllByPropertyId(@Param("propertyId") propertyId: Long)

    @Modifying
    @Query("UPDATE PropertyMedia pm SET pm.isPrimary = false WHERE pm.property.id = :propertyId AND pm.id != :mediaId")
    fun clearPrimaryFlagExcept(@Param("propertyId") propertyId: Long, @Param("mediaId") mediaId: Long)
}
