package com.example.hyundai.vehicle

import com.example.hyundai.model.GearState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsoleStateParserTest {

    /** 콘솔을 아무도 조작하지 않았을 때 실제로 받은 값과 같은 모양(필요한 부분만). */
    private val untouched = """
        {"gear":{"bridge_ready":false,"actual":null},
         "controls":{"speed_kph":0,"source":"default","input_active":false,"emulator_active":false},
         "driving_mode":{"ad_mode_state":"ready","target_speed_mps":13.8889,"source":"default","emulator_active":false},
         "odometer":{"remaining_range_m":0.0,"source":"default","emulator_active":false},
         "ev_battery":{"state_of_charge":100.0,"charging_activated":false,"source":"default","emulator_active":false},
         "telltales":{"actual":{}},
         "etcs":{"activated":false,"current_balance":50000,"last_transaction":null}}
    """.trimIndent()

    @Test
    fun defaultValuesAreIgnored() {
        val s = ConsoleStateParser.parse(untouched)!!
        assertNull(s.speedKph)
        assertNull(s.gear)
        assertFalse(s.cruiseOn)
        assertNull(s.cruiseTargetKph)
        assertNull(s.batteryPct)
        assertNull(s.rangeKm)
        assertTrue(s.warnings.isEmpty())
        assertNull(s.toll)
    }

    @Test
    fun liveValuesAreRead() {
        val json = """
            {"gear":{"actual":{"value":"Drive"}},
             "controls":{"speed_kph":72.4,"source":"http","input_active":true},
             "driving_mode":{"ad_mode_state":"active","target_speed_mps":27.78,"emulator_active":true},
             "odometer":{"remaining_range_m":52300.0,"emulator_active":true},
             "ev_battery":{"state_of_charge":14.4,"charging_activated":true,"source":"http"},
             "telltales":{"actual":{"SeatBelt":{"state":"On","color":"Red"},"DoorOpen":"Blink",
                                    "TPMS":{"state":"Off"},"TurnSignalLeft":{"state":"Blink"}}},
             "etcs":{"current_balance":47500,
                     "last_transaction":{"tollgate_name":"서울TG","tollgate_type":"exit","amount_paid":2500}}}
        """.trimIndent()
        val s = ConsoleStateParser.parse(json)!!
        assertEquals(72.4f, s.speedKph!!, 0.01f)
        assertEquals(GearState.D, s.gear)
        assertTrue(s.cruiseOn)
        assertEquals(100, s.cruiseTargetKph)
        assertEquals(14, s.batteryPct)
        assertEquals(52, s.rangeKm)
        assertTrue(s.charging)
        // 방향지시등 같은 정보 표시등은 경고로 보지 않는다. 순서는 정해진 표시 순서.
        assertEquals(listOf("안전벨트", "문 열림"), s.warnings)
        val toll = s.toll!!
        assertEquals("서울TG", toll.gateName)
        assertEquals(2500, toll.amountWon)
        assertEquals(47500, toll.balanceWon)
    }

    @Test
    fun tollKeyChangesWithEachPayment() {
        fun state(amount: Int, balance: Int) = """
            {"etcs":{"current_balance":$balance,"last_transaction":{"tollgate_id":"TG1","amount_paid":$amount}}}
        """.trimIndent()
        val first = ConsoleStateParser.parse(state(1000, 49000))!!.toll!!
        val same = ConsoleStateParser.parse(state(1000, 49000))!!.toll!!
        val next = ConsoleStateParser.parse(state(1000, 48000))!!.toll!!
        assertEquals("TG1", first.gateName)
        assertEquals(first.key, same.key)
        assertNotEquals(first.key, next.key)
    }

    @Test
    fun gearAsPlainStringAndUnknownNames() {
        assertEquals(GearState.P, ConsoleStateParser.parse("""{"gear":{"actual":"Park"}}""")!!.gear)
        assertNull(ConsoleStateParser.parse("""{"gear":{"actual":{"value":"Sport"}}}""")!!.gear)
        assertEquals(GearState.R, ConsoleStateParser.mapGear(" reverse "))
    }

    @Test
    fun garbageGivesNull_emptyObjectGivesEmptyState() {
        assertNull(ConsoleStateParser.parse("not json"))
        assertEquals(ConsoleVehicleState(), ConsoleStateParser.parse("{}"))
    }

    @Test
    fun cruiseReadyIsNotOn() {
        val s = ConsoleStateParser.parse(
            """{"driving_mode":{"ad_mode_state":"Ready","target_speed_mps":20,"emulator_active":true}}"""
        )!!
        assertFalse(s.cruiseOn)
        assertNull(s.cruiseTargetKph)
    }
}
