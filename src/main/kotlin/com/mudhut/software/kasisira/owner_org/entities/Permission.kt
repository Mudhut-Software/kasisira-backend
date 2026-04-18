package com.mudhut.software.kasisira.owner_org.entities

enum class Permission(val key: String) {
    MANAGE_LISTINGS("manage_listings"),
    MANAGE_TENANCIES("manage_tenancies"),
    MANAGE_FAULTS("manage_faults"),
    MANAGE_TEAM("manage_team"),
    MANAGE_BILLING("manage_billing");

    companion object {
        fun fromKey(key: String): Permission? = values().firstOrNull { it.key == key }
    }
}
