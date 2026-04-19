package com.mudhut.software.kasisira.properties.models.request

import com.mudhut.software.kasisira.properties.entities.FurnishingStatus
import com.mudhut.software.kasisira.properties.entities.RentalDuration
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal

// PATCH-style: every field is optional. Each validator fires only when the
// field is present (Jakarta treats null as valid for @Size, @DecimalMin/Max,
// @Positive, @Min). On update, a value is either kept unchanged (null in the
// request) or bounds-checked before it reaches the mapper.
data class UpdatePropertyRequest(
    @field:Size(min = 5, max = 200, message = "Title must be between 5 and 200 characters")
    val title: String? = null,

    @field:Size(min = 20, message = "Description must be at least 20 characters")
    val description: String? = null,

    val rentalDuration: RentalDuration? = null,

    val furnishingStatus: FurnishingStatus? = null,

    @field:Positive(message = "Price must be positive")
    val price: BigDecimal? = null,

    val currency: String? = null,

    @field:Size(min = 1, max = 100, message = "City must not be blank")
    val city: String? = null,

    @field:Size(min = 1, max = 100, message = "District must not be blank")
    val district: String? = null,

    @field:Size(min = 1, max = 255, message = "Address must not be blank")
    val address: String? = null,

    @field:DecimalMin(value = "-90.0", message = "Latitude must be between -90 and 90")
    @field:DecimalMax(value = "90.0", message = "Latitude must be between -90 and 90")
    val latitude: BigDecimal? = null,

    @field:DecimalMin(value = "-180.0", message = "Longitude must be between -180 and 180")
    @field:DecimalMax(value = "180.0", message = "Longitude must be between -180 and 180")
    val longitude: BigDecimal? = null,

    @field:Min(value = 0, message = "Bedrooms cannot be negative")
    val bedrooms: Int? = null,

    @field:Min(value = 0, message = "Bathrooms cannot be negative")
    val bathrooms: Int? = null,

    @field:Positive(message = "Land size must be positive")
    val landSize: BigDecimal? = null,

    val landSizeUnit: String? = null,

    @field:Positive(message = "Built area must be positive")
    val builtArea: BigDecimal? = null,

    @field:Min(value = 1900, message = "Invalid year built")
    val yearBuilt: Int? = null,

    val features: List<String>? = null
)
