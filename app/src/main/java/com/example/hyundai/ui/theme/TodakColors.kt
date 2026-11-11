package com.example.hyundai.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 온길 색상. 현대차의 차분한 톤을 따라 색을 세 가지로만 쓴다.
 *
 *  - 메인 1 · 현대 블루(#002C5F)와 그 명도 변형: 배경, 패널, 지도
 *  - 메인 2 · 샌드(#E4DCD3): 글자, 보조 요소
 *  - 포인트 · 액티브 블루(#00AAD2): 경로, 누를 수 있는 것, 진행 상태
 *
 * 현대 블루는 여러 출처에서 공통으로 확인되는 값이다. 액티브 블루와 샌드는 공식 값으로
 * 확인하지 못해 현대차 톤을 참고해 정한 값이니, 정확한 브랜드 값은 현대차 가이드로 확인해 바꾼다.
 * 색을 더 늘리지 않는다. 상태 구분이 필요하면 글자와 모양으로 구분한다.
 */
object TodakColors {
    // ── 메인 1: 현대 블루 계열 ──
    val HyundaiBlue = Color(0xFF002C5F)
    val Background = Color(0xFF001A3A)
    val Panel = HyundaiBlue
    val PanelDeep = Color(0xFF00234C)
    val Edge = Color(0xFF123E73)
    val EdgeStrong = Color(0xFF1F5290)

    /** 눌렀을 때 쓰는 보조 버튼과 눌림 상태. */
    val Neutral = Color(0xFF1C4A80)
    val Disabled = Color(0xFF3A5A82)

    // ── 메인 2: 샌드 ──
    val Sand = Color(0xFFE4DCD3)

    // ── 포인트: 액티브 블루 ──
    val Route = Color(0xFF00AAD2)

    /** 포인트의 어두운 단계. 흰 글자를 올리는 버튼 바탕에 쓴다. */
    val RouteDeep = Color(0xFF007EA3)

    // ── 글자 ──
    val TextPrimary = Color.White
    val TextSecondary = Sand
    val TextDim = Color(0xFFA9B6C8)

    // 예전 이름은 세 가지 색 안으로 모은다. 새 색을 만들지 않는다.
    val Good = Route
    val Warm = Sand
    val Heart = RouteDeep
    val Danger = Sand
}
