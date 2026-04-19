package com.mudhut.software.kasisira.properties.services

import com.mudhut.software.kasisira.properties.models.request.AddMediaRequest
import com.mudhut.software.kasisira.properties.models.request.UpdateMediaRequest
import com.mudhut.software.kasisira.properties.models.response.PropertyMediaResponse

interface PropertyMediaService {

    fun addMedia(propertyId: Long, callerUserId: Long, request: AddMediaRequest): PropertyMediaResponse

    fun getMediaByPropertyId(propertyId: Long): List<PropertyMediaResponse>

    fun updateMedia(mediaId: Long, callerUserId: Long, request: UpdateMediaRequest): PropertyMediaResponse

    fun deleteMedia(mediaId: Long, callerUserId: Long)

    fun setAsPrimary(mediaId: Long, callerUserId: Long): PropertyMediaResponse

    fun reorderMedia(propertyId: Long, callerUserId: Long, mediaIds: List<Long>)
}
