package com.mudhut.software.kasisira.properties.entities

enum class PropertyType {
    LAND,       // only sale, never rent (enforced in PropertyServiceImpl)
    HOUSE,      // generic standalone dwelling
    APARTMENT,
    CONDO,
    STUDIO,
    BUNGALOW,
    MANSION,
    TOWNHOUSE,
    DUPLEX,
    PENTHOUSE,
    VILLA
}
