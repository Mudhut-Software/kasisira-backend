package com.mudhut.software.kasisira.profiles.models.response

data class AuthResponse(
    val user: UserResponse,
    val token: String?,
    val message: String
)
