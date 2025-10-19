package com.mudhut.software.kasisira.profiles.models.request

import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank(message = "Username is required")
    @field:Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    val username: String,

    @field:NotBlank(message = "Email is required")
    @field:Email(message = "Please provide a valid email address")
    val email: String,

    @field:NotBlank(message = "Password is required")
    @field:Size(min = 8, message = "Password must be at least 8 characters long")
    val password: String,

    val provider: AuthProvider = AuthProvider.LOCAL,

    val providerId: String? = null,

    val imageUrl: String? = null,

    val phoneNumber: String? = null,

    val phoneLabel: String? = null
)
