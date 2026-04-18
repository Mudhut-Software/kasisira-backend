package com.mudhut.software.kasisira.profiles.models.request

import com.mudhut.software.kasisira.profiles.entities.OtpPurpose
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class RequestOtpRequest(
    @field:NotBlank(message = "Phone number is required")
    @field:Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone number must be E.164 (e.g. +256700000000)")
    val phoneNumber: String,

    val purpose: OtpPurpose = OtpPurpose.LOGIN
)
