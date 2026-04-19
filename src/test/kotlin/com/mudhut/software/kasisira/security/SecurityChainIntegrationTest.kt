package com.mudhut.software.kasisira.security

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.owner_org.models.request.CreateOrgRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("testing")
class SecurityChainIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @Test
    fun `POST orgs create without auth returns 401 JSON`() {
        mockMvc.post("/v1/orgs/create") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateOrgRequest("My Org"))
        }.andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.errorCode") { value("AUTHENTICATION_ERROR") }
            jsonPath("$.message") { value("Authentication is required to access this resource") }
        }
    }

    @Test
    fun `POST orgs create with malformed JWT returns 401 JSON`() {
        mockMvc.post("/v1/orgs/create") {
            header(HttpHeaders.AUTHORIZATION, "Bearer malformed.jwt.token")
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateOrgRequest("My Org"))
        }.andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.errorCode") { value("AUTHENTICATION_ERROR") }
        }
    }

    @Test
    fun `GET orgs me without auth returns 401 JSON`() {
        mockMvc.get("/v1/orgs/me").andExpect {
            status { isUnauthorized() }
            content { contentTypeCompatibleWith(MediaType.APPLICATION_JSON) }
            jsonPath("$.errorCode") { value("AUTHENTICATION_ERROR") }
        }
    }
}
