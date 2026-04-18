package com.mudhut.software.kasisira.owner_org.mappers

import com.mudhut.software.kasisira.owner_org.entities.Invite
import com.mudhut.software.kasisira.owner_org.models.response.InviteResponse
import org.springframework.stereotype.Component

@Component
class InviteMapper {
    fun toResponse(inv: Invite): InviteResponse =
        InviteResponse(
            id = inv.id,
            orgId = inv.ownerOrg.id,
            email = inv.email,
            phoneNumber = inv.phoneNumber,
            role = inv.role,
            expiresAt = inv.expiresAt,
            acceptedAt = inv.acceptedAt,
            revokedAt = inv.revokedAt
        )
}
