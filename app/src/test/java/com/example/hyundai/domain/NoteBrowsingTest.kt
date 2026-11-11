package com.example.hyundai.domain

import com.example.hyundai.model.GearState
import com.example.hyundai.model.ModerationStatus
import com.example.hyundai.model.NoteCategory
import com.example.hyundai.model.VoiceNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteAccessTest {

    @Test
    fun parked_canBrowse() {
        assertTrue(NoteAccess.canBrowse(GearState.P, 0f))
    }

    @Test
    fun parked_withStaleSpeed_canStillBrowse() {
        assertTrue(NoteAccess.canBrowse(GearState.P, 30f))
    }

    @Test
    fun stoppedAtLight_canBrowse() {
        assertTrue(NoteAccess.canBrowse(GearState.D, 0f))
    }

    @Test
    fun sensorNoise_belowThreshold_countsAsStopped() {
        assertTrue(NoteAccess.canBrowse(GearState.D, 0.3f))
    }

    @Test
    fun creeping_cannotBrowse() {
        assertFalse(NoteAccess.canBrowse(GearState.D, 3f))
    }

    @Test
    fun driving_cannotBrowse() {
        assertFalse(NoteAccess.canBrowse(GearState.D, 62f))
    }

    @Test
    fun reversing_cannotBrowse() {
        assertFalse(NoteAccess.canBrowse(GearState.R, 5f))
    }
}

class NoteInboxTest {

    private fun note(
        id: String,
        heard: Boolean = false,
        mine: Boolean = false,
        headingDiff: Float = 3f,
        commute: Boolean = false
    ) = VoiceNote(
        id = id,
        authorNickname = "작성자",
        authorPetName = "제로",
        authorPetLevel = 2,
        rawText = "원문 $id",
        displayText = "표시 $id",
        category = NoteCategory.CHEER_24H,
        moderationStatus = ModerationStatus.APPROVED,
        roadSegmentName = "구간",
        latitude = 37.5,
        longitude = 127.0,
        headingDeg = 90f,
        distanceMeters = 100,
        headingDiffDeg = headingDiff,
        createdAtLabel = "방금",
        remainingLifeLabel = "3시간",
        todakCount = 0,
        listenerCount = 0,
        isListenedByMe = heard,
        isCommuteBandOnly = commute,
        isMine = mine
    )

    @Test
    fun received_keepsOnlyHeardNotesFromOthers_inOrder() {
        val notes = listOf(
            note("a", heard = true),
            note("b"),
            note("c", heard = true, mine = true),
            note("d", heard = true)
        )
        assertEquals(listOf("a", "d"), NoteInbox.received(notes).map { it.id })
    }

    @Test
    fun mine_keepsOnlyMyNotes() {
        val notes = listOf(note("a", heard = true), note("b", mine = true), note("c", mine = true))
        assertEquals(listOf("b", "c"), NoteInbox.mine(notes).map { it.id })
    }

    @Test
    fun nextIncoming_skipsHeardOppositeLaneAndExcluded() {
        val notes = listOf(
            note("heard", heard = true),
            note("opposite", headingDiff = 180f),
            note("excluded"),
            note("target")
        )
        assertEquals("target", NoteInbox.nextIncoming(notes, excludeId = "excluded")?.id)
    }

    @Test
    fun nextIncoming_commuteOnly_skipsOtherRoads() {
        val notes = listOf(note("other"), note("commute", commute = true))
        assertEquals("other", NoteInbox.nextIncoming(notes)?.id)
        assertEquals("commute", NoteInbox.nextIncoming(notes, commuteOnly = true)?.id)
    }

    @Test
    fun nextIncoming_isNullWhenNothingPlayable() {
        val notes = listOf(note("heard", heard = true), note("opposite", headingDiff = 90f))
        assertNull(NoteInbox.nextIncoming(notes))
    }

    @Test
    fun speechTimeout_hasFloorAndCeiling() {
        assertEquals(6_000L, NoteInbox.speechTimeoutMs("짧아요"))
        assertEquals(30_000L, NoteInbox.speechTimeoutMs("가".repeat(500)))
    }

    @Test
    fun introLine_picksParticleByFinalConsonant() {
        assertEquals("제로가 남긴 쪽지예요.", NoteInbox.introLine("제로"))
        assertEquals("고래가 남긴 쪽지예요.", NoteInbox.introLine("고래"))
        assertEquals("돌이 남긴 쪽지예요.", NoteInbox.introLine("돌"))
        assertEquals("Zero가 남긴 쪽지예요.", NoteInbox.introLine("Zero"))
    }

    @Test
    fun speechTimeout_growsWithLength() {
        val short = NoteInbox.speechTimeoutMs("가".repeat(40))
        val long = NoteInbox.speechTimeoutMs("가".repeat(80))
        assertTrue(long > short)
    }
}
