package com.mudhut.software.kasisira.profiles.services

import com.mudhut.software.kasisira.profiles.entities.RoleName
import com.mudhut.software.kasisira.profiles.entities.User
import com.mudhut.software.kasisira.profiles.entities.UserRole
import com.mudhut.software.kasisira.profiles.repositories.UserRoleRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RoleServiceImpl(
    private val userRoleRepository: UserRoleRepository
) : RoleService {

    @Transactional
    override fun grant(user: User, roleName: RoleName) {
        if (userRoleRepository.existsByUserIdAndRoleName(user.id, roleName)) {
            return
        }
        userRoleRepository.save(UserRole(user = user, roleName = roleName))
    }

    @Transactional
    override fun revoke(userId: Long, roleName: RoleName) {
        userRoleRepository.deleteByUserIdAndRoleName(userId, roleName)
    }

    @Transactional(readOnly = true)
    override fun rolesOf(userId: Long): Set<RoleName> {
        return userRoleRepository.findAllByUserId(userId).map { it.roleName }.toSet()
    }

    @Transactional(readOnly = true)
    override fun has(userId: Long, roleName: RoleName): Boolean {
        return userRoleRepository.existsByUserIdAndRoleName(userId, roleName)
    }
}
