package com.mudhut.software.kasisira.profiles.controllers

import com.mudhut.software.kasisira.profiles.entities.TokenType
import com.mudhut.software.kasisira.profiles.models.request.LoginRequest
import com.mudhut.software.kasisira.profiles.models.request.RefreshTokenRequest
import com.mudhut.software.kasisira.profiles.models.request.RegisterRequest
import com.mudhut.software.kasisira.profiles.models.response.TokenResponse
import com.mudhut.software.kasisira.profiles.services.AuthService
import com.mudhut.software.kasisira.profiles.services.UserService
import com.mudhut.software.kasisira.profiles.services.VerificationService
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.view.RedirectView

@RestController
@RequestMapping("/v1/auth")
class AuthController {

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var authService: AuthService

    @Autowired
    private lateinit var verificationService: VerificationService

    @Value("\${app.frontend.url}")
    private lateinit var frontendUrl: String

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: RegisterRequest): ResponseEntity<Map<String, Any>> {
        val user = userService.registerUser(request)

        return ResponseEntity.status(HttpStatus.CREATED).body(
            mapOf(
                "success" to true,
                "message" to "Registration successful! Please check your email to verify your account.",
                "user" to user
            )
        )
    }

    @GetMapping("/verify-email")
    fun verifyEmail(@RequestParam token: String): RedirectView {
        return try {
            // Verify the token and get the user
            val user = verificationService.verifyToken(token, TokenType.EMAIL_VERIFICATION)

            // Activate the user. verifyEmail also enqueues the welcome email
            // via NotificationService so it commits atomically with the
            // emailVerified flag write.
            userService.verifyEmail(user.id)
            userService.activateUser(user.id)

            // Redirect to success page
            RedirectView("$frontendUrl/email-verified?success=true")
        } catch (e: Exception) {
            // Redirect to error page with error message
            RedirectView("$frontendUrl/email-verified?success=false&error=${e.message}")
        }
    }

    @PostMapping("/resend-verification")
    fun resendVerificationEmail(@RequestParam email: String): ResponseEntity<Map<String, Any>> {
        return try {
            verificationService.resendVerificationEmail(email)

            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "message" to "Verification email sent successfully! Please check your inbox."
                )
            )
        } catch (e: NoSuchElementException) {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                mapOf(
                    "success" to false,
                    "message" to e.message.orEmpty()
                )
            )
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().body(
                mapOf(
                    "success" to false,
                    "message" to e.message.orEmpty()
                )
            )
        } catch (e: Exception) {
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf(
                    "success" to false,
                    "message" to "Failed to send verification email. Please try again later."
                )
            )
        }
    }

    @GetMapping("/check-email-verified")
    fun checkEmailVerified(@RequestParam email: String): ResponseEntity<Map<String, Any>> {
        val user = userService.findByEmail(email)

        return if (user != null) {
            ResponseEntity.ok(
                mapOf(
                    "emailVerified" to user.emailVerified,
                    "isActive" to user.isActive
                )
            )
        } else {
            ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                mapOf(
                    "success" to false,
                    "message" to "User not found"
                )
            )
        }
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<TokenResponse> {
        val tokenResponse = authService.login(request, httpRequest)
        return ResponseEntity.ok(tokenResponse)
    }

    @PostMapping("/refresh")
    fun refreshToken(
        @Valid @RequestBody request: RefreshTokenRequest,
        httpRequest: HttpServletRequest
    ): ResponseEntity<TokenResponse> {
        val tokenResponse = authService.refreshToken(request.refreshToken, httpRequest)
        return ResponseEntity.ok(tokenResponse)
    }

    @PostMapping("/logout")
    fun logout(@Valid @RequestBody request: RefreshTokenRequest): ResponseEntity<Map<String, Any>> {
        authService.logout(request.refreshToken)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "message" to "Logged out successfully"
            )
        )
    }

    @PostMapping("/logout-all")
    fun logoutAll(@RequestParam userId: Long): ResponseEntity<Map<String, Any>> {
        authService.logoutAll(userId)
        return ResponseEntity.ok(
            mapOf(
                "success" to true,
                "message" to "Logged out from all devices successfully"
            )
        )
    }
}
