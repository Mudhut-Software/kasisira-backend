package com.mudhut.software.kasisira.properties.mappers

import com.mudhut.software.kasisira.properties.entities.Property
import com.mudhut.software.kasisira.properties.entities.PropertyMedia
import com.mudhut.software.kasisira.properties.models.request.AddMediaRequest
import com.mudhut.software.kasisira.properties.models.response.PropertyMediaResponse
import org.springframework.stereotype.Component

@Component
class PropertyMediaMapper {

    fun toResponse(media: PropertyMedia): PropertyMediaResponse {
        return PropertyMediaResponse(
            id = media.id,
            mediaType = media.mediaType,
            url = media.url,
            thumbnailUrl = media.thumbnailUrl,
            description = media.description,
            isPrimary = media.isPrimary,
            displayOrder = media.displayOrder,
            createdAt = media.createdAt
        )
    }

    fun toResponseList(mediaList: List<PropertyMedia>): List<PropertyMediaResponse> {
        return mediaList.map { toResponse(it) }
    }

    fun fromAddRequest(request: AddMediaRequest, property: Property): PropertyMedia {
        return PropertyMedia(
            id = 0,
            property = property,
            mediaType = request.mediaType,
            url = request.url,
            thumbnailUrl = request.thumbnailUrl,
            description = request.description,
            isPrimary = request.isPrimary,
            displayOrder = request.displayOrder,
            fileSize = request.fileSize,
            mimeType = request.mimeType
        )
    }
}
