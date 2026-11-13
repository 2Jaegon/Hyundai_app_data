package com.example.hyundai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RangeAdvisorTest {

    @Test
    fun lowBatteryNeedsCharge() {
        assertTrue(RangeAdvisor.needsCharge(batteryPct = 20, rangeKm = null, remainingRouteM = 0.0))
        assertFalse(RangeAdvisor.needsCharge(batteryPct = 21, rangeKm = null, remainingRouteM = 0.0))
    }

    @Test
    fun rangeShorterThanRouteWithMarginNeedsCharge() {
        // 남은 경로 10km → 12km는 있어야 안심.
        assertTrue(RangeAdvisor.needsCharge(batteryPct = 80, rangeKm = 11, remainingRouteM = 10_000.0))
        assertFalse(RangeAdvisor.needsCharge(batteryPct = 80, rangeKm = 13, remainingRouteM = 10_000.0))
        // 경로가 없으면 거리로는 판단하지 않는다.
        assertFalse(RangeAdvisor.needsCharge(batteryPct = null, rangeKm = 1, remainingRouteM = 0.0))
    }

    @Test
    fun unknownValuesNeverWarn() {
        assertNull(RangeAdvisor.warning(null, null, 50_000.0))
    }

    @Test
    fun warningTextShowsBatteryFirst() {
        assertEquals("배터리 14% · 주차하면 충전소를 찾아 드릴게요", RangeAdvisor.warning(14, 30, 10_000.0))
        assertEquals("주행가능 5km · 주차하면 충전소를 찾아 드릴게요", RangeAdvisor.warning(null, 5, 10_000.0))
    }

    @Test
    fun warningNamesRangeWhenOnlyRangeIsShort() {
        // 배터리는 넉넉해도 남은 경로보다 주행가능거리가 짧으면, 배터리가 아니라 거리를 알려야 한다.
        assertEquals("주행가능 10km · 주차하면 충전소를 찾아 드릴게요", RangeAdvisor.warning(60, 10, 20_000.0))
        assertNull(RangeAdvisor.warning(60, 300, 20_000.0))
    }
}
