package com.mudhut.software.kasisira.owner_org.models.response

import com.mudhut.software.kasisira.owner_org.entities.MembershipRole
import java.time.Instant

data class InviteResponse(
    val id: Long,
    val orgId: Long,
    val email: String?,
    val phoneNumber: String?,
    val role: MembershipRole,
    val expiresAt: Instant,
    val acceptedAt: Instant?,
    val revokedAt: Instant?
)
