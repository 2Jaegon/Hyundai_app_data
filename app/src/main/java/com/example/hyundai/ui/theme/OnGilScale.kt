package com.example.hyundai.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Density

/**
 * 차량 화면 배율.
 *
 * Pleos Connect 화면은 2560x1440에 160dpi라서 1dp가 1px이다. 휴대폰 기준 크기를 그대로 쓰면
 * 운전석에서 볼 때 PC 웹페이지처럼 글자와 버튼이 작다. 그래서 크기 값을 하나하나 바꾸는 대신
 * Compose 전체의 밀도를 한 번 키운다. 상자·여백·터치 영역은 [UI_SCALE]배, 글자는 그보다 [FONT_SCALE]배 더 커진다.
 * 예: 18sp 글자는 약 28px, 56dp 버튼은 약 73px.
 */
object OnGilScale {
    const val UI_SCALE = 1.3f
    const val FONT_SCALE = 1.2f

    fun car(device: Density): Density =
        Density(device.density * UI_SCALE, device.fontScale * FONT_SCALE)
}

/** 배율을 적용하기 전 기기 그대로의 밀도. 시연용 제어바처럼 크게 키우지 않을 화면에서 쓴다. */
val LocalDeviceDensity = staticCompositionLocalOf<Density?> { null }
