package com.example.hyundai.nav

import ai.pleos.playground.navi.constants.TurnType

/** 화면에 그릴 회전 아이콘 종류. */
enum class TurnIcon {
    STRAIGHT, RIGHT, LEFT, SLIGHT_RIGHT, SLIGHT_LEFT, SHARP_RIGHT, SHARP_LEFT, UTURN, ROUNDABOUT, ARRIVE
}

/** 경로 시작점 기준 [distanceAlongM] 위치에서 일어나는 회전. */
data class RouteStep(
    val distanceAlongM: Double,
    val turn: TurnIcon,
    val roadName: String
)

object TurnMapping {

    /** OSRM maneuver(type, modifier) → 아이콘. */
    fun fromOsrm(type: String, modifier: String?): TurnIcon {
        if (type == "arrive") return TurnIcon.ARRIVE
        if (type == "roundabout" || type == "rotary" || type == "roundabout turn") return TurnIcon.ROUNDABOUT
        return when (modifier) {
            "right" -> TurnIcon.RIGHT
            "left" -> TurnIcon.LEFT
            "slight right" -> TurnIcon.SLIGHT_RIGHT
            "slight left" -> TurnIcon.SLIGHT_LEFT
            "sharp right" -> TurnIcon.SHARP_RIGHT
            "sharp left" -> TurnIcon.SHARP_LEFT
            "uturn" -> TurnIcon.UTURN
            else -> TurnIcon.STRAIGHT
        }
    }

    /** Pleos NaviHelper TBT 종류 → 아이콘. */
    fun fromPleos(type: TurnType): TurnIcon = when (type) {
        TurnType.TURN_RIGHT -> TurnIcon.RIGHT
        TurnType.TURN_LEFT -> TurnIcon.LEFT
        TurnType.TURN_KEEP_R,
        TurnType.TURN_ENTER_IC_R,
        TurnType.TURN_EXIT_IC_R,
        TurnType.TURN_ENTER_UNDER_R,
        TurnType.TURN_AVOID_UNDER_R,
        TurnType.TURN_ENTER_OVER_R,
        TurnType.TURN_AVOID_OVER_R -> TurnIcon.SLIGHT_RIGHT
        TurnType.TURN_KEEP_L,
        TurnType.TURN_ENTER_IC_L,
        TurnType.TURN_EXIT_IC_L,
        TurnType.TURN_ENTER_UNDER_L,
        TurnType.TURN_AVOID_UNDER_L,
        TurnType.TURN_ENTER_OVER_L,
        TurnType.TURN_AVOID_OVER_L -> TurnIcon.SLIGHT_LEFT
        TurnType.TURN_SHARP_R -> TurnIcon.SHARP_RIGHT
        TurnType.TURN_SHARP_L -> TurnIcon.SHARP_LEFT
        TurnType.TURN_UTURN -> TurnIcon.UTURN
        TurnType.TURN_ENTER_ROUNDABOUT_02,
        TurnType.TURN_ENTER_ROUNDABOUT_03,
        TurnType.TURN_ENTER_ROUNDABOUT_04,
        TurnType.TURN_ENTER_ROUNDABOUT_06,
        TurnType.TURN_ENTER_ROUNDABOUT_08,
        TurnType.TURN_ENTER_ROUNDABOUT_09,
        TurnType.TURN_ENTER_ROUNDABOUT_10,
        TurnType.TURN_ENTER_ROUNDABOUT_12,
        TurnType.TURN_EXIT_ROUNDABOUT -> TurnIcon.ROUNDABOUT
        TurnType.TURN_ARRIVE -> TurnIcon.ARRIVE
        else -> TurnIcon.STRAIGHT
    }
}

/** 어느 한 순간의 안내 상태 (순수 계산 결과). */
data class NavSnapshot(
    val nextTurn: TurnIcon,
    val distToTurnM: Double,
    val roadName: String,
    val remainingM: Double,
    val etaSec: Int,
    val arrived: Boolean
) {
    companion object {
        const val MIN_SPEED_MPS = 5.0

        fun compute(
            track: RouteTrack,
            steps: List<RouteStep>,
            progressM: Double,
            speedMps: Double
        ): NavSnapshot {
            val progress = progressM.coerceIn(0.0, track.totalM)
            val remaining = (track.totalM - progress).coerceAtLeast(0.0)
            val arrived = remaining <= ARRIVE_EPSILON_M
            val next = steps.firstOrNull { it.distanceAlongM > progress + PASSED_MARGIN_M }
            val speed = speedMps.coerceAtLeast(MIN_SPEED_MPS)
            return NavSnapshot(
                nextTurn = next?.turn ?: TurnIcon.ARRIVE,
                distToTurnM = if (next != null) (next.distanceAlongM - progress).coerceAtLeast(0.0) else remaining,
                roadName = next?.roadName.orEmpty(),
                remainingM = remaining,
                etaSec = (remaining / speed).toInt(),
                arrived = arrived
            )
        }

        private const val ARRIVE_EPSILON_M = 0.5
        private const val PASSED_MARGIN_M = 0.0
    }
}
