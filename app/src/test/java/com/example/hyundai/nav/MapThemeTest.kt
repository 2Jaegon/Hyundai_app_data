package com.example.hyundai.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapThemeTest {

    @Test
    fun fromName_roundTripsEveryTheme() {
        MapTheme.entries.forEach { assertEquals(it, MapTheme.fromName(it.name)) }
    }

    @Test
    fun fromName_fallsBackToAuto_forMissingOrUnknown() {
        assertEquals(MapTheme.AUTO, MapTheme.fromName(null))
        assertEquals(MapTheme.AUTO, MapTheme.fromName(""))
        assertEquals(MapTheme.AUTO, MapTheme.fromName("SUNSET"))
    }

    @Test
    fun isDay_overridesToNight_whenHeadlampIsOnInAutoMode() {
        val seoul = GeoPoint(37.5665, 126.9780)
        // 2026-06-21 03:00 UTC = 12:00 KST (midday in Seoul)
        val middayUtcMs = 1782010800_000L
        assertTrue(MapTheme.AUTO.isDay(seoul, middayUtcMs, headlampOn = false))
        assertFalse(MapTheme.AUTO.isDay(seoul, middayUtcMs, headlampOn = true))
        // Explicit DAY theme ignores headlamp
        assertTrue(MapTheme.DAY.isDay(seoul, middayUtcMs, headlampOn = true))
    }
}
