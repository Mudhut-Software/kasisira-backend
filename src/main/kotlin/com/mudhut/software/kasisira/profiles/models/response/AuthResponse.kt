package com.mudhut.software.kasisira.profiles.models.response

/**
 * Response returned by authentication flows that issue both access and refresh tokens
 * (e.g. phone OTP login/signup). [user] is optional so callers that don't need to return
 * the profile alongside the tokens can pass `null`.
 */
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResponse? = null
)
