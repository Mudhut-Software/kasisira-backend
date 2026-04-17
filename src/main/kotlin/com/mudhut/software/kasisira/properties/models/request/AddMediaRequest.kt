package com.mudhut.software.kasisira.properties.models.request

import com.mudhut.software.kasisira.properties.entities.MediaType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class AddMediaRequest(
    @field:NotNull(message = "Media type is required")
    val mediaType: MediaType,

    @field:NotBlank(message = "URL is required")
    val url: String,

    val thumbnailUrl: String? = null,

    val description: String? = null,

    val isPrimary: Boolean = false,

    val displayOrder: Int = 0,

    val fileSize: Long? = null,

    val mimeType: String? = null
)
