package com.mudhut.software.kasisira.properties.services

import com.mudhut.software.kasisira.properties.models.request.AddMediaRequest
import com.mudhut.software.kasisira.properties.models.request.UpdateMediaRequest
import com.mudhut.software.kasisira.properties.models.response.PropertyMediaResponse

interface PropertyMediaService {

    fun addMedia(propertyId: Long, ownerId: Long, request: AddMediaRequest): PropertyMediaResponse

    fun getMediaByPropertyId(propertyId: Long): List<PropertyMediaResponse>

    fun updateMedia(mediaId: Long, ownerId: Long, request: UpdateMediaRequest): PropertyMediaResponse

    fun deleteMedia(mediaId: Long, ownerId: Long)

    fun setAsPrimary(mediaId: Long, ownerId: Long): PropertyMediaResponse

    fun reorderMedia(propertyId: Long, ownerId: Long, mediaIds: List<Long>)
}
