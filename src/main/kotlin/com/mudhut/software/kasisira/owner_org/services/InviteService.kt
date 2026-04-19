package com.mudhut.software.kasisira.owner_org.services

import com.mudhut.software.kasisira.owner_org.entities.Invite
import com.mudhut.software.kasisira.owner_org.entities.Membership
import com.mudhut.software.kasisira.owner_org.entities.MembershipRole

interface InviteService {
    fun createInvite(
        inviterUserId: Long,
        orgId: Long,
        email: String?,
        phoneNumber: String?,
        role: MembershipRole,
        permissions: Map<String, Boolean>
    ): Invite

    fun acceptInvite(userId: Long, rawToken: String): Membership

    fun revokeInvite(inviterUserId: Long, inviteId: Long)
}
