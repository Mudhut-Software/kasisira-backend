package com.mudhut.software.kasisira.owner_org.models.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateOrgRequest(
    @field:NotBlank(message = "Name is required")
    @field:Size(min = 2, max = 120)
    val name: String
)
