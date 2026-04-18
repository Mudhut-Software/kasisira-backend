package com.mudhut.software.kasisira.profiles.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.profiles.entities.OtpPurpose
import com.mudhut.software.kasisira.profiles.models.request.RequestOtpRequest
import com.mudhut.software.kasisira.profiles.models.request.VerifyOtpRequest
import com.mudhut.software.kasisira.profiles.models.response.AuthResponse
import com.mudhut.software.kasisira.profiles.services.PhoneAuthService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("testing")
class PhoneAuthControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var phoneAuthService: PhoneAuthService

    @Test
    fun `POST otp request returns 202`() {
        every { phoneAuthService.requestOtp(any()) } just Runs

        mockMvc.post("/v1/auth/phone/otp/request") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                RequestOtpRequest("+256700000000", OtpPurpose.LOGIN)
            )
        }.andExpect {
            status { isAccepted() }
            jsonPath("$.status") { value("sent") }
        }
    }

    @Test
    fun `POST otp verify returns JWT`() {
        val auth = AuthResponse(
            accessToken = "access-token-xyz",
            refreshToken = "refresh-token-xyz",
            user = null
        )
        every { phoneAuthService.verify(any()) } returns auth

        mockMvc.post("/v1/auth/phone/otp/verify") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(
                VerifyOtpRequest(
                    phoneNumber = "+256700000000",
                    code = "123456",
                    purpose = OtpPurpose.LOGIN
                )
            )
        }.andExpect {
            status { isOk() }
            jsonPath("$.accessToken") { value("access-token-xyz") }
            jsonPath("$.refreshToken") { value("refresh-token-xyz") }
        }
    }

    @Test
    fun `POST otp request returns 400 on bad phone format`() {
        val badRequest = mapOf(
            "phoneNumber" to "256-700-000-000", // missing + prefix, non-digits
            "purpose" to "LOGIN"
        )

        mockMvc.post("/v1/auth/phone/otp/request") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(badRequest)
        }.andExpect {
            status { isBadRequest() }
        }
    }
}
