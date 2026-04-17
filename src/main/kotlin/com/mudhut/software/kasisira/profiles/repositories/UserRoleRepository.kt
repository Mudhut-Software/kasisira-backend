package com.mudhut.software.kasisira.profiles.repositories

import com.mudhut.software.kasisira.profiles.entities.RoleName
import com.mudhut.software.kasisira.profiles.entities.UserRole
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface UserRoleRepository : JpaRepository<UserRole, Long> {

    fun findAllByUserId(userId: Long): List<UserRole>

    fun existsByUserIdAndRoleName(userId: Long, roleName: RoleName): Boolean

    fun deleteByUserIdAndRoleName(userId: Long, roleName: RoleName): Long
}
