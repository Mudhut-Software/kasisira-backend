package com.mudhut.software.kasisira.owner_org.controllers

import com.mudhut.software.kasisira.owner_org.mappers.OwnerOrgMapper
import com.mudhut.software.kasisira.owner_org.models.request.CreateOrgRequest
import com.mudhut.software.kasisira.owner_org.models.response.OwnerOrgResponse
import com.mudhut.software.kasisira.owner_org.services.MembershipService
import com.mudhut.software.kasisira.owner_org.services.OwnerOrgService
import com.mudhut.software.kasisira.security.UserPrincipal
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/orgs")
class OwnerOrgController(
    private val ownerOrgService: OwnerOrgService,
    private val membershipService: MembershipService,
    private val mapper: OwnerOrgMapper
) {
    @PostMapping
    fun create(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody body: CreateOrgRequest
    ): ResponseEntity<OwnerOrgResponse> {
        val org = ownerOrgService.createOrg(principal.id, body.name)
        return ResponseEntity.status(201).body(mapper.toResponse(org))
    }

    @GetMapping("/me")
    fun listMine(@AuthenticationPrincipal principal: UserPrincipal): List<OwnerOrgResponse> =
        membershipService.listOrgsForUser(principal.id).map(mapper::toResponse)
}
