package com.mudhut.software.kasisira.owner_org.models.request

data class UpdatePermissionsRequest(
    val permissions: Map<String, Boolean>
)
