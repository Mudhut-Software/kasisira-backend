package com.mudhut.software.kasisira.security

import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.utils.exceptions.UserNotFoundException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CustomUserDetailsService : UserDetailsService {

    @Autowired
    private lateinit var userRepository: UserRepository

    @Transactional
    override fun loadUserByUsername(email: String): UserDetails {
        val user = userRepository.findByEmail(email)
            .orElseThrow { UsernameNotFoundException("User not found with email: $email") }

        return UserPrincipal.create(user)
    }

    @Transactional
    fun loadUserById(id: Long): UserDetails {
        val user = userRepository.findById(id)
            .orElseThrow { UserNotFoundException("User not found with id: $id") }

        return UserPrincipal.create(user)
    }
}
