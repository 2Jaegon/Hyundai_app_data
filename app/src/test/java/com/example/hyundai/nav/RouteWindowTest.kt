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

    @Test
    fun computeNoteRouteZones_mapsSameDirectionNotesToRouteSegments() {
        val route = RouteRepository.fallback()
        val notes = listOf(
            com.example.hyundai.model.VoiceNote(
                id = "n1",
                authorNickname = "성수동",
                authorPetName = "제로",
                authorPetLevel = 3,
                rawText = "안전운전",
                displayText = "안전운전",
                category = com.example.hyundai.model.NoteCategory.SAFETY_3H,
                moderationStatus = com.example.hyundai.model.ModerationStatus.APPROVED,
                roadSegmentName = "성수대교",
                latitude = 37.5448,
                longitude = 127.0462,
                headingDeg = 82f,
                distanceMeters = 180,
                headingDiffDeg = 3f,
                createdAtLabel = "방금",
                remainingLifeLabel = "3시간",
                todakCount = 1,
                listenerCount = 1
            ),
            com.example.hyundai.model.VoiceNote(
                id = "n_opposite",
                authorNickname = "반대차선",
                authorPetName = "제로",
                authorPetLevel = 2,
                rawText = "반대",
                displayText = "반대",
                category = com.example.hyundai.model.NoteCategory.VIBE_7D,
                moderationStatus = com.example.hyundai.model.ModerationStatus.APPROVED,
                roadSegmentName = "반대차선",
                latitude = 37.5450,
                longitude = 127.0445,
                headingDeg = 265f,
                distanceMeters = 120,
                headingDiffDeg = 180f, // Opposite lane (> 45 deg) should be excluded
                createdAtLabel = "방금",
                remainingLifeLabel = "7일",
                todakCount = 1,
                listenerCount = 1
            )
        )
        val zones = com.example.hyundai.viewmodel.computeNoteRouteZones(
            track = route.track,
            notes = notes,
            commuteOnly = false
        )
        assertEquals(1, zones.size)
        assertEquals("n1", zones[0].noteId)
        assertTrue(zones[0].endM > zones[0].startM)
        // 쪽지 한 개가 차지하는 구간은 최대 60m. 더 넓으면 지도에서 너무 길게 강조된다.
        assertTrue(zones[0].endM - zones[0].startM <= 60.0 + 1e-6)
    }
}
