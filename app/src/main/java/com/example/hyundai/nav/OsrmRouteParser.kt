package com.example.hyundai.nav

import org.json.JSONObject

/** 경로 폴리라인과 그 위의 회전 목록. */
class RouteData(
    val track: RouteTrack,
    val steps: List<RouteStep>
)

/** OSRM `/route/v1` 응답(geometries=geojson, steps=true)을 [RouteData]로 바꾼다. */
object OsrmRouteParser {

    /** 외부 응답이 비정상으로 클 때 메모리를 지키기 위한 상한. */
    const val MAX_POINTS = 50_000

    private fun isValid(lat: Double, lng: Double) =
        lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0

    fun parse(json: String): RouteData? {
        if (json.isBlank()) return null
        return try {
            val root = JSONObject(json)
            if (root.optString("code") != "Ok") return null
            val route = root.getJSONArray("routes").optJSONObject(0) ?: return null
            val coords = route.getJSONObject("geometry").getJSONArray("coordinates")
            if (coords.length() > MAX_POINTS) return null
            val points = ArrayList<GeoPoint>(coords.length())
            for (i in 0 until coords.length()) {
                val c = coords.getJSONArray(i)
                val lat = c.getDouble(1)
                val lng = c.getDouble(0)
                if (!isValid(lat, lng)) return null
                points += GeoPoint(lat = lat, lng = lng)
            }
            if (points.size < 2) return null
            val track = RouteTrack(points)

            val steps = ArrayList<RouteStep>()
            var last = 0.0
            val legs = route.optJSONArray("legs")
            if (legs != null) {
                for (l in 0 until legs.length()) {
                    val rawSteps = legs.getJSONObject(l).optJSONArray("steps") ?: continue
                    for (k in 0 until rawSteps.length()) {
                        val step = rawSteps.getJSONObject(k)
                        val maneuver = step.getJSONObject("maneuver")
                        val type = maneuver.optString("type")
                        if (type == "depart") continue
                        val icon = TurnMapping.fromOsrm(type, maneuver.optString("modifier").ifEmpty { null })
                        val isArrive = icon == TurnIcon.ARRIVE
                        if (icon == TurnIcon.STRAIGHT) continue
                        val loc = maneuver.getJSONArray("location")
                        val along = if (isArrive) {
                            track.totalM
                        } else {
                            track.project(GeoPoint(lat = loc.getDouble(1), lng = loc.getDouble(0)))
                        }
                        val monotone = along.coerceAtLeast(last)
                        last = monotone
                        steps += RouteStep(
                            distanceAlongM = monotone,
                            turn = icon,
                            roadName = step.optString("name")
                        )
                    }
                }
            }
            if (steps.isEmpty() || steps.last().turn != TurnIcon.ARRIVE) {
                steps += RouteStep(track.totalM, TurnIcon.ARRIVE, "")
            }
            RouteData(track, steps)
        } catch (_: Exception) {
            null
        }
    }
}
