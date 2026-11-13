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

    fun contains(progressM: Double): Boolean = progressM in startM..endM

    companion object {
        /**
         * 차 앞으로 올려 두는 길이. 지도 엔진은 선 색을 256칸으로 나눠 칠하므로 구간이 짧을수록 칸이 촘촘하다
         * (900m면 한 칸이 약 3.7m). 고정 줌에서 화면에 보이는 앞쪽 길이는 200m 안팎이라 이 정도면 충분하다.
         */
        const val AHEAD_M = 900.0
        const val REBUILD_M = 400.0

        /** 차 뒤쪽 여유. 위치가 조금 뒤로 튀어도 선을 다시 올리지 않게 하고, 뒤쪽은 그라데이션으로 가려진다. */
        const val BEHIND_M = 50.0

        fun at(progressM: Double, totalM: Double): RouteWindow {
            val start = max(0.0, min(progressM, totalM) - BEHIND_M)
            return RouteWindow(start, min(totalM, start + AHEAD_M))
        }
    }
}

/**
 * 경로선 구간을 두 칸(0, 1)에 번갈아 올린다.
 *
 * 지도 엔진은 새 선 모양(GeoJSON)을 백그라운드에서 처리하고, 선 색(그라데이션)은 바로 바꾼다.
 * 한 칸에서 모양과 색을 같이 바꾸면 몇 프레임 동안 옛 모양에 새 색이 칠해져 선이 툭 튄다.
 * 그래서 다음 구간은 안 보이는 칸에 미리 올려 두고, 다 올라갈 시간([settleMs])이 지난 뒤에 보이는 칸을 바꾼다.
 */
class RouteWindowSwitcher(
    private val prepareLeadM: Double = 120.0,
    private val settleMs: Long = 600L
) {
    sealed interface Action {
        /** [slot] 칸에 [window] 구간의 선 모양을 올린다. */
        data class Upload(val slot: Int, val window: RouteWindow) : Action

        /** 이제부터 [slot] 칸을 보이고, 다른 칸은 숨긴다. */
        data class Show(val slot: Int) : Action
    }

    var active: RouteWindow? = null
        private set
    var activeSlot: Int = 0
        private set
    private var pending: RouteWindow? = null
    private var pendingAtMs = 0L

    /** 경로가 바뀌면 부른다. 다음 [update]에서 지금 칸에 바로 다시 올린다. */
    fun reset() {
        active = null
        pending = null
    }

    fun update(progressM: Double, totalM: Double, nowMs: Long): List<Action> {
        val current = active
        if (current == null || !current.contains(progressM)) {
            // 처음이거나 구간 밖으로 튀었으면(경로 변경, 시뮬레이션 재시작) 지금 칸에 바로 올린다.
            val window = RouteWindow.at(progressM, totalM)
            active = window
            pending = null
            return listOf(Action.Upload(activeSlot, window), Action.Show(activeSlot))
        }
        val next = pending
        if (next == null) {
            val goneM = progressM - current.startM
            if (goneM > RouteWindow.REBUILD_M - prepareLeadM && current.endM < totalM) {
                val window = RouteWindow.at(progressM, totalM)
                pending = window
                pendingAtMs = nowMs
                return listOf(Action.Upload(1 - activeSlot, window))
            }
            return emptyList()
        }
        val ready = nowMs - pendingAtMs >= settleMs
        if (ready && progressM - current.startM > RouteWindow.REBUILD_M && next.contains(progressM)) {
            activeSlot = 1 - activeSlot
            active = next
            pending = null
            return listOf(Action.Show(activeSlot))
        }
        return emptyList()
    }
}
