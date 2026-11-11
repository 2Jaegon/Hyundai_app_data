package com.example.hyundai.nav

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val lat: Double, val lng: Double)

object GeoMath {
    private const val EARTH_RADIUS_M = 6_371_008.8

    private fun rad(deg: Double) = Math.toRadians(deg)
    private fun deg(rad: Double) = Math.toDegrees(rad)

    /** 두 좌표 사이의 대원 거리(m, haversine). */
    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = rad(b.lat - a.lat)
        val dLng = rad(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) +
            cos(rad(a.lat)) * cos(rad(b.lat)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /** a에서 b를 바라보는 방위각 (0=북, 90=동, 0..360). */
    fun bearingDeg(a: GeoPoint, b: GeoPoint): Double {
        val phi1 = rad(a.lat)
        val phi2 = rad(b.lat)
        val dLng = rad(b.lng - a.lng)
        val y = sin(dLng) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLng)
        return (deg(atan2(y, x)) + 360.0) % 360.0
    }

    /** a에서 [bearing] 방향으로 [distM] 만큼 이동한 좌표. */
    fun destination(a: GeoPoint, bearing: Double, distM: Double): GeoPoint {
        val delta = distM / EARTH_RADIUS_M
        val theta = rad(bearing)
        val phi1 = rad(a.lat)
        val lambda1 = rad(a.lng)
        val phi2 = asin(sin(phi1) * cos(delta) + cos(phi1) * sin(delta) * cos(theta))
        val lambda2 = lambda1 + atan2(
            sin(theta) * sin(delta) * cos(phi1),
            cos(delta) - sin(phi1) * sin(phi2)
        )
        return GeoPoint(deg(phi2), ((deg(lambda2) + 540.0) % 360.0) - 180.0)
    }

    /** 두 방위각의 최소 차이 (0..180). */
    fun angleDiffDeg(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    /** 짧은 구간 사이를 직선으로 이은 위치. 내비 카메라가 1초 사이를 부드럽게 이을 때 쓴다. */
    fun lerp(a: GeoPoint, b: GeoPoint, t: Double) =
        GeoPoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)

    /** 방위각을 가까운 쪽으로 돌려 잇는다(350도에서 10도는 북쪽을 지나 +20도). 결과는 0~360도. */
    fun lerpBearingDeg(from: Double, to: Double, t: Double): Double {
        val delta = ((to - from + 540.0) % 360.0) - 180.0
        return ((from + delta * t) % 360.0 + 360.0) % 360.0
    }
}

/** 경로 폴리라인과 누적 거리. 좌표는 2개 이상이어야 한다. */
class RouteTrack(val points: List<GeoPoint>) {
    init {
        require(points.size >= 2) { "route needs at least 2 points" }
    }

    /** cumulative[i] = points[0]부터 points[i]까지의 거리(m). */
    private val cumulative: DoubleArray = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) {
            c[i] = c[i - 1] + GeoMath.distanceM(points[i - 1], points[i])
        }
    }

    val totalM: Double get() = cumulative.last()

    /** 거리 [distM]이 놓인 구간의 시작 인덱스. */
    private fun segmentIndex(distM: Double): Int {
        val d = distM.coerceIn(0.0, totalM)
        var lo = 0
        var hi = points.size - 2
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (cumulative[mid] <= d) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun pointAt(distM: Double): GeoPoint {
        val d = distM.coerceIn(0.0, totalM)
        val i = segmentIndex(d)
        val segLen = cumulative[i + 1] - cumulative[i]
        if (segLen <= 0.0) return points[i]
        val t = (d - cumulative[i]) / segLen
        val a = points[i]
        val b = points[i + 1]
        return GeoPoint(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
    }

    fun bearingAt(distM: Double): Double {
        val i = segmentIndex(distM)
        return GeoMath.bearingDeg(points[i], points[i + 1])
    }

    /** [p]에 가장 가까운 경로상 지점까지의 누적 거리(m). */
    fun project(p: GeoPoint): Double {
        var best = Double.MAX_VALUE
        var bestAlong = 0.0
        val cosLat = cos(Math.toRadians(p.lat))
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            // 국지 평면 근사 (수 km 이하 구간에서 충분히 정확)
            val ax = 0.0
            val ay = 0.0
            val bx = (b.lng - a.lng) * cosLat
            val by = b.lat - a.lat
            val px = (p.lng - a.lng) * cosLat
            val py = p.lat - a.lat
            val len2 = bx * bx + by * by
            val t = if (len2 == 0.0) 0.0 else max(0.0, min(1.0, ((px - ax) * bx + (py - ay) * by) / len2))
            val cx = bx * t
            val cy = by * t
            val dist2 = (px - cx) * (px - cx) + (py - cy) * (py - cy)
            if (dist2 < best) {
                best = dist2
                bestAlong = cumulative[i] + (cumulative[i + 1] - cumulative[i]) * t
            }
        }
        return bestAlong
    }

    /** [p]에서 경로에 가장 가까운 지점까지의 거리(m). 경로 밖 위치를 가려낼 때 쓴다. */
    fun distanceToRouteM(p: GeoPoint): Double = GeoMath.distanceM(p, pointAt(project(p)))

    /** [fromM]부터 [toM]까지의 경로 좌표. 양 끝은 그 위치의 보간점이고 범위는 경로 길이로 잘린다. */
    fun window(fromM: Double, toM: Double): List<GeoPoint> {
        val from = fromM.coerceIn(0.0, totalM)
        val to = toM.coerceIn(from, totalM)
        val first = segmentIndex(from)
        val last = segmentIndex(to)
        val out = ArrayList<GeoPoint>(last - first + 3)
        out.add(pointAt(from))
        for (k in first + 1..last) {
            if (cumulative[k] in from..to) out.add(points[k])
        }
        out.add(pointAt(to))
        return out
    }

    /** [distM] 이후 남은 경로 좌표 (첫 점은 [distM] 위치). */
    fun remainingPoints(distM: Double): List<GeoPoint> {
        val d = distM.coerceIn(0.0, totalM)
        val i = segmentIndex(d)
        val rest = ArrayList<GeoPoint>(points.size - i)
        rest.add(pointAt(d))
        for (k in i + 1 until points.size) rest.add(points[k])
        return rest
    }
}
