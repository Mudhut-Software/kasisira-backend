package com.mudhut.software.kasisira.owner_org.repositories

import com.mudhut.software.kasisira.owner_org.entities.Invite
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface InviteRepository : JpaRepository<Invite, Long> {
    fun findByTokenHash(tokenHash: String): Invite?
    fun findAllByOwnerOrgIdAndAcceptedAtIsNullAndRevokedAtIsNull(ownerOrgId: Long): List<Invite>
}
