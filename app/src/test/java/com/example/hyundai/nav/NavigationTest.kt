package com.example.hyundai.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoMathTest {

    @Test
    fun `one degree of latitude is about 111 km`() {
        val d = GeoMath.distanceM(GeoPoint(0.0, 0.0), GeoPoint(1.0, 0.0))
        assertEquals(111_195.0, d, 600.0)
    }

    @Test
    fun `bearing follows compass directions`() {
        val o = GeoPoint(0.0, 0.0)
        assertEquals(0.0, GeoMath.bearingDeg(o, GeoPoint(1.0, 0.0)), 0.5)
        assertEquals(90.0, GeoMath.bearingDeg(o, GeoPoint(0.0, 1.0)), 0.5)
        assertEquals(180.0, GeoMath.bearingDeg(o, GeoPoint(-1.0, 0.0)), 0.5)
        assertEquals(270.0, GeoMath.bearingDeg(o, GeoPoint(0.0, -1.0)), 0.5)
    }

    @Test
    fun `angle difference wraps around north`() {
        assertEquals(20.0, GeoMath.angleDiffDeg(350.0, 10.0), 1e-9)
        assertEquals(20.0, GeoMath.angleDiffDeg(10.0, 350.0), 1e-9)
        assertEquals(180.0, GeoMath.angleDiffDeg(90.0, 270.0), 1e-9)
    }

    @Test
    fun `destination point is the requested distance away`() {
        val a = GeoPoint(37.5445, 127.0440)
        val b = GeoMath.destination(a, 45.0, 1_000.0)
        assertEquals(1_000.0, GeoMath.distanceM(a, b), 1.0)
        assertEquals(45.0, GeoMath.bearingDeg(a, b), 0.5)
    }
}

class RouteTrackTest {

    private val east = RouteTrack(
        listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01), GeoPoint(0.0, 0.02))
    )

    @Test
    fun `total length is the sum of segments`() {
        assertEquals(2_224.0, east.totalM, 10.0)
    }

    @Test
    fun `pointAt clamps to both ends`() {
        assertEquals(0.0, east.pointAt(-50.0).lng, 1e-9)
        assertEquals(0.02, east.pointAt(east.totalM + 50.0).lng, 1e-9)
    }

    @Test
    fun `pointAt returns the vertex at the half way mark`() {
        val p = east.pointAt(east.totalM / 2)
        assertEquals(0.01, p.lng, 1e-4)
        assertEquals(0.0, p.lat, 1e-6)
    }

    @Test
    fun `bearingAt follows the segment direction`() {
        assertEquals(90.0, east.bearingAt(500.0), 0.5)
        assertEquals(90.0, east.bearingAt(east.totalM), 0.5)
    }

    @Test
    fun `project finds the distance along the route`() {
        val offRoute = GeoPoint(0.0005, 0.015)
        val along = east.project(offRoute)
        assertEquals(1_668.0, along, 15.0)
    }

    @Test
    fun `distanceToRoute is near zero on the route and large far away`() {
        assertEquals(0.0, east.distanceToRouteM(GeoPoint(0.0, 0.015)), 1.0)
        // 경로에서 북쪽으로 약 111m
        assertEquals(111.0, east.distanceToRouteM(GeoPoint(0.001, 0.015)), 5.0)
        // 지구 반대편이면 아주 크다
        assertTrue(east.distanceToRouteM(GeoPoint(37.4, -122.0)) > 1_000_000.0)
    }

    @Test
    fun `window keeps only the requested stretch`() {
        val w = east.window(500.0, 1_500.0)
        assertEquals(500.0, GeoMath.distanceM(east.points.first(), w.first()), 5.0)
        assertEquals(1_500.0, GeoMath.distanceM(east.points.first(), w.last()), 5.0)
        // 꾸지점(약 1112m)이 중간에 들어 있어야 한다.
        assertEquals(3, w.size)
    }

    @Test
    fun `window clamps to the route ends`() {
        val w = east.window(-100.0, east.totalM + 100.0)
        assertEquals(0.0, w.first().lng, 1e-9)
        assertEquals(0.02, w.last().lng, 1e-9)
    }
}

class NavSnapshotTest {

    private val track = RouteTrack(
        listOf(GeoPoint(0.0, 0.0), GeoPoint(0.0, 0.01), GeoPoint(0.0, 0.02))
    )
    private val steps = listOf(
        RouteStep(distanceAlongM = 1_000.0, turn = TurnIcon.RIGHT, roadName = "A로"),
        RouteStep(distanceAlongM = track.totalM, turn = TurnIcon.ARRIVE, roadName = "")
    )

    @Test
    fun `next turn is the first step ahead of the car`() {
        val s = NavSnapshot.compute(track, steps, progressM = 400.0, speedMps = 20.0)
        assertEquals(TurnIcon.RIGHT, s.nextTurn)
        assertEquals(600.0, s.distToTurnM, 1e-6)
        assertEquals(track.totalM - 400.0, s.remainingM, 1e-6)
        assertFalse(s.arrived)
    }

    @Test
    fun `after a turn the following step becomes next`() {
        val s = NavSnapshot.compute(track, steps, progressM = 1_500.0, speedMps = 20.0)
        assertEquals(TurnIcon.ARRIVE, s.nextTurn)
        assertEquals(track.totalM - 1_500.0, s.distToTurnM, 1e-6)
    }

    @Test
    fun `eta uses speed but never divides by a crawl`() {
        val fast = NavSnapshot.compute(track, steps, progressM = 0.0, speedMps = 20.0)
        assertEquals(track.totalM / 20.0, fast.etaSec.toDouble(), 1.0)
        val stopped = NavSnapshot.compute(track, steps, progressM = 0.0, speedMps = 0.0)
        assertEquals(track.totalM / NavSnapshot.MIN_SPEED_MPS, stopped.etaSec.toDouble(), 1.0)
    }

    @Test
    fun `arrived when progress reaches the end`() {
        val s = NavSnapshot.compute(track, steps, progressM = track.totalM, speedMps = 10.0)
        assertTrue(s.arrived)
        assertEquals(0.0, s.remainingM, 1e-6)
    }
}

class TurnMappingTest {

    @Test
    fun `osrm maneuvers map to icons`() {
        assertEquals(TurnIcon.RIGHT, TurnMapping.fromOsrm("turn", "right"))
        assertEquals(TurnIcon.LEFT, TurnMapping.fromOsrm("turn", "left"))
        assertEquals(TurnIcon.SLIGHT_RIGHT, TurnMapping.fromOsrm("fork", "slight right"))
        assertEquals(TurnIcon.SHARP_LEFT, TurnMapping.fromOsrm("turn", "sharp left"))
        assertEquals(TurnIcon.UTURN, TurnMapping.fromOsrm("turn", "uturn"))
        assertEquals(TurnIcon.ARRIVE, TurnMapping.fromOsrm("arrive", null))
        assertEquals(TurnIcon.ROUNDABOUT, TurnMapping.fromOsrm("roundabout", "right"))
        assertEquals(TurnIcon.STRAIGHT, TurnMapping.fromOsrm("continue", "straight"))
        assertEquals(TurnIcon.RIGHT, TurnMapping.fromOsrm("off ramp", "right"))
    }
}
