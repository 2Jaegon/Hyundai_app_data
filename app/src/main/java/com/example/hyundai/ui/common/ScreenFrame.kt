package com.example.hyundai.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hyundai.ui.theme.TodakColors

/**
 * 쪽지함·설정처럼 정차 중에만 여는 전체 화면의 틀. 위에 뒤로 가기와 제목이 있다.
 * 아래 화면의 터치가 새어 들어가지 않도록 화면 전체가 터치를 받는다.
 */
@Composable
fun ScreenFrame(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TodakColors.Background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(TodakColors.Neutral)
                    .clickable(onClick = onBack)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BackChevron(modifier = Modifier.size(18.dp))
                Text("뒤로", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) { content() }
    }
}

@Composable
private fun BackChevron(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.7f, size.height * 0.1f)
            lineTo(size.width * 0.25f, size.height * 0.5f)
            lineTo(size.width * 0.7f, size.height * 0.9f)
        }
        drawPath(
            path, Color.White,
            style = Stroke(width = size.width * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

/** 켜짐/꺼짐 스위치. 차량 화면에서 손가락으로 누르기 쉽게 크게 만든다. */
@Composable
fun BigToggle(checked: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 84.dp, height = 46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(if (checked) TodakColors.RouteDeep else TodakColors.Edge)
            .border(1.dp, if (checked) TodakColors.Route else TodakColors.EdgeStrong, RoundedCornerShape(23.dp))
            .clickable(onClick = onClick)
            .padding(5.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(if (checked) Color.White else TodakColors.TextDim)
        )
    }
}

/** 여러 값 중 하나를 고르는 막대. */
@Composable
fun <T> Segmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(TodakColors.PanelDeep)
            .border(1.dp, TodakColors.Edge, RoundedCornerShape(16.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) TodakColors.RouteDeep else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 26.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (on) Color.White else TodakColors.TextDim,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
