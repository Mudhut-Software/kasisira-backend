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

    @Transactional
    override fun requestOtp(request: RequestOtpRequest) {
        val phone = request.phoneNumber.trim()

        // Defence against phone-number enumeration: only issue a LOGIN OTP if a contact
        // already exists. SIGNUP is always allowed (the whole point is to create the user
        // on verify). The DTO validates E.164 format; we just trim whitespace at entry.
        if (request.purpose == OtpPurpose.LOGIN) {
            contactRepository.findByPhoneNumber(phone)
                ?: throw UserNotFoundException("No account for this phone.")
        }

        otpService.requestOtp(phone, request.purpose)
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
                val contact = contactRepository.findByPhoneNumber(phone)
                    ?: throw UserNotFoundException("No account for this phone.")
                contact.user
                    ?: throw UserNotFoundException("Contact has no associated user.")
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
