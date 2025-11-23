package com.mudhut.software.kasisira.profiles.models.request

import jakarta.validation.constraints.NotBlank

data class LoginRequest(
    @field:NotBlank(message = "Email or username is required")
    val email: String,

    @field:NotBlank(message = "Password is required")
    val password: String
)
