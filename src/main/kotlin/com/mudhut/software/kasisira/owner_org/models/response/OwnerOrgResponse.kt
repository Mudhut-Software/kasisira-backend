package com.mudhut.software.kasisira.owner_org.models.response

import java.time.Instant

data class OwnerOrgResponse(
    val id: Long,
    val name: String,
    val isVerified: Boolean,
    val createdAt: Instant?
)
