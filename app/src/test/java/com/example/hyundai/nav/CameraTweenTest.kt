package com.example.hyundai.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraTweenTest {

    @Test
    fun lerpPoint_movesLinearly() {
        val a = GeoPoint(37.0, 127.0)
        val b = GeoPoint(38.0, 128.0)
        val mid = GeoMath.lerp(a, b, 0.5)
        assertEquals(37.5, mid.lat, 1e-9)
        assertEquals(127.5, mid.lng, 1e-9)
        assertEquals(a, GeoMath.lerp(a, b, 0.0))
        assertEquals(b, GeoMath.lerp(a, b, 1.0))
    }

    @Test
    fun lerpBearing_takesTheShortWayAcrossNorth() {
        // 350도에서 10도로 가는 가장 짧은 길은 +20도(북쪽을 지나감)다.
        assertEquals(0.0, GeoMath.lerpBearingDeg(350.0, 10.0, 0.5), 1e-9)
        assertEquals(355.0, GeoMath.lerpBearingDeg(350.0, 10.0, 0.25), 1e-9)
    }

    @Test
    fun lerpBearing_staysInZeroTo360() {
        // 10도에서 350도로 가면 -20도(북쪽을 지나감)이고, 절반은 0도다.
        assertEquals(0.0, GeoMath.lerpBearingDeg(10.0, 350.0, 0.5), 1e-9)
        assertEquals(355.0, GeoMath.lerpBearingDeg(10.0, 350.0, 0.75), 1e-9)
    }

    @Test
    fun lerpBearing_endsAtTarget() {
        assertEquals(10.0, GeoMath.lerpBearingDeg(350.0, 10.0, 1.0), 1e-9)
        assertEquals(350.0, GeoMath.lerpBearingDeg(350.0, 10.0, 0.0), 1e-9)
    }
}
