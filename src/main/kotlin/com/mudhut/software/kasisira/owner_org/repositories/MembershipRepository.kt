package com.mudhut.software.kasisira.owner_org.repositories

import com.mudhut.software.kasisira.owner_org.entities.Membership
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface MembershipRepository : JpaRepository<Membership, Long> {
    fun findAllByOwnerOrgId(ownerOrgId: Long): List<Membership>
    fun findAllByUserId(userId: Long): List<Membership>
    fun findByOwnerOrgIdAndUserId(ownerOrgId: Long, userId: Long): Membership?
    fun existsByOwnerOrgIdAndUserId(ownerOrgId: Long, userId: Long): Boolean
    fun countByOwnerOrgId(ownerOrgId: Long): Long
}
