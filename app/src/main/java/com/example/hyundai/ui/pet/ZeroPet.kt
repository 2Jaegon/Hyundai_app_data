package com.example.hyundai.ui.pet

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform

/** 레벨에 따라 붙는 장식. 목도리(2), 볼 터치(3), 왕관(4 이상). */
internal data class PetLook(val scarf: Boolean, val blush: Boolean, val crown: Boolean) {
    companion object {
        fun forLevel(level: Int) = PetLook(scarf = level >= 2, blush = level >= 3, crown = level >= 4)
    }
}

private object Fur {
    val Light = Color(0xFFD2A07A)
    val Mid = Color(0xFFB07A52)
    val Dark = Color(0xFF8A5C3C)
    val Outline = Color(0xFF5E3B26)
    val InnerEar = Color(0xFF9A6650)
    val CreamLight = Color(0xFFFFF1DC)
    val Cream = Color(0xFFF2DDBF)
    val CreamDark = Color(0xFFD9BC94)
    val Ink = Color(0xFF1E1A1A)
    val Mouth = Color(0xFF7A5238)
    val Paper = Color(0xFFFFF8E8)
    val PaperEdge = Color(0xFFD8C8A8)
    val Line = Color(0xFF9C8B72)
    val GoldLight = Color(0xFFF3E2B3)
    val Gold = Color(0xFFD9B66A)
}

/**
 * 펫 제로. 입체감을 살린 부드러운 인형 느낌의 그림으로, 3D 엔진 없이 캔버스로만 그린다.
 * [animate]가 true면 숨 쉬듯 까딱이고 살짝 흔들린다. [excited]가 true면 통통 튄다(쪽지·응원 도착 때).
 * [holdingNote]가 true면 길에서 주운 쪽지를 두 손으로 들고 있다.
 */
@Composable
fun ZeroPet(
    level: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    excited: Boolean = false,
    holdingNote: Boolean = false
) {
    val look = PetLook.forLevel(level)

    if (!animate) {
        Canvas(modifier = modifier) { drawZero(look, accent, holdingNote, bob = 0f, sway = 0f) }
        return
    }

    val transition = rememberInfiniteTransition(label = "pet")
    val bob = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (excited) 380 else 1500, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "bob"
    )
    val sway = transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sway"
    )
    // State를 그리기 단계에서 읽어, 매 프레임 리컴포지션 없이 그리기만 다시 한다.
    Canvas(modifier = modifier) {
        drawZero(look, accent, holdingNote, bob = bob.value * (if (excited) 2.4f else 1f), sway = sway.value)
    }
}

/** 100x100 칸을 기준으로 그린다. 한 칸 = 화면 한 변의 1/100. */
private fun DrawScope.drawZero(look: PetLook, accent: Color, holdingNote: Boolean, bob: Float, sway: Float) {
    val u = size.minDimension / 100f
    val originX = (size.width - size.minDimension) / 2f
    val originY = (size.height - size.minDimension) / 2f
    fun p(x: Float, y: Float) = Offset(originX + x * u, originY + y * u)

    // 바닥 그림자는 움직이지 않는다. 튈 때는 살짝 작아진다.
    val shadowScale = 1f - 0.06f * bob
    drawOval(
        Color(0x40000000),
        topLeft = p(50f - 28f * shadowScale, 87f),
        size = Size(56f * u * shadowScale, 9f * u)
    )

    // 몸 전체가 발 아래 가운데를 중심으로 까딱이고 흔들린다.
    withTransform({
        translate(0f, -bob * 1.6f * u)
        rotate(sway * 2.2f, pivot = p(50f, 90f))
    }) {
        fun blob(center: Offset, rx: Float, ry: Float, light: Color, mid: Color, dark: Color, lightAt: Offset = center) {
            val radius = maxOf(rx, ry) * u * 1.6f
            val brush = Brush.radialGradient(
                colors = listOf(light, mid, dark),
                center = Offset(lightAt.x - rx * u * 0.25f, lightAt.y - ry * u * 0.35f),
                radius = radius
            )
            drawOval(brush, topLeft = Offset(center.x - rx * u, center.y - ry * u), size = Size(rx * 2 * u, ry * 2 * u))
            drawOval(
                Fur.Outline.copy(alpha = 0.35f),
                topLeft = Offset(center.x - rx * u, center.y - ry * u),
                size = Size(rx * 2 * u, ry * 2 * u),
                style = Stroke(width = 0.7f * u)
            )
        }

        fun fur(center: Offset, rx: Float, ry: Float) = blob(center, rx, ry, Fur.Light, Fur.Mid, Fur.Dark)

        // 발
        for (x in listOf(36f, 64f)) {
            fur(p(x, 85f), 10.5f, 6.5f)
            drawOval(
                Fur.CreamDark.copy(alpha = 0.85f),
                topLeft = p(x - 5f, 85.5f),
                size = Size(10f * u, 5.2f * u)
            )
        }

        // 몸통과 배
        fur(p(50f, 68f), 22f, 21f)
        blob(p(50f, 71f), 13f, 14f, Fur.CreamLight, Fur.Cream, Fur.CreamDark)

        // 팔: 쪽지를 들면 앞으로 모은다.
        if (holdingNote) {
            rotate(-38f, pivot = p(33f, 70f)) { fur(p(33f, 70f), 6.5f, 11f) }
            rotate(38f, pivot = p(67f, 70f)) { fur(p(67f, 70f), 6.5f, 11f) }
        } else {
            rotate(-10f, pivot = p(27f, 68f)) { fur(p(27f, 68f), 7f, 13f) }
            rotate(10f, pivot = p(73f, 68f)) { fur(p(73f, 68f), 7f, 13f) }
        }

        // 목도리 (머리 뒤에서 몸을 감싼다)
        if (look.scarf) {
            val band = Brush.verticalGradient(
                listOf(accent.copy(alpha = 1f).lighten(0.25f), accent, accent.darken(0.2f)),
                startY = originY + 59f * u,
                endY = originY + 70f * u
            )
            drawRoundRect(
                band,
                topLeft = p(31f, 59.5f),
                size = Size(38f * u, 10f * u),
                cornerRadius = CornerRadius(5f * u)
            )
            rotate(9f, pivot = p(60f, 66f)) {
                drawRoundRect(
                    band,
                    topLeft = p(56f, 64f),
                    size = Size(8f * u, 15f * u),
                    cornerRadius = CornerRadius(3.5f * u)
                )
            }
        }

        // 귀
        for (x in listOf(28f, 72f)) {
            fur(p(x, 19f), 9.5f, 9.5f)
            drawCircle(Fur.InnerEar, radius = 5.2f * u, center = p(x + (50f - x) * 0.03f, 20.2f))
            drawCircle(Color.White.copy(alpha = 0.10f), radius = 2.4f * u, center = p(x - 1.5f, 18f))
        }

        // 머리
        fur(p(50f, 38f), 27.5f, 24f)
        // 머리 위 부드러운 하이라이트
        rotate(-18f, pivot = p(38f, 21f)) {
            drawOval(
                Color.White.copy(alpha = 0.16f),
                topLeft = p(28f, 17.5f),
                size = Size(20f * u, 8f * u)
            )
        }

        // 볼 터치
        if (look.blush) {
            for (x in listOf(30.5f, 69.5f)) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.65f), accent.copy(alpha = 0f)),
                        center = p(x, 43.5f),
                        radius = 5.5f * u
                    ),
                    radius = 5.5f * u,
                    center = p(x, 43.5f)
                )
            }
        }

        // 주둥이
        blob(p(50f, 46.5f), 11.5f, 8.5f, Fur.CreamLight, Fur.Cream, Fur.CreamDark)
        // 코
        drawOval(
            Brush.radialGradient(
                listOf(Color(0xFF4A4242), Fur.Ink),
                center = p(49f, 41.5f),
                radius = 5f * u
            ),
            topLeft = p(45.4f, 39.4f),
            size = Size(9.2f * u, 6.4f * u)
        )
        drawOval(Color.White.copy(alpha = 0.75f), topLeft = p(47f, 40.3f), size = Size(2.8f * u, 1.5f * u))
        // 입
        val mouth = Path().apply {
            moveTo(originX + 50f * u, originY + 45.8f * u)
            lineTo(originX + 50f * u, originY + 48.6f * u)
            quadraticTo(originX + 50f * u, originY + 51.2f * u, originX + 45.6f * u, originY + 50.4f * u)
            moveTo(originX + 50f * u, originY + 48.6f * u)
            quadraticTo(originX + 50f * u, originY + 51.2f * u, originX + 54.4f * u, originY + 50.4f * u)
        }
        drawPath(mouth, Fur.Mouth, style = Stroke(width = 1.1f * u, cap = StrokeCap.Round))

        // 눈
        for (x in listOf(39f, 61f)) {
            drawOval(Fur.Ink, topLeft = p(x - 3.6f, 29.5f), size = Size(7.2f * u, 9.4f * u))
            drawCircle(Color.White, radius = 1.6f * u, center = p(x + 0.9f, 31.4f))
            drawCircle(Color.White.copy(alpha = 0.8f), radius = 0.8f * u, center = p(x - 1.2f, 36f))
        }

        // 왕관
        if (look.crown) {
            val crown = Path().apply {
                moveTo(originX + 40f * u, originY + 17f * u)
                lineTo(originX + 40f * u, originY + 8.5f * u)
                lineTo(originX + 45f * u, originY + 12.5f * u)
                lineTo(originX + 50f * u, originY + 6f * u)
                lineTo(originX + 55f * u, originY + 12.5f * u)
                lineTo(originX + 60f * u, originY + 8.5f * u)
                lineTo(originX + 60f * u, originY + 17f * u)
                close()
            }
            drawPath(
                crown,
                Brush.verticalGradient(
                    listOf(Fur.GoldLight, Fur.Gold),
                    startY = originY + 6f * u,
                    endY = originY + 17f * u
                )
            )
            drawPath(crown, Fur.Outline.copy(alpha = 0.35f), style = Stroke(width = 0.7f * u))
            drawCircle(accent, radius = 1.3f * u, center = p(50f, 13.6f))
        }

        // 쪽지와 두 손
        if (holdingNote) {
            rotate(-4f, pivot = p(50f, 76f)) {
                // 종이 그림자
                drawRoundRect(
                    Color(0x33000000),
                    topLeft = p(37.4f, 65.6f),
                    size = Size(26f * u, 22f * u),
                    cornerRadius = CornerRadius(2.4f * u)
                )
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(Fur.Paper, Color(0xFFF3E7CC)),
                        startY = originY + 64f * u,
                        endY = originY + 86f * u
                    ),
                    topLeft = p(37f, 64.5f),
                    size = Size(26f * u, 22f * u),
                    cornerRadius = CornerRadius(2.4f * u)
                )
                drawRoundRect(
                    Fur.PaperEdge,
                    topLeft = p(37f, 64.5f),
                    size = Size(26f * u, 22f * u),
                    cornerRadius = CornerRadius(2.4f * u),
                    style = Stroke(width = 0.6f * u)
                )
                drawRoundRect(accent, topLeft = p(40.5f, 68f), size = Size(15f * u, 3.4f * u), cornerRadius = CornerRadius(1.7f * u))
                drawRoundRect(Fur.Line, topLeft = p(40.5f, 74.5f), size = Size(19f * u, 2f * u), cornerRadius = CornerRadius(1f * u))
                drawRoundRect(Fur.Line, topLeft = p(40.5f, 79f), size = Size(19f * u, 2f * u), cornerRadius = CornerRadius(1f * u))
                drawRoundRect(Fur.Line, topLeft = p(40.5f, 83.4f), size = Size(11f * u, 2f * u), cornerRadius = CornerRadius(1f * u))
            }
            // 종이 양쪽을 잡은 손
            fur(p(37.5f, 77f), 5.2f, 5.6f)
            fur(p(62f, 75f), 5.2f, 5.6f)
        }
    }
}

private fun Color.lighten(amount: Float) = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha
)

private fun Color.darken(amount: Float) = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = alpha
)
