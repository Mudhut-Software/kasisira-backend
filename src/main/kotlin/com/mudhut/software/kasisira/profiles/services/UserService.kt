package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.models.request.RegisterRequest
import com.mudhut.software.kasisira.profiles.models.request.SocialLoginRequest
import com.mudhut.software.kasisira.profiles.models.request.UpdateUserRequest
import com.mudhut.software.kasisira.profiles.models.response.UserResponse

interface UserService {

    fun findById(id: Long): UserResponse

    fun findByEmail(email: String): UserResponse?

    fun findByUsername(username: String): UserResponse?

    fun existsByEmail(email: String): Boolean

    fun existsByUsername(username: String): Boolean

    fun registerUser(request: RegisterRequest): UserResponse

    fun registerSocialUser(request: SocialLoginRequest): UserResponse

    /**
     * Creates a new OAuth-provisioned user and grants the default TENANT role in a single
     * transaction. If the role grant fails, the user insert rolls back so we never persist
     * a user without a role. Returns the managed [User] entity so the OAuth success handler
     * can issue tokens using the generated id.
     */
    fun createOAuthUser(
        username: String,
        email: String,
        provider: AuthProvider,
        providerId: String,
        imageUrl: String?
    ): User

    fun updateUser(id: Long, request: UpdateUserRequest): UserResponse

    fun deleteUser(id: Long)

    fun findAllUsers(): List<UserResponse>

    fun updateLastLogin(userId: Long)

    fun activateUser(userId: Long): UserResponse

    fun deactivateUser(userId: Long): UserResponse

    fun verifyEmail(userId: Long): UserResponse
}
