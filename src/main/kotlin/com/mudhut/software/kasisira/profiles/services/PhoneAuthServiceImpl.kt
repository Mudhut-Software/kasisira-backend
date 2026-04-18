package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.Contact
import com.mudhut.software.kasisira.profiles.entities.OtpPurpose
import com.mudhut.software.kasisira.profiles.entities.RoleName
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.mappers.UserMapper
import com.mudhut.software.kasisira.profiles.models.request.RequestOtpRequest
import com.mudhut.software.kasisira.profiles.models.request.SignupHint
import com.mudhut.software.kasisira.profiles.models.request.VerifyOtpRequest
import com.mudhut.software.kasisira.profiles.models.response.AuthResponse
import com.mudhut.software.kasisira.profiles.repositories.ContactRepository
import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.security.JwtTokenProvider
import com.mudhut.software.kasisira.utils.exceptions.InvalidOtpException
import com.mudhut.software.kasisira.utils.exceptions.UserNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PhoneAuthServiceImpl(
    private val otpService: OtpService,
    private val userRepository: UserRepository,
    private val contactRepository: ContactRepository,
    private val roleService: RoleService,
    private val jwtTokenProvider: JwtTokenProvider,
    private val userMapper: UserMapper
) : PhoneAuthService {

    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun requestOtp(request: RequestOtpRequest) {
        val phone = request.phoneNumber.trim()

        // Anti-enumeration: for LOGIN, silently succeed when no account exists for the
        // phone rather than returning a 404. The controller always responds 202, so an
        // attacker probing phones cannot distinguish "account exists" from "no account"
        // based on the response alone. SIGNUP always proceeds — the whole point is to
        // create the user on verify. The DTO validates E.164; we just trim whitespace.
        when (request.purpose) {
            OtpPurpose.LOGIN -> {
                val existing = contactRepository.findByPhoneNumber(phone)
                if (existing == null) {
                    log.info("OTP login request for unknown phone; returning 202 without sending")
                    return
                }
                otpService.requestOtp(phone, request.purpose)
            }
            OtpPurpose.SIGNUP -> {
                otpService.requestOtp(phone, request.purpose)
            }
        }
    }

    @Transactional
    override fun verify(request: VerifyOtpRequest): AuthResponse {
        val phone = request.phoneNumber.trim()

        // OtpServiceImpl either returns true or throws InvalidOtpException for any failure
        // mode (wrong code, expired, locked). The explicit `false` branch is defensive —
        // if the contract ever loosens we still reject without issuing tokens.
        if (!otpService.verifyOtp(phone, request.code, request.purpose)) {
            throw InvalidOtpException("Verification failed.")
        }

        val user = when (request.purpose) {
            OtpPurpose.LOGIN -> {
                // Defence in depth: if the contact disappeared between requestOtp and
                // verify (race), surface the same generic InvalidOtpException as a
                // wrong-code path. Keeps LOGIN failure responses indistinguishable.
                val contact = contactRepository.findByPhoneNumber(phone)
                    ?: throw InvalidOtpException("Verification failed.")
                contact.user
                    ?: throw InvalidOtpException("Verification failed.")
            }
            OtpPurpose.SIGNUP -> findOrCreate(phone, request.signupHint)
        }

        val tokens = jwtTokenProvider.issueTokens(user)
        return AuthResponse(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            user = userMapper.toResponse(user)
        )
    }

    /**
     * Returns the existing user for [phone] if a contact already maps to one (idempotent
     * signup), otherwise provisions a new user + primary verified contact + TENANT role.
     *
     * Placeholder username and email are generated from UUIDs because the `users` table's
     * NOT NULL + UNIQUE constraints on those columns predate phone-first signup; users
     * complete their profile after authentication. `emailVerified` stays false — even when
     * a hint email is provided, we haven't proven ownership yet.
     */
    private fun findOrCreate(phone: String, hint: SignupHint?): User {
        contactRepository.findByPhoneNumber(phone)?.let { existing ->
            return existing.user
                ?: throw UserNotFoundException("Contact has no associated user.")
        }

        val placeholderUsername = "u_${UUID.randomUUID().toString().substring(0, 8)}"
        val placeholderEmail = "${UUID.randomUUID()}@placeholder.kasisira"

        val saved = userRepository.save(
            User(
                id = 0,
                username = placeholderUsername,
                email = hint?.email ?: placeholderEmail,
                provider = AuthProvider.LOCAL,
                isActive = true,
                isEnabled = true,
                emailVerified = false
            )
        )

        contactRepository.save(
            Contact(
                id = 0,
                user = saved,
                phoneNumber = phone,
                isPrimary = true,
                isVerified = true,
                label = null
            )
        )

        roleService.grant(saved, RoleName.TENANT)
        return saved
    }
}
