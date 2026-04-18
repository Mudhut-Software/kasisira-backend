package com.mudhut.software.kasisira.owner_org.controllers

import com.mudhut.software.kasisira.owner_org.mappers.InviteMapper
import com.mudhut.software.kasisira.owner_org.mappers.MembershipMapper
import com.mudhut.software.kasisira.owner_org.models.request.AcceptInviteRequest
import com.mudhut.software.kasisira.owner_org.models.request.InviteMemberRequest
import com.mudhut.software.kasisira.owner_org.models.response.InviteResponse
import com.mudhut.software.kasisira.owner_org.models.response.MembershipResponse
import com.mudhut.software.kasisira.owner_org.services.InviteService
import com.mudhut.software.kasisira.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1")
class InviteController(
    private val inviteService: InviteService,
    private val inviteMapper: InviteMapper,
    private val membershipMapper: MembershipMapper
) {
    @PostMapping("/orgs/{orgId}/invites")
    fun invite(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable orgId: Long,
        @Valid @RequestBody body: InviteMemberRequest
    ): ResponseEntity<InviteResponse> {
        val invite = inviteService.createInvite(
            inviterUserId = principal.id,
            orgId = orgId,
            email = body.email,
            phoneNumber = body.phoneNumber,
            role = body.role,
            permissions = body.permissions
        )
        return ResponseEntity.status(201).body(inviteMapper.toResponse(invite))
    }

    @PostMapping("/invites/accept")
    fun accept(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody body: AcceptInviteRequest
    ): ResponseEntity<MembershipResponse> {
        val membership = inviteService.acceptInvite(principal.id, body.token)
        return ResponseEntity.ok(membershipMapper.toResponse(membership))
    }

    @PostMapping("/invites/{inviteId}/revoke")
    fun revoke(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable inviteId: Long
    ): ResponseEntity<Void> {
        inviteService.revokeInvite(principal.id, inviteId)
        return ResponseEntity.noContent().build()
    }
}
