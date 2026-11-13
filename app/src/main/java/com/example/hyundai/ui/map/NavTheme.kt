package com.example.hyundai.ui.map

import androidx.compose.ui.graphics.Color

/**
 * 내비 화면(지도와 주행 중 안내 카드) 전용 색.
 * 앱 전체의 현대차 색상(TodakColors)과 별개로 관리한다. 지도는 도로·건물·물·숲을 구분해 보여야 해서
 * 색을 세 가지로 줄이지 않는다.
 */
object NavTheme {
    // 안내 카드(회전, 남은 시간, 펫)
    val Panel = Color(0xE60B1320)
    val PanelEdge = Color(0xFF29405F)
    val TextSecondary = Color(0xFFCBD5E1)
    val Dot = Color(0xFF475569)

    /** 경고등·배터리 부족 알림 테두리. 쪽지 구간과 같은 앰버 계열이다. */
    val Alert = Color(0xFFF59E0B)

    // 경로와 차량 (밤 지도)
    const val ROUTE = "#38bdf8"
    const val ROUTE_CORE = "#e0f7ff"
    const val CAR_RING = "#0284c7"

    // 경로와 차량 (낮 지도). 밝은 회청색 바탕 위에서도 눈에 띄도록 더 진한 파랑을 쓴다.
    const val ROUTE_DAY = "#0B6FD6"
    const val ROUTE_CORE_DAY = "#ffffff"
    const val CAR_RING_DAY = "#0B6FD6"

    // 도로 위에 쪽지가 있는 구간 강조 색상 (따뜻한 앰버 골드 / 밝은 코어)
    const val NOTE_ZONE = "#F59E0B"
    const val NOTE_ZONE_DAY = "#EA580C"
    const val NOTE_ZONE_CORE = "#FEF3C7"

    fun route(isDay: Boolean) = if (isDay) ROUTE_DAY else ROUTE
    fun routeCore(isDay: Boolean) = if (isDay) ROUTE_CORE_DAY else ROUTE_CORE
    fun carRing(isDay: Boolean) = if (isDay) CAR_RING_DAY else CAR_RING
    fun noteZone(isDay: Boolean) = if (isDay) NOTE_ZONE_DAY else NOTE_ZONE

    /** 펫 강조색 (안내 카드의 테두리, 목도리, 알림 점). 경로선과 같은 하늘색으로 맞춘다. */
    val PetAccent = Color(0xFF38BDF8)
}
