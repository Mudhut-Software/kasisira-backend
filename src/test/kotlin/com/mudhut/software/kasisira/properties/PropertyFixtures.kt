package com.mudhut.software.kasisira.properties

import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import com.mudhut.software.kasisira.properties.entities.FurnishingStatus
import com.mudhut.software.kasisira.properties.entities.ListingType
import com.mudhut.software.kasisira.properties.entities.Property
import com.mudhut.software.kasisira.properties.entities.PropertyStatus
import com.mudhut.software.kasisira.properties.entities.PropertyType
import com.mudhut.software.kasisira.properties.entities.RentalDuration
import java.math.BigDecimal
import java.time.Instant

// Shared Property fixture builder for tests. Defaults describe a plausible
// Kampala-area HOUSE listing; override any parameter to vary a single test.
fun aProperty(
    ownerOrg: OwnerOrg,
    id: Long = 0,
    title: String = "Beautiful House",
    description: String = "A beautiful house for sale in Kampala",
    propertyType: PropertyType = PropertyType.HOUSE,
    listingType: ListingType = ListingType.FOR_SALE,
    rentalDuration: RentalDuration? = null,
    furnishingStatus: FurnishingStatus? = null,
    price: BigDecimal = BigDecimal("500000000"),
    currency: String = "UGX",
    city: String = "Kampala",
    district: String = "Wakiso",
    address: String = "123 Main Street",
    latitude: BigDecimal = BigDecimal("0.3476"),
    longitude: BigDecimal = BigDecimal("32.5825"),
    bedrooms: Int? = null,
    bathrooms: Int? = null,
    landSize: BigDecimal? = null,
    landSizeUnit: String? = "sqm",
    builtArea: BigDecimal? = null,
    yearBuilt: Int? = null,
    features: String? = null,
    status: PropertyStatus = PropertyStatus.DRAFT,
    viewCount: Long = 0,
    createdAt: Instant? = null,
    updatedAt: Instant? = null
): Property = Property(
    id = id,
    ownerOrg = ownerOrg,
    title = title,
    description = description,
    propertyType = propertyType,
    listingType = listingType,
    rentalDuration = rentalDuration,
    furnishingStatus = furnishingStatus,
    price = price,
    currency = currency,
    city = city,
    district = district,
    address = address,
    latitude = latitude,
    longitude = longitude,
    bedrooms = bedrooms,
    bathrooms = bathrooms,
    landSize = landSize,
    landSizeUnit = landSizeUnit,
    builtArea = builtArea,
    yearBuilt = yearBuilt,
    features = features,
    status = status,
    viewCount = viewCount,
    createdAt = createdAt,
    updatedAt = updatedAt
)
