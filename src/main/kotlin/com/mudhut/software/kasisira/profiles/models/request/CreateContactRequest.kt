package com.mudhut.software.kasisira.profiles.models.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateContactRequest(
    @field:NotBlank(message = "Phone number is required")
    @field:Size(max = 20, message = "Phone number must not exceed 20 characters")
    val phoneNumber: String,

    val isPrimary: Boolean = false,

    @field:Size(max = 100, message = "Label must not exceed 100 characters")
    val label: String? = null
)
