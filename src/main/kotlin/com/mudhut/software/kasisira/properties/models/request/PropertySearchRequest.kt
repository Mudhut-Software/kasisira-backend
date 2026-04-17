package com.mudhut.software.kasisira.properties.models.request

import com.mudhut.software.kasisira.properties.entities.ListingType
import com.mudhut.software.kasisira.properties.entities.PropertyType
import com.mudhut.software.kasisira.properties.entities.RentalDuration
import java.math.BigDecimal

data class PropertySearchRequest(
    val propertyType: PropertyType? = null,
    val listingType: ListingType? = null,
    val rentalDuration: RentalDuration? = null,
    val city: String? = null,
    val district: String? = null,
    val minPrice: BigDecimal? = null,
    val maxPrice: BigDecimal? = null,
    val bedrooms: Int? = null,
    val bathrooms: Int? = null,
    val furnished: Boolean? = null
)
