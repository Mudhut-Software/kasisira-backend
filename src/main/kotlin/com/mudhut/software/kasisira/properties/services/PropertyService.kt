package com.mudhut.software.kasisira.properties.services

import com.mudhut.software.kasisira.properties.entities.ListingType
import com.mudhut.software.kasisira.properties.entities.PropertyStatus
import com.mudhut.software.kasisira.properties.entities.PropertyType
import com.mudhut.software.kasisira.properties.models.request.CreatePropertyRequest
import com.mudhut.software.kasisira.properties.models.request.PropertySearchRequest
import com.mudhut.software.kasisira.properties.models.request.UpdatePropertyRequest
import com.mudhut.software.kasisira.properties.models.response.PropertyResponse
import com.mudhut.software.kasisira.properties.models.response.PropertySummaryResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface PropertyService {

    fun createProperty(ownerId: Long, request: CreatePropertyRequest): PropertyResponse

    fun getPropertyById(id: Long): PropertyResponse

    fun updateProperty(id: Long, ownerId: Long, request: UpdatePropertyRequest): PropertyResponse

    fun deleteProperty(id: Long, ownerId: Long)

    fun getPropertiesByOwner(ownerId: Long, pageable: Pageable): Page<PropertySummaryResponse>

    fun searchProperties(request: PropertySearchRequest, pageable: Pageable): Page<PropertySummaryResponse>

    fun getActiveProperties(pageable: Pageable): Page<PropertySummaryResponse>

    fun getPropertiesByType(propertyType: PropertyType, pageable: Pageable): Page<PropertySummaryResponse>

    fun getPropertiesByListingType(listingType: ListingType, pageable: Pageable): Page<PropertySummaryResponse>

    fun updatePropertyStatus(id: Long, ownerId: Long, status: PropertyStatus): PropertyResponse

    fun incrementViewCount(id: Long)

    fun getPropertyStatsByOwner(ownerId: Long): Map<String, Any>
}
