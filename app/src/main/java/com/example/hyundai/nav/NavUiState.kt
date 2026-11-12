package com.example.hyundai.nav

/** 화면(지도/주행 HUD)이 그리는 내비 상태. */
data class NavUiState(
    val hasRoute: Boolean = false,
    /** 인터넷이 없어서 오프라인 데모 경로를 쓰는 중이면 true. */
    val isFallbackRoute: Boolean = false,
    val carPosition: GeoPoint = RouteRepository.DEFAULT_ORIGIN,
    val carBearing: Float = 85f,
    /** 현재 경로 전체. 지도는 차가 지나간 부분을 [progressM] 위치까지 잘라 내고 그린다. */
    val track: RouteTrack? = null,
    /** 경로 시작점에서 차까지 온 거리(m). */
    val progressM: Double = 0.0,
    val nextTurn: TurnIcon = TurnIcon.STRAIGHT,
    val distToTurnM: Double = 0.0,
    val remainingM: Double = 0.0,
    val etaSec: Int = 0,
    val arrived: Boolean = false
)
