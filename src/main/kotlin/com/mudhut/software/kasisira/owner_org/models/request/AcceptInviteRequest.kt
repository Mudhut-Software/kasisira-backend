package com.mudhut.software.kasisira.owner_org.models.request

import jakarta.validation.constraints.NotBlank

data class AcceptInviteRequest(
    @field:NotBlank val token: String
)
