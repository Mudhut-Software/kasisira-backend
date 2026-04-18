package com.mudhut.software.kasisira.owner_org.services

import com.mudhut.software.kasisira.owner_org.entities.Membership
import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg
import com.mudhut.software.kasisira.owner_org.entities.Permission

interface MembershipService {
    fun requirePermission(userId: Long, orgId: Long, permission: Permission)
    fun hasPermission(userId: Long, orgId: Long, permission: Permission): Boolean
    fun listMembersOfOrg(orgId: Long): List<Membership>
    fun listOrgsForUser(userId: Long): List<OwnerOrg>
}
