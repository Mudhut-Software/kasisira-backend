package com.mudhut.software.kasisira.properties.controllers

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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal

@RestController
@RequestMapping("/v1/properties")
class PropertyController {

    @Autowired
    private lateinit var propertyService: PropertyService

    @PostMapping
    fun createProperty(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @Valid @RequestBody request: CreatePropertyRequest
    ): ResponseEntity<PropertyResponse> {
        val property = propertyService.createProperty(userPrincipal.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(property)
    }

    @GetMapping("/{id}")
    fun getProperty(@PathVariable id: Long): ResponseEntity<PropertyResponse> {
        propertyService.incrementViewCount(id)
        val property = propertyService.getPropertyById(id)
        return ResponseEntity.ok(property)
    }

    @PutMapping("/{id}")
    fun updateProperty(
        @PathVariable id: Long,
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @Valid @RequestBody request: UpdatePropertyRequest
    ): ResponseEntity<PropertyResponse> {
        val property = propertyService.updateProperty(id, userPrincipal.id, request)
        return ResponseEntity.ok(property)
    }

    @DeleteMapping("/{id}")
    fun deleteProperty(
        @PathVariable id: Long,
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<Void> {
        propertyService.deleteProperty(id, userPrincipal.id)
        return ResponseEntity.noContent().build()
    }

    @GetMapping
    fun getActiveProperties(
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<PropertySummaryResponse>> {
        val properties = propertyService.getActiveProperties(pageable)
        return ResponseEntity.ok(properties)
    }

    @GetMapping("/search")
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

    @GetMapping("/my-properties")
    fun getMyProperties(
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<PropertySummaryResponse>> {
        val properties = propertyService.getPropertiesByOwner(userPrincipal.id, pageable)
        return ResponseEntity.ok(properties)
    }

    @GetMapping("/my-properties/stats")
    fun getMyPropertyStats(
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<Map<String, Any>> {
        val stats = propertyService.getPropertyStatsByOwner(userPrincipal.id)
        return ResponseEntity.ok(stats)
    }

    @GetMapping("/type/{propertyType}")
    fun getPropertiesByType(
        @PathVariable propertyType: PropertyType,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<PropertySummaryResponse>> {
        val properties = propertyService.getPropertiesByType(propertyType, pageable)
        return ResponseEntity.ok(properties)
    }

    @GetMapping("/listing/{listingType}")
    fun getPropertiesByListingType(
        @PathVariable listingType: ListingType,
        @PageableDefault(size = 20) pageable: Pageable
    ): ResponseEntity<Page<PropertySummaryResponse>> {
        val properties = propertyService.getPropertiesByListingType(listingType, pageable)
        return ResponseEntity.ok(properties)
    }

    @PatchMapping("/{id}/status")
    fun updatePropertyStatus(
        @PathVariable id: Long,
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @Valid @RequestBody request: UpdatePropertyStatusRequest
    ): ResponseEntity<PropertyResponse> {
        val property = propertyService.updatePropertyStatus(id, userPrincipal.id, request.status)
        return ResponseEntity.ok(property)
    }
}
