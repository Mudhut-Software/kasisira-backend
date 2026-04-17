package com.mudhut.software.kasisira.properties.services

import com.mudhut.software.kasisira.properties.entities.ListingType
import com.mudhut.software.kasisira.properties.entities.PropertyStatus
import com.mudhut.software.kasisira.properties.entities.PropertyType
import com.mudhut.software.kasisira.properties.mappers.PropertyMapper
import com.mudhut.software.kasisira.properties.models.request.CreatePropertyRequest
import com.mudhut.software.kasisira.properties.models.request.PropertySearchRequest
import com.mudhut.software.kasisira.properties.models.request.UpdatePropertyRequest
import com.mudhut.software.kasisira.properties.models.response.PropertyResponse
import com.mudhut.software.kasisira.properties.models.response.PropertySummaryResponse
import com.mudhut.software.kasisira.properties.repositories.PropertyRepository
import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.utils.exceptions.InvalidPropertyConfigurationException
import com.mudhut.software.kasisira.utils.exceptions.PropertyNotFoundException
import com.mudhut.software.kasisira.utils.exceptions.UnauthorizedAccessException
import com.mudhut.software.kasisira.utils.exceptions.UserNotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class PropertyServiceImpl : PropertyService {

    @Autowired
    private lateinit var propertyRepository: PropertyRepository

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var propertyMapper: PropertyMapper

    override fun createProperty(ownerId: Long, request: CreatePropertyRequest): PropertyResponse {
        val owner = userRepository.findById(ownerId)
            .orElseThrow { UserNotFoundException("User with id $ownerId not found") }

        validatePropertyRequest(request)

        val property = propertyMapper.fromCreateRequest(request, owner)
        val savedProperty = propertyRepository.save(property)

        return propertyMapper.toResponse(savedProperty)
    }

    override fun getPropertyById(id: Long): PropertyResponse {
        val property = propertyRepository.findById(id)
            .orElseThrow { PropertyNotFoundException("Property with id $id not found") }
        return propertyMapper.toResponse(property)
    }

    override fun updateProperty(id: Long, ownerId: Long, request: UpdatePropertyRequest): PropertyResponse {
        val property = propertyRepository.findById(id)
            .orElseThrow { PropertyNotFoundException("Property with id $id not found") }

        if (property.owner?.id != ownerId) {
            throw UnauthorizedAccessException("You are not authorized to update this property")
        }

        val updatedProperty = propertyMapper.applyUpdate(property, request)
        val savedProperty = propertyRepository.save(updatedProperty)

        return propertyMapper.toResponse(savedProperty)
    }

    override fun deleteProperty(id: Long, ownerId: Long) {
        val property = propertyRepository.findById(id)
            .orElseThrow { PropertyNotFoundException("Property with id $id not found") }

        if (property.owner?.id != ownerId) {
            throw UnauthorizedAccessException("You are not authorized to delete this property")
        }

        propertyRepository.delete(property)
    }

    override fun getPropertiesByOwner(ownerId: Long, pageable: Pageable): Page<PropertySummaryResponse> {
        return propertyRepository.findByOwnerId(ownerId, pageable)
            .map { propertyMapper.toSummaryResponse(it) }
    }

    override fun searchProperties(request: PropertySearchRequest, pageable: Pageable): Page<PropertySummaryResponse> {
        return propertyRepository.searchProperties(
            status = PropertyStatus.ACTIVE,
            propertyType = request.propertyType,
            listingType = request.listingType,
            city = request.city,
            minPrice = request.minPrice,
            maxPrice = request.maxPrice,
            bedrooms = request.bedrooms,
            pageable = pageable
        ).map { propertyMapper.toSummaryResponse(it) }
    }

    override fun getActiveProperties(pageable: Pageable): Page<PropertySummaryResponse> {
        return propertyRepository.findByStatus(PropertyStatus.ACTIVE, pageable)
            .map { propertyMapper.toSummaryResponse(it) }
    }

    override fun getPropertiesByType(propertyType: PropertyType, pageable: Pageable): Page<PropertySummaryResponse> {
        return propertyRepository.findByPropertyType(propertyType, pageable)
            .map { propertyMapper.toSummaryResponse(it) }
    }

    override fun getPropertiesByListingType(listingType: ListingType, pageable: Pageable): Page<PropertySummaryResponse> {
        return propertyRepository.findByListingType(listingType, pageable)
            .map { propertyMapper.toSummaryResponse(it) }
    }

    override fun updatePropertyStatus(id: Long, ownerId: Long, status: PropertyStatus): PropertyResponse {
        val property = propertyRepository.findById(id)
            .orElseThrow { PropertyNotFoundException("Property with id $id not found") }

        if (property.owner?.id != ownerId) {
            throw UnauthorizedAccessException("You are not authorized to update this property")
        }

        val updatedProperty = property.copy(status = status)
        val savedProperty = propertyRepository.save(updatedProperty)

        return propertyMapper.toResponse(savedProperty)
    }

    @Transactional
    override fun incrementViewCount(id: Long) {
        propertyRepository.incrementViewCount(id)
    }

    override fun getPropertyStatsByOwner(ownerId: Long): Map<String, Any> {
        return mapOf(
            "total" to propertyRepository.countByOwnerId(ownerId),
            "active" to propertyRepository.countByOwnerIdAndStatus(ownerId, PropertyStatus.ACTIVE),
            "draft" to propertyRepository.countByOwnerIdAndStatus(ownerId, PropertyStatus.DRAFT),
            "sold" to propertyRepository.countByOwnerIdAndStatus(ownerId, PropertyStatus.SOLD),
            "rented" to propertyRepository.countByOwnerIdAndStatus(ownerId, PropertyStatus.RENTED)
        )
    }

    private fun validatePropertyRequest(request: CreatePropertyRequest) {
        if (request.propertyType == PropertyType.LAND && request.listingType == ListingType.FOR_RENT) {
            throw InvalidPropertyConfigurationException("Land plots can only be listed for sale")
        }

        if (request.listingType == ListingType.FOR_RENT && request.rentalDuration == null) {
            throw InvalidPropertyConfigurationException("Rental duration is required for rental listings")
        }

        if (request.listingType == ListingType.FOR_SALE && request.furnishingStatus != null) {
            throw InvalidPropertyConfigurationException("Furnishing status is only applicable for rental listings")
        }
    }
}
