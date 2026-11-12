package com.example.hyundai.nav

import kotlin.math.max
import kotlin.math.min

/**
 * 지도에 올려 둔 경로선 한 구간. 선은 구간을 한 번만 올려 두고, 차가 지나간 부분은 [fraction]만큼
 * 앞에서부터 투명하게 잘라 낸다. 그래서 선이 차 위치에서 끊김 없이 사라진다.
 * 차가 [REBUILD_M] 넘게 가면 지금 위치 근처부터 새 구간으로 다시 올린다.
 */
class RouteWindow(val startM: Double, val endM: Double) {
    val lengthM: Double get() = (endM - startM).coerceAtLeast(1.0)

    /** 차 위치([progressM])까지 지나온 비율 0~1. 선의 이 비율까지는 보이지 않게 한다. */
    fun fraction(progressM: Double): Float = ((progressM - startM) / lengthM).coerceIn(0.0, 1.0).toFloat()

    fun needsRebuild(progressM: Double): Boolean = progressM < startM || progressM - startM > REBUILD_M

    companion object {
        /** 차 앞으로 올려 두는 길이. 선 그라데이션 해상도(256칸)에 맞춰 짧게 둬서 자르는 경계를 곱게 유지한다. */
        const val AHEAD_M = 1_500.0
        const val REBUILD_M = 500.0

        /** 차 뒤쪽 여유. 위치가 조금 뒤로 튀어도 선을 다시 올리지 않게 하고, 뒤쪽은 그라데이션으로 가려진다. */
        const val BEHIND_M = 50.0

        fun at(progressM: Double, totalM: Double): RouteWindow {
            val start = max(0.0, min(progressM, totalM) - BEHIND_M)
            return RouteWindow(start, min(totalM, start + AHEAD_M))
        }
    }
}
