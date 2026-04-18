package com.mudhut.software.kasisira.owner_org.models.response

import com.mudhut.software.kasisira.owner_org.entities.MembershipRole
import java.time.Instant

data class MembershipResponse(
    val id: Long,
    val orgId: Long,
    val userId: Long,
    val userUsername: String,
    val role: MembershipRole,
    val permissions: Map<String, Boolean>,
    val acceptedAt: Instant
)
