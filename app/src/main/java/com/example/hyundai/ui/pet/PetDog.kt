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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform

/** 레벨에 따라 붙는 성장 장식. Lv.2 목도리, Lv.3 발그레한 볼터치, Lv.4 황금 왕관. */
internal data class PetLook(
    val scarf: Boolean,
    val blush: Boolean,
    val crown: Boolean
) {
    companion object {
        fun forLevel(level: Int) = PetLook(
            scarf = level >= 2,
            blush = level >= 3,
            crown = level >= 4
        )
    }
}

private object AnimeDogPalette {
    // 따뜻한 크림 & 골든 시바/리트리버 톤의 애니메이션 셀 셰이딩 + 부드러운 그라데이션 팔레트
    val CoatLight = Color(0xFFFFE5B8)
    val CoatMid = Color(0xFFF6C27A)
    val CoatShadow = Color(0xFFDF9E52)
    val EarInner = Color(0xFFF39C93)

    // 얼굴 마즐 & 배·눈썹·발끝 크림 화이트
    val CreamBright = Color(0xFFFFFDF8)
    val CreamSoft = Color(0xFFFFF3E0)
    val CreamShade = Color(0xFFEBD6BA)

    // 따뜻한 다크 브라운 애니메이션 외곽선 (차갑고 투박한 순검정 지양)
    val Outline = Color(0xFF422818)
    val NoseDark = Color(0xFF2E1A10)
    val NoseMid = Color(0xFF4E3022)

    // 반짝이는 애니메이션 눈동자 (초롱초롱한 앰버/에스프레소 홍채)
    val EyeIrisTop = Color(0xFF2A1912)
    val EyeIrisBottom = Color(0xFF6B3E26)

    // 핑크빛 볼터치 & 웃는 입안
    val BlushPink = Color(0xFFFF7A8A)
    val TonguePink = Color(0xFFFF8E9E)
    val TongueDeep = Color(0xFFE06377)

    // 황금 왕관 & 별 장식
    val GoldBright = Color(0xFFFFF0A8)
    val GoldMid = Color(0xFFFFCC4D)
    val GoldDeep = Color(0xFFE09F24)

    // 쪽지 편지지
    val NotePaper = Color(0xFFFFFBF2)
    val NotePaperShade = Color(0xFFF4E8D1)
    val NoteLine = Color(0xFFC2B39B)
}

/**
 * 온길의 마스코트 강아지 '제로(Zero)'.
 * 극장판 애니메이션 캐릭터처럼 부드러운 크림·골든 투톤 털과 초롱초롱한 눈망울,
 * 귀엽게 흔들리는 귀·꼬리, 그리고 레벨별 성장 아이템(목도리·볼터치·왕관)을 갖춘 캐릭터다.
 *
 * [spin]이나 [excited]가 true일 때 숨 쉬듯 통통 튀며 귀와 꼬리를 살랑거리고,
 * 주행 중 평상시에는 프레임 저하를 막기 위해 정지 일러스트로 선명하게 유지된다.
 */
@Composable
fun PetModel(
    accent: Color,
    modifier: Modifier = Modifier,
    level: Int = 2,
    spin: Boolean = true,
    excited: Boolean = false,
    holdingNote: Boolean = false
) {
    val look = PetLook.forLevel(level)

    if (!spin && !excited) {
        Canvas(modifier) {
            drawAnimeDog(
                look = look,
                accent = accent,
                holdingNote = holdingNote,
                bob = 0f,
                sway = 0f,
                wag = 0f,
                excited = false
            )
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "pet_zero_anime")
    val bob = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (excited) 340 else 1300, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "bob"
    )
    val sway = transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (excited) 900 else 2400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "sway"
    )
    val wag = transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (excited) 220 else 550, easing = LinearEasing),
            RepeatMode.Reverse
        ),
        label = "wag"
    )

    Canvas(modifier) {
        drawAnimeDog(
            look = look,
            accent = accent,
            holdingNote = holdingNote,
            bob = bob.value * (if (excited) 2.2f else 1f),
            sway = sway.value,
            wag = wag.value,
            excited = excited
        )
    }
}

/** 100x100 정규화 좌표계 기준 애니메이션 스타일 캐릭터 렌더링 */
private fun DrawScope.drawAnimeDog(
    look: PetLook,
    accent: Color,
    holdingNote: Boolean,
    bob: Float,
    sway: Float,
    wag: Float,
    excited: Boolean
) {
    val u = size.minDimension / 100f
    val ox = (size.width - size.minDimension) / 2f
    val oy = (size.height - size.minDimension) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * u, oy + y * u)

    val outlineStroke = Stroke(width = 2.0f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val thinStroke = Stroke(width = 1.4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)

    // 1. 은은한 후광 & 바닥 그림자
    val shadowScale = 1f - 0.05f * bob
    drawOval(
        color = Color(0x38000000),
        topLeft = p(50f - 25f * shadowScale, 88f),
        size = Size(50f * u * shadowScale, 7.5f * u)
    )

    withTransform({
        translate(0f, -bob * 1.7f * u)
        rotate(sway * 1.8f, pivot = p(50f, 88f))
    }) {
        // 2. 복슬복슬 살랑거리는 말린 꼬리 (오른쪽 뒤)
        rotate(wag * 14f - 12f, pivot = p(68f, 75f)) {
            val tailPath = Path().apply {
                moveTo(p(66f, 76f).x, p(66f, 76f).y)
                cubicTo(p(82f, 75f).x, p(82f, 75f).y, p(88f, 58f).x, p(88f, 58f).y, p(76f, 54f).x, p(76f, 54f).y)
                cubicTo(p(68f, 52f).x, p(68f, 52f).y, p(64f, 64f).x, p(64f, 64f).y, p(66f, 76f).x, p(66f, 76f).y)
                close()
            }
            drawPath(
                tailPath,
                Brush.linearGradient(
                    colors = listOf(AnimeDogPalette.CreamBright, AnimeDogPalette.CoatMid),
                    start = p(84f, 55f),
                    end = p(66f, 75f)
                )
            )
            // 꼬리 끝 하얀 털 포인트
            drawCircle(AnimeDogPalette.CreamBright, radius = 4.6f * u, center = p(78.5f, 58f))
            drawPath(tailPath, AnimeDogPalette.Outline, style = outlineStroke)
        }

        // 3. 뒷발 (앉아 있는 귀여운 찹쌀떡 발)
        for (side in listOf(-1f, 1f)) {
            val footX = 50f + side * 16.5f
            drawOval(
                color = AnimeDogPalette.CoatShadow,
                topLeft = p(footX - 8.5f, 81f),
                size = Size(17f * u, 10f * u)
            )
            drawOval(
                color = AnimeDogPalette.CreamBright,
                topLeft = p(footX - 7.5f, 82.5f),
                size = Size(15f * u, 8.2f * u)
            )
            drawOval(
                color = AnimeDogPalette.Outline,
                topLeft = p(footX - 8.5f, 81f),
                size = Size(17f * u, 10f * u),
                style = outlineStroke
            )
        }

        // 4. 통통한 몸통 & 하얀 앞가슴 털
        val bodyBrush = Brush.radialGradient(
            colors = listOf(AnimeDogPalette.CoatLight, AnimeDogPalette.CoatMid, AnimeDogPalette.CoatShadow),
            center = p(45f, 63f),
            radius = 28f * u
        )
        drawOval(
            brush = bodyBrush,
            topLeft = p(29f, 53f),
            size = Size(42f * u, 35f * u)
        )
        drawOval(
            color = AnimeDogPalette.Outline,
            topLeft = p(29f, 53f),
            size = Size(42f * u, 35f * u),
            style = outlineStroke
        )

        // 복슬복슬한 가슴 크림색 털
        val chestBrush = Brush.verticalGradient(
            colors = listOf(AnimeDogPalette.CreamBright, AnimeDogPalette.CreamSoft, AnimeDogPalette.CreamShade),
            startY = p(50f, 57f).y,
            endY = p(50f, 85f).y
        )
        drawOval(
            brush = chestBrush,
            topLeft = p(36.5f, 58f),
            size = Size(27f * u, 26f * u)
        )

        // 5. 앞발 (쪽지를 안 들었을 때 얌전히 모은 찹쌀떡 앞발)
        if (!holdingNote) {
            for (side in listOf(-1f, 1f)) {
                val pawX = 50f + side * 9.5f
                rotate(side * -5f, pivot = p(pawX, 76f)) {
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(AnimeDogPalette.CoatLight, AnimeDogPalette.CoatMid)
                        ),
                        topLeft = p(pawX - 5.2f, 67f),
                        size = Size(10.4f * u, 19f * u),
                        cornerRadius = CornerRadius(5.2f * u)
                    )
                    // 하얀 양말 팁
                    drawOval(
                        color = AnimeDogPalette.CreamBright,
                        topLeft = p(pawX - 5.2f, 79f),
                        size = Size(10.4f * u, 7.5f * u)
                    )
                    drawRoundRect(
                        color = AnimeDogPalette.Outline,
                        topLeft = p(pawX - 5.2f, 67f),
                        size = Size(10.4f * u, 19f * u),
                        cornerRadius = CornerRadius(5.2f * u),
                        style = outlineStroke
                    )
                    // 발가락 구분선
                    drawLine(
                        color = AnimeDogPalette.Outline.copy(alpha = 0.6f),
                        start = p(pawX, 82.5f),
                        end = p(pawX, 85.5f),
                        strokeWidth = 1.1f * u,
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        // 6. 스카프 / 목도리 & 온길 배지 (Lv.2 이상이면 풍성한 스카프, Lv.1이면 귀여운 리본 칼라)
        if (look.scarf) {
            val scarfLight = accent.lighten(0.28f)
            val scarfDark = accent.darken(0.22f)
            val scarfBrush = Brush.verticalGradient(
                colors = listOf(scarfLight, accent, scarfDark),
                startY = p(50f, 53f).y,
                endY = p(50f, 64f).y
            )
            // 목을 감싸는 둥근 머플러
            drawRoundRect(
                brush = scarfBrush,
                topLeft = p(30.5f, 53.5f),
                size = Size(39f * u, 9.5f * u),
                cornerRadius = CornerRadius(4.8f * u)
            )
            drawRoundRect(
                color = AnimeDogPalette.Outline,
                topLeft = p(30.5f, 53.5f),
                size = Size(39f * u, 9.5f * u),
                cornerRadius = CornerRadius(4.8f * u),
                style = outlineStroke
            )
            // 옆으로 삐져나온 머플러 자락
            rotate(12f + sway * 3f, pivot = p(61f, 59f)) {
                drawRoundRect(
                    brush = scarfBrush,
                    topLeft = p(57f, 58f),
                    size = Size(8.5f * u, 14f * u),
                    cornerRadius = CornerRadius(3.5f * u)
                )
                drawRoundRect(
                    color = AnimeDogPalette.Outline,
                    topLeft = p(57f, 58f),
                    size = Size(8.5f * u, 14f * u),
                    cornerRadius = CornerRadius(3.5f * u),
                    style = outlineStroke
                )
            }
            // 중앙 황금 방울 / 하트 메달
            drawCircle(AnimeDogPalette.GoldMid, radius = 3.4f * u, center = p(50f, 60.5f))
            drawCircle(AnimeDogPalette.GoldBright, radius = 1.2f * u, center = p(49f, 59.3f))
            drawCircle(AnimeDogPalette.Outline, radius = 3.4f * u, center = p(50f, 60.5f), style = thinStroke)
        } else {
            // Lv.1 기본 목걸이
            drawRoundRect(
                color = accent,
                topLeft = p(34f, 55f),
                size = Size(32f * u, 5.5f * u),
                cornerRadius = CornerRadius(3f * u)
            )
            drawRoundRect(
                color = AnimeDogPalette.Outline,
                topLeft = p(34f, 55f),
                size = Size(32f * u, 5.5f * u),
                cornerRadius = CornerRadius(3f * u),
                style = thinStroke
            )
        }

        // 7. 쫑긋하고 부드러운 애니메이션 강아지 귀 (좌/우)
        for (side in listOf(-1f, 1f)) {
            val earPivot = p(50f + side * 20f, 24f)
            rotate(side * (16f + wag * 3f), pivot = earPivot) {
                val earPath = Path().apply {
                    moveTo(p(50f + side * 10f, 22f).x, p(50f + side * 10f, 22f).y)
                    cubicTo(
                        p(50f + side * 16f, 6f).x, p(50f + side * 16f, 6f).y,
                        p(50f + side * 32f, 8f).x, p(50f + side * 32f, 8f).y,
                        p(50f + side * 28f, 28f).x, p(50f + side * 28f, 28f).y
                    )
                    close()
                }
                drawPath(
                    earPath,
                    Brush.verticalGradient(
                        colors = listOf(AnimeDogPalette.CoatShadow, AnimeDogPalette.CoatMid),
                        startY = p(50f, 8f).y,
                        endY = p(50f, 28f).y
                    )
                )
                drawPath(earPath, AnimeDogPalette.Outline, style = outlineStroke)

                // 귀 안쪽 핑크빛 솜털
                val innerEar = Path().apply {
                    moveTo(p(50f + side * 14f, 22f).x, p(50f + side * 14f, 22f).y)
                    cubicTo(
                        p(50f + side * 18f, 11f).x, p(50f + side * 18f, 11f).y,
                        p(50f + side * 27f, 13f).x, p(50f + side * 27f, 13f).y,
                        p(50f + side * 24f, 26f).x, p(50f + side * 24f, 26f).y
                    )
                    close()
                }
                drawPath(innerEar, AnimeDogPalette.EarInner.copy(alpha = 0.85f))
            }
        }

        // 8. 둥글고 볼이 통통한 애니메이션 헤드 실루엣
        val headBrush = Brush.radialGradient(
            colors = listOf(AnimeDogPalette.CoatLight, AnimeDogPalette.CoatMid, AnimeDogPalette.CoatShadow),
            center = p(44f, 28f),
            radius = 34f * u
        )
        // 위쪽 이마 + 아래쪽 통통한 볼(Cheek pouch)
        drawOval(
            brush = headBrush,
            topLeft = p(21f, 15.5f),
            size = Size(58f * u, 43f * u)
        )
        drawOval(
            brush = headBrush,
            topLeft = p(19f, 30f),
            size = Size(62f * u, 27f * u)
        )
        // 머리 윤곽선
        val headOutline = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(topLeft = p(21f, 15.5f), bottomRight = p(79f, 58.5f)))
        }
        drawPath(headOutline, AnimeDogPalette.Outline, style = outlineStroke)

        // 9. 시바/리트리버 특유의 하얀 얼굴 우라지로(Urajiro) 볼·입가 패턴 & 마즐
        val muzzleFaceBrush = Brush.verticalGradient(
            colors = listOf(AnimeDogPalette.CreamBright, AnimeDogPalette.CreamSoft),
            startY = p(50f, 34f).y,
            endY = p(50f, 57f).y
        )
        // 양쪽 볼 하얀 털
        drawOval(muzzleFaceBrush, topLeft = p(22.5f, 36f), size = Size(26f * u, 20f * u))
        drawOval(muzzleFaceBrush, topLeft = p(51.5f, 36f), size = Size(26f * u, 20f * u))
        // 중앙 입가 둥근 마즐
        drawOval(muzzleFaceBrush, topLeft = p(33f, 35f), size = Size(34f * u, 22f * u))

        // 10. 정수리 앞머리 털 한 송이 & 둥근 크림색 눈썹 점(콩눈썹)
        val tuft = Path().apply {
            moveTo(p(47f, 16.5f).x, p(47f, 16.5f).y)
            quadraticTo(p(50f, 11f).x, p(50f, 11f).y, p(53f, 16.2f).x, p(53f, 16.2f).y)
            quadraticTo(p(56f, 12.5f).x, p(56f, 12.5f).y, p(55.5f, 17.2f).x, p(55.5f, 17.2f).y)
        }
        drawPath(tuft, AnimeDogPalette.CoatLight)
        drawPath(tuft, AnimeDogPalette.Outline, style = thinStroke)

        // 귀여운 마로(콩) 눈썹
        for (side in listOf(-1f, 1f)) {
            rotate(side * -10f, pivot = p(50f + side * 11.5f, 26.2f)) {
                drawOval(
                    color = AnimeDogPalette.CreamBright,
                    topLeft = p(50f + side * 11.5f - 3.6f, 24.4f),
                    size = Size(7.2f * u, 3.8f * u)
                )
                drawOval(
                    color = AnimeDogPalette.Outline.copy(alpha = 0.35f),
                    topLeft = p(50f + side * 11.5f - 3.6f, 24.4f),
                    size = Size(7.2f * u, 3.8f * u),
                    style = Stroke(width = 0.8f * u)
                )
            }
        }

        // 11. 발그레한 핑크 볼터치 (기본은 은은하게, Lv.3 이상이거나 신났을 때는 더 화사하게)
        val blushAlpha = if (look.blush || excited) 0.72f else 0.38f
        for (side in listOf(-1f, 1f)) {
            val bx = 50f + side * 19.5f
            val by = 43.5f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(AnimeDogPalette.BlushPink.copy(alpha = blushAlpha), Color.Transparent),
                    center = p(bx, by),
                    radius = 7.2f * u
                ),
                radius = 7.2f * u,
                center = p(bx, by)
            )
            if (look.blush) {
                // 애니메이션 특유의 볼 빗금 하이라이트
                drawLine(
                    color = Color.White.copy(alpha = 0.55f),
                    start = p(bx - 1.5f, by - 1f),
                    end = p(bx + 1.5f, by + 1f),
                    strokeWidth = 0.9f * u,
                    cap = StrokeCap.Round
                )
            }
        }

        // 12. 초롱초롱한 애니메이션 눈망울 (듀얼 캐치라이트 + 홍채 그라데이션)
        for (side in listOf(-1f, 1f)) {
            val ex = 50f + side * 12.5f
            val ey = 34.5f
            // 눈 바탕 그림자
            drawOval(
                color = AnimeDogPalette.Outline,
                topLeft = p(ex - 4.8f, ey - 5.6f),
                size = Size(9.6f * u, 11.2f * u)
            )
            // 홍채 그라데이션
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(AnimeDogPalette.EyeIrisTop, AnimeDogPalette.EyeIrisBottom),
                    startY = p(ex, ey - 5f).y,
                    endY = p(ex, ey + 5f).y
                ),
                topLeft = p(ex - 4.2f, ey - 5.0f),
                size = Size(8.4f * u, 10.0f * u)
            )
            // 아래쪽 영롱한 반사광
            drawOval(
                color = accent.lighten(0.35f).copy(alpha = 0.45f),
                topLeft = p(ex - 2.8f, ey + 1.2f),
                size = Size(5.6f * u, 3.0f * u)
            )
            // 큰 메인 하이라이트 (왼쪽 위)
            drawCircle(
                color = Color.White,
                radius = 2.1f * u,
                center = p(ex - 1.4f, ey - 2.0f)
            )
            // 작은 서브 하이라이트 (오른쪽 아래)
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = 1.0f * u,
                center = p(ex + 1.6f, ey + 1.8f)
            )
        }

        // 13. 반질반질한 밤톨 코 & 밝게 웃는 입 (신났거나 쪽지 읽을 때는 분홍 혀가 살짝 보임)
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(AnimeDogPalette.NoseMid, AnimeDogPalette.NoseDark),
                center = p(49f, 39.5f),
                radius = 5f * u
            ),
            topLeft = p(46.2f, 38.2f),
            size = Size(7.6f * u, 5.2f * u)
        )
        drawOval(
            color = Color.White.copy(alpha = 0.8f),
            topLeft = p(47.4f, 39.0f),
            size = Size(2.4f * u, 1.3f * u)
        )

        // 웃는 입 & 혀
        if (excited || holdingNote) {
            val tonguePath = Path().apply {
                moveTo(p(46.8f, 46.8f).x, p(46.8f, 46.8f).y)
                cubicTo(
                    p(46.8f, 52.8f).x, p(46.8f, 52.8f).y,
                    p(53.2f, 52.8f).x, p(53.2f, 52.8f).y,
                    p(53.2f, 46.8f).x, p(53.2f, 46.8f).y
                )
                close()
            }
            drawPath(
                tonguePath,
                Brush.verticalGradient(listOf(AnimeDogPalette.TongueDeep, AnimeDogPalette.TonguePink))
            )
            drawPath(tonguePath, AnimeDogPalette.Outline, style = thinStroke)
        }

        val mouthPath = Path().apply {
            moveTo(p(50f, 43.2f).x, p(50f, 43.2f).y)
            lineTo(p(50f, 45.8f).x, p(50f, 45.8f).y)
            quadraticTo(p(46.5f, 49.0f).x, p(46.5f, 49.0f).y, p(43.5f, 46.2f).x, p(43.5f, 46.2f).y)
            moveTo(p(50f, 45.8f).x, p(50f, 45.8f).y)
            quadraticTo(p(53.5f, 49.0f).x, p(53.5f, 49.0f).y, p(56.5f, 46.2f).x, p(56.5f, 46.2f).y)
        }
        drawPath(mouthPath, AnimeDogPalette.Outline, style = thinStroke)

        // 14. 황금 왕관 (Lv.4 이상 성체 달성 시 머리 위에 빛나는 왕관 장착)
        if (look.crown) {
            val crownPath = Path().apply {
                moveTo(p(40.5f, 16.5f).x, p(40.5f, 16.5f).y)
                lineTo(p(39.0f, 7.5f).x, p(39.0f, 7.5f).y)
                lineTo(p(45.0f, 12.0f).x, p(45.0f, 12.0f).y)
                lineTo(p(50.0f, 5.0f).x, p(50.0f, 5.0f).y)
                lineTo(p(55.0f, 12.0f).x, p(55.0f, 12.0f).y)
                lineTo(p(61.0f, 7.5f).x, p(61.0f, 7.5f).y)
                lineTo(p(59.5f, 16.5f).x, p(59.5f, 16.5f).y)
                close()
            }
            drawPath(
                crownPath,
                Brush.verticalGradient(
                    colors = listOf(AnimeDogPalette.GoldBright, AnimeDogPalette.GoldMid, AnimeDogPalette.GoldDeep),
                    startY = p(50f, 5f).y,
                    endY = p(50f, 16.5f).y
                )
            )
            drawPath(crownPath, AnimeDogPalette.Outline, style = thinStroke)
            drawCircle(accent, radius = 1.6f * u, center = p(50f, 13.0f))
        }

        // 15. 쪽지를 두 손으로 소중히 들고 있는 모습
        if (holdingNote) {
            rotate(-3.5f, pivot = p(50f, 74f)) {
                // 쪽지 그림자
                drawRoundRect(
                    color = Color(0x30000000),
                    topLeft = p(36.2f, 64.2f),
                    size = Size(28f * u, 21.5f * u),
                    cornerRadius = CornerRadius(3.0f * u)
                )
                // 쪽지 본체
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(AnimeDogPalette.NotePaper, AnimeDogPalette.NotePaperShade)
                    ),
                    topLeft = p(35.5f, 63.0f),
                    size = Size(28f * u, 21.5f * u),
                    cornerRadius = CornerRadius(3.0f * u)
                )
                drawRoundRect(
                    color = AnimeDogPalette.Outline,
                    topLeft = p(35.5f, 63.0f),
                    size = Size(28f * u, 21.5f * u),
                    cornerRadius = CornerRadius(3.0f * u),
                    style = outlineStroke
                )
                // 쪽지 헤더 배지 & 본문 줄
                drawRoundRect(
                    color = accent,
                    topLeft = p(39.0f, 66.5f),
                    size = Size(14.5f * u, 3.4f * u),
                    cornerRadius = CornerRadius(1.7f * u)
                )
                drawRoundRect(
                    color = AnimeDogPalette.NoteLine,
                    topLeft = p(39.0f, 73.0f),
                    size = Size(20.5f * u, 2.0f * u),
                    cornerRadius = CornerRadius(1.0f * u)
                )
                drawRoundRect(
                    color = AnimeDogPalette.NoteLine,
                    topLeft = p(39.0f, 77.5f),
                    size = Size(16.0f * u, 2.0f * u),
                    cornerRadius = CornerRadius(1.0f * u)
                )
            }
            // 쪽지 양옆을 꼭 쥔 하얀 찹쌀떡 앞발
            for (pawPos in listOf(p(35.5f, 75.0f), p(63.5f, 73.5f))) {
                drawOval(
                    color = AnimeDogPalette.CreamBright,
                    topLeft = Offset(pawPos.x - 5.4f * u, pawPos.y - 4.8f * u),
                    size = Size(10.8f * u, 9.6f * u)
                )
                drawOval(
                    color = AnimeDogPalette.Outline,
                    topLeft = Offset(pawPos.x - 5.4f * u, pawPos.y - 4.8f * u),
                    size = Size(10.8f * u, 9.6f * u),
                    style = outlineStroke
                )
            }
        }
    }
}

private fun Color.lighten(amount: Float) = Color(
    red = (red + (1f - red) * amount).coerceIn(0f, 1f),
    green = (green + (1f - green) * amount).coerceIn(0f, 1f),
    blue = (blue + (1f - blue) * amount).coerceIn(0f, 1f),
    alpha = alpha
)

private fun Color.darken(amount: Float) = Color(
    red = (red * (1f - amount)).coerceIn(0f, 1f),
    green = (green * (1f - amount)).coerceIn(0f, 1f),
    blue = (blue * (1f - amount)).coerceIn(0f, 1f),
    alpha = alpha
)
