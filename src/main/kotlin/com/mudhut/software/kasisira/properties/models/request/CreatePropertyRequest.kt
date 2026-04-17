package com.mudhut.software.kasisira.properties.models.request

import com.mudhut.software.kasisira.properties.entities.FurnishingStatus
import com.mudhut.software.kasisira.properties.entities.ListingType
import com.mudhut.software.kasisira.properties.entities.PropertyType
import com.mudhut.software.kasisira.properties.entities.RentalDuration
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class CreatePropertyRequest(
    @field:NotBlank(message = "Title is required")
    @field:Size(min = 5, max = 200, message = "Title must be between 5 and 200 characters")
    val title: String,

    @field:NotBlank(message = "Description is required")
    @field:Size(min = 20, message = "Description must be at least 20 characters")
    val description: String,

    @field:NotNull(message = "Property type is required")
    val propertyType: PropertyType,

    @field:NotNull(message = "Listing type is required")
    val listingType: ListingType,

    val rentalDuration: RentalDuration? = null,

    val furnishingStatus: FurnishingStatus? = null,

    @field:NotNull(message = "Price is required")
    @field:Positive(message = "Price must be positive")
    val price: BigDecimal,

    val currency: String = "UGX",

    @field:NotBlank(message = "City is required")
    val city: String,

    val district: String? = null,

    val address: String? = null,

    val latitude: BigDecimal? = null,

    val longitude: BigDecimal? = null,

    @field:Min(value = 0, message = "Bedrooms cannot be negative")
    val bedrooms: Int? = null,

    @field:Min(value = 0, message = "Bathrooms cannot be negative")
    val bathrooms: Int? = null,

    @field:Positive(message = "Land size must be positive")
    val landSize: BigDecimal? = null,

    val landSizeUnit: String? = "sqm",

    @field:Positive(message = "Built area must be positive")
    val builtArea: BigDecimal? = null,

    @field:Min(value = 1900, message = "Invalid year built")
    val yearBuilt: Int? = null,

    val features: List<String>? = null
)
