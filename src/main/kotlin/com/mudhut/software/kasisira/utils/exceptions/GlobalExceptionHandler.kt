package com.mudhut.software.kasisira.utils.exceptions

import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.security.core.AuthenticationException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.mail.MailAuthenticationException

@ControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    companion object {
        // Standardized error codes
        private const val ERROR_CODE_VALIDATION = "VALIDATION_ERROR"
        private const val ERROR_CODE_AUTHENTICATION = "AUTHENTICATION_ERROR"
        private const val ERROR_CODE_AUTHORIZATION = "AUTHORIZATION_ERROR"
        private const val ERROR_CODE_NOT_FOUND = "NOT_FOUND_ERROR"
        private const val ERROR_CODE_MAIL = "MAIL_ERROR"
        private const val ERROR_CODE_INTERNAL = "INTERNAL_ERROR"
        private const val ERROR_CODE_USER = "USER_ERROR"
        private const val ERROR_CODE_REQUEST = "REQUEST_ERROR"
        private const val ERROR_CODE_CONFLICT = "CONFLICT_ERROR"
        private const val ERROR_CODE_TOKEN = "TOKEN_ERROR"
    }

    // User-related exception handlers
    @ExceptionHandler(UserAlreadyExistsException::class)
    fun handleUserAlreadyExistsException(ex: UserAlreadyExistsException): ResponseEntity<ErrorResponse> {
        logger.warn("User already exists: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse(ERROR_CODE_CONFLICT, ex.message ?: "User already exists"))
    }

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFoundException(ex: UserNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("User not found: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ERROR_CODE_NOT_FOUND, ex.message ?: "User not found"))
    }

    @ExceptionHandler(UserNotActiveException::class)
    fun handleUserNotActiveException(ex: UserNotActiveException): ResponseEntity<ErrorResponse> {
        logger.warn("User not active: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(ERROR_CODE_USER, ex.message ?: "User account is not active"))
    }

    @ExceptionHandler(EmailNotVerifiedException::class)
    fun handleEmailNotVerifiedException(ex: EmailNotVerifiedException): ResponseEntity<ErrorResponse> {
        logger.warn("Email not verified: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(ERROR_CODE_USER, ex.message ?: "Email address is not verified"))
    }

    // Token-related exception handlers
    @ExceptionHandler(InvalidTokenException::class)
    fun handleInvalidTokenException(ex: InvalidTokenException): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid token: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ERROR_CODE_TOKEN, ex.message ?: "Invalid token"))
    }

    @ExceptionHandler(TokenExpiredException::class)
    fun handleTokenExpiredException(ex: TokenExpiredException): ResponseEntity<ErrorResponse> {
        logger.warn("Token expired: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ERROR_CODE_TOKEN, ex.message ?: "Token has expired"))
    }

    @ExceptionHandler(TokenAlreadyUsedException::class)
    fun handleTokenAlreadyUsedException(ex: TokenAlreadyUsedException): ResponseEntity<ErrorResponse> {
        logger.warn("Token already used: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ERROR_CODE_TOKEN, ex.message ?: "Token has already been used"))
    }

    // Resource not found exceptions
    @ExceptionHandler(ResourceNotFoundException::class)
    fun handleResourceNotFoundException(ex: ResourceNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("Resource not found: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ERROR_CODE_NOT_FOUND, ex.message ?: "Resource not found"))
    }

    @ExceptionHandler(ContactNotFoundException::class)
    fun handleContactNotFoundException(ex: ContactNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("Contact not found: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ERROR_CODE_NOT_FOUND, ex.message ?: "Contact not found"))
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElementException(ex: NoSuchElementException): ResponseEntity<ErrorResponse> {
        logger.warn("Element not found: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ERROR_CODE_NOT_FOUND, ex.message ?: "Resource not found"))
    }

    // Validation exception handlers
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolationException(ex: ConstraintViolationException): ResponseEntity<ErrorResponse> {
        logger.warn("Constraint violation: {}", ex.message)
        val errors = mutableMapOf<String, String>()
        ex.constraintViolations.forEach { violation: ConstraintViolation<*> ->
            val propertyPath = violation.propertyPath.toString()
            val field = propertyPath.substring(propertyPath.lastIndexOf('.') + 1)
            errors[field] = violation.message
        }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ERROR_CODE_VALIDATION, errors))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        logger.warn("Method argument validation failed: {}", ex.message)
        val errors = mutableMapOf<String, String>()
        ex.bindingResult.allErrors.forEach { error ->
            val fieldName = (error as FieldError).field
            val errorMessage = error.defaultMessage ?: "Invalid value"
            errors[fieldName] = errorMessage
        }
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ERROR_CODE_VALIDATION, errors))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(ex: IllegalArgumentException): ResponseEntity<ErrorResponse> {
        logger.warn("Illegal argument: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ERROR_CODE_VALIDATION, ex.message ?: "Invalid argument"))
    }

    @ExceptionHandler(InvalidEmailFormatException::class)
    fun handleInvalidEmailFormatException(ex: InvalidEmailFormatException): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid email format: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ERROR_CODE_VALIDATION, ex.message ?: "Invalid email format"))
    }

    @ExceptionHandler(InvalidPhoneNumberException::class)
    fun handleInvalidPhoneNumberException(ex: InvalidPhoneNumberException): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid phone number: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ERROR_CODE_VALIDATION, ex.message ?: "Invalid phone number format"))
    }

    @ExceptionHandler(WeakPasswordException::class)
    fun handleWeakPasswordException(ex: WeakPasswordException): ResponseEntity<ErrorResponse> {
        logger.warn("Weak password: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ERROR_CODE_VALIDATION, ex.message ?: "Password does not meet security requirements"))
    }

    // Security-related exception handlers
    @ExceptionHandler(BadCredentialsException::class)
    fun handleBadCredentialsException(ex: BadCredentialsException): ResponseEntity<ErrorResponse> {
        logger.warn("Authentication failed: Bad credentials")
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(ERROR_CODE_AUTHENTICATION, "Invalid username or password"))
    }

    @ExceptionHandler(DisabledException::class)
    fun handleDisabledException(ex: DisabledException): ResponseEntity<ErrorResponse> {
        logger.warn("Authentication failed: Account disabled")
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(ERROR_CODE_AUTHENTICATION, "Account is disabled"))
    }

    @ExceptionHandler(LockedException::class)
    fun handleLockedException(ex: LockedException): ResponseEntity<ErrorResponse> {
        logger.warn("Authentication failed: Account locked")
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(ERROR_CODE_AUTHENTICATION, "Account is locked"))
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDeniedException(ex: AccessDeniedException): ResponseEntity<ErrorResponse> {
        logger.warn("Authorization failed: Access denied")
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(ERROR_CODE_AUTHORIZATION, "You don't have permission to access this resource"))
    }

    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationException(ex: AuthenticationException): ResponseEntity<ErrorResponse> {
        logger.warn("Authentication failed: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.UNAUTHORIZED)
            .body(ErrorResponse(ERROR_CODE_AUTHENTICATION, "Authentication failed"))
    }

    // Mail-related exception handlers with sanitized messages
    @ExceptionHandler(MailAuthenticationException::class)
    fun handleMailAuthenticationException(ex: MailAuthenticationException): ResponseEntity<ErrorResponse> {
        logger.error("Mail authentication error: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(ERROR_CODE_MAIL, "Failed to authenticate with mail server"))
    }

    @ExceptionHandler(MailerSendException::class)
    fun handleMailerSendException(ex: MailerSendException): ResponseEntity<ErrorResponse> {
        logger.error("MailerSend error: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(ERROR_CODE_MAIL, "Failed to send email"))
    }

    @ExceptionHandler(MailSendingException::class)
    fun handleMailSendingException(ex: MailSendingException): ResponseEntity<ErrorResponse> {
        logger.error("Mail sending error: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(ERROR_CODE_MAIL, "Failed to send email"))
    }

    // Request body exception handler
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleRequestBodyException(ex: HttpMessageNotReadableException): ResponseEntity<ErrorResponse> {
        logger.warn("Request body error: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ERROR_CODE_REQUEST, "Required request body is missing or malformed"))
    }

    // Property-related exception handlers
    @ExceptionHandler(PropertyNotFoundException::class)
    fun handlePropertyNotFoundException(ex: PropertyNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("Property not found: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ERROR_CODE_NOT_FOUND, ex.message ?: "Property not found"))
    }

    @ExceptionHandler(MediaNotFoundException::class)
    fun handleMediaNotFoundException(ex: MediaNotFoundException): ResponseEntity<ErrorResponse> {
        logger.warn("Media not found: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ERROR_CODE_NOT_FOUND, ex.message ?: "Media not found"))
    }

    @ExceptionHandler(InvalidPropertyConfigurationException::class)
    fun handleInvalidPropertyConfigurationException(ex: InvalidPropertyConfigurationException): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid property configuration: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ERROR_CODE_VALIDATION, ex.message ?: "Invalid property configuration"))
    }

    @ExceptionHandler(UnauthorizedAccessException::class)
    fun handleUnauthorizedAccessException(ex: UnauthorizedAccessException): ResponseEntity<ErrorResponse> {
        logger.warn("Unauthorized access: {}", ex.message)
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(ERROR_CODE_AUTHORIZATION, ex.message ?: "Unauthorized access"))
    }

    // Generic exception handler (catch-all)
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled exception occurred", ex)
        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(ERROR_CODE_INTERNAL, "An unexpected error occurred"))
    }
}
