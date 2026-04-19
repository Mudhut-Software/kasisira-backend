package com.mudhut.software.kasisira.security

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.mudhut.software.kasisira.utils.exceptions.ErrorResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.InsufficientAuthenticationException

class RestAuthenticationEntryPointTest {

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val entryPoint = RestAuthenticationEntryPoint(objectMapper)

    @Test
    fun `writes 401 with AUTHENTICATION_ERROR JSON body`() {
        val request = MockHttpServletRequest("GET", "/v1/orgs/me")
        val response = MockHttpServletResponse()
        val exception = InsufficientAuthenticationException("no auth")

        entryPoint.commence(request, response, exception)

        assertEquals(401, response.status)
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.contentType)

        val body = objectMapper.readValue(response.contentAsString, ErrorResponse::class.java)
        assertEquals("AUTHENTICATION_ERROR", body.errorCode)
        assertEquals("Authentication is required to access this resource", body.message)
    }
}
