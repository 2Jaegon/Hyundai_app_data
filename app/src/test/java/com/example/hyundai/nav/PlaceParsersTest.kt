package com.example.hyundai.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceParsersTest {

    private val seongsu = GeoPoint(37.5445, 127.0440)

    @Test
    fun nominatim_readsNameAddressAndDistance() {
        val json = """
            [{"lat":"37.3947","lon":"127.1112","name":"판교역","category":"railway",
              "display_name":"판교역, 판교역로, 백현동, 분당구, 성남시, 경기도, 13529, 대한민국"}]
        """.trimIndent()
        val places = NominatimParser.parse(json, seongsu)
        assertEquals(1, places.size)
        val p = places.first()
        assertEquals("판교역", p.name)
        assertEquals("성남시 분당구 백현동", p.address)
        assertEquals(PlaceKind.SEARCH, p.kind)
        assertTrue(p.distanceM!! in 17_000.0..20_000.0)
    }

    @Test
    fun nominatim_usesFirstDisplayPartWhenNameMissing_andSkipsBadRows() {
        val json = """
            [{"lat":"abc","lon":"127.0","name":"bad"},
             {"lat":"37.55","lon":"126.97","name":"","display_name":"서울역, 한강대로, 중구, 서울, 대한민국"},
             {"lat":"95","lon":"127.0","name":"out of range"}]
        """.trimIndent()
        val places = NominatimParser.parse(json, null)
        assertEquals(listOf("서울역"), places.map { it.name })
        assertNull(places.first().distanceM)
        assertEquals("서울 중구 한강대로", places.first().address)
    }

    @Test
    fun nominatim_unnamedChargerKeepsRoadInAddress() {
        val json = """
            [{"lat":"37.5069752","lon":"127.0656627","name":"","category":"amenity","type":"charging_station",
              "display_name":"영동대로82길, 대치동, 강남구, 서울특별시, 06174, 대한민국"}]
        """.trimIndent()
        val p = NominatimParser.parse(json, seongsu, kind = PlaceKind.CHARGER, unnamedLabel = "전기차 충전소").single()
        assertEquals("전기차 충전소", p.name)
        assertEquals(PlaceKind.CHARGER, p.kind)
        assertEquals("강남구 대치동 영동대로82길", p.address)
    }

    @Test
    fun nominatim_invalidJsonGivesEmptyList() {
        assertTrue(NominatimParser.parse("<html>rate limited</html>", seongsu).isEmpty())
    }

    @Test
    fun overpass_sortsByDistance_readsWayCenters_andNamesUnnamed() {
        val json = """
            {"elements":[
              {"type":"node","lat":37.5600,"lon":127.0440,"tags":{"name":"먼 충전소"}},
              {"type":"way","center":{"lat":37.5450,"lon":127.0445},"tags":{"operator":"환경부"}},
              {"type":"node","lat":37.5460,"lon":127.0440,"tags":{"name":"가까운 충전소","addr:full":"서울 성동구 성수동"}},
              {"type":"relation","tags":{"name":"좌표 없음"}}
            ]}
        """.trimIndent()
        val chargers = OverpassChargerParser.parse(json, seongsu)
        assertEquals(listOf("환경부", "가까운 충전소", "먼 충전소"), chargers.map { it.name })
        assertTrue(chargers.all { it.kind == PlaceKind.CHARGER })
        assertEquals("서울 성동구 성수동", chargers[1].address)
    }

    @Test
    fun overpass_limitsCount() {
        val nodes = (1..20).joinToString(",") { """{"type":"node","lat":${37.5 + it * 0.01},"lon":127.0,"tags":{}}""" }
        val chargers = OverpassChargerParser.parse("""{"elements":[$nodes]}""", seongsu, limit = 5)
        assertEquals(5, chargers.size)
        assertEquals("전기차 충전소", chargers.first().name)
    }

    @Test
    fun offline_matchesIgnoringSpaces_nearestFirst() {
        val result = OfflinePlaces.search("판 교", seongsu)
        assertEquals("판교역", result.single().name)
        assertTrue(OfflinePlaces.search("   ", seongsu).isEmpty())
        // 주소로도 찾는다.
        assertTrue(OfflinePlaces.search("강남구", seongsu).map { it.name }.containsAll(listOf("강남역", "코엑스")))
    }

    @Test
    fun savedPlaces_mergePrefersNaviAndDedupesNearbyPoints() {
        val naviHome = NavPlace("내비 집", "", GeoPoint(37.50, 127.00), PlaceKind.HOME)
        val localHome = NavPlace("내 집", "", GeoPoint(37.60, 127.10), PlaceKind.HOME)
        val localWork = NavPlace("회사", "", GeoPoint(37.40, 127.10), PlaceKind.WORK)
        val fav = NavPlace("카페", "", GeoPoint(37.5000, 127.0000))
        val favNearDuplicate = NavPlace("같은 카페", "", GeoPoint(37.5002, 127.0001))
        val merged = SavedPlaces.merge(
            navi = SavedPlaces(home = naviHome, favorites = listOf(fav)),
            local = SavedPlaces(home = localHome, work = localWork, favorites = listOf(favNearDuplicate))
        )
        assertEquals("내비 집", merged.home?.name)
        assertEquals("회사", merged.work?.name)
        assertEquals(listOf("카페"), merged.favorites.map { it.name })
    }

    @Test
    fun savedPlaces_recentsAreCapped() {
        val recents = (0 until 10).map { NavPlace("곳$it", "", GeoPoint(37.0 + it * 0.1, 127.0), PlaceKind.RECENT) }
        val merged = SavedPlaces.merge(SavedPlaces(), SavedPlaces(recents = recents))
        assertEquals(SavedPlaces.MAX_RECENTS, merged.recents.size)
        assertEquals("곳0", merged.recents.first().name)
    }
}
