package com.mudhut.software.kasisira.security

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.mudhut.software.kasisira.utils.exceptions.ErrorResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException

class RestAccessDeniedHandlerTest {

    private val objectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
    private val handler = RestAccessDeniedHandler(objectMapper)

    @Test
    fun `writes 403 with AUTHORIZATION_ERROR JSON body`() {
        val request = MockHttpServletRequest("POST", "/v1/orgs/create")
        val response = MockHttpServletResponse()
        val exception = AccessDeniedException("forbidden")

        handler.handle(request, response, exception)

        assertEquals(403, response.status)
        assertEquals(MediaType.APPLICATION_JSON_VALUE, response.contentType?.substringBefore(";")?.trim())
        assertEquals("UTF-8", response.characterEncoding)

        val body = objectMapper.readValue(response.contentAsString, ErrorResponse::class.java)
        assertEquals("AUTHORIZATION_ERROR", body.errorCode)
        assertEquals("You don't have permission to access this resource", body.message)
    }
}
