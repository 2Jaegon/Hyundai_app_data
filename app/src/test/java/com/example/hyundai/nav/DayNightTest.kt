package com.example.hyundai.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DayNightTest {

    private val seoul = GeoPoint(37.5665, 126.9780)
    private val kst = ZoneId.of("Asia/Seoul")

    private fun at(hour: Int, minute: Int = 0, month: Int = 11, day: Int = 10): Long =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, kst).toInstant().toEpochMilli()

    @Test
    fun `solar elevation at noon in november matches the sun's winter height`() {
        // 11월 10일 서울의 정오 태양 고도는 약 35도 (90 - 위도 37.6 + 적위 -17.2)
        val e = DayNight.solarElevationDeg(seoul, at(12, 30))
        assertEquals(35.0, e, 3.0)
    }

    @Test
    fun `it is dark at midnight and bright at noon`() {
        assertFalse(DayNight.isDaytime(seoul, at(0, 0)))
        assertFalse(DayNight.isDaytime(seoul, at(3, 0)))
        assertTrue(DayNight.isDaytime(seoul, at(12, 0)))
    }

    @Test
    fun `november in seoul turns bright after seven and dark after half past five`() {
        assertFalse(DayNight.isDaytime(seoul, at(6, 0)))
        assertTrue(DayNight.isDaytime(seoul, at(8, 0)))
        assertTrue(DayNight.isDaytime(seoul, at(17, 0)))
        assertFalse(DayNight.isDaytime(seoul, at(18, 30)))
    }

    @Test
    fun `summer evening is still bright when winter evening is already dark`() {
        // 6월 21일 오후 7시는 아직 낮, 11월 10일 오후 7시는 밤
        assertTrue(DayNight.isDaytime(seoul, at(19, 0, month = 6, day = 21)))
        assertFalse(DayNight.isDaytime(seoul, at(19, 0)))
    }

    @Test
    fun `theme override wins over the sun`() {
        val midnight = at(0, 0)
        assertTrue(MapTheme.DAY.isDay(seoul, midnight))
        assertFalse(MapTheme.NIGHT.isDay(seoul, at(12, 0)))
        assertFalse(MapTheme.AUTO.isDay(seoul, midnight))
        assertTrue(MapTheme.AUTO.isDay(seoul, at(12, 0)))
    }

    @Test
    fun `theme cycles auto then day then night`() {
        assertEquals(MapTheme.DAY, MapTheme.AUTO.next())
        assertEquals(MapTheme.NIGHT, MapTheme.DAY.next())
        assertEquals(MapTheme.AUTO, MapTheme.NIGHT.next())
    }
}
