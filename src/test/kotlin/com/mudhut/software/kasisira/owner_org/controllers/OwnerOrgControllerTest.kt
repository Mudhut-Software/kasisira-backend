package com.mudhut.software.kasisira.owner_org.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import com.mudhut.software.kasisira.owner_org.models.request.CreateOrgRequest
import com.mudhut.software.kasisira.owner_org.services.MembershipService
import com.mudhut.software.kasisira.owner_org.services.OwnerOrgService
import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.security.UserPrincipal
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("testing")
class OwnerOrgControllerTest {
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @MockkBean private lateinit var ownerOrgService: OwnerOrgService
    @MockkBean private lateinit var membershipService: MembershipService

    @Test
    fun `POST orgs creates org`() {
        val user = User(id = 1L, username = "u", email = "u@x.com", provider = AuthProvider.LOCAL)
        val principal = UserPrincipal.create(user, setOf("TENANT", "OWNER"))
        val org = OwnerOrg(id = 10L, creator = user, name = "My Org")

        every { ownerOrgService.createOrg(1L, "My Org") } returns org

        mockMvc.post("/v1/orgs/create") {
            with(authentication(UsernamePasswordAuthenticationToken(principal, null, principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(CreateOrgRequest("My Org"))
        }.andExpect {
            status { isCreated() }
            jsonPath("$.id") { value(10) }
            jsonPath("$.name") { value("My Org") }
            jsonPath("$.isVerified") { value(false) }
        }
    }
}
