package com.mudhut.software.kasisira.utils.exceptions

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val errorCode: String,
    val message: String? = null,
    val errors: Map<String, String>? = null,
    val timestamp: Instant = Instant.now()
) {
    constructor(errorCode: String, message: String) : this(
        errorCode = errorCode,
        message = message,
        errors = null
    )

    constructor(errorCode: String, errors: Map<String, String>) : this(
        errorCode = errorCode,
        message = null,
        errors = errors
    )
}
