package com.mudhut.software.kasisira.properties.entities

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PropertyTypeTest {

    @Test
    fun `enum contains all expected dwelling types`() {
        val expected = setOf(
            "LAND",
            "HOUSE",
            "APARTMENT",
            "CONDO",
            "STUDIO",
            "BUNGALOW",
            "MANSION",
            "TOWNHOUSE",
            "DUPLEX",
            "PENTHOUSE",
            "VILLA"
        )
        val actual = PropertyType.values().map { it.name }.toSet()
        assertEquals(expected, actual)
    }
}
