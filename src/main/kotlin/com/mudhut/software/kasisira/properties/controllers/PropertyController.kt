package com.mudhut.software.kasisira.properties.controllers

import com.mudhut.software.kasisira.owner_org.entities.Permission
import com.mudhut.software.kasisira.owner_org.services.MembershipService
import com.mudhut.software.kasisira.properties.entities.ListingType
import com.mudhut.software.kasisira.properties.entities.PropertyType
import com.mudhut.software.kasisira.properties.models.request.CreatePropertyRequest
import com.mudhut.software.kasisira.properties.models.request.PropertySearchRequest
import com.mudhut.software.kasisira.properties.models.request.UpdatePropertyRequest
import com.mudhut.software.kasisira.properties.models.request.UpdatePropertyStatusRequest
import com.mudhut.software.kasisira.properties.models.response.PropertyResponse
import com.mudhut.software.kasisira.properties.models.response.PropertySummaryResponse
import com.mudhut.software.kasisira.properties.services.PropertyService
import com.mudhut.software.kasisira.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
class PropertyController(
    private val propertyService: PropertyService,
    private val membershipService: MembershipService
) {

    // --- Org-scoped endpoints ---

    @PostMapping("/v1/orgs/{orgId}/properties")
    fun createProperty(
        @PathVariable orgId: Long,
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @Valid @RequestBody request: CreatePropertyRequest
    ): ResponseEntity<PropertyResponse> {
        val property = propertyService.createProperty(userPrincipal.id, orgId, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(property)
    }

    @GetMapping("/v1/orgs/{orgId}/properties")
    fun getPropertiesForOrg(
        @PathVariable orgId: Long,
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<PropertySummaryResponse>> {
        membershipService.requirePermission(userPrincipal.id, orgId, Permission.MANAGE_LISTINGS)
        val properties = propertyService.getPropertiesByOrg(orgId, pageable)
        return ResponseEntity.ok(properties)
    }

    @GetMapping("/v1/orgs/{orgId}/properties/stats")
    fun getPropertyStatsForOrg(
        @PathVariable orgId: Long,
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<Map<String, Any>> {
        membershipService.requirePermission(userPrincipal.id, orgId, Permission.MANAGE_LISTINGS)
        val stats = propertyService.getPropertyStatsByOrg(orgId)
        return ResponseEntity.ok(stats)
    }

    // --- Per-resource endpoints (public reads + authorised mutations) ---

    @GetMapping("/v1/properties/{id}")
    fun getProperty(@PathVariable id: Long): ResponseEntity<PropertyResponse> {
        propertyService.incrementViewCount(id)
        val property = propertyService.getPropertyById(id)
        return ResponseEntity.ok(property)
    }

    @PutMapping("/v1/properties/{id}")
    fun updateProperty(
        @PathVariable id: Long,
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @Valid @RequestBody request: UpdatePropertyRequest
    ): ResponseEntity<PropertyResponse> {
        val property = propertyService.updateProperty(id, userPrincipal.id, request)
        return ResponseEntity.ok(property)
    }

    @DeleteMapping("/v1/properties/{id}")
    fun deleteProperty(
        @PathVariable id: Long,
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<Void> {
        propertyService.deleteProperty(id, userPrincipal.id)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/v1/properties/{id}/status")
    fun updatePropertyStatus(
        @PathVariable id: Long,
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @Valid @RequestBody request: UpdatePropertyStatusRequest
    ): ResponseEntity<PropertyResponse> {
        val property = propertyService.updatePropertyStatus(id, userPrincipal.id, request.status)
        return ResponseEntity.ok(property)
    }

    // --- Public list/search endpoints ---

    @GetMapping("/v1/properties")
    fun getActiveProperties(
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<PropertySummaryResponse>> {
        val properties = propertyService.getActiveProperties(pageable)
        return ResponseEntity.ok(properties)
    }

    @GetMapping("/v1/properties/search")
    fun searchProperties(
        @RequestParam(required = false) propertyType: PropertyType?,
        @RequestParam(required = false) listingType: ListingType?,
        @RequestParam(required = false) city: String?,
        @RequestParam(required = false) minPrice: BigDecimal?,
        @RequestParam(required = false) maxPrice: BigDecimal?,
        @RequestParam(required = false) bedrooms: Int?,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<PropertySummaryResponse>> {
        val searchRequest = PropertySearchRequest(
            propertyType = propertyType,
            listingType = listingType,
            city = city,
            minPrice = minPrice,
            maxPrice = maxPrice,
            bedrooms = bedrooms
        )
        val properties = propertyService.searchProperties(searchRequest, pageable)
        return ResponseEntity.ok(properties)
    }

    @GetMapping("/v1/properties/type/{propertyType}")
    fun getPropertiesByType(
        @PathVariable propertyType: PropertyType,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<PropertySummaryResponse>> {
        val properties = propertyService.getPropertiesByType(propertyType, pageable)
        return ResponseEntity.ok(properties)
    }

    @GetMapping("/v1/properties/listing/{listingType}")
    fun getPropertiesByListingType(
        @PathVariable listingType: ListingType,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<PropertySummaryResponse>> {
        val properties = propertyService.getPropertiesByListingType(listingType, pageable)
        return ResponseEntity.ok(properties)
    }
}
