package com.mudhut.software.kasisira.owner_org.mappers

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.owner_org.entities.Membership
import com.mudhut.software.kasisira.owner_org.models.response.MembershipResponse
import org.springframework.stereotype.Component

@Component
class MembershipMapper(private val objectMapper: ObjectMapper) {
    fun toResponse(m: Membership): MembershipResponse {
        @Suppress("UNCHECKED_CAST")
        val perms = objectMapper.readValue(m.permissions, Map::class.java) as Map<String, Boolean>
        return MembershipResponse(
            id = m.id,
            orgId = m.ownerOrg.id,
            userId = m.user.id,
            userUsername = m.user.username,
            role = m.role,
            permissions = perms,
            acceptedAt = m.acceptedAt
        )
    }
}
