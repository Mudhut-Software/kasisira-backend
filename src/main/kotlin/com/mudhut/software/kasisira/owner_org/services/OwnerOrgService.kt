package com.mudhut.software.kasisira.owner_org.services

import com.mudhut.software.kasisira.owner_org.entities.OwnerOrg

interface OwnerOrgService {
    fun createOrg(creatorUserId: Long, name: String): OwnerOrg
    fun getOrgById(id: Long): OwnerOrg
}
