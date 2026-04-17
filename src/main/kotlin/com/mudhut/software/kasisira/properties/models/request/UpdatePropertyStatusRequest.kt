package com.mudhut.software.kasisira.properties.models.request

import com.mudhut.software.kasisira.properties.entities.PropertyStatus
import jakarta.validation.constraints.NotNull

data class UpdatePropertyStatusRequest(
    @field:NotNull(message = "Status is required")
    val status: PropertyStatus
)
