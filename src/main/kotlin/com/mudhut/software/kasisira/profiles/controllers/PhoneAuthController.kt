package com.mudhut.software.kasisira.profiles.controllers

import com.mudhut.software.kasisira.profiles.models.request.RequestOtpRequest
import com.mudhut.software.kasisira.profiles.models.request.VerifyOtpRequest
import com.mudhut.software.kasisira.profiles.models.response.AuthResponse
import com.mudhut.software.kasisira.profiles.services.PhoneAuthService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/auth/phone")
class PhoneAuthController(
    private val phoneAuthService: PhoneAuthService
) {
    @PostMapping("/otp/request")
    fun requestOtp(@Valid @RequestBody body: RequestOtpRequest): ResponseEntity<Map<String, String>> {
        phoneAuthService.requestOtp(body)
        return ResponseEntity.accepted().body(mapOf("status" to "sent"))
    }

    @PostMapping("/otp/verify")
    fun verifyOtp(@Valid @RequestBody body: VerifyOtpRequest): ResponseEntity<AuthResponse> =
        ResponseEntity.ok(phoneAuthService.verify(body))
}
