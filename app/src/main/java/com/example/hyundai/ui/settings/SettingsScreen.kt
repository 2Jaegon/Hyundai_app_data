package com.example.hyundai.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hyundai.nav.DriveView
import com.example.hyundai.nav.MapTheme
import com.example.hyundai.ui.common.BigToggle
import com.example.hyundai.ui.common.ScreenFrame
import com.example.hyundai.ui.common.Segmented
import com.example.hyundai.ui.theme.TodakColors
import com.example.hyundai.viewmodel.AppUiState

/** 설정. 정차 중에만 열린다. 바꾼 값은 기기에 저장된다. */
@Composable
fun SettingsScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onToggleAutoPlay: () -> Unit,
    onToggleCommuteOnly: () -> Unit,
    onSelectMapTheme: (MapTheme) -> Unit,
    onToggleMap3d: () -> Unit = {},
    onSelectDriveView: (DriveView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    ScreenFrame(title = "설정", onBack = onBack, modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 980.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingRow(
                title = "쪽지 자동 재생",
                hint = "끄면 들을지 먼저 물어봐요",
                control = { BigToggle(state.autoPlayTts, onToggleAutoPlay) }
            )
            SettingRow(
                title = "출퇴근 길 쪽지만",
                hint = "자주 다니는 길의 쪽지만 알려 줘요",
                control = { BigToggle(state.showOnlyCommuteBand, onToggleCommuteOnly) }
            )
            SettingRow(
                title = "지도 화면",
                hint = "자동은 해가 지면 밤 지도로 바뀌어요",
                control = {
                    Segmented(
                        options = listOf(
                            MapTheme.AUTO to "자동",
                            MapTheme.DAY to "낮",
                            MapTheme.NIGHT to "밤"
                        ),
                        selected = state.mapTheme,
                        onSelect = onSelectMapTheme
                    )
                }
            )
            SettingRow(
                title = "지도 시점",
                hint = "2D는 위에서 내려다봐요",
                control = {
                    Segmented(
                        options = listOf(true to "3D", false to "2D"),
                        selected = state.map3d,
                        onSelect = { if (it != state.map3d) onToggleMap3d() }
                    )
                }
            )
            SettingRow(
                title = "주행 화면",
                hint = "3D 도로는 가상 콘솔이 켜진 에뮬레이터에서만 보여요",
                control = {
                    Segmented(
                        options = listOf(DriveView.MAP to "지도", DriveView.HIGHWAY_3D to "3D 도로"),
                        selected = state.driveView,
                        onSelect = onSelectDriveView
                    )
                }
            )
            Text(
                text = "온길 · 도로 위를 따뜻하게",
                color = TodakColors.TextDim,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 6.dp, top = 8.dp)
            )
        }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    hint: String,
    control: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(TodakColors.Panel)
            .border(1.dp, TodakColors.Edge, RoundedCornerShape(20.dp))
            .padding(horizontal = 26.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, color = TodakColors.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(hint, color = TodakColors.TextDim, fontSize = 16.sp)
        }
        control()
    }
}
