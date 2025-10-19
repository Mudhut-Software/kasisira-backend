package com.mudhut.software.kasisira.profiles.repositories

import com.mudhut.software.kasisira.profiles.entities.AuthProvider
import com.mudhut.software.kasisira.profiles.entities.User
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface UserRepository : JpaRepository<User, Long> {

    fun findByEmail(email: String): Optional<User>

    fun findByUsername(username: String): Optional<User>

    fun findByProviderAndProviderId(provider: AuthProvider, providerId: String): Optional<User>

    fun existsByEmail(email: String): Boolean

    fun existsByUsername(username: String): Boolean
}
