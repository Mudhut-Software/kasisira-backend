package com.mudhut.software.kasisira.properties.services

import com.mudhut.software.kasisira.owner_org.entities.Permission
import com.mudhut.software.kasisira.owner_org.services.MembershipService
import com.mudhut.software.kasisira.owner_org.services.OwnerOrgService
import com.mudhut.software.kasisira.properties.entities.FurnishingStatus
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
import com.mudhut.software.kasisira.utils.exceptions.InvalidPropertyConfigurationException
import com.mudhut.software.kasisira.utils.exceptions.PropertyNotFoundException
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
    private lateinit var propertyMapper: PropertyMapper

    @Autowired
    private lateinit var membershipService: MembershipService

    @Autowired
    private lateinit var ownerOrgService: OwnerOrgService

    override fun createProperty(callerUserId: Long, orgId: Long, request: CreatePropertyRequest): PropertyResponse {
        membershipService.requirePermission(callerUserId, orgId, Permission.MANAGE_LISTINGS)
        val org = ownerOrgService.getOrgById(orgId)

        validatePropertyRequest(request)

        val property = propertyMapper.fromCreateRequest(request, org)
        val savedProperty = propertyRepository.save(property)

        return propertyMapper.toResponse(savedProperty)
    }

    override fun getPropertyById(id: Long): PropertyResponse {
        val property = propertyRepository.findById(id)
            .orElseThrow { PropertyNotFoundException("Property with id $id not found") }
        return propertyMapper.toResponse(property)
    }

    override fun updateProperty(id: Long, callerUserId: Long, request: UpdatePropertyRequest): PropertyResponse {
        val property = propertyRepository.findById(id)
            .orElseThrow { PropertyNotFoundException("Property with id $id not found") }

        membershipService.requirePermission(callerUserId, property.ownerOrg.id, Permission.MANAGE_LISTINGS)

        val updatedProperty = propertyMapper.applyUpdate(property, request)
        val savedProperty = propertyRepository.save(updatedProperty)

        return propertyMapper.toResponse(savedProperty)
    }

    override fun deleteProperty(id: Long, callerUserId: Long) {
        val property = propertyRepository.findById(id)
            .orElseThrow { PropertyNotFoundException("Property with id $id not found") }

        membershipService.requirePermission(callerUserId, property.ownerOrg.id, Permission.MANAGE_LISTINGS)

        propertyRepository.delete(property)
    }

    override fun getPropertiesByOrg(orgId: Long, pageable: Pageable): Page<PropertySummaryResponse> {
        return propertyRepository.findByOwnerOrgId(orgId, pageable)
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

    override fun updatePropertyStatus(id: Long, callerUserId: Long, status: PropertyStatus): PropertyResponse {
        val property = propertyRepository.findById(id)
            .orElseThrow { PropertyNotFoundException("Property with id $id not found") }

        membershipService.requirePermission(callerUserId, property.ownerOrg.id, Permission.MANAGE_LISTINGS)

        val updatedProperty = property.copy(status = status)
        val savedProperty = propertyRepository.save(updatedProperty)

        return propertyMapper.toResponse(savedProperty)
    }

    @Transactional
    override fun incrementViewCount(id: Long) {
        propertyRepository.incrementViewCount(id)
    }

    override fun getPropertyStatsByOrg(orgId: Long): Map<String, Any> {
        return mapOf(
            "total" to propertyRepository.countByOwnerOrgId(orgId),
            "active" to propertyRepository.countByOwnerOrgIdAndStatus(orgId, PropertyStatus.ACTIVE),
            "draft" to propertyRepository.countByOwnerOrgIdAndStatus(orgId, PropertyStatus.DRAFT),
            "sold" to propertyRepository.countByOwnerOrgIdAndStatus(orgId, PropertyStatus.SOLD),
            "rented" to propertyRepository.countByOwnerOrgIdAndStatus(orgId, PropertyStatus.RENTED)
        )
    }

    private fun validatePropertyRequest(request: CreatePropertyRequest) {
        if (request.propertyType == PropertyType.LAND && request.listingType == ListingType.FOR_RENT) {
            throw InvalidPropertyConfigurationException("Land plots can only be listed for sale")
        }

        if (request.listingType == ListingType.FOR_RENT && request.rentalDuration == null) {
            throw InvalidPropertyConfigurationException("Rental duration is required for rental listings")
        }

        // Reject FOR_SALE listings that specify a non-default furnishing status.
        // We compare against UNFURNISHED (the DTO default) rather than null, because
        // Jackson applies the default for both absent and null JSON values — so we
        // cannot distinguish "client omitted the field" from "client sent UNFURNISHED".
        if (request.listingType == ListingType.FOR_SALE && request.furnishingStatus != FurnishingStatus.UNFURNISHED) {
            throw InvalidPropertyConfigurationException("Furnishing status is only applicable for rental listings")
        }
    }
}
