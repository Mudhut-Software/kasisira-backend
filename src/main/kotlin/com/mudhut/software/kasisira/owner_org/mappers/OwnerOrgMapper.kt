package com.mudhut.software.kasisira.owner_org.mappers

import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import com.mudhut.software.kasisira.owner_org.models.response.OwnerOrgResponse
import org.springframework.stereotype.Component

@Component
class OwnerOrgMapper {
    fun toResponse(org: OwnerOrg): OwnerOrgResponse =
        OwnerOrgResponse(
            id = org.id,
            name = org.name,
            isVerified = org.verifiedAt != null,
            createdAt = org.createdAt
        )
}
