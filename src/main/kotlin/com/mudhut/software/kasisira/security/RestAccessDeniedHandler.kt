package com.mudhut.software.kasisira.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.utils.exceptions.ErrorResponse
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.stereotype.Component

@Component
class RestAccessDeniedHandler(
    private val objectMapper: ObjectMapper
) : AccessDeniedHandler {

    override fun handle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        accessDeniedException: AccessDeniedException
    ) {
        response.status = HttpServletResponse.SC_FORBIDDEN
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        val body = ErrorResponse(
            errorCode = "AUTHORIZATION_ERROR",
            message = "You don't have permission to access this resource"
        )
        response.writer.write(objectMapper.writeValueAsString(body))
    }
}
