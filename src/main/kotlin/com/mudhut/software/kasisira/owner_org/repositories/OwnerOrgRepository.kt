package com.mudhut.software.kasisira.owner_org.repositories

import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface OwnerOrgRepository : JpaRepository<OwnerOrg, Long> {
    fun findAllByCreatorId(creatorId: Long): List<OwnerOrg>
    fun countByCreatorId(creatorId: Long): Long
}
