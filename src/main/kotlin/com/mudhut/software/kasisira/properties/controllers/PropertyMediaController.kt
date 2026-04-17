package com.mudhut.software.kasisira.properties.controllers

import com.mudhut.software.kasisira.properties.models.request.AddMediaRequest
import com.mudhut.software.kasisira.properties.models.request.ReorderMediaRequest
import com.mudhut.software.kasisira.properties.models.request.UpdateMediaRequest
import com.mudhut.software.kasisira.properties.models.response.PropertyMediaResponse
import com.mudhut.software.kasisira.properties.services.PropertyMediaService
import com.mudhut.software.kasisira.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/properties/{propertyId}/media")
class PropertyMediaController {

    @Autowired
    private lateinit var propertyMediaService: PropertyMediaService

    @PostMapping
    fun addMedia(
        @PathVariable propertyId: Long,
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @Valid @RequestBody request: AddMediaRequest
    ): ResponseEntity<PropertyMediaResponse> {
        val media = propertyMediaService.addMedia(propertyId, userPrincipal.id, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(media)
    }

    @GetMapping
    fun getMedia(@PathVariable propertyId: Long): ResponseEntity<List<PropertyMediaResponse>> {
        val media = propertyMediaService.getMediaByPropertyId(propertyId)
        return ResponseEntity.ok(media)
    }

    @PutMapping("/{mediaId}")
    fun updateMedia(
        @PathVariable propertyId: Long,
        @PathVariable mediaId: Long,
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @Valid @RequestBody request: UpdateMediaRequest
    ): ResponseEntity<PropertyMediaResponse> {
        val media = propertyMediaService.updateMedia(mediaId, userPrincipal.id, request)
        return ResponseEntity.ok(media)
    }

    @DeleteMapping("/{mediaId}")
    fun deleteMedia(
        @PathVariable propertyId: Long,
        @PathVariable mediaId: Long,
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<Void> {
        propertyMediaService.deleteMedia(mediaId, userPrincipal.id)
        return ResponseEntity.noContent().build()
    }

    @PatchMapping("/{mediaId}/primary")
    fun setAsPrimary(
        @PathVariable propertyId: Long,
        @PathVariable mediaId: Long,
        @AuthenticationPrincipal userPrincipal: UserPrincipal
    ): ResponseEntity<PropertyMediaResponse> {
        val media = propertyMediaService.setAsPrimary(mediaId, userPrincipal.id)
        return ResponseEntity.ok(media)
    }

    @PutMapping("/reorder")
    fun reorderMedia(
        @PathVariable propertyId: Long,
        @AuthenticationPrincipal userPrincipal: UserPrincipal,
        @Valid @RequestBody request: ReorderMediaRequest
    ): ResponseEntity<Void> {
        propertyMediaService.reorderMedia(propertyId, userPrincipal.id, request.mediaIds)
        return ResponseEntity.ok().build()
    }
}
