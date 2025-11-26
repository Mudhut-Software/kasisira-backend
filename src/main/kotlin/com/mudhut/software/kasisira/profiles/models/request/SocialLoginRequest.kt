package com.mudhut.software.kasisira.profiles.models.request

import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class SocialLoginRequest(
    @field:NotNull(message = "Provider is required")
    val provider: AuthProvider,

    @field:NotBlank(message = "Provider ID is required")
    val providerId: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Please provide a valid email address")
    val email: String,

    val username: String? = null,

    val imageUrl: String? = null
)
