package com.mudhut.software.kasisira.security

import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.repositories.RefreshTokenRepository
import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.profiles.entities.RefreshToken
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDateTime

@Component
class OAuth2AuthenticationSuccessHandler : SimpleUrlAuthenticationSuccessHandler() {

    @Autowired
    private lateinit var jwtTokenProvider: JwtTokenProvider

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var refreshTokenRepository: RefreshTokenRepository

    @Value("\${app.frontend.url}")
    private lateinit var frontendUrl: String

    @Value("\${app.jwt.refresh-token-expiration-ms}")
    private var refreshTokenExpirationMs: Long = 0

    private val logger = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler::class.java)

    override fun onAuthenticationSuccess(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ) {
        val targetUrl = determineTargetUrl(request, response, authentication)

        if (response.isCommitted) {
            logger.debug("Response has already been committed. Unable to redirect to $targetUrl")
            return
        }

        clearAuthenticationAttributes(request)
        redirectStrategy.sendRedirect(request, response, targetUrl)
    }

    override fun determineTargetUrl(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authentication: Authentication
    ): String {
        val oauth2User = authentication.principal as OAuth2User
        val email = oauth2User.getAttribute<String>("email")
            ?: throw IllegalStateException("Email not found from OAuth2 provider")

        // Find or create user with account linking
        val user = findOrCreateUser(oauth2User, email)

        // Generate tokens
        val accessToken = jwtTokenProvider.generateAccessToken(user.id, user.email)
        val refreshToken = jwtTokenProvider.generateRefreshToken(user.id)

        // Save refresh token
        saveRefreshToken(user, refreshToken, request)

        // Build redirect URL with tokens
        return UriComponentsBuilder.fromUriString(frontendUrl)
            .path("/oauth2/redirect")
            .queryParam("token", accessToken)
            .queryParam("refreshToken", refreshToken)
            .build()
            .toUriString()
    }

    private fun findOrCreateUser(oauth2User: OAuth2User, email: String): User {
        val providerId = oauth2User.getAttribute<String>("sub")
            ?: throw IllegalStateException("Provider ID not found from OAuth2 provider")

        // Try to find user by provider and providerId
        val existingUserByProvider = userRepository.findByProviderAndProviderId(
            AuthProvider.GOOGLE,
            providerId
        )

        if (existingUserByProvider.isPresent) {
            // User already exists with this Google account
            val user = existingUserByProvider.get()
            val updatedUser = user.copy(lastLogin = LocalDateTime.now())
            return userRepository.save(updatedUser)
        }

        // Try to find user by email (account linking)
        val existingUserByEmail = userRepository.findByEmail(email)

        return if (existingUserByEmail.isPresent) {
            // Link Google account to existing user
            val user = existingUserByEmail.get()

            // Only link if user doesn't already have a different provider
            if (user.provider == AuthProvider.LOCAL || user.providerId == null) {
                val linkedUser = user.copy(
                    provider = AuthProvider.GOOGLE,
                    providerId = providerId,
                    imageUrl = oauth2User.getAttribute("picture") ?: user.imageUrl,
                    emailVerified = true, // Google emails are verified
                    isActive = true,
                    lastLogin = LocalDateTime.now()
                )
                userRepository.save(linkedUser)
            } else {
                // User already linked to a different provider
                throw IllegalStateException("Email already registered with a different provider")
            }
        } else {
            // Create new user from Google data
            val username = generateUsername(email, oauth2User)

            val newUser = User(
                id = 0,
                username = username,
                email = email,
                passwordHash = null, // No password for OAuth users
                provider = AuthProvider.GOOGLE,
                providerId = providerId,
                imageUrl = oauth2User.getAttribute("picture"),
                emailVerified = true,
                isActive = true,
                isEnabled = true
            )
            userRepository.save(newUser)
        }
    }

    private fun generateUsername(email: String, oauth2User: OAuth2User): String {
        var username = oauth2User.getAttribute<String>("name")
            ?: oauth2User.getAttribute<String>("given_name")
            ?: email.substringBefore("@")

        username = username.replace(" ", "_").lowercase()

        // Ensure username is unique
        var finalUsername = username
        var counter = 1
        while (userRepository.existsByUsername(finalUsername)) {
            finalUsername = "$username$counter"
            counter++
        }

        return finalUsername
    }

    private fun saveRefreshToken(user: User, token: String, request: HttpServletRequest) {
        val expiresAt = LocalDateTime.now().plusSeconds(refreshTokenExpirationMs / 1000)

        val refreshToken = RefreshToken(
            token = token,
            user = user,
            expiresAt = expiresAt,
            deviceInfo = request.getHeader("User-Agent"),
            ipAddress = request.remoteAddr
        )

        refreshTokenRepository.save(refreshToken)
    }
}
