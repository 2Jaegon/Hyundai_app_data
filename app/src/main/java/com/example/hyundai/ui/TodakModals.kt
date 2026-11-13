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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.example.hyundai.ui.pet.PetModel
import com.example.hyundai.ui.pet.PetPose
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
                .border(1.5.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(26.dp)),
            color = TodakColors.Background
        ) { content() }
    }
}

/** 6. 음성 쪽지 남기기. "쪽지 남겨줘" 음성 호출 → 음성 입력("비 오니까 다들 천천히 가요!") → AI 검증 및 등록 완료. */
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

    ModalFrame(widthFraction = 0.80f, accent = TodakColors.Route, onDismiss = onDismiss) {
        Column(
            modifier = Modifier.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("음성 쪽지 작성", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(TodakColors.Route.copy(alpha = 0.2f))
                            .border(1.dp, TodakColors.Route, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "음성 호출: “${state.voiceHudUserUtterance ?: "쪽지 남겨줘"}”",
                            color = TodakColors.Route,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Text(
                    "오늘의 작성권 ${state.remainingNoteTickets}장",
                    color = TodakColors.Route,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 제로 경청/완료 포즈
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(TodakColors.Panel)
                        .border(1.dp, TodakColors.Edge, RoundedCornerShape(20.dp))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PetModel(
                        accent = TodakColors.Route,
                        modifier = Modifier.size(116.dp),
                        level = state.activePet.level,
                        spin = state.isRecordingStt,
                        excited = !state.isRecordingStt && state.draftVoiceText.isNotBlank(),
                        pose = if (state.isRecordingStt) PetPose.BEG else PetPose.HAPPY
                    )
                    Text(
                        text = if (state.isRecordingStt) "제로가 귀 기울여 듣고 있어요" else "작성 준비 완료!",
                        color = TodakColors.TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(TodakColors.Panel)
                        .border(
                            1.5.dp,
                            if (state.isRecordingStt) TodakColors.Route else TodakColors.Good,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (state.isRecordingStt) {
                                "음성 입력 중 · 남은 시간 ${state.recordingSecondsLeft}초"
                            } else {
                                "인식된 음성 쪽지 내용"
                            },
                            color = if (state.isRecordingStt) TodakColors.Route else TodakColors.Good,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Button(
                            onClick = { onStartStt(null) },
                            enabled = !state.isRecordingStt,
                            colors = ButtonDefaults.buttonColors(containerColor = TodakColors.RouteDeep)
                        ) {
                            Text(
                                if (state.isRecordingStt) "음성 듣는 중" else "다시 말하기",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = if (state.draftVoiceText.isNotBlank()) {
                            "“${state.draftVoiceText}”"
                        } else {
                            "“비 오니까 다들 천천히 가요!”"
                        },
                        color = Color.White,
                        fontSize = 23.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.ExtraBold
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
                        .background(tone.copy(alpha = 0.14f))
                        .border(1.dp, tone.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when (mod.status) {
                        ModerationStatus.APPROVED -> Text(
                            "AI 안전 검증 통과 · ${mod.category.label} 쪽지 (${mod.category.lifespanLabel}) · 등록 시 +15 EXP, +20 P 적립",
                            color = tone,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        ModerationStatus.PURIFIED -> {
                            Text("AI 다정한 표현 순화 완료", color = tone, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "“${mod.cleanedText}”",
                                color = Color.White,
                                fontSize = 20.sp,
                                lineHeight = 28.sp
                            )
                            Text(
                                "${mod.category.label} 쪽지 · ${mod.category.lifespanLabel}",
                                color = TodakColors.TextSecondary,
                                fontSize = 16.sp
                            )
                        }
                        ModerationStatus.BLOCKED -> Text(
                            "남길 수 없는 표현이 있어요. 다시 말씀해 주세요.",
                            color = tone,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
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
                    Text("출퇴근 동행 밴드에 공유", color = TodakColors.TextSecondary, fontSize = 18.sp)
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
                        Text("쪽지 등록하기", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 7. 주행 종료 후 리포트.
 * Safe Drive, Smooth Drive, Manner Drive 기록과 이번 주행으로 획득한 경험치·포인트를
 * 감점 없이 좋은 운전만 쌓이는 긍정 강화 구조로 보여 준다.
 */
@Composable
fun ParkingReportAndTireTrackModal(
    state: AppUiState,
    onDismiss: () -> Unit
) {
    val report = state.parkingReport
    val eval = state.evaluation
    val safeExp = eval.completedSafeBlocks * 10
    val safePts = eval.completedSafeBlocks * 5
    val smoothExp = eval.completedSmoothWindows * 20
    val smoothPts = eval.completedSmoothWindows * 10
    val mannerExp = eval.completedMannerTurns * 15
    val mannerPts = eval.completedMannerTurns * 10
    val totalSessionExp = safeExp + smoothExp + mannerExp
    val totalSessionPts = safePts + smoothPts + mannerPts

    ModalFrame(
        widthFraction = 0.88f,
        accent = TodakColors.Good,
        modifier = Modifier.fillMaxHeight(0.88f),
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("주행 종료 리포트", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(TodakColors.Good.copy(alpha = 0.2f))
                            .border(1.dp, TodakColors.Good, RoundedCornerShape(10.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "감점 0점 · 좋은 운전만 쌓이는 긍정 강화 평가",
                            color = TodakColors.Good,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
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
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(0.52f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(TodakColors.Panel)
                        .padding(22.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Safe · Smooth · Manner 주행 기록",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        ReportDriveRow(
                            title = "Safe Drive (안전 주행)",
                            subtitle = "1.0km 정속·안정 주행 달성 (${eval.completedSafeBlocks}회)",
                            reward = "+${safeExp} EXP · +${safePts} P",
                            color = TodakColors.Route
                        )
                        ReportDriveRow(
                            title = "Smooth Drive (부드러운 주행)",
                            subtitle = "2.0km 연속 무급가감속 완주 (${eval.completedSmoothWindows}회 · 급제동 감점 없음)",
                            reward = "+${smoothExp} EXP · +${smoothPts} P",
                            color = TodakColors.Good
                        )
                        ReportDriveRow(
                            title = "Manner Drive (매너 운전)",
                            subtitle = "방향지시등 1.5초 유지 + 차선변경 인증 (${eval.completedMannerTurns}회)",
                            reward = "+${mannerExp} EXP · +${mannerPts} P",
                            color = TodakColors.Warm
                        )
                    }

                    // 이번 주행으로 획득한 총 경험치와 포인트 요약
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(TodakColors.Background)
                            .border(1.5.dp, TodakColors.Good.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("이번 주행 총 획득 보상", color = TodakColors.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text("감점 없이 좋은 운전 기록만 누적됨", color = TodakColors.Good, fontSize = 14.sp)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(
                                text = "+${totalSessionExp} EXP",
                                color = TodakColors.Route,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "+${totalSessionPts} P",
                                color = TodakColors.Warm,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(0.48f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(20.dp))
                        .background(TodakColors.Panel)
                        .padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("오늘의 바퀴자취 & 온기 요약", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            String.format(Locale.KOREA, "총 %.1fkm 주행", report.todayDrivenKm),
                            color = TodakColors.Route,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Text(
                        text = "오늘 ${report.todayListenersReached}명이 내 쪽지를 들었고, ${report.todayTodakReceived}명이 하트(좋아요)를 보냈어요",
                        color = TodakColors.TextSecondary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )

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
                                .background(TodakColors.Background.copy(alpha = 0.92f))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            state.tireTracks.takeLast(4).forEach { node ->
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(TodakColors.Panel)
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(node.timeLabel, color = TodakColors.Good, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    Text(node.segmentName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    Text(
                                        node.mannerGrade,
                                        color = TodakColors.Warm,
                                        fontSize = 14.sp,
                                        maxLines = 1
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
private fun ReportDriveRow(
    title: String,
    subtitle: String,
    reward: String,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TodakColors.PanelDeep)
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = TodakColors.TextSecondary, fontSize = 14.sp)
        }
        Text(
            text = reward,
            color = color,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold
        )
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

/**
 * 9-2. 포인트 화면 (적립 포인트와 차량 관리 혜택 활용 시안).
 * 차량 정비 등 실제 연계가 확정된 것처럼 보이지 않도록 '예시 화면 · 연계 예정'을 명확히 표시한다.
 */
@Composable
fun HyundaiRewardShopModal(
    state: AppUiState,
    onRedeem: (RewardShopItem) -> Unit,
    onDismiss: () -> Unit
) {
    ModalFrame(widthFraction = 0.78f, accent = TodakColors.Warm, onDismiss = onDismiss) {
        Column(
            modifier = Modifier.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("온길 포인트 & 차량 관리 혜택", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(TodakColors.Warm.copy(alpha = 0.2f))
                            .border(1.5.dp, TodakColors.Warm, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "예시 화면 · 제휴 연계 예정",
                            color = TodakColors.Warm,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Text(
                    "보유 포인트 ${won(state.warmthPoints)}P",
                    color = TodakColors.Warm,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TodakColors.PanelDeep)
                    .border(1.dp, TodakColors.EdgeStrong, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "※ 본 화면은 적립된 온길 포인트의 차량 관리·정비·보험 혜택 활용 기획을 보여주는 예시 화면(시안)이며, 실제 제휴 서비스는 향후 연계 예정입니다.",
                    color = TodakColors.TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.rewardItems) { item ->
                    val affordable = state.warmthPoints >= item.pointCost
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(TodakColors.Panel)
                            .border(1.dp, TodakColors.Edge, RoundedCornerShape(16.dp))
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(item.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(TodakColors.EdgeStrong)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("연계 예정", color = TodakColors.Warm, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                "${item.brand} · ${item.categoryLabel} (기획 예시)",
                                color = TodakColors.TextDim,
                                fontSize = 15.sp
                            )
                        }
                        Button(
                            onClick = { onRedeem(item) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (affordable) TodakColors.RouteDeep else TodakColors.Disabled
                            )
                        ) {
                            Text(
                                "${won(item.pointCost)}P 교환 예시",
                                color = Color.White,
                                fontSize = 17.sp,
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
