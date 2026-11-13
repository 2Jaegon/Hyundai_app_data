package com.example.hyundai.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteGradientTest {

    private val window = RouteWindow(startM = 1_000.0, endM = 2_000.0)

    @Test
    fun stops_areStrictlyIncreasing_andCoverTheWindow() {
        val zones = listOf(NoteRouteZone("a", 1_300.0, 1_360.0), NoteRouteZone("b", 1_800.0, 1_850.0))
        val stops = RouteGradient.stops(window, progressM = 1_200.0, zones = zones, tailM = 12.0)
        assertEquals(0f, stops.first().position, 1e-6f)
        assertEquals(1f, stops.last().position, 1e-6f)
        for (i in 1 until stops.size) assertTrue(stops[i].position > stops[i - 1].position)
    }

    @Test
    fun drivenPart_isTransparent_andAheadIsOpaque() {
        val stops = RouteGradient.stops(window, progressM = 1_500.0, zones = emptyList(), tailM = 12.0)
        assertEquals(0f, stops.first().alpha, 1e-6f)
        val atCar = stops.first { kotlin.math.abs(it.position - window.fraction(1_500.0)) < 1e-5f }
        assertEquals(1f, atCar.alpha, 1e-6f)
        assertEquals(1f, stops.last().alpha, 1e-6f)
    }

    @Test
    fun tail_fadesOutBehindTheCar_insteadOfCuttingSharply() {
        val stops = RouteGradient.stops(window, progressM = 1_500.0, zones = emptyList(), tailM = 12.0)
        val fadeStart = stops.first { kotlin.math.abs(it.position - window.fraction(1_488.0)) < 1e-5f }
        assertEquals(0f, fadeStart.alpha, 1e-6f)
        // 0과 1 사이는 두 점을 이어 섞으므로, 지워지는 끝이 한 칸 단위로 뚝 넘어가지 않는다.
        assertTrue(stops.count { it.alpha == 0f } >= 1)
        assertTrue(stops.count { it.alpha == 1f } >= 2)
    }

    @Test
    fun noteZone_isColoredOnlyInsideItsRange() {
        val zone = NoteRouteZone("a", 1_600.0, 1_660.0)
        val stops = RouteGradient.stops(window, progressM = 1_100.0, zones = listOf(zone), tailM = 12.0)
        val inside = stops.filter { it.position >= window.fraction(1_600.0) - 1e-6f && it.position <= window.fraction(1_660.0) + 1e-6f }
        assertTrue(inside.isNotEmpty())
        assertTrue(inside.all { it.onNote })
        assertFalse(stops.first().onNote)
        assertFalse(stops.last().onNote)
    }

    @Test
    fun zonesOutsideTheWindow_areIgnored() {
        val stops = RouteGradient.stops(
            window, progressM = 1_100.0,
            zones = listOf(NoteRouteZone("far", 5_000.0, 5_060.0)), tailM = 12.0
        )
        assertTrue(stops.none { it.onNote })
    }
}

class RouteWindowSwitcherTest {

    @Test
    fun firstUpdate_uploadsAndShowsTheCurrentSlot() {
        val s = RouteWindowSwitcher()
        val actions = s.update(progressM = 100.0, totalM = 10_000.0, nowMs = 0)
        assertEquals(2, actions.size)
        assertTrue(actions[0] is RouteWindowSwitcher.Action.Upload)
        assertEquals(RouteWindowSwitcher.Action.Show(0), actions[1])
    }

    @Test
    fun nextWindow_isUploadedToTheHiddenSlot_beforeItIsShown() {
        val s = RouteWindowSwitcher(prepareLeadM = 120.0, settleMs = 600)
        s.update(1_000.0, 10_000.0, 0)
        val start = s.active!!.startM
        // 다시 만들 거리에 가까워지면 숨긴 칸(1)에 먼저 올린다.
        val prep = s.update(start + RouteWindow.REBUILD_M - 100.0, 10_000.0, 1_000)
        assertEquals(1, prep.size)
        assertEquals(1, (prep[0] as RouteWindowSwitcher.Action.Upload).slot)
        assertEquals(0, s.activeSlot)
        // 올린 직후에는 아직 바꾸지 않는다.
        assertTrue(s.update(start + RouteWindow.REBUILD_M + 5.0, 10_000.0, 1_100).isEmpty())
        // 충분히 기다린 뒤 바꾼다.
        val swap = s.update(start + RouteWindow.REBUILD_M + 10.0, 10_000.0, 1_700)
        assertEquals(listOf(RouteWindowSwitcher.Action.Show(1)), swap)
        assertEquals(1, s.activeSlot)
    }

    @Test
    fun jumpOutsideTheWindow_reuploadsImmediately() {
        val s = RouteWindowSwitcher()
        s.update(1_000.0, 10_000.0, 0)
        val actions = s.update(8_000.0, 10_000.0, 100)
        assertTrue(actions.first() is RouteWindowSwitcher.Action.Upload)
        assertTrue(s.active!!.contains(8_000.0))
    }

    @Test
    fun nearRouteEnd_noFurtherWindowIsPrepared() {
        val s = RouteWindowSwitcher()
        s.update(9_600.0, 10_000.0, 0)
        assertTrue(s.update(9_950.0, 10_000.0, 5_000).isEmpty())
    }
}
