package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.entities.RoleName
import com.mudhut.software.kasisira.profiles.entities.User

interface RoleService {
    fun grant(user: User, roleName: RoleName)
    fun revoke(userId: Long, roleName: RoleName)
    fun rolesOf(userId: Long): Set<RoleName>
    fun has(userId: Long, roleName: RoleName): Boolean
}
