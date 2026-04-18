package com.mudhut.software.kasisira.profiles.models.response

import java.time.Instant

data class ContactResponse(
    val id: Long,
    val phoneNumber: String,
    val isPrimary: Boolean,
    val isVerified: Boolean,
    val label: String?,
    val createdAt: Instant?,
    val updatedAt: Instant?
)
