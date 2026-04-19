package com.mudhut.software.kasisira.utils.exceptions

// Canonical error codes emitted in ErrorResponse bodies. Keep in sync with
// any client-side enum/typing so that the API contract stays stable.
object ErrorCodes {
    const val VALIDATION = "VALIDATION_ERROR"
    const val AUTHENTICATION = "AUTHENTICATION_ERROR"
    const val AUTHORIZATION = "AUTHORIZATION_ERROR"
    const val NOT_FOUND = "NOT_FOUND_ERROR"
    const val MAIL = "MAIL_ERROR"
    const val INTERNAL = "INTERNAL_ERROR"
    const val USER = "USER_ERROR"
    const val REQUEST = "REQUEST_ERROR"
    const val CONFLICT = "CONFLICT_ERROR"
    const val TOKEN = "TOKEN_ERROR"
    const val RATE_LIMITED = "RATE_LIMITED"
    const val OTP = "OTP_ERROR"
}
