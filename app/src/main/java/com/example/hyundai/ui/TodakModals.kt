package com.example.hyundai.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.hyundai.model.ModerationStatus
import com.example.hyundai.model.RewardShopItem
import com.example.hyundai.ui.theme.TodakColors
import com.example.hyundai.viewmodel.AppUiState
import java.util.Locale

private fun won(n: Int): String = String.format(Locale.KOREA, "%,d", n)

@Composable
private fun ModalFrame(
    widthFraction: Float,
    accent: Color,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(widthFraction)
                .clip(RoundedCornerShape(26.dp))
                .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(26.dp)),
            color = TodakColors.Background
        ) { content() }
    }
}

/** 음성 쪽지 남기기. 말하고, 확인하고, 남기는 세 단계만 보여 준다. */
@Composable
fun VoiceNoteComposeModal(
    state: AppUiState,
    onStartStt: (String?) -> Unit,
    onPublish: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var commuteBandOnly by remember { mutableStateOf(true) }
    val mod = state.lastModerationResult
    val canPublish = mod != null && mod.status != ModerationStatus.BLOCKED && !state.isRecordingStt

    ModalFrame(widthFraction = 0.76f, accent = TodakColors.Route, onDismiss = onDismiss) {
        Column(
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("음성 쪽지", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "작성권 ${state.remainingNoteTickets}장",
                    color = TodakColors.Route,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(TodakColors.Panel)
                    .border(
                        1.dp,
                        if (state.isRecordingStt) TodakColors.Danger else TodakColors.Edge,
                        RoundedCornerShape(20.dp)
                    )
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.isRecordingStt) "듣고 있어요 · ${state.recordingSecondsLeft}초" else "말씀하신 내용",
                        color = if (state.isRecordingStt) TodakColors.Danger else TodakColors.TextSecondary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { onStartStt(null) },
                        enabled = !state.isRecordingStt,
                        colors = ButtonDefaults.buttonColors(containerColor = TodakColors.RouteDeep)
                    ) {
                        Text(
                            if (state.isRecordingStt) "녹음 중" else "다시 말하기",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (!state.isRecordingStt && state.draftVoiceText.isNotBlank()) {
                    Text(
                        text = "“${state.draftVoiceText}”",
                        color = Color.White,
                        fontSize = 22.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (mod != null && !state.isRecordingStt) {
                val tone = when (mod.status) {
                    ModerationStatus.APPROVED -> TodakColors.Good
                    ModerationStatus.PURIFIED -> TodakColors.Warm
                    ModerationStatus.BLOCKED -> TodakColors.Danger
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(tone.copy(alpha = 0.12f))
                        .border(1.dp, tone.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (mod.status) {
                        ModerationStatus.APPROVED -> Text(
                            "${mod.category.label} 쪽지 · ${mod.category.lifespanLabel}",
                            color = tone, fontSize = 18.sp, fontWeight = FontWeight.Bold
                        )
                        ModerationStatus.PURIFIED -> {
                            Text("다정한 표현으로 다듬었어요", color = tone, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "“${mod.cleanedText}”",
                                color = Color.White, fontSize = 20.sp, lineHeight = 28.sp
                            )
                            Text(
                                "${mod.category.label} 쪽지 · ${mod.category.lifespanLabel}",
                                color = TodakColors.TextSecondary, fontSize = 16.sp
                            )
                        }
                        ModerationStatus.BLOCKED -> Text(
                            "남길 수 없는 표현이 있어요. 다시 말씀해 주세요.",
                            color = tone, fontSize = 18.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Switch(
                        checked = commuteBandOnly,
                        onCheckedChange = { commuteBandOnly = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = TodakColors.RouteDeep)
                    )
                    Text("출퇴근 길에만 남기기", color = TodakColors.TextSecondary, fontSize = 18.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedButton(onClick = onDismiss) {
                        Text("취소", color = TodakColors.TextSecondary, fontSize = 18.sp)
                    }
                    Button(
                        onClick = { onPublish(commuteBandOnly) },
                        enabled = canPublish,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = TodakColors.RouteDeep,
                            disabledContainerColor = TodakColors.Disabled
                        )
                    ) {
                        Text("쪽지 남기기", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** 주차하면 보여 주는 하루 요약. */
@Composable
fun ParkingReportAndTireTrackModal(
    state: AppUiState,
    onDismiss: () -> Unit
) {
    val report = state.parkingReport

    ModalFrame(
        widthFraction = 0.84f,
        accent = TodakColors.Good,
        modifier = Modifier.fillMaxHeight(0.82f),
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("오늘의 리포트", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = TodakColors.Neutral)
                ) {
                    Text("닫기", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.45f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(TodakColors.Panel)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(
                            text = "오늘 ${report.todayListenersReached}명이 내 쪽지를 들었고,\n${report.todayTodakReceived}명이 좋아요를 눌렀어요",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 34.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ReportStat(Modifier.weight(1f), "주행", String.format(Locale.KOREA, "%.1fkm", report.todayDrivenKm), TodakColors.Route)
                            ReportStat(Modifier.weight(1f), "매너 방향전환", "${report.todayMannerTurns}회", TodakColors.Good)
                            ReportStat(Modifier.weight(1f), "부드러운 주행", "${report.todaySmoothWindows}회", TodakColors.Warm)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("이번 달", color = TodakColors.TextDim, fontSize = 16.sp)
                        Text(
                            report.monthlyWarmthRank,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "받은 좋아요 ${report.monthlyTotalTodak}회",
                            color = TodakColors.TextSecondary,
                            fontSize = 18.sp
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(0.55f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(TodakColors.Panel)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("지나온 길", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(TodakColors.PanelDeep)
                    ) {
                        TrackCanvas(state)
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(TodakColors.Background.copy(alpha = 0.9f))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            state.tireTracks.forEach { node ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(TodakColors.Panel)
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(node.timeLabel, color = TodakColors.Good, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                    Text(node.segmentName, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "쪽지 ${node.noteCount} · 좋아요 ${node.todakReceived}",
                                        color = TodakColors.TextSecondary,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackCanvas(state: AppUiState) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val river = Path().apply {
            moveTo(0f, h * 0.82f)
            cubicTo(w * 0.35f, h * 0.72f, w * 0.65f, h * 0.60f, w, h * 0.48f)
        }
        drawPath(river, TodakColors.RouteDeep.copy(alpha = 0.22f), style = Stroke(width = 36f, cap = StrokeCap.Round))

        val nodes = state.tireTracks
        val track = Path().apply {
            if (nodes.isNotEmpty()) {
                moveTo(w * nodes.first().xFraction, h * nodes.first().yFraction)
                for (i in 1 until nodes.size) lineTo(w * nodes[i].xFraction, h * nodes[i].yFraction)
            }
        }
        drawPath(track, TodakColors.Good.copy(alpha = 0.35f), style = Stroke(width = 18f, cap = StrokeCap.Round))
        drawPath(
            track,
            TodakColors.Route,
            style = Stroke(width = 5f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), 0f))
        )
        nodes.forEach { node ->
            val c = Offset(w * node.xFraction, h * node.yFraction)
            drawCircle(TodakColors.Good, radius = 12f, center = c)
            drawCircle(Color.White, radius = 5f, center = c)
        }
    }
}

@Composable
private fun ReportStat(modifier: Modifier, label: String, value: String, color: Color) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TodakColors.Background)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, color = TodakColors.TextDim, fontSize = 15.sp)
        Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/** 모은 포인트로 혜택을 바꾸는 화면. */
@Composable
fun HyundaiRewardShopModal(
    state: AppUiState,
    onRedeem: (RewardShopItem) -> Unit,
    onDismiss: () -> Unit
) {
    ModalFrame(widthFraction = 0.70f, accent = TodakColors.Warm, onDismiss = onDismiss) {
        Column(
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("리워드 교환", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "${won(state.warmthPoints)}P",
                    color = TodakColors.Warm,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.rewardItems) { item ->
                    val affordable = state.warmthPoints >= item.pointCost
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(TodakColors.Panel)
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(item.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "${item.brand} · ${item.categoryLabel}",
                                color = TodakColors.TextDim,
                                fontSize = 16.sp
                            )
                        }
                        Button(
                            onClick = { onRedeem(item) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (affordable) TodakColors.RouteDeep else TodakColors.Disabled
                            )
                        ) {
                            Text(
                                "${won(item.pointCost)}P 교환",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = TodakColors.Neutral)
                ) {
                    Text("닫기", color = Color.White, fontSize = 18.sp)
                }
            }
        }
    }
}
