package com.mudhut.software.kasisira.security

import com.mudhut.software.kasisira.profiles.entities.User
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.oauth2.core.user.OAuth2User

class UserPrincipal(
    val id: Long,
    val email: String,
    private val password: String?,
    val emailVerified: Boolean,
    val isActive: Boolean,
    val enabled: Boolean,
    private val authorities: Collection<GrantedAuthority>,
    private var attributes: Map<String, Any> = emptyMap()
) : UserDetails, OAuth2User {

    companion object {
        fun create(user: User, roles: Set<String>): UserPrincipal {
            // Spring Security's hasRole('X') expects a GrantedAuthority of "ROLE_X".
            // We prefix here so role names stored in the DB stay unprefixed (e.g. "OWNER")
            // and @PreAuthorize("hasRole('OWNER')") just works.
            // An empty role set is a legitimate state (e.g. a suspended or unrecognized
            // user) and yields an empty authorities collection so hasRole(anything)
            // correctly denies.
            val authorities = roles.map { SimpleGrantedAuthority("ROLE_$it") }

            return UserPrincipal(
                id = user.id,
                email = user.email,
                password = user.passwordHash,
                emailVerified = user.emailVerified,
                isActive = user.isActive,
                enabled = user.isEnabled,
                authorities = authorities
            )
        }

        fun create(user: User, roles: Set<String>, attributes: Map<String, Any>): UserPrincipal {
            val userPrincipal = create(user, roles)
            userPrincipal.attributes = attributes
            return userPrincipal
        }
    }

    override fun getPassword(): String? = password

    override fun getUsername(): String = email

    override fun getAuthorities(): Collection<GrantedAuthority> = authorities

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = enabled && isActive

    override fun getAttributes(): Map<String, Any> = attributes

    override fun getName(): String = id.toString()

    fun setAttributes(attributes: Map<String, Any>) {
        this.attributes = attributes
    }
}
