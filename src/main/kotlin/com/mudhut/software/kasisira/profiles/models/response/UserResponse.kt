package com.mudhut.software.kasisira.profiles.models.response

import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import java.time.Instant

data class UserResponse(
    val id: Long,
    val username: String,
    val email: String,
    val provider: AuthProvider,
    val imageUrl: String?,
    val emailVerified: Boolean,
    val isActive: Boolean,
    val isEnabled: Boolean,
    val contacts: List<ContactResponse>,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val lastLogin: Instant?,
    val roles: Set<String> = emptySet()
)
