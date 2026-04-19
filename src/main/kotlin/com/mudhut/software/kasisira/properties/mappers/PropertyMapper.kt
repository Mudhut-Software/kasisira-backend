package com.mudhut.software.kasisira.properties.mappers

import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import com.mudhut.software.kasisira.properties.entities.Property
import com.mudhut.software.kasisira.properties.entities.PropertyStatus
import com.mudhut.software.kasisira.properties.models.request.CreatePropertyRequest
import com.mudhut.software.kasisira.properties.models.request.UpdatePropertyRequest
import com.mudhut.software.kasisira.properties.models.response.PropertyOwnerResponse
import com.mudhut.software.kasisira.properties.models.response.PropertyResponse
import com.mudhut.software.kasisira.properties.models.response.PropertySummaryResponse
import org.springframework.stereotype.Component

@Component
class PropertyMapper(private val propertyMediaMapper: PropertyMediaMapper) {

    fun toResponse(property: Property): PropertyResponse {
        val primaryMedia = property.media.find { it.isPrimary }

        return PropertyResponse(
            id = property.id,
            owner = PropertyOwnerResponse(
                id = property.owner?.id ?: 0,
                username = property.owner?.username ?: "",
                imageUrl = property.owner?.imageUrl
            ),
            title = property.title,
            description = property.description,
            propertyType = property.propertyType,
            listingType = property.listingType,
            rentalDuration = property.rentalDuration,
            furnishingStatus = property.furnishingStatus,
            price = property.price,
            currency = property.currency,
            city = property.city,
            district = property.district,
            address = property.address,
            latitude = property.latitude,
            longitude = property.longitude,
            bedrooms = property.bedrooms,
            bathrooms = property.bathrooms,
            landSize = property.landSize,
            landSizeUnit = property.landSizeUnit,
            builtArea = property.builtArea,
            yearBuilt = property.yearBuilt,
            features = property.features?.split(",")?.map { it.trim() } ?: emptyList(),
            status = property.status,
            viewCount = property.viewCount,
            media = property.media.sortedBy { it.displayOrder }.map { propertyMediaMapper.toResponse(it) },
            primaryImage = primaryMedia?.url ?: property.media.firstOrNull()?.url,
            createdAt = property.createdAt,
            updatedAt = property.updatedAt
        )
    }

    fun toSummaryResponse(property: Property): PropertySummaryResponse {
        val primaryMedia = property.media.find { it.isPrimary }

        return PropertySummaryResponse(
            id = property.id,
            title = property.title,
            propertyType = property.propertyType,
            listingType = property.listingType,
            rentalDuration = property.rentalDuration,
            price = property.price,
            currency = property.currency,
            city = property.city,
            district = property.district,
            bedrooms = property.bedrooms,
            bathrooms = property.bathrooms,
            landSize = property.landSize,
            status = property.status,
            primaryImage = primaryMedia?.url ?: property.media.firstOrNull()?.url,
            viewCount = property.viewCount,
            createdAt = property.createdAt
        )
    }

    fun toResponseList(properties: List<Property>): List<PropertyResponse> {
        return properties.map { toResponse(it) }
    }

    fun toSummaryResponseList(properties: List<Property>): List<PropertySummaryResponse> {
        return properties.map { toSummaryResponse(it) }
    }

    fun fromCreateRequest(request: CreatePropertyRequest, org: OwnerOrg): Property {
        return Property(
            id = 0,
            ownerOrg = org,
            title = request.title,
            description = request.description,
            propertyType = request.propertyType,
            listingType = request.listingType,
            rentalDuration = request.rentalDuration,
            furnishingStatus = request.furnishingStatus,
            price = request.price,
            currency = request.currency,
            city = request.city,
            district = request.district,
            address = request.address,
            latitude = request.latitude,
            longitude = request.longitude,
            bedrooms = request.bedrooms,
            bathrooms = request.bathrooms,
            landSize = request.landSize,
            landSizeUnit = request.landSizeUnit,
            builtArea = request.builtArea,
            yearBuilt = request.yearBuilt,
            features = request.features?.joinToString(","),
            status = PropertyStatus.DRAFT
        )
    }

    fun applyUpdate(property: Property, request: UpdatePropertyRequest): Property {
        return property.copy(
            title = request.title ?: property.title,
            description = request.description ?: property.description,
            rentalDuration = request.rentalDuration ?: property.rentalDuration,
            furnishingStatus = request.furnishingStatus ?: property.furnishingStatus,
            price = request.price ?: property.price,
            currency = request.currency ?: property.currency,
            city = request.city ?: property.city,
            district = request.district ?: property.district,
            address = request.address ?: property.address,
            latitude = request.latitude ?: property.latitude,
            longitude = request.longitude ?: property.longitude,
            bedrooms = request.bedrooms ?: property.bedrooms,
            bathrooms = request.bathrooms ?: property.bathrooms,
            landSize = request.landSize ?: property.landSize,
            landSizeUnit = request.landSizeUnit ?: property.landSizeUnit,
            builtArea = request.builtArea ?: property.builtArea,
            yearBuilt = request.yearBuilt ?: property.yearBuilt,
            features = request.features?.joinToString(",") ?: property.features
        )
    }
}
