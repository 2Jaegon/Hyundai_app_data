package com.example.hyundai.ui.pet

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.hyundai.ui.theme.TodakColors

/**
 * 펫이 들고 있는 쪽지(편지 봉투). 살구색 봉투에 하트 봉인.
 * [open]이면 봉투 위로 편지지가 살짝 나와 있어 "읽는 중"으로 보인다.
 * 크기는 [modifier]로 정하고, 가로:세로 = 4:3 정도가 보기 좋다.
 */
@Composable
fun NoteLetter(open: Boolean, modifier: Modifier = Modifier) {
    val apricot = TodakColors.Apricot
    val deep = TodakColors.ApricotDeep
    val cream = TodakColors.Cream
    val sky = TodakColors.Sky
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bodyTop = h * 0.30f
        val bodyH = h - bodyTop
        val radius = CornerRadius(w * 0.08f)
        val line = Stroke(width = w * 0.035f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        if (open) {
            // 봉투에서 나온 편지지와 글줄 두 개
            drawRoundRect(cream, topLeft = Offset(w * 0.12f, 0f), size = Size(w * 0.76f, h * 0.62f), cornerRadius = radius)
            drawRoundRect(deep, topLeft = Offset(w * 0.12f, 0f), size = Size(w * 0.76f, h * 0.62f), cornerRadius = radius, style = Stroke(w * 0.025f))
            drawLine(sky, Offset(w * 0.24f, h * 0.13f), Offset(w * 0.76f, h * 0.13f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
            drawLine(sky, Offset(w * 0.24f, h * 0.24f), Offset(w * 0.62f, h * 0.24f), strokeWidth = w * 0.04f, cap = StrokeCap.Round)
        }

        // 봉투 몸통
        drawRoundRect(apricot, topLeft = Offset(0f, bodyTop), size = Size(w, bodyH), cornerRadius = radius)
        // 아래쪽 접힌 선
        val fold = Path().apply {
            moveTo(w * 0.04f, h * 0.96f)
            lineTo(w * 0.5f, bodyTop + bodyH * 0.52f)
            lineTo(w * 0.96f, h * 0.96f)
        }
        drawPath(fold, deep.copy(alpha = 0.55f), style = line)
        // 위쪽 덮개
        val flap = Path().apply {
            moveTo(w * 0.02f, bodyTop + h * 0.02f)
            lineTo(w * 0.5f, bodyTop + bodyH * 0.58f)
            lineTo(w * 0.98f, bodyTop + h * 0.02f)
        }
        drawPath(flap, deep, style = line)

        // 하트 봉인
        val cx = w * 0.5f
        val cy = bodyTop + bodyH * 0.56f
        val s = w * 0.17f
        val heart = Path().apply {
            moveTo(cx, cy + s * 0.55f)
            cubicTo(cx - s * 0.95f, cy - s * 0.05f, cx - s * 0.55f, cy - s * 0.85f, cx, cy - s * 0.35f)
            cubicTo(cx + s * 0.55f, cy - s * 0.85f, cx + s * 0.95f, cy - s * 0.05f, cx, cy + s * 0.55f)
            close()
        }
        drawPath(heart, cream)
    }
}
