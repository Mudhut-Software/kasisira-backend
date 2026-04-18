package com.mudhut.software.kasisira.properties.models.response

import com.mudhut.software.kasisira.properties.entities.MediaType
import java.time.Instant

data class PropertyMediaResponse(
    val id: Long,
    val mediaType: MediaType,
    val url: String,
    val thumbnailUrl: String?,
    val description: String?,
    val isPrimary: Boolean,
    val displayOrder: Int,
    val createdAt: Instant?
)
