package com.mudhut.software.kasisira.profiles.models.response

import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import java.time.LocalDateTime

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
    val createdAt: LocalDateTime?,
    val updatedAt: LocalDateTime?,
    val lastLogin: LocalDateTime?
)
