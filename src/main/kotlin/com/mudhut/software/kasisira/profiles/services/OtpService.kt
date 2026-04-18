package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.entities.OtpPurpose

/**
 * Generates and verifies one-time passcodes for phone-based flows (login, signup).
 *
 * Implementations must:
 *  - rate-limit [requestOtp] per phone number,
 *  - hash codes at rest,
 *  - enforce an attempt cap per challenge,
 *  - delegate delivery to an SMS transport.
 */
interface OtpService {
    fun requestOtp(phoneNumberE164: String, purpose: OtpPurpose)
    fun verifyOtp(phoneNumberE164: String, code: String, purpose: OtpPurpose): Boolean
}
