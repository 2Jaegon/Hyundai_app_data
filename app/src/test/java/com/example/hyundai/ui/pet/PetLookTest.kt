package com.example.hyundai.ui.pet

import com.example.hyundai.model.GearState
import com.example.hyundai.model.MannerTurnPhase
import com.example.hyundai.ui.map.FIXED_CAMERA_ZOOM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetLookTest {

    @Test
    fun `level 1 has no accessories and uses PUPPY stage`() {
        val look = PetLook.forLevel(1)
        assertFalse(look.scarf)
        assertFalse(look.blush)
        assertFalse(look.crown)
        assertEquals(PetPose.PUPPY, look.stagePose)
        assertEquals(0, PetPose.PUPPY.gridRow)
        assertEquals(2, PetPose.PUPPY.gridCol)
    }

    @Test
    fun `level 2 unlocks scarf and uses ADULT stage`() {
        val look = PetLook.forLevel(2)
        assertTrue(look.scarf)
        assertFalse(look.blush)
        assertFalse(look.crown)
        assertEquals(PetPose.ADULT, look.stagePose)
        assertEquals(1, PetPose.ADULT.gridRow)
        assertEquals(2, PetPose.ADULT.gridCol)
    }

    @Test
    fun `level 3 unlocks blush and uses ADULT stage`() {
        val look = PetLook.forLevel(3)
        assertTrue(look.scarf)
        assertTrue(look.blush)
        assertFalse(look.crown)
        assertEquals(PetPose.ADULT, look.stagePose)
    }

    @Test
    fun `level 4 and above unlocks crown and uses SENIOR stage`() {
        val look = PetLook.forLevel(4)
        assertTrue(look.scarf)
        assertTrue(look.blush)
        assertTrue(look.crown)
        assertEquals(PetPose.SENIOR, look.stagePose)
        assertEquals(2, PetPose.SENIOR.gridRow)
        assertEquals(2, PetPose.SENIOR.gridCol)
    }

    @Test
    fun `all 9 grid poses map to situations and manner levels`() {
        assertEquals(9, PetPose.entries.size)
        // 1. Level-based parked idle poses: PUPPY (Lv.1), ADULT (Lv.2-3), SENIOR (Lv.4+)
        assertEquals(PetPose.PUPPY, PetPose.resolve(level = 1, gear = GearState.P))
        assertEquals(PetPose.ADULT, PetPose.resolve(level = 2, gear = GearState.P))
        assertEquals(PetPose.SENIOR, PetPose.resolve(level = 4, gear = GearState.P))

        // 2. Driving pose: RUN
        assertEquals(PetPose.RUN, PetPose.resolve(level = 2, gear = GearState.D, speedKmh = 60f))

        // 3. Traffic light stop or night parked rest: SLEEP
        assertEquals(PetPose.SLEEP, PetPose.resolve(level = 2, gear = GearState.D, speedKmh = 0f))
        assertEquals(PetPose.SLEEP, PetPose.resolve(level = 2, gear = GearState.P, headlampOn = true))

        // 4. Manner turn verified reward: EAT
        assertEquals(
            PetPose.EAT,
            PetPose.resolve(level = 2, gear = GearState.D, speedKmh = 50f, mannerPhase = MannerTurnPhase.VERIFIED)
        )

        // 5. Turn signal holding / waiting for voice approval / STT recording: BEG
        assertEquals(
            PetPose.BEG,
            PetPose.resolve(level = 2, gear = GearState.D, speedKmh = 50f, mannerPhase = MannerTurnPhase.SIGNAL_ON_HOLDING)
        )
        assertEquals(
            PetPose.BEG,
            PetPose.resolve(level = 2, gear = GearState.D, speedKmh = 50f, waitingForVoiceApproval = true)
        )
        assertEquals(
            PetPose.BEG,
            PetPose.resolve(level = 2, gear = GearState.P, isRecordingStt = true)
        )

        // 6. Cooldown after manner turn or playing with ball: PLAY
        assertEquals(
            PetPose.PLAY,
            PetPose.resolve(level = 2, gear = GearState.D, speedKmh = 50f, mannerPhase = MannerTurnPhase.COOLDOWN)
        )
        assertEquals(
            PetPose.PLAY,
            PetPose.resolve(level = 2, gear = GearState.P, playingWithBall = true)
        )

        // 7. Listening to note or excited: HAPPY
        assertEquals(
            PetPose.HAPPY,
            PetPose.resolve(level = 2, gear = GearState.D, speedKmh = 50f, holdingNote = true)
        )
        assertEquals(
            PetPose.HAPPY,
            PetPose.resolve(level = 2, gear = GearState.P, excited = true)
        )
    }

    @Test
    fun `camera zoom is fixed at 18_6 regardless of intersection or building height`() {
        assertEquals(18.6, FIXED_CAMERA_ZOOM, 1e-6)
    }
}
