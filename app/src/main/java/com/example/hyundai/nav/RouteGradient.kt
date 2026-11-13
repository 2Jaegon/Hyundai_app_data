package com.example.hyundai.nav

/** 경로선 그라데이션의 한 점. [position]은 구간 안 비율 0~1, [onNote]면 쪽지 구간 색, [alpha]는 0(안 보임)~1. */
data class RouteStop(val position: Float, val onNote: Boolean, val alpha: Float)

/**
 * 경로선 한 구간의 색 칠하기 계획.
 *
 * - 차가 지나간 곳은 투명하게 지운다. 딱 자르지 않고 차 뒤 [tailM] 동안 서서히 옅어지게 해서,
 *   지도 엔진이 선 색을 256칸으로 나눠 칠해도 지워지는 끝이 칸 단위로 뚝뚝 넘어가지 않는다.
 * - 쪽지 구간([zones])은 같은 선 위에서 살구색으로 칠한다. 쪽지 구간을 따로 매 프레임 올리지 않아도 된다.
 */
object RouteGradient {
    /** 색이 바뀌는 경계를 이만큼 좁게 섞는다(m). */
    private const val EDGE_M = 0.5

    fun stops(
        window: RouteWindow,
        progressM: Double,
        zones: List<NoteRouteZone>,
        tailM: Double
    ): List<RouteStop> {
        val start = window.startM
        val end = window.endM
        val tail = tailM.coerceAtLeast(EDGE_M)
        val fadeStart = progressM - tail

        val marks = ArrayList<Double>()
        marks += start
        marks += end
        marks += fadeStart
        marks += progressM
        for (z in zones) {
            if (z.endM < start || z.startM > end) continue
            marks += z.startM - EDGE_M
            marks += z.startM
            marks += z.endM
            marks += z.endM + EDGE_M
        }
        val sorted = marks.filter { it in start..end }.sorted()

        val out = ArrayList<RouteStop>(sorted.size)
        var lastPos = -1f
        for (m in sorted) {
            val pos = window.fraction(m)
            // 지도 엔진은 점의 위치가 꼭 커져야 받아 준다. 같은 자리의 점은 하나만 남긴다.
            if (out.isNotEmpty() && pos <= lastPos + POSITION_EPS) continue
            out += RouteStop(pos, zones.any { m >= it.startM && m <= it.endM }, alphaAt(m, fadeStart, progressM))
            lastPos = pos
        }
        return out
    }

    private fun alphaAt(m: Double, fadeStart: Double, progressM: Double): Float = when {
        m <= fadeStart -> 0f
        m >= progressM -> 1f
        else -> ((m - fadeStart) / (progressM - fadeStart)).toFloat()
    }

    private const val POSITION_EPS = 1e-5f
}
