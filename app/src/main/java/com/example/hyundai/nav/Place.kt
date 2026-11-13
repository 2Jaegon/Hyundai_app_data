package com.example.hyundai.nav

/** 목적지 화면에서 고를 수 있는 장소의 출처. */
enum class PlaceKind { HOME, WORK, FAVORITE, CHARGER, RECENT, SEARCH }

/** 목적지 후보 한 곳. */
data class NavPlace(
    val name: String,
    val address: String,
    val point: GeoPoint,
    val kind: PlaceKind = PlaceKind.SEARCH,
    /** 현재 위치에서 직선 거리(m). 모르면 null. */
    val distanceM: Double? = null
)

/** 집·회사·즐겨찾기·최근 목적지. 기본 내비(NaviHelper)에 저장된 값이 있으면 그것을 먼저 쓴다. */
data class SavedPlaces(
    val home: NavPlace? = null,
    val work: NavPlace? = null,
    val favorites: List<NavPlace> = emptyList(),
    val recents: List<NavPlace> = emptyList()
) {
    /** 기본 내비 즐겨찾기([navi])와 이 앱에 저장한 값([local])을 합친다. 같은 자리(50m 이내)는 한 번만 둔다. */
    companion object {
        fun merge(navi: SavedPlaces, local: SavedPlaces): SavedPlaces = SavedPlaces(
            home = navi.home ?: local.home,
            work = navi.work ?: local.work,
            favorites = dedupe(navi.favorites + local.favorites),
            recents = dedupe(navi.recents + local.recents).take(MAX_RECENTS)
        )

        fun dedupe(places: List<NavPlace>): List<NavPlace> {
            val kept = ArrayList<NavPlace>(places.size)
            for (p in places) {
                if (kept.none { GeoMath.distanceM(it.point, p.point) < SAME_PLACE_M }) kept.add(p)
            }
            return kept
        }

        const val MAX_RECENTS = 6
        private const val SAME_PLACE_M = 50.0
    }
}

/** 목적지 화면에 지금 보여 주는 목록. */
enum class PlaceListMode { RECENT, SEARCH, FAVORITES, CHARGERS }

data class PlaceSearchUi(
    val mode: PlaceListMode = PlaceListMode.RECENT,
    val query: String = "",
    val results: List<NavPlace> = emptyList(),
    val loading: Boolean = false,
    /** 인터넷 검색이 안 되어 기기에 들어 있는 장소 목록으로 대신 찾았으면 true. */
    val offline: Boolean = false,
    val message: String? = null
)

/** 주행 화면 아래쪽에 깔리는 장면. */
enum class DriveView { MAP, HIGHWAY_3D }
