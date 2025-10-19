package com.mudhut.software.kasisira.profiles.models.request

import jakarta.validation.constraints.Size

data class UpdateContactRequest(
    @field:Size(max = 20, message = "Phone number must not exceed 20 characters")
    val phoneNumber: String? = null,

    val isPrimary: Boolean? = null,

    @field:Size(max = 100, message = "Label must not exceed 100 characters")
    val label: String? = null
)
