package com.mudhut.software.kasisira.properties.models.response

import com.mudhut.software.kasisira.properties.entities.FurnishingStatus
import com.mudhut.software.kasisira.properties.entities.ListingType
import com.mudhut.software.kasisira.properties.entities.PropertyStatus
import com.mudhut.software.kasisira.properties.entities.PropertyType
import com.mudhut.software.kasisira.properties.entities.RentalDuration
import java.math.BigDecimal
import java.time.Instant

data class PropertyResponse(
    val id: Long,
    val org: PropertyOrgResponse,
    val title: String,
    val description: String,
    val propertyType: PropertyType,
    val listingType: ListingType,
    val rentalDuration: RentalDuration?,
    val furnishingStatus: FurnishingStatus?,
    val price: BigDecimal,
    val currency: String,
    val city: String,
    val district: String?,
    val address: String?,
    val latitude: BigDecimal?,
    val longitude: BigDecimal?,
    val bedrooms: Int?,
    val bathrooms: Int?,
    val landSize: BigDecimal?,
    val landSizeUnit: String?,
    val builtArea: BigDecimal?,
    val yearBuilt: Int?,
    val features: List<String>,
    val status: PropertyStatus,
    val viewCount: Long,
    val media: List<PropertyMediaResponse>,
    val primaryImage: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
