package com.mudhut.software.kasisira.profiles.models.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

data class UpdateUserRequest(
    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    val username: String? = null,

    @field:Email(message = "Please provide a valid email address")
    val email: String? = null,

    val imageUrl: String? = null,

    val isActive: Boolean? = null,

    val isEnabled: Boolean? = null
)
