package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.email.EmailService
import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.RoleName
import com.mudhut.software.kasisira.profiles.entities.TokenType
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.mappers.UserMapper
import com.mudhut.software.kasisira.profiles.models.request.RegisterRequest
import com.mudhut.software.kasisira.profiles.models.request.SocialLoginRequest
import com.mudhut.software.kasisira.profiles.models.request.UpdateUserRequest
import com.mudhut.software.kasisira.profiles.models.response.UserResponse
import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.utils.PasswordValidator
import com.mudhut.software.kasisira.utils.exceptions.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.regex.Pattern

@Service
@Transactional
class UserServiceImpl : UserService {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var userMapper: UserMapper

    @Autowired
    private lateinit var passwordEncoder: PasswordEncoder

    @Autowired
    private lateinit var passwordValidator: PasswordValidator

    @Autowired
    private lateinit var verificationService: VerificationService

    @Autowired
    private lateinit var emailService: EmailService

    @Autowired
    private lateinit var roleService: RoleService

    companion object {
        private const val EMAIL_PATTERN = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        private const val PHONE_PATTERN = "^\\+[1-9]\\d{1,14}$"
    }

    override fun findById(id: Long): UserResponse {
        val user = userRepository.findById(id)
            .orElseThrow { UserNotFoundException("User with id $id not found") }
        return userMapper.toResponse(user)
    }

    override fun findByEmail(email: String): UserResponse? {
        return userRepository.findByEmail(email)
            .map { userMapper.toResponse(it) }
            .orElse(null)
    }

    override fun findByUsername(username: String): UserResponse? {
        return userRepository.findByUsername(username)
            .map { userMapper.toResponse(it) }
            .orElse(null)
    }

    override fun existsByEmail(email: String): Boolean {
        return userRepository.existsByEmail(email)
    }

    override fun existsByUsername(username: String): Boolean {
        return userRepository.existsByUsername(username)
    }

    override fun registerUser(request: RegisterRequest): UserResponse {
        // Validate email format
        if (!Pattern.compile(EMAIL_PATTERN).matcher(request.email).matches()) {
            throw InvalidEmailFormatException("Invalid email format")
        }

        // Validate phone number format (if provided)
        if (request.phoneNumber != null && request.phoneNumber.isNotEmpty() &&
            !Pattern.compile(PHONE_PATTERN).matcher(request.phoneNumber).matches()
        ) {
            throw InvalidPhoneNumberException("Invalid phone number format")
        }

        // Check if email already exists
        if (existsByEmail(request.email)) {
            throw UserAlreadyExistsException("Email already registered: ${request.email}")
        }

        // Check if username already exists
        if (existsByUsername(request.username)) {
            throw UserAlreadyExistsException("Username already taken: ${request.username}")
        }

        // Validate password
        passwordValidator.validatePassword(request.password)

        // Encode password
        val encodedPassword = passwordEncoder.encode(request.password)

        // Map request to entity
        val user = if (request.phoneNumber != null) {
            userMapper.fromRegisterRequestWithContact(request, encodedPassword!!)
        } else {
            userMapper.fromRegisterRequest(request, encodedPassword!!)
        }

        // Save user
        val savedUser = userRepository.save(user)

        // Grant default TENANT role. Executed within the same @Transactional boundary:
        // if this fails, the user insert above rolls back.
        roleService.grant(savedUser, RoleName.TENANT)

        try {
            // Generate verification token
            val token = verificationService.createVerificationToken(savedUser, TokenType.EMAIL_VERIFICATION)

            // Send verification email
            emailService.sendVerificationEmail(savedUser.email, savedUser.username, token)
        } catch (e: Exception) {
            // Log the error but don't fail the registration
            // User can request a resend later
            println("Failed to send verification email: ${e.message}")
        }

        return userMapper.toResponse(savedUser)
    }

    override fun registerSocialUser(request: SocialLoginRequest): UserResponse {
        // Check if user already exists with this provider
        val existingUser = userRepository.findByProviderAndProviderId(request.provider, request.providerId)

        if (existingUser.isPresent) {
            // Update last login and return existing user
            val user = existingUser.get()
            val updatedUser = user.copy(lastLogin = LocalDateTime.now())
            val saved = userRepository.save(updatedUser)
            return userMapper.toResponse(saved)
        }

        // Check if email already exists
        if (existsByEmail(request.email)) {
            throw UserAlreadyExistsException("Email already registered: ${request.email}")
        }

        // Create new social user
        val username = request.username ?: request.email.substringBefore("@")
        var finalUsername = username

        // Ensure username is unique
        var counter = 1
        while (existsByUsername(finalUsername)) {
            finalUsername = "$username$counter"
            counter++
        }

        val user = userMapper.fromRegisterRequest(
            RegisterRequest(
                username = finalUsername,
                email = request.email,
                password = "", // No password for social login
                provider = request.provider,
                providerId = request.providerId,
                imageUrl = request.imageUrl,
                phoneNumber = null // Social logins don't require phone
            ),
            "" // No password hash for social login
        ).copy(
            passwordHash = null,
            emailVerified = true, // Social logins are pre-verified
            isActive = true
        )

        val savedUser = userRepository.save(user)

        // Grant default TENANT role. Executed within the same @Transactional boundary:
        // if this fails, the user insert above rolls back.
        roleService.grant(savedUser, RoleName.TENANT)

        return userMapper.toResponse(savedUser)
    }

    override fun createOAuthUser(
        username: String,
        email: String,
        provider: AuthProvider,
        providerId: String,
        imageUrl: String?
    ): User {
        val newUser = User(
            id = 0,
            username = username,
            email = email,
            passwordHash = null, // No password for OAuth users
            provider = provider,
            providerId = providerId,
            imageUrl = imageUrl,
            emailVerified = true, // OAuth provider has already verified the email
            isActive = true,
            isEnabled = true
        )
        val savedUser = userRepository.save(newUser)

        // Grant default TENANT role within the same @Transactional boundary:
        // if this fails, the user insert above rolls back.
        roleService.grant(savedUser, RoleName.TENANT)

        return savedUser
    }

    override fun updateUser(id: Long, request: UpdateUserRequest): UserResponse {
        val existingUser = userRepository.findById(id)
            .orElseThrow { UserNotFoundException("User with id $id not found") }

        // Check if email is being changed and if new email is already taken
        if (request.email != null && request.email != existingUser.email && existsByEmail(request.email)) {
            throw UserAlreadyExistsException("Email ${request.email} is already registered")
        }

        // Check if username is being changed and if new username is already taken
        if (request.username != null && request.username != existingUser.username && existsByUsername(request.username)) {
            throw UserAlreadyExistsException("Username ${request.username} is already taken")
        }

        // Update only provided fields
        val updatedUser = existingUser.copy(
            username = request.username ?: existingUser.username,
            email = request.email ?: existingUser.email,
            imageUrl = request.imageUrl ?: existingUser.imageUrl,
            isActive = request.isActive ?: existingUser.isActive,
            isEnabled = request.isEnabled ?: existingUser.isEnabled
        )

        val savedUser = userRepository.save(updatedUser)
        return userMapper.toResponse(savedUser)
    }

    override fun deleteUser(id: Long) {
        if (!userRepository.existsById(id)) {
            throw UserNotFoundException("User with id $id not found")
        }
        userRepository.deleteById(id)
    }

    override fun findAllUsers(): List<UserResponse> {
        val users = userRepository.findAll()
        return userMapper.toResponseList(users)
    }

    override fun updateLastLogin(userId: Long) {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User with id $userId not found") }

        val updatedUser = user.copy(lastLogin = LocalDateTime.now())
        userRepository.save(updatedUser)
    }

    override fun activateUser(userId: Long): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User with id $userId not found") }

        val updatedUser = user.copy(isActive = true)
        val savedUser = userRepository.save(updatedUser)
        return userMapper.toResponse(savedUser)
    }

    override fun deactivateUser(userId: Long): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User with id $userId not found") }

        val updatedUser = user.copy(isActive = false)
        val savedUser = userRepository.save(updatedUser)
        return userMapper.toResponse(savedUser)
    }

    override fun verifyEmail(userId: Long): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException("User with id $userId not found") }

        val updatedUser = user.copy(emailVerified = true)
        val savedUser = userRepository.save(updatedUser)
        return userMapper.toResponse(savedUser)
    }
}
