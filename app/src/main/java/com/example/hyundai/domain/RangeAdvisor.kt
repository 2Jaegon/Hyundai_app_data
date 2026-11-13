package com.example.hyundai.domain

/** 배터리로 목적지까지 갈 수 있는지 보고, 충전소 안내가 필요한지 정한다. */
object RangeAdvisor {

    const val LOW_BATTERY_PCT = 20

    /** 남은 주행가능거리가 남은 경로보다 이만큼 넉넉해야 안심한다. */
    private const val RANGE_MARGIN = 1.2

    fun needsCharge(batteryPct: Int?, rangeKm: Int?, remainingRouteM: Double): Boolean {
        if (batteryPct != null && batteryPct <= LOW_BATTERY_PCT) return true
        if (rangeKm != null && remainingRouteM > 0.0) return rangeKm * 1000.0 < remainingRouteM * RANGE_MARGIN
        return false
    }

    /** 주행 화면에 띄울 한 줄. 충전이 필요 없으면 null. */
    fun warning(batteryPct: Int?, rangeKm: Int?, remainingRouteM: Double): String? {
        if (!needsCharge(batteryPct, rangeKm, remainingRouteM)) return null
        val head = when {
            batteryPct != null -> "배터리 $batteryPct%"
            rangeKm != null -> "주행가능 ${rangeKm}km"
            else -> "배터리 부족"
        }
        return "$head · 정차하면 충전소를 찾아 드릴게요"
    }
}
