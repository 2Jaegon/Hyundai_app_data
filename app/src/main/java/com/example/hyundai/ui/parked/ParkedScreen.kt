package com.example.hyundai.ui.parked

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hyundai.domain.NoteInbox
import com.example.hyundai.model.MannerTurnPhase
import com.example.hyundai.model.VoiceNote
import com.example.hyundai.ui.pet.PetModel
import com.example.hyundai.ui.pet.PetPose
import com.example.hyundai.ui.theme.TodakColors
import com.example.hyundai.viewmodel.AppUiState
import java.util.Locale

/**
 * 정차(P/N/R) 홈 화면. 기본 펫 제로, 현재 성장 단계와 경험치, 오늘 사용할 수 있는 쪽지 작성권,
 * 오늘의 운전 기록과 도로 위 쪽지를 한눈에 살펴보는 화면이다.
 */
@Composable
fun ParkedScreen(
    state: AppUiState,
    onOpenRewardShop: () -> Unit,
    onOpenParkingReport: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVoiceComposer: () -> Unit,
    onListenNote: (VoiceNote) -> Unit,
    onStopTts: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDestination: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TodakColors.Background)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ParkedHeader(state, onOpenRewardShop, onOpenParkingReport, onOpenSettings, onOpenDestination)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PetPane(
                modifier = Modifier
                    .weight(0.32f)
                    .fillMaxHeight(),
                state = state
            )
            DrivingPane(
                modifier = Modifier
                    .weight(0.30f)
                    .fillMaxHeight(),
                state = state
            )
            NotesPane(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight(),
                state = state,
                onOpenVoiceComposer = onOpenVoiceComposer,
                onOpenInbox = onOpenInbox,
                onListenNote = onListenNote,
                onStopTts = onStopTts
            )
        }
    }
}

@Composable
private fun PanelBox(
    modifier: Modifier = Modifier,
    border: Color = TodakColors.Edge,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(TodakColors.Panel)
            .border(1.5.dp, border, RoundedCornerShape(22.dp))
            .padding(20.dp)
    ) { content() }
}

@Composable
private fun ParkedHeader(
    state: AppUiState,
    onOpenRewardShop: () -> Unit,
    onOpenParkingReport: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDestination: () -> Unit
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
            Text("온길", color = TodakColors.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(TodakColors.Panel)
                    .border(1.dp, TodakColors.Edge, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "기어 ${state.telemetry.gear.label} · 홈 화면",
                    color = TodakColors.TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Chip(
                text = "오늘의 쪽지 작성권 ${state.remainingNoteTickets}장",
                color = TodakColors.Route,
                onClick = null
            )
            Chip(
                text = String.format(Locale.KOREA, "%,dP", state.warmthPoints),
                color = TodakColors.Warm,
                onClick = onOpenRewardShop
            )
            Chip(text = "목적지", color = TodakColors.Route, onClick = onOpenDestination)
            Chip(text = "리포트", color = TodakColors.Good, onClick = onOpenParkingReport)
            Chip(text = "설정", color = TodakColors.Warm, onClick = onOpenSettings)
        }
    }
}

@Composable
private fun Chip(text: String, color: Color, onClick: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text, color = color, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ───────────────────────── 펫 (제로) ─────────────────────────

@Composable
private fun PetPane(modifier: Modifier, state: AppUiState) {
    val pet = state.activePet
    // 펫은 포인트 색(살구)으로 통일한다.
    val accent = TodakColors.ApricotDeep
    val expRatio = (pet.currentExp.toFloat() / pet.maxExp.toFloat()).coerceIn(0f, 1f)
    val isLevelingUp = state.levelUpBanner != null
    var previewPoseIndex by remember { mutableStateOf(-1) }
    val allPoses = PetPose.entries
    val activePose = if (previewPoseIndex in allPoses.indices) {
        allPoses[previewPoseIndex]
    } else {
        PetPose.fromState(state, excited = isLevelingUp)
    }

    val panelBorder = if (isLevelingUp) TodakColors.Good else accent.copy(alpha = 0.55f)

    PanelBox(modifier = modifier, border = panelBorder) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.levelUpBanner != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(TodakColors.Good.copy(alpha = 0.22f))
                        .border(1.5.dp, TodakColors.Good, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.levelUpBanner,
                        color = TodakColors.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(
                                if (isLevelingUp) TodakColors.Good.copy(alpha = 0.42f) else accent.copy(alpha = 0.28f),
                                TodakColors.PanelDeep
                            )
                        )
                    )
                    .border(
                        width = if (isLevelingUp) 2.dp else 1.dp,
                        color = if (isLevelingUp) TodakColors.Good else TodakColors.Edge,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable {
                        previewPoseIndex = if (previewPoseIndex + 1 >= allPoses.size) -1 else previewPoseIndex + 1
                    }
            ) {
                PetModel(
                    accent = accent,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    level = pet.level,
                    spin = state.heldNote != null || isLevelingUp,
                    holdingNote = state.heldNote != null,
                    excited = state.isTtsSpeaking || isLevelingUp,
                    pose = if (isLevelingUp) PetPose.forLevel(pet.level) else activePose
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(pet.name, color = TodakColors.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.22f))
                        .border(1.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "Lv.${pet.level} · ${activePose.label}",
                        color = accent,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            // 제로의 현재 성장 단계 표시
            Text(
                text = "성장 단계: ${pet.stageName}",
                color = TodakColors.Warm,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            // 경험치 바 및 수치 표시
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "경험치 (EXP)",
                        color = TodakColors.TextSecondary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${pet.currentExp} / ${pet.maxExp} EXP (${(expRatio * 100).toInt()}%)",
                        color = accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                LinearProgressIndicator(
                    progress = { expRatio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(11.dp)
                        .clip(RoundedCornerShape(6.dp)),
                    color = if (isLevelingUp) TodakColors.Good else accent,
                    trackColor = TodakColors.Edge
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TodakColors.Edge)
                    .padding(12.dp)
            ) {
                Text(
                    text = pet.currentSpeech,
                    color = TodakColors.Text,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ───────────────────────── 오늘의 운전 ─────────────────────────

@Composable
private fun DrivingPane(modifier: Modifier, state: AppUiState) {
    val eval = state.evaluation

    PanelBox(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("오늘의 운전", color = TodakColors.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)

            ScoreRow(
                title = "Safe Drive · 안전 주행",
                count = eval.completedSafeBlocks,
                progress = (eval.safeBlockProgressKm / eval.safeBlockTargetKm).coerceIn(0f, 1f),
                detail = String.format(Locale.KOREA, "%.1f / %.0fkm (+10 EXP · +5 P)", eval.safeBlockProgressKm, eval.safeBlockTargetKm),
                accent = TodakColors.Route
            )
            ScoreRow(
                title = "Smooth Drive · 부드러운 주행",
                count = eval.completedSmoothWindows,
                progress = (eval.smoothWindowProgressKm / eval.smoothWindowTargetKm).coerceIn(0f, 1f),
                detail = String.format(Locale.KOREA, "%.1f / %.0fkm (+20 EXP · 감점 없음)", eval.smoothWindowProgressKm, eval.smoothWindowTargetKm),
                accent = TodakColors.Good
            )

            // 방향전환은 막대 대신, 지금 상태가 눈에 띌 때만 한 줄로 알려 준다.
            val (note, tone) = when (eval.mannerPhase) {
                MannerTurnPhase.VERIFIED -> "방금 매너 방향전환을 인정받았어요 (+15 EXP · +10 P)" to TodakColors.Good
                MannerTurnPhase.ABUSE_BLOCKED -> "이번 깜빡이는 적립되지 않았어요" to TodakColors.Danger
                MannerTurnPhase.COOLDOWN -> "잠시 후 다시 적립돼요" to TodakColors.Warm
                else -> "깜빡이 1.5초 유지 + 방향전환 시 +15 EXP · +10 P" to TodakColors.TextDim
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TodakColors.PanelDeep)
                    .border(1.dp, tone.copy(alpha = if (eval.mannerPhase == MannerTurnPhase.VERIFIED) 0.7f else 0.25f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Manner Drive · 매너 운전", color = TodakColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("${eval.completedMannerTurns}회", color = TodakColors.Good, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                }
                if (note != null) {
                    Text(note, color = tone, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(title: String, count: Int, progress: Float, detail: String, accent: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TodakColors.PanelDeep)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = TodakColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text("${count}회", color = accent, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = accent,
            trackColor = TodakColors.Edge
        )
        Text(detail, color = TodakColors.TextDim, fontSize = 15.sp)
    }
}

// ───────────────────────── 쪽지 ─────────────────────────

/**
 * 쪽지 요약. 새로 들어온 쪽지와 가장 최근에 받은 쪽지만 보여 주고, 전체 목록은 쪽지함에서 본다.
 */
@Composable
private fun NotesPane(
    modifier: Modifier,
    state: AppUiState,
    onOpenVoiceComposer: () -> Unit,
    onOpenInbox: () -> Unit,
    onListenNote: (VoiceNote) -> Unit,
    onStopTts: () -> Unit
) {
    val received = NoteInbox.received(state.notes)
    val latest = received.firstOrNull { it.id != state.activeIncomingNote?.id }

    PanelBox(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("도로 위 쪽지", color = TodakColors.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Button(
                    onClick = onOpenVoiceComposer,
                    colors = ButtonDefaults.buttonColors(containerColor = TodakColors.RouteDeep)
                ) {
                    Text("남기기", color = TodakColors.OnColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 이미 들은 쪽지는 읽는 중에만 보여 주어, 읽기가 끝난 직후 다시 눌러 재생되는 일이 없게 한다.
            state.activeIncomingNote?.takeIf { !it.isListenedByMe || state.isTtsSpeaking }?.let { incoming ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(TodakColors.Neutral.copy(alpha = 0.55f))
                        .border(1.5.dp, TodakColors.Route, RoundedCornerShape(18.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (state.isTtsSpeaking) "읽고 있어요" else "${incoming.distanceMeters}m 앞에 쪽지가 있어요",
                        color = TodakColors.Route,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "“${incoming.displayText}”",
                        color = TodakColors.Text,
                        fontSize = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(
                        onClick = { if (state.isTtsSpeaking) onStopTts() else onListenNote(incoming) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isTtsSpeaking) TodakColors.EdgeStrong else TodakColors.RouteDeep
                        )
                    ) {
                        Text(
                            if (state.isTtsSpeaking) "멈추기" else "듣기",
                            color = if (state.isTtsSpeaking) TodakColors.Text else TodakColors.OnColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (latest != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TodakColors.PanelDeep)
                        .border(1.dp, TodakColors.Edge, RoundedCornerShape(16.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(Color(latest.category.badgeColorHex))
                        )
                        Text("최근에 받은 쪽지", color = TodakColors.TextDim, fontSize = 16.sp)
                    }
                    Text(
                        "“${latest.displayText}”",
                        color = TodakColors.TextSecondary,
                        fontSize = 17.sp,
                        lineHeight = 24.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(modifier = Modifier.weight(1f))

            Button(
                onClick = onOpenInbox,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = TodakColors.Neutral)
            ) {
                Text(
                    "쪽지함 ${received.size + NoteInbox.mine(state.notes).size}",
                    color = TodakColors.RouteDeep,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
