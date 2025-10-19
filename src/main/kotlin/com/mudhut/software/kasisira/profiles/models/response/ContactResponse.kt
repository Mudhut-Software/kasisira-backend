package com.mudhut.software.kasisira.profiles.models.response

import java.time.LocalDateTime

data class ContactResponse(
    val id: Long,
    val phoneNumber: String,
    val isPrimary: Boolean,
    val isVerified: Boolean,
    val label: String?,
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?
)
