package com.mudhut.software.kasisira.profiles.models.request

import com.mudhut.software.kasisira.profiles.entities.OtpPurpose
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class VerifyOtpRequest(
    @field:NotBlank
    @field:Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone number must be E.164")
    val phoneNumber: String,

    @field:NotBlank
    @field:Pattern(regexp = "^\\d{6}$", message = "OTP must be 6 digits")
    val code: String,

    val purpose: OtpPurpose = OtpPurpose.LOGIN,

    /** Optional: used only when purpose = SIGNUP to set email/full name. */
    val signupHint: SignupHint? = null
)

data class SignupHint(
    val email: String? = null,
    val fullName: String? = null
)
