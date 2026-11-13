package com.example.hyundai.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 온길 색상. 기본 색은 세 가지뿐이다.
 *
 *  - 바탕 · 크림(#FFF8EE): 화면 바탕, 카드
 *  - 메인 · 하늘(#4C8DF6): 버튼, 경로선, 진행 상태
 *  - 포인트 · 살구(#FF9C73): 쪽지, 펫, 하트, 보상
 *
 * 글자는 색으로 세지 않는 중립색 두 가지(진한 남색 [Ink], 흰색)만 쓴다.
 * 아래의 나머지 값은 모두 이 세 색의 진하기만 바꾼 것이다. 새 색을 만들지 않는다.
 */
object TodakColors {
    // ── 기본 세 색 ──
    val Cream = Color(0xFFFFF8EE)
    val Sky = Color(0xFF4C8DF6)
    val Apricot = Color(0xFFFF9C73)

    // ── 글자(중립) ──
    val Ink = Color(0xFF23304A)
    val OnColor = Color.White

    // ── 크림 단계 ──
    val Background = Cream
    /** 크림 바탕 위에 올리는 카드. */
    val Panel = Color.White
    /** 카드 안에서 한 칸 들어간 곳. */
    val PanelDeep = Color(0xFFFFF3E3)
    val Edge = Color(0xFFF2E6D6)
    val EdgeStrong = Color(0xFFE5D4BE)
    val Disabled = Color(0xFFEFE8DE)

    // ── 하늘 단계 ──
    val Route = Sky
    /** 흰 글자를 올리는 버튼 바탕, 하늘색 글자. */
    val RouteDeep = Color(0xFF2F6FD6)
    /** 보조 버튼과 선택 칩의 옅은 바탕. 글자는 [RouteDeep]. */
    val Neutral = Color(0xFFE6EFFE)

    // ── 살구 단계 ──
    /** 살구색 글자와 테두리. 크림·흰 바탕에서 읽히도록 진하게 했다. */
    val ApricotDeep = Color(0xFFD9653A)
    val ApricotSoft = Color(0xFFFFEADF)

    // ── 글자 ──
    val Text = Ink
    val TextSecondary = Color(0xFF56627C)
    val TextDim = Color(0xFF8C95A8)

    // 예전 이름은 세 가지 색 안으로 모은다.
    val Good = RouteDeep
    val Warm = ApricotDeep
    val Danger = ApricotDeep
    val Sand = TextSecondary
    val HyundaiBlue = Ink
}
