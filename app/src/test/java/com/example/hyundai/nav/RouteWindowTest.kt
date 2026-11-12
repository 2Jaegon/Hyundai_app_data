package com.example.hyundai.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteWindowTest {

    @Test
    fun at_startsJustBehindTheCarAndLooksAhead() {
        val w = RouteWindow.at(progressM = 1_000.0, totalM = 20_000.0)
        assertEquals(1_000.0 - RouteWindow.BEHIND_M, w.startM, 1e-9)
        assertEquals(w.startM + RouteWindow.AHEAD_M, w.endM, 1e-9)
    }

    @Test
    fun at_neverStartsBeforeTheRoute() {
        assertEquals(0.0, RouteWindow.at(progressM = 10.0, totalM = 20_000.0).startM, 1e-9)
    }

    @Test
    fun at_isClippedToRouteEnd() {
        val w = RouteWindow.at(progressM = 19_800.0, totalM = 20_000.0)
        assertEquals(20_000.0, w.endM, 1e-9)
        assertEquals(20_000.0 - (19_800.0 - RouteWindow.BEHIND_M), w.lengthM, 1e-9)
    }

    @Test
    fun at_neverHasZeroLength() {
        val w = RouteWindow.at(progressM = 20_000.0, totalM = 20_000.0)
        assertTrue(w.lengthM > 0.0)
    }

    @Test
    fun fraction_rises_asTheCarMoves() {
        val w = RouteWindow(startM = 1_000.0, endM = 2_000.0)
        assertEquals(0f, w.fraction(1_000.0), 1e-6f)
        assertEquals(0.25f, w.fraction(1_250.0), 1e-6f)
        assertEquals(1f, w.fraction(2_000.0), 1e-6f)
    }

    @Test
    fun fraction_isClampedWhenCarIsOutsideTheWindow() {
        val w = RouteWindow(startM = 1_000.0, endM = 2_000.0)
        assertEquals(0f, w.fraction(500.0), 1e-6f)
        assertEquals(1f, w.fraction(5_000.0), 1e-6f)
    }

    @Test
    fun fraction_atWindowCreation_isASmallPositiveValue() {
        // 차 뒤쪽 여유 구간이 있어서, 만든 직후에도 선의 맨 앞(차 뒤)은 이미 지워진 상태다.
        val w = RouteWindow.at(progressM = 5_000.0, totalM = 20_000.0)
        assertTrue(w.fraction(5_000.0) > 0f)
        assertTrue(w.fraction(5_000.0) < 0.1f)
    }

    @Test
    fun needsRebuild_whenCarDrivesFarFromWindowStart() {
        val w = RouteWindow(startM = 1_000.0, endM = 2_500.0)
        assertFalse(w.needsRebuild(1_000.0))
        assertFalse(w.needsRebuild(1_000.0 + RouteWindow.REBUILD_M))
        assertTrue(w.needsRebuild(1_000.0 + RouteWindow.REBUILD_M + 1.0))
    }

    @Test
    fun needsRebuild_whenCarJumpsBackwardPastTheWindow() {
        assertTrue(RouteWindow(startM = 1_000.0, endM = 2_500.0).needsRebuild(900.0))
    }

    @Test
    fun smallBackwardJitter_doesNotRebuild() {
        // GPS 투영이 몇 m 뒤로 튀어도 매번 선을 다시 올리지 않는다.
        val w = RouteWindow.at(progressM = 5_000.0, totalM = 20_000.0)
        assertFalse(w.needsRebuild(5_000.0 - RouteWindow.BEHIND_M / 2))
    }

    @Test
    fun rebuildDistance_leavesRoomInsideTheWindow() {
        // 다시 만들기 전에 차가 창 끝에 닿으면 앞 선이 비어 보이므로, 창이 항상 더 길어야 한다.
        assertTrue(RouteWindow.AHEAD_M > RouteWindow.REBUILD_M * 2)
    }
}
