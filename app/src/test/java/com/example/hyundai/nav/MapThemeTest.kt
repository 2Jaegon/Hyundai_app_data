package com.example.hyundai.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class MapThemeTest {

    @Test
    fun fromName_roundTripsEveryTheme() {
        MapTheme.values().forEach { assertEquals(it, MapTheme.fromName(it.name)) }
    }

    @Test
    fun fromName_fallsBackToAuto_forMissingOrUnknown() {
        assertEquals(MapTheme.AUTO, MapTheme.fromName(null))
        assertEquals(MapTheme.AUTO, MapTheme.fromName(""))
        assertEquals(MapTheme.AUTO, MapTheme.fromName("SUNSET"))
    }
}
