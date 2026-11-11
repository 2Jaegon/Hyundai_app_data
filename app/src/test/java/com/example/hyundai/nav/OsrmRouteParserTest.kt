package com.example.hyundai.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OsrmRouteParserTest {

    private val sample = """
    {
      "code": "Ok",
      "routes": [{
        "distance": 2000.0,
        "duration": 150.0,
        "geometry": {
          "type": "LineString",
          "coordinates": [[127.0000, 37.5000], [127.0100, 37.5000], [127.0100, 37.5090]]
        },
        "legs": [{
          "steps": [
            { "distance": 880.0, "name": "강변북로",
              "maneuver": { "type": "depart", "location": [127.0000, 37.5000] } },
            { "distance": 1000.0, "name": "성수대교",
              "maneuver": { "type": "turn", "modifier": "left", "location": [127.0100, 37.5000] } },
            { "distance": 0.0, "name": "",
              "maneuver": { "type": "arrive", "location": [127.0100, 37.5090] } }
          ]
        }]
      }]
    }
    """.trimIndent()

    @Test
    fun `parses geometry into points in lat lng order`() {
        val route = OsrmRouteParser.parse(sample)
        assertNotNull(route)
        assertEquals(3, route!!.track.points.size)
        assertEquals(37.5000, route.track.points[0].lat, 1e-9)
        assertEquals(127.0000, route.track.points[0].lng, 1e-9)
    }

    @Test
    fun `keeps only real maneuvers and the arrival`() {
        val route = OsrmRouteParser.parse(sample)!!
        assertEquals(listOf(TurnIcon.LEFT, TurnIcon.ARRIVE), route.steps.map { it.turn })
        assertEquals("성수대교", route.steps.first().roadName)
    }

    @Test
    fun `step positions grow along the route and end at the route length`() {
        val route = OsrmRouteParser.parse(sample)!!
        assertTrue(route.steps[0].distanceAlongM < route.steps[1].distanceAlongM)
        assertEquals(route.track.totalM, route.steps.last().distanceAlongM, 1.0)
    }

    @Test
    fun `step position is snapped to where the maneuver is on the line`() {
        val route = OsrmRouteParser.parse(sample)!!
        // 첫 번째 회전은 꺾이는 꼭짓점(경로 시작에서 약 880m)에 있어야 한다.
        assertEquals(880.0, route.steps.first().distanceAlongM, 15.0)
    }

    @Test
    fun `rejects coordinates outside the earth`() {
        val badLat = sample.replace("37.5090", "91.0")
        assertNull(OsrmRouteParser.parse(badLat))
        val badLng = sample.replace("127.0100, 37.5000", "181.0, 37.5000")
        assertNull(OsrmRouteParser.parse(badLng))
    }

    @Test
    fun `rejects routes with too many points`() {
        val coords = (0..OsrmRouteParser.MAX_POINTS).joinToString(",") { "[127.0,37.5]" }
        val huge = """{"code":"Ok","routes":[{"geometry":{"coordinates":[$coords]},"legs":[]}]}"""
        assertNull(OsrmRouteParser.parse(huge))
    }

    @Test
    fun `returns null for errors and garbage`() {
        assertNull(OsrmRouteParser.parse("""{"code":"NoRoute","routes":[]}"""))
        assertNull(OsrmRouteParser.parse("not json"))
        assertNull(OsrmRouteParser.parse(""))
    }
}
