package com.example.hyundai.domain

import com.example.hyundai.model.GearState
import com.example.hyundai.model.NoteCategory
import com.example.hyundai.model.ModerationStatus
import com.example.hyundai.model.TurnSignalState
import com.example.hyundai.model.VoiceNote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleSignalAndGleoTest {

    @Test
    fun `mapVehicleGear maps Pleos VehicleGear enum names accurately`() {
        assertEquals(GearState.P, VehicleSignalTracker.mapVehicleGear("GEAR_PARK"))
        assertEquals(GearState.R, VehicleSignalTracker.mapVehicleGear("GEAR_REVERSE"))
        assertEquals(GearState.N, VehicleSignalTracker.mapVehicleGear("GEAR_NEUTRAL"))
        assertEquals(GearState.D, VehicleSignalTracker.mapVehicleGear("GEAR_DRIVE"))
        assertEquals(GearState.D, VehicleSignalTracker.mapVehicleGear("GEAR_3"))
    }

    @Test
    fun `mapTurnSignal and isHeadlampOn map Pleos constants accurately`() {
        assertEquals(TurnSignalState.LEFT, VehicleSignalTracker.mapTurnSignal("LEFT"))
        assertEquals(TurnSignalState.RIGHT, VehicleSignalTracker.mapTurnSignal("RIGHT"))
        assertEquals(TurnSignalState.OFF, VehicleSignalTracker.mapTurnSignal("OFF"))
        assertTrue(VehicleSignalTracker.isHeadlampOn("ON"))
        assertFalse(VehicleSignalTracker.isHeadlampOn("OFF"))
        assertFalse(VehicleSignalTracker.isHeadlampOn("DAYTIME_RUNNING"))
    }

    @Test
    fun `VehicleSignalTracker computes speed and detects harsh braking from location samples`() {
        val tracker = VehicleSignalTracker()
        // First sample initializes baseline and returns null
        val first = tracker.onLocationSample(37.5445, 127.0440, timestampMs = 1_000L, brakePressed = false)
        assertNull(first)

        // Move ~20m east in 1.0s (~72 km/h)
        val second = tracker.onLocationSample(37.5445, 127.044226, timestampMs = 2_000L, brakePressed = false)
        assertNotNull(second)
        assertTrue(second!!.speedKmh > 35f)

        // Sudden stop in 1.0s with brake pedal pressed -> harsh brake detected
        val third = tracker.onLocationSample(37.5445, 127.044226, timestampMs = 3_000L, brakePressed = true)
        assertNotNull(third)
        assertEquals(0f, third!!.speedKmh, 0.01f)
        assertTrue(third.isHarshBrake)
    }

    @Test
    fun `VehicleSignalTracker tracks turn signal hold duration and odometer deltas`() {
        val tracker = VehicleSignalTracker()
        assertEquals(0f, tracker.onTurnSignalChanged(TurnSignalState.RIGHT, 1_000L), 0.01f)
        assertEquals(TurnSignalState.RIGHT, tracker.activeSignal)
        assertEquals(1.8f, tracker.currentSignalHoldSeconds(2_800L), 0.01f)
        assertEquals(2.2f, tracker.onTurnSignalChanged(TurnSignalState.OFF, 3_200L), 0.01f)
        assertEquals(TurnSignalState.OFF, tracker.activeSignal)

        // Odometer first reading establishes baseline (0 delta), subsequent reading returns delta
        assertEquals(0f, tracker.onOdometerUpdated(14520.0), 0.001f)
        assertEquals(0.6f, tracker.onOdometerUpdated(14520.6), 0.001f)
    }

    @Test
    fun `GleoAiAssistant parses hands-free voice commands during driving`() {
        assertEquals(
            GleoAiAssistant.VoiceCommandIntent.APPROVE_LISTEN,
            GleoAiAssistant.parseVoiceCommand(
                utterance = "응 들려줘",
                isComposeDialogOpen = false,
                hasIncomingOrHeldNote = true,
                isSpeakingTts = false
            )
        )
        assertEquals(
            GleoAiAssistant.VoiceCommandIntent.SEND_LIKE,
            GleoAiAssistant.parseVoiceCommand(
                utterance = "좋아요 고마워",
                isComposeDialogOpen = false,
                hasIncomingOrHeldNote = true,
                isSpeakingTts = false
            )
        )
        assertEquals(
            GleoAiAssistant.VoiceCommandIntent.STOP_OR_SKIP,
            GleoAiAssistant.parseVoiceCommand(
                utterance = "그만 멈춰",
                isComposeDialogOpen = false,
                hasIncomingOrHeldNote = true,
                isSpeakingTts = true
            )
        )
        // When compose dialog is open, even short phrases are treated as note dictation
        assertEquals(
            GleoAiAssistant.VoiceCommandIntent.NOTE_DICTATION,
            GleoAiAssistant.parseVoiceCommand(
                utterance = "응 좋아요",
                isComposeDialogOpen = true,
                hasIncomingOrHeldNote = true,
                isSpeakingTts = false
            )
        )
    }

    @Test
    fun `GleoAiAssistant generates contextual pet speech for headlamp and note categories`() {
        val nightSpeech = GleoAiAssistant.generateContextualPetSpeech(
            roadName = "강변북로",
            speedKmh = 60f,
            gear = GearState.D,
            isNightOrHeadlampOn = true
        )
        assertTrue(nightSpeech.contains("전조등"))

        val sampleNote = VoiceNote(
            id = "n1",
            authorNickname = "운전자",
            authorPetName = "제로",
            authorPetLevel = 2,
            rawText = "앞쪽 포트홀 조심하세요",
            displayText = "앞쪽 포트홀 조심하세요",
            category = NoteCategory.SAFETY_3H,
            moderationStatus = ModerationStatus.APPROVED,
            roadSegmentName = "성수대교",
            latitude = 37.54,
            longitude = 127.04,
            headingDeg = 85f,
            distanceMeters = 100,
            headingDiffDeg = 0f,
            createdAtLabel = "방금",
            remainingLifeLabel = "3시간",
            todakCount = 1,
            listenerCount = 1
        )
        val noteSpeech = GleoAiAssistant.generateContextualPetSpeech(
            roadName = "성수대교",
            speedKmh = 50f,
            gear = GearState.D,
            isNightOrHeadlampOn = false,
            note = sampleNote
        )
        assertTrue(noteSpeech.contains("안전 쪽지"))
    }
}
