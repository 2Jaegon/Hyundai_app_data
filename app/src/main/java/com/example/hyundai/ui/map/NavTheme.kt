package com.example.hyundai.ui.map

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.hyundai.ui.theme.TodakColors

/**
 * 주행 화면 위 카드의 색. 낮에는 크림 카드에 남색 글자, 밤에는 남색 카드에 크림 글자를 써서
 * 밤에 카드가 눈부시지 않게 한다. 강조색은 앱과 같은 하늘·살구만 쓴다.
 */
data class NavSkin(
    val card: Color,
    val cardEdge: Color,
    val text: Color,
    val textSub: Color,
    /** 하늘색 옅은 바탕(배지, 선택). */
    val skySoft: Color,
    /** 하늘색 글자. */
    val sky: Color,
    /** 살구색 옅은 바탕. */
    val apricotSoft: Color,
    /** 살구색 글자와 테두리. */
    val apricot: Color,
    val shadow: Color
)

val LocalNavSkin = staticCompositionLocalOf { NavTheme.skin(isDay = true) }

/**
 * 내비 화면(지도와 주행 중 안내 카드) 전용 색.
 * 지도 자체는 도로·건물·물·숲을 구분해야 해서 세 가지 색 규칙에서 뺀다. 지도 위 카드와 경로선은 앱 색을 따른다.
 */
object NavTheme {
    private val Day = NavSkin(
        card = Color(0xF7FFFCF6),
        cardEdge = Color(0xFFEFE2D0),
        text = TodakColors.Ink,
        textSub = TodakColors.TextSecondary,
        skySoft = TodakColors.Neutral,
        sky = TodakColors.RouteDeep,
        apricotSoft = TodakColors.ApricotSoft,
        apricot = TodakColors.ApricotDeep,
        shadow = Color(0x2223304A)
    )

    private val Night = NavSkin(
        card = Color(0xF223304A),
        cardEdge = Color(0xFF34425F),
        text = TodakColors.Cream,
        textSub = Color(0xFFC9CEDA),
        skySoft = Color(0xFF2C4673),
        sky = Color(0xFF8DB6FF),
        apricotSoft = Color(0xFF4A3A3A),
        apricot = TodakColors.Apricot,
        shadow = Color(0x55000000)
    )

    fun skin(isDay: Boolean): NavSkin = if (isDay) Day else Night

    // 예전 이름. 지도 밖(3D 도로 안내 말풍선 등)에서 쓴다.
    val Panel = Night.card
    val PanelEdge = Night.cardEdge
    val TextSecondary = Night.textSub
    val Dot = Color(0xFF9AA3B5)
    val Alert = TodakColors.ApricotDeep
    val PetAccent = TodakColors.Sky

    // 경로선: 속은 하늘색으로 꽉 채우고, 바깥에 진한 하늘색 테두리 한 줄만 둔다. 빛 번짐은 쓰지 않는다.
    const val ROUTE_FILL = "#4C8DF6"
    const val ROUTE_CASING_DAY = "#2F6FD6"
    const val ROUTE_CASING_NIGHT = "#1C3F82"

    // 쪽지가 있는 구간: 같은 선 위에서 살구색으로 바뀐다.
    const val NOTE_FILL = "#FF9C73"
    const val NOTE_CASING_DAY = "#D9653A"
    const val NOTE_CASING_NIGHT = "#8F3E22"

    fun routeCasing(isDay: Boolean) = if (isDay) ROUTE_CASING_DAY else ROUTE_CASING_NIGHT
    fun noteCasing(isDay: Boolean) = if (isDay) NOTE_CASING_DAY else NOTE_CASING_NIGHT
}
