package com.mudhut.software.kasisira.utils.exceptions

// User-related exceptions
class UserAlreadyExistsException(message: String) : RuntimeException(message)

class UserNotFoundException(message: String) : RuntimeException(message)

class UserNotActiveException(message: String) : RuntimeException(message)

class EmailNotVerifiedException(message: String) : RuntimeException(message)

// Verification-related exceptions
class InvalidTokenException(message: String) : RuntimeException(message)

class TokenExpiredException(message: String) : RuntimeException(message)

class TokenAlreadyUsedException(message: String) : RuntimeException(message)

// Resource not found exceptions
class ResourceNotFoundException(message: String) : RuntimeException(message)

class ContactNotFoundException(message: String) : RuntimeException(message)

// Validation exceptions
class InvalidEmailFormatException(message: String) : RuntimeException(message)

class InvalidPhoneNumberException(message: String) : RuntimeException(message)

class WeakPasswordException(message: String) : RuntimeException(message)

// Mail-related exceptions
class MailSendingException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class MailerSendException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
