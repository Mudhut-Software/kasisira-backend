package com.mudhut.software.kasisira.properties.models.response

import com.mudhut.software.kasisira.properties.entities.ListingType
import com.mudhut.software.kasisira.properties.entities.PropertyStatus
import com.mudhut.software.kasisira.properties.entities.PropertyType
import com.mudhut.software.kasisira.properties.entities.RentalDuration
import java.math.BigDecimal
import java.time.Instant

data class PropertySummaryResponse(
    val id: Long,
    val title: String,
    val propertyType: PropertyType,
    val listingType: ListingType,
    val rentalDuration: RentalDuration?,
    val price: BigDecimal,
    val currency: String,
    val city: String,
    val district: String?,
    val bedrooms: Int?,
    val bathrooms: Int?,
    val landSize: BigDecimal?,
    val status: PropertyStatus,
    val primaryImage: String?,
    val viewCount: Long,
    val createdAt: Instant?
)
