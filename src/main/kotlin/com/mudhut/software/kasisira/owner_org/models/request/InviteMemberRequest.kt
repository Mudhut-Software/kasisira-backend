package com.mudhut.software.kasisira.owner_org.models.request

import com.mudhut.software.kasisira.owner_org.entities.MembershipRole
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Pattern

data class InviteMemberRequest(
    @field:Email(message = "Email must be valid")
    val email: String? = null,

    @field:Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "Phone must be E.164")
    val phoneNumber: String? = null,

    val role: MembershipRole = MembershipRole.MANAGER,

    val permissions: Map<String, Boolean> = emptyMap()
) {
    @AssertTrue(message = "Either email or phoneNumber is required")
    fun isRecipientProvided(): Boolean = email != null || phoneNumber != null
}
