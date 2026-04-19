package com.mudhut.software.kasisira.owner_org.controllers

import com.mudhut.software.kasisira.owner_org.entities.Permission
import com.mudhut.software.kasisira.owner_org.mappers.MembershipMapper
import com.mudhut.software.kasisira.owner_org.models.response.MembershipResponse
import com.mudhut.software.kasisira.owner_org.services.MembershipService
import com.mudhut.software.kasisira.security.UserPrincipal
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/orgs/{orgId}/members")
class MembershipController(
    private val membershipService: MembershipService,
    private val mapper: MembershipMapper
) {
    @GetMapping
    fun list(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable orgId: Long
    ): List<MembershipResponse> {
        membershipService.requirePermission(principal.id, orgId, Permission.MANAGE_TEAM)
        return membershipService.listMembersOfOrg(orgId).map(mapper::toResponse)
    }
}
