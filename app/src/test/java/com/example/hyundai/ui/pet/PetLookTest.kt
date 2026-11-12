package com.example.hyundai.ui.pet

import com.example.hyundai.ui.map.FIXED_CAMERA_ZOOM
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PetLookTest {

    @Test
    fun `level 1 has no accessories`() {
        val look = PetLook.forLevel(1)
        assertFalse(look.scarf)
        assertFalse(look.blush)
        assertFalse(look.crown)
    }

    @Test
    fun `level 2 unlocks scarf`() {
        val look = PetLook.forLevel(2)
        assertTrue(look.scarf)
        assertFalse(look.blush)
        assertFalse(look.crown)
    }

    @Test
    fun `level 3 unlocks blush`() {
        val look = PetLook.forLevel(3)
        assertTrue(look.scarf)
        assertTrue(look.blush)
        assertFalse(look.crown)
    }

    @Test
    fun `level 4 and above unlocks crown`() {
        val look = PetLook.forLevel(4)
        assertTrue(look.scarf)
        assertTrue(look.blush)
        assertTrue(look.crown)
    }

    @Test
    fun `camera zoom is fixed at 18_6 regardless of intersection or building height`() {
        assertEquals(18.6, FIXED_CAMERA_ZOOM, 1e-6)
    }
}
