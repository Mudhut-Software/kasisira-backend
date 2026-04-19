package com.mudhut.software.kasisira.owner_org.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.mudhut.software.kasisira.owner_org.entities.Membership
import com.mudhut.software.kasisira.owner_org.entities.MembershipRole
import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import com.mudhut.software.kasisira.owner_org.repositories.MembershipRepository
import com.mudhut.software.kasisira.owner_org.repositories.OwnerOrgRepository
import com.mudhut.software.kasisira.profiles.entities.RoleName
import com.mudhut.software.kasisira.profiles.repositories.UserRepository
import com.mudhut.software.kasisira.profiles.services.RoleService
import com.mudhut.software.kasisira.utils.exceptions.OrgNotFoundException
import com.mudhut.software.kasisira.utils.exceptions.UserNotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OwnerOrgServiceImpl(
    private val ownerOrgRepository: OwnerOrgRepository,
    private val membershipRepository: MembershipRepository,
    private val userRepository: UserRepository,
    private val roleService: RoleService,
    private val objectMapper: ObjectMapper
) : OwnerOrgService {

    @Transactional
    override fun createOrg(creatorUserId: Long, name: String): OwnerOrg {
        val creator = userRepository.findById(creatorUserId)
            .orElseThrow { UserNotFoundException("User $creatorUserId not found") }

        val org = ownerOrgRepository.save(OwnerOrg(creator = creator, name = name))

        val allPermissions = mapOf(
            "manage_listings"   to true,
            "manage_tenancies"  to true,
            "manage_faults"     to true,
            "manage_team"       to true,
            "manage_billing"    to true
        )
        membershipRepository.save(Membership(
            ownerOrg = org,
            user = creator,
            role = MembershipRole.OWNER,
            permissions = objectMapper.writeValueAsString(allPermissions)
        ))

        roleService.grant(creator, RoleName.OWNER)
        return org
    }

    @Transactional(readOnly = true)
    override fun getOrgById(id: Long): OwnerOrg =
        ownerOrgRepository.findById(id).orElseThrow { OrgNotFoundException("Org $id not found") }
}
