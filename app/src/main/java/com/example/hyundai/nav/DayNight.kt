package com.example.hyundai.nav

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/** 지도 색 모드. 자동은 차량 전조등(LowBeam)이 켜져 있으면 즉시 밤으로, 아니면 해가 떠 있는지로 낮/밤을 고른다. */
enum class MapTheme {
    AUTO, DAY, NIGHT;

    fun isDay(
        position: GeoPoint,
        epochMillis: Long,
        headlampOn: Boolean = false
    ): Boolean = when (this) {
        DAY -> true
        NIGHT -> false
        AUTO -> if (headlampOn) false else DayNight.isDaytime(position, epochMillis)
    }

    /** 시연용으로 자동 → 낮 → 밤 순서로 돌린다. */
    fun next(): MapTheme = when (this) {
        AUTO -> DAY
        DAY -> NIGHT
        NIGHT -> AUTO
    }

    companion object {
        /** 저장된 이름에서 되살린다. 없거나 모르는 값이면 자동이다. */
        fun fromName(name: String?): MapTheme = entries.firstOrNull { it.name == name } ?: AUTO
    }
}

/**
 * 위치와 시각으로 해가 떠 있는지 계산한다 (NOAA 태양 위치 공식).
 * 시간대에 상관없이 UTC 시각과 경도만 쓰므로 기기의 시간대 설정에 영향받지 않는다.
 */
object DayNight {

    /** 태양 고도가 이 값을 넘으면 낮으로 본다. 해돋이·해넘이 표준값(-0.833°)이고, 이보다 낮은 어스름은 밤으로 본다. */
    private const val DAY_ELEVATION_DEG = -0.833

    fun isDaytime(position: GeoPoint, epochMillis: Long): Boolean =
        solarElevationDeg(position, epochMillis) > DAY_ELEVATION_DEG

    fun solarElevationDeg(position: GeoPoint, epochMillis: Long): Double {
        val jd = epochMillis / 86_400_000.0 + 2_440_587.5
        val t = (jd - 2_451_545.0) / 36_525.0

        val l0 = ((280.46646 + t * (36_000.76983 + t * 0.0003032)) % 360.0 + 360.0) % 360.0
        val m = rad(357.52911 + t * (35_999.05029 - 0.0001537 * t))
        val e = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)

        val c = sin(m) * (1.914602 - t * (0.004817 + 0.000014 * t)) +
            sin(2 * m) * (0.019993 - 0.000101 * t) +
            sin(3 * m) * 0.000289
        val trueLong = l0 + c
        val omega = rad(125.04 - 1934.136 * t)
        val lambda = rad(trueLong - 0.00569 - 0.00478 * sin(omega))

        val eps0 = 23.0 + (26.0 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60.0) / 60.0
        val eps = rad(eps0 + 0.00256 * cos(omega))
        val decl = asin(sin(eps) * sin(lambda))

        val y = tan(eps / 2).pow(2)
        val l0Rad = rad(l0)
        val eqTimeMin = 4.0 * deg(
            y * sin(2 * l0Rad) - 2 * e * sin(m) + 4 * e * y * sin(m) * cos(2 * l0Rad) -
                0.5 * y * y * sin(4 * l0Rad) - 1.25 * e * e * sin(2 * m)
        )

        val utcMinutes = (epochMillis % 86_400_000L) / 60_000.0
        val trueSolarMin = ((utcMinutes + eqTimeMin + 4.0 * position.lng) % 1440.0 + 1440.0) % 1440.0
        val hourAngle = rad(trueSolarMin / 4.0 - 180.0)

        val lat = rad(position.lat)
        val cosZenith = sin(lat) * sin(decl) + cos(lat) * cos(decl) * cos(hourAngle)
        return 90.0 - deg(acos(cosZenith.coerceIn(-1.0, 1.0)))
    }

    private fun rad(d: Double) = d * PI / 180.0
    private fun deg(r: Double) = r * 180.0 / PI
}
