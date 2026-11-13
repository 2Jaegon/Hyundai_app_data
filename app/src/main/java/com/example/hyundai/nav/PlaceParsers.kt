package com.example.hyundai.nav

import org.json.JSONArray
import org.json.JSONObject

/** Nominatim(OpenStreetMap 장소 검색) `format=jsonv2` 응답을 목적지 목록으로 바꾼다. */
object NominatimParser {

    /**
     * [unnamedLabel]이 있으면 이름 없는 곳(예: 이름이 안 붙은 충전소)을 도로명 대신 그 이름으로 부른다.
     * [kind]는 결과 장소의 종류다.
     */
    fun parse(
        json: String,
        origin: GeoPoint?,
        kind: PlaceKind = PlaceKind.SEARCH,
        unnamedLabel: String? = null
    ): List<NavPlace> {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        val out = ArrayList<NavPlace>(arr.length())
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val lat = item.optString("lat").toDoubleOrNull() ?: continue
            val lng = item.optString("lon").toDoubleOrNull() ?: continue
            if (!validLatLng(lat, lng)) continue
            val display = item.optString("display_name")
            val ownName = item.optString("name")
            val name = ownName.ifBlank { unnamedLabel ?: display.substringBefore(',').trim() }
            if (name.isBlank()) continue
            val point = GeoPoint(lat, lng)
            out.add(
                NavPlace(
                    name = name,
                    // 이름을 display_name 첫 조각에서 빌려 왔으면 주소에서 빼고, [unnamedLabel]을 썼으면 그 조각(도로명)을 주소에 남긴다.
                    address = shortAddress(display, if (ownName.isBlank() && unnamedLabel != null) "" else name),
                    point = point,
                    kind = kind,
                    distanceM = origin?.let { GeoMath.distanceM(it, point) }
                )
            )
        }
        return out
    }

    /**
     * "판교역, 판교역로, 백현동, 분당구, 성남시, 경기도, 13529, 대한민국"처럼 작은 단위부터 오는 주소를
     * "성남시 분당구 백현동"처럼 큰 단위부터 세 단계만 남긴다. 장소 이름, 우편번호, 나라 이름은 뺀다.
     */
    fun shortAddress(display: String, name: String): String {
        val parts = display.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != name && it != "대한민국" && it != "South Korea" && !it.all(Char::isDigit) }
            .reversed()
        val trimmed = if (parts.size > 3) parts.drop(1) else parts
        return trimmed.take(3).joinToString(" ")
    }
}

/** Overpass API의 `amenity=charging_station` 결과를 가까운 순서의 충전소 목록으로 바꾼다. */
object OverpassChargerParser {

    fun parse(json: String, origin: GeoPoint, limit: Int = 8): List<NavPlace> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val elements = root.optJSONArray("elements") ?: return emptyList()
        val out = ArrayList<NavPlace>(elements.length())
        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            // node는 lat/lon을 바로 갖고, way/relation은 `out center`로 받은 center를 쓴다.
            val src = if (el.has("lat")) el else el.optJSONObject("center") ?: continue
            val lat = src.optDouble("lat", Double.NaN)
            val lng = src.optDouble("lon", Double.NaN)
            if (!validLatLng(lat, lng)) continue
            val tags = el.optJSONObject("tags") ?: JSONObject()
            val point = GeoPoint(lat, lng)
            out.add(
                NavPlace(
                    name = tags.optString("name").ifBlank { tags.optString("operator").ifBlank { "전기차 충전소" } },
                    address = chargerAddress(tags),
                    point = point,
                    kind = PlaceKind.CHARGER,
                    distanceM = GeoMath.distanceM(origin, point)
                )
            )
        }
        return SavedPlaces.dedupe(out.sortedBy { it.distanceM }).take(limit)
    }

    private fun chargerAddress(tags: JSONObject): String {
        tags.optString("addr:full").takeIf { it.isNotBlank() }?.let { return it }
        val street = listOf(tags.optString("addr:city"), tags.optString("addr:street"), tags.optString("addr:housenumber"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (street.isNotBlank()) return street
        return tags.optString("operator")
    }
}

/**
 * 인터넷 장소 검색이 안 될 때 쓰는 기기 안 목록. 시연 경로 주변의 잘 알려진 곳만 넣었다.
 * 좌표는 역·건물 중심 근처의 대략적인 값이다.
 */
object OfflinePlaces {
    val ALL: List<NavPlace> = listOf(
        NavPlace("판교역", "성남시 분당구 백현동", RouteRepository.DEFAULT_DESTINATION),
        NavPlace("성수역", "서울 성동구 성수동2가", GeoPoint(37.5446, 127.0559)),
        NavPlace("서울역", "서울 중구 봉래동2가", GeoPoint(37.5547, 126.9707)),
        NavPlace("강남역", "서울 강남구 역삼동", GeoPoint(37.4979, 127.0276)),
        NavPlace("코엑스", "서울 강남구 삼성동", GeoPoint(37.5115, 127.0595)),
        NavPlace("잠실역", "서울 송파구 잠실동", GeoPoint(37.5133, 127.1001)),
        NavPlace("여의도역", "서울 영등포구 여의도동", GeoPoint(37.5216, 126.9243)),
        NavPlace("광화문", "서울 종로구 세종로", GeoPoint(37.5759, 126.9768)),
        NavPlace("현대자동차 양재 사옥", "서울 서초구 양재동", GeoPoint(37.4633, 127.0429)),
        NavPlace("수원역", "수원시 팔달구 매산로", GeoPoint(37.2659, 127.0000)),
        NavPlace("인천국제공항 제1터미널", "인천 중구 운서동", GeoPoint(37.4492, 126.4508))
    )

    /** 띄어쓰기를 무시하고 이름이나 주소에 [query]가 들어 있는 곳을 [origin]에서 가까운 순서로 준다. */
    fun search(query: String, origin: GeoPoint?): List<NavPlace> {
        val q = query.replace(" ", "")
        if (q.isEmpty()) return emptyList()
        return ALL.filter { it.name.replace(" ", "").contains(q) || it.address.replace(" ", "").contains(q) }
            .map { p -> p.copy(distanceM = origin?.let { GeoMath.distanceM(it, p.point) }) }
            .sortedBy { it.distanceM ?: 0.0 }
    }
}

private fun validLatLng(lat: Double, lng: Double): Boolean =
    lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
