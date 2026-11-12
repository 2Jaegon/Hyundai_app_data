package com.example.hyundai.ui.debug

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hyundai.model.GearState
import com.example.hyundai.nav.MapTheme
import com.example.hyundai.ui.theme.TodakColors
import com.example.hyundai.viewmodel.AppUiState

/** 시연에서 쪽지 순화·차단을 보여 줄 때 쓰는 예시 문장. */
object DemoNotes {
    const val SOFTEN = "앞에 12가 3456 차 왜 이렇게 느려터진 거야 답답하네 010-1234-5678 연락바람"
    const val BLOCK = "야 끼어들지 마 박아버려 보복운전 간다"
}

/**
 * 시연용 조작판. 평소에는 화면 아래 가운데 얇은 손잡이만 보이고, 눌러야 펼쳐진다.
 * 운전자에게 보여 줄 화면이 아니므로 연결 상태 같은 내부 정보는 담지 않는다.
 */
@Composable
fun SimulatorDrawer(
    state: AppUiState,
    onSafeDriveStep: () -> Unit,
    onMannerTurnSuccess: () -> Unit,
    onBlinkerAbuseTest: () -> Unit,
    onHarshBrakeTest: () -> Unit,
    onToggleGearPark: () -> Unit,
    onDemoNote: (String) -> Unit,
    onCycleMapTheme: () -> Unit,
    onToggleHeadlamp: () -> Unit = {},
    onStopAtLight: () -> Unit,
    onVoiceApprove: () -> Unit,
    modifier: Modifier = Modifier
) {
    var open by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            Column(
                modifier = Modifier
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(TodakColors.Background.copy(alpha = 0.96f))
                    .border(1.dp, TodakColors.EdgeStrong, RoundedCornerShape(20.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SimButton("500m 주행", TodakColors.RouteDeep, onSafeDriveStep)
                    SimButton("차선 변경", TodakColors.RouteDeep, onMannerTurnSuccess)
                    SimButton("깜빡이만 켜기", TodakColors.RouteDeep, onBlinkerAbuseTest)
                    SimButton("급제동", TodakColors.RouteDeep, onHarshBrakeTest)
                    SimButton("신호 대기", TodakColors.RouteDeep, onStopAtLight)
                    SimButton("음성 승인", TodakColors.Neutral, onVoiceApprove)
                    SimButton(
                        if (state.telemetry.gear == GearState.P) "주행" else "주차",
                        TodakColors.RouteDeep,
                        onToggleGearPark
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SimButton("쪽지 순화 예시", TodakColors.Neutral) { onDemoNote(DemoNotes.SOFTEN) }
                    SimButton("쪽지 차단 예시", TodakColors.Neutral) { onDemoNote(DemoNotes.BLOCK) }
                    SimButton(
                        when (state.mapTheme) {
                            MapTheme.AUTO -> "지도 자동"
                            MapTheme.DAY -> "지도 낮"
                            MapTheme.NIGHT -> "지도 밤"
                        },
                        TodakColors.Neutral,
                        onCycleMapTheme
                    )
                    SimButton(
                        if (state.telemetry.headlampOn) "전조등 ON" else "전조등 OFF",
                        if (state.telemetry.headlampOn) TodakColors.Good else TodakColors.Neutral,
                        onToggleHeadlamp
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .width(132.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(TodakColors.Background.copy(alpha = 0.7f))
                .clickable { open = !open },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(44.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TodakColors.TextDim)
            )
        }
    }
}

@Composable
private fun SimButton(label: String, bg: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}
