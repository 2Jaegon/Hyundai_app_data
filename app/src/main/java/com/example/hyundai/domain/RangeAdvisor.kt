package com.example.hyundai.domain

/** 배터리로 목적지까지 갈 수 있는지 보고, 충전소 안내가 필요한지 정한다. */
object RangeAdvisor {

    const val LOW_BATTERY_PCT = 20

    /** 남은 주행가능거리가 남은 경로보다 이만큼 넉넉해야 안심한다. */
    private const val RANGE_MARGIN = 1.2

    fun needsCharge(batteryPct: Int?, rangeKm: Int?, remainingRouteM: Double): Boolean =
        isLowBattery(batteryPct) || isRangeShort(rangeKm, remainingRouteM)

    /** 주행 화면에 띄울 한 줄. 충전이 필요 없으면 null. 경고를 띄운 이유(배터리 또는 주행가능거리)를 앞에 쓴다. */
    fun warning(batteryPct: Int?, rangeKm: Int?, remainingRouteM: Double): String? {
        val head = when {
            isLowBattery(batteryPct) -> "배터리 $batteryPct%"
            isRangeShort(rangeKm, remainingRouteM) -> "주행가능 ${rangeKm}km"
            else -> return null
        }
        return "$head · 주차하면 충전소를 찾아 드릴게요"
    }

    private fun isLowBattery(batteryPct: Int?): Boolean = batteryPct != null && batteryPct <= LOW_BATTERY_PCT

    private fun isRangeShort(rangeKm: Int?, remainingRouteM: Double): Boolean =
        rangeKm != null && remainingRouteM > 0.0 && rangeKm * 1000.0 < remainingRouteM * RANGE_MARGIN
}
