package com.example.hyundai.nav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder
import java.util.Locale

/**
 * 목적지 검색과 주변 충전소 찾기.
 *
 * 장소 검색은 Nominatim(OpenStreetMap), 충전소는 Overpass API를 쓴다. 둘 다 키가 없는 공개 서버라
 * 시연용이며, 사용 정책에 맞게 사용자가 누를 때만 한 번씩 부른다. 인터넷이 안 되면 [OfflinePlaces]로 대신 찾는다.
 *
 * 프라이버시: 검색어와 대략적인 위치가 제3자 서버로 나간다. 검색은 약 1km(소수점 2자리),
 * 충전소는 약 100m(소수점 3자리) 단위로 줄여 보낸다.
 */
class PlaceRepository(
    private val searchBaseUrl: String = "https://nominatim.openstreetmap.org",
    private val overpassUrl: String = "https://overpass-api.de/api/interpreter"
) {
    init {
        require(searchBaseUrl.startsWith("https://") && overpassUrl.startsWith("https://")) {
            "place servers must use https"
        }
    }

    data class SearchResult(val places: List<NavPlace>, val offline: Boolean)

    suspend fun search(query: String, near: GeoPoint): SearchResult = withContext(Dispatchers.IO) {
        val online = withTimeoutOrNull(TOTAL_TIMEOUT_MS) { searchOnline(query, near) }
        if (!online.isNullOrEmpty()) {
            SearchResult(online, offline = false)
        } else {
            // 인터넷 결과가 비었을 때도 기기 목록에 맞는 곳이 있으면 보여 준다.
            SearchResult(OfflinePlaces.search(query, near), offline = online == null)
        }
    }

    /**
     * 가까운 순서의 충전소. 실패하면 null, 주변에 없으면 빈 목록.
     * 공개 Overpass 서버는 자주 밀려서(2026-11 확인: 주 서버와 미러 모두 시간 초과) Nominatim을 먼저 쓰고,
     * 그것이 안 될 때만 Overpass를 부른다.
     */
    suspend fun nearbyChargers(near: GeoPoint, radiusM: Int = 5_000): List<NavPlace>? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            chargersFromNominatim(near, radiusM) ?: chargersFromOverpass(near, radiusM)
        }
    }

    private fun chargersFromNominatim(near: GeoPoint, radiusM: Int): List<NavPlace>? {
        val dLat = radiusM / 111_000.0
        val dLng = dLat / kotlin.math.cos(Math.toRadians(near.lat)).coerceAtLeast(0.1)
        val url = String.format(
            Locale.US,
            "%s/search?format=jsonv2&amenity=charging_station&countrycodes=kr&limit=30&accept-language=ko&viewbox=%.2f,%.2f,%.2f,%.2f&bounded=1",
            searchBaseUrl, near.lng - dLng, near.lat + dLat, near.lng + dLng, near.lat - dLat
        )
        val body = HttpText.get(url, maxBytes = 512 * 1024, readTimeoutMs = 8_000) ?: return null
        val found = NominatimParser.parse(body, near, kind = PlaceKind.CHARGER, unnamedLabel = CHARGER_LABEL)
        return SavedPlaces.dedupe(found.sortedBy { it.distanceM }).take(MAX_CHARGERS)
    }

    private fun chargersFromOverpass(near: GeoPoint, radiusM: Int): List<NavPlace>? {
        val q = String.format(
            Locale.US,
            "[out:json][timeout:10];nwr(around:%d,%.3f,%.3f)[\"amenity\"=\"charging_station\"];out center 40;",
            radiusM, near.lat, near.lng
        )
        val body = HttpText.postForm(overpassUrl, "data=" + URLEncoder.encode(q, "UTF-8")) ?: return null
        return OverpassChargerParser.parse(body, near, limit = MAX_CHARGERS)
    }

    private fun searchOnline(query: String, near: GeoPoint): List<NavPlace>? {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        // 현재 위치 주변(약 ±50km)을 먼저 보여 주되, 밖의 결과도 막지는 않는다(bounded=0).
        val url = String.format(
            Locale.US,
            "%s/search?format=jsonv2&q=%s&countrycodes=kr&limit=8&accept-language=ko&viewbox=%.2f,%.2f,%.2f,%.2f&bounded=0",
            searchBaseUrl, q,
            near.lng - VIEWBOX_DEG, near.lat + VIEWBOX_DEG, near.lng + VIEWBOX_DEG, near.lat - VIEWBOX_DEG
        )
        val body = HttpText.get(url, maxBytes = 512 * 1024) ?: return null
        return NominatimParser.parse(body, near)
    }

    private companion object {
        const val TOTAL_TIMEOUT_MS = 15_000L
        const val VIEWBOX_DEG = 0.5
        const val MAX_CHARGERS = 8
        const val CHARGER_LABEL = "전기차 충전소"
    }
}
