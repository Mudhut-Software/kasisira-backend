package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.models.request.RequestOtpRequest
import com.mudhut.software.kasisira.profiles.models.request.VerifyOtpRequest
import com.mudhut.software.kasisira.profiles.models.response.AuthResponse

/**
 * Orchestration layer between the phone-auth REST controller and the underlying OTP /
 * user / contact / role primitives.
 *
 *  - [requestOtp] — issues an OTP via [OtpService]. For LOGIN requests it first confirms
 *    a contact exists for the given phone number so that the endpoint does not leak which
 *    phone numbers are registered.
 *  - [verify] — verifies the OTP and then either resolves the existing user (LOGIN) or
 *    creates a new user + primary contact + TENANT role (SIGNUP), returning a signed JWT
 *    token pair in both cases.
 */
interface PhoneAuthService {
    fun requestOtp(request: RequestOtpRequest)
    fun verify(request: VerifyOtpRequest): AuthResponse
}
