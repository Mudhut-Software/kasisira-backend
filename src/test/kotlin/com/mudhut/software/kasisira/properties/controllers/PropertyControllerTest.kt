package com.mudhut.software.kasisira.properties.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import com.mudhut.software.kasisira.owner_org.services.MembershipService
import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.properties.entities.FurnishingStatus
import com.mudhut.software.kasisira.properties.entities.ListingType
import com.mudhut.software.kasisira.properties.entities.PropertyStatus
import com.mudhut.software.kasisira.properties.entities.PropertyType
import com.mudhut.software.kasisira.properties.models.request.CreatePropertyRequest
import com.mudhut.software.kasisira.properties.models.response.PropertyOrgResponse
import com.mudhut.software.kasisira.properties.models.response.PropertyResponse
import com.mudhut.software.kasisira.properties.services.PropertyService
import com.mudhut.software.kasisira.security.UserPrincipal
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
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
import java.math.BigDecimal

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("testing")
class PropertyControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper

    @MockkBean private lateinit var propertyService: PropertyService
    @MockkBean private lateinit var membershipService: MembershipService

    private val user = User(id = 1L, username = "u", email = "u@x.com", provider = AuthProvider.LOCAL)
    private val principal: UserPrincipal = UserPrincipal.create(user, setOf("TENANT", "OWNER"))

    private fun validBody(
        overrides: Map<String, Any?> = emptyMap()
    ): Map<String, Any?> {
        val base = mutableMapOf<String, Any?>(
            "title" to "Beautiful House",
            "description" to "A beautiful house for sale in Kampala with lots of space",
            "propertyType" to "HOUSE",
            "listingType" to "FOR_SALE",
            "price" to 500000000,
            "city" to "Kampala",
            "district" to "Wakiso",
            "address" to "123 Main Street",
            "latitude" to 0.3476,
            "longitude" to 32.5825
        )
        for ((k, v) in overrides) {
            if (v == null) base.remove(k) else base[k] = v
        }
        return base
    }

    private fun mockCreatedResponse(): PropertyResponse = PropertyResponse(
        id = 1L,
        org = PropertyOrgResponse(10L, "Test Org", false),
        title = "Beautiful House",
        description = "A beautiful house for sale in Kampala with lots of space",
        propertyType = PropertyType.HOUSE,
        listingType = ListingType.FOR_SALE,
        rentalDuration = null,
        furnishingStatus = FurnishingStatus.UNFURNISHED,
        price = BigDecimal("500000000"),
        currency = "UGX",
        city = "Kampala",
        district = "Wakiso",
        address = "123 Main Street",
        latitude = BigDecimal("0.3476"),
        longitude = BigDecimal("32.5825"),
        bedrooms = null,
        bathrooms = null,
        landSize = null,
        landSizeUnit = "sqm",
        builtArea = null,
        yearBuilt = null,
        features = emptyList(),
        status = PropertyStatus.DRAFT,
        viewCount = 0,
        media = emptyList(),
        primaryImage = null,
        createdAt = null,
        updatedAt = null
    )

    @Test
    fun `POST properties with all required fields returns 201`() {
        val captured = slot<CreatePropertyRequest>()
        every { propertyService.createProperty(1L, 10L, capture(captured)) } returns mockCreatedResponse()

        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(this@PropertyControllerTest.principal, null, this@PropertyControllerTest.principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody())
        }.andExpect {
            status { isCreated() }
        }

        assertEquals("123 Main Street", captured.captured.address)
        assertEquals("Wakiso", captured.captured.district)
        assertEquals(BigDecimal("0.3476"), captured.captured.latitude)
        assertEquals(BigDecimal("32.5825"), captured.captured.longitude)
    }

    @Test
    fun `POST properties omitting address returns 400 VALIDATION_ERROR`() {
        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(this@PropertyControllerTest.principal, null, this@PropertyControllerTest.principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody(mapOf("address" to null)))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            jsonPath("$.errors.address") { exists() }
        }
    }

    @Test
    fun `POST properties omitting district returns 400 VALIDATION_ERROR`() {
        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(this@PropertyControllerTest.principal, null, this@PropertyControllerTest.principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody(mapOf("district" to null)))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            jsonPath("$.errors.district") { exists() }
        }
    }

    @Test
    fun `POST properties omitting latitude returns 400 VALIDATION_ERROR`() {
        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(this@PropertyControllerTest.principal, null, this@PropertyControllerTest.principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody(mapOf("latitude" to null)))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            jsonPath("$.errors.latitude") { exists() }
        }
    }

    @Test
    fun `POST properties omitting longitude returns 400 VALIDATION_ERROR`() {
        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(this@PropertyControllerTest.principal, null, this@PropertyControllerTest.principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody(mapOf("longitude" to null)))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            jsonPath("$.errors.longitude") { exists() }
        }
    }

    @Test
    fun `POST properties with out-of-range latitude returns 400 VALIDATION_ERROR`() {
        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(this@PropertyControllerTest.principal, null, this@PropertyControllerTest.principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody(mapOf("latitude" to 91)))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            jsonPath("$.errors.latitude") { exists() }
        }
    }

    @Test
    fun `POST properties with out-of-range longitude returns 400 VALIDATION_ERROR`() {
        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(this@PropertyControllerTest.principal, null, this@PropertyControllerTest.principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody(mapOf("longitude" to -181)))
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode") { value("VALIDATION_ERROR") }
            jsonPath("$.errors.longitude") { exists() }
        }
    }

    @Test
    fun `POST properties omitting furnishingStatus defaults to UNFURNISHED`() {
        val captured = slot<CreatePropertyRequest>()
        every { propertyService.createProperty(1L, 10L, capture(captured)) } returns mockCreatedResponse()

        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(this@PropertyControllerTest.principal, null, this@PropertyControllerTest.principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody())
        }.andExpect {
            status { isCreated() }
        }

        assertEquals(FurnishingStatus.UNFURNISHED, captured.captured.furnishingStatus)
    }

    @Test
    fun `POST properties with new STUDIO PropertyType is accepted`() {
        val captured = slot<CreatePropertyRequest>()
        every { propertyService.createProperty(1L, 10L, capture(captured)) } returns mockCreatedResponse()

        mockMvc.post("/v1/orgs/10/properties") {
            with(authentication(UsernamePasswordAuthenticationToken(this@PropertyControllerTest.principal, null, this@PropertyControllerTest.principal.authorities)))
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(validBody(mapOf("propertyType" to "STUDIO")))
        }.andExpect {
            status { isCreated() }
        }

        assertEquals(PropertyType.STUDIO, captured.captured.propertyType)
    }
}
