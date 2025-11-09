package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.models.request.LoginRequest
import com.mudhut.software.kasisira.profiles.models.response.TokenResponse
import jakarta.servlet.http.HttpServletRequest

interface AuthService {

    fun login(request: LoginRequest, httpRequest: HttpServletRequest): TokenResponse

    fun refreshToken(refreshToken: String, httpRequest: HttpServletRequest): TokenResponse

    fun logout(refreshToken: String)

    fun logoutAll(userId: Long)
}
