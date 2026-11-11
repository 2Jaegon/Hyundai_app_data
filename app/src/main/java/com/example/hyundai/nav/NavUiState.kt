package com.example.hyundai.nav

/** 화면(지도/주행 HUD)이 그리는 내비 상태. */
data class NavUiState(
    val hasRoute: Boolean = false,
    /** 인터넷이 없어서 오프라인 데모 경로를 쓰는 중이면 true. */
    val isFallbackRoute: Boolean = false,
    val carPosition: GeoPoint = RouteRepository.DEFAULT_ORIGIN,
    val carBearing: Float = 85f,
    /** 지나온 부분을 뺀, 앞으로 갈 경로. 지도에는 이것만 그린다. */
    val upcomingRoute: List<GeoPoint> = emptyList(),
    val nextTurn: TurnIcon = TurnIcon.STRAIGHT,
    val distToTurnM: Double = 0.0,
    val remainingM: Double = 0.0,
    val etaSec: Int = 0,
    val arrived: Boolean = false
)
