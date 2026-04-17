package com.mudhut.software.kasisira.properties.models.request

import jakarta.validation.constraints.NotEmpty

data class ReorderMediaRequest(
    @field:NotEmpty(message = "Media IDs list cannot be empty")
    val mediaIds: List<Long>
)
