package com.example.hyundai.ui.pet

import org.junit.Assert.assertEquals
import org.junit.Test

class PetLookTest {

    @Test
    fun level1_hasNoAccessories() {
        assertEquals(PetLook(scarf = false, blush = false, crown = false), PetLook.forLevel(1))
    }

    @Test
    fun level2_addsScarf() {
        assertEquals(PetLook(scarf = true, blush = false, crown = false), PetLook.forLevel(2))
    }

    @Test
    fun level3_addsBlush() {
        assertEquals(PetLook(scarf = true, blush = true, crown = false), PetLook.forLevel(3))
    }

    @Test
    fun level4AndAbove_addsCrown() {
        assertEquals(PetLook(scarf = true, blush = true, crown = true), PetLook.forLevel(4))
        assertEquals(PetLook(scarf = true, blush = true, crown = true), PetLook.forLevel(12))
    }

    @Test
    fun invalidLevel_behavesLikeLevel1() {
        assertEquals(PetLook.forLevel(1), PetLook.forLevel(0))
        assertEquals(PetLook.forLevel(1), PetLook.forLevel(-3))
    }
}
