package com.mudhut.software.kasisira.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.utils.exceptions.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component

@Component
class RestAuthenticationEntryPoint(
    private val objectMapper: ObjectMapper
) : AuthenticationEntryPoint {

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException
    ) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        val body = ErrorResponse(
            errorCode = "AUTHENTICATION_ERROR",
            message = "Authentication is required to access this resource"
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
