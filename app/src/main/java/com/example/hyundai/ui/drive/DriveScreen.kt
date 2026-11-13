package com.example.hyundai.ui.drive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hyundai.domain.NoteAccess
import com.example.hyundai.domain.NoteInbox
import com.example.hyundai.domain.RangeAdvisor
import com.example.hyundai.model.MannerTurnPhase
import com.example.hyundai.model.TurnSignalState
import com.example.hyundai.nav.DriveView
import com.example.hyundai.nav.GeoPoint
import com.example.hyundai.nav.MapTheme
import com.example.hyundai.nav.NavUiState
import com.example.hyundai.nav.TurnIcon
import com.example.hyundai.ui.map.NavMap
import com.example.hyundai.ui.map.NavTheme
import com.example.hyundai.ui.pet.PetModel
import com.example.hyundai.ui.pet.PetPose
import com.example.hyundai.ui.theme.TodakColors
import com.example.hyundai.viewmodel.AppUiState
import kotlinx.coroutines.delay

private val Panel = NavTheme.Panel
private val PanelEdge = NavTheme.PanelEdge

/**
 * 주행 중(D 기어) 화면.
 * 주행 중에는 화면이 간결해지고 TBT 회전 안내, 속도/기어, ETA 및 우측 상단 제로 아이콘 등 최소한의 정보만 표시된다.
 * 쪽지 수신 안내·음성 응답·하트 반응은 제로 옆 음성 카드와 TTS로 전달된다.
 */
@Composable
fun DriveScreen(
    state: AppUiState,
    modifier: Modifier = Modifier,
    onOpenInbox: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenDestination: () -> Unit = {},
    onToggleMap3d: () -> Unit = {},
    /** 다른 화면에 가려 안 보일 때는 false로 두어 펫을 그리지 않는다. */
    petAnimated: Boolean = true,
    /** 미리보기처럼 지도를 대신할 때만 넘긴다. */
    map: (@Composable (Modifier) -> Unit)? = null
) {
    val isDay = rememberIsDay(state.mapTheme, state.nav.carPosition, state.telemetry.headlampOn)
    val highway = state.driveView == DriveView.HIGHWAY_3D
    val stopped = NoteAccess.canBrowse(state.telemetry.gear, state.telemetry.speedKmh)

    Box(modifier = modifier.fillMaxSize()) {
        if (map != null) {
            map(Modifier.fillMaxSize())
        } else {
            // 3D 도로가 덮는 동안 지도는 보간하지 않고 위치만 맞춰 둔다(GPU를 두 장면이 나눠 쓰지 않게).
            NavMap(
                nav = state.nav,
                isDay = isDay,
                modifier = Modifier.fillMaxSize(),
                covered = !petAnimated || highway,
                tilted = state.map3d
            )
            if (highway) {
                Highway3dView(
                    speedKmh = state.telemetry.speedKmh,
                    gear = state.telemetry.gear,
                    active = petAnimated,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TurnCard(nav = state.nav)
            VehicleChips(state = state)
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            EtaChip(nav = state.nav, destination = state.destinationName)
            AddressChip(address = state.telemetry.roadName)
        }

        // 우측 상단: 음성 대화/쪽지 재생/하트 반응 HUD 카드 + 제로 아이콘 배지
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp, top = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            VoiceInteractionHudCard(state = state)
            PetBadge(
                state = state,
                animate = petAnimated
            )
        }

        // 정차 중일 때만 터치 메뉴 버튼을 보여 주고, 주행 중에는 숨겨 화면을 간결하게 유지한다.
        if (stopped) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 24.dp, top = 22.dp + 150.dp + 12.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StoppedAction("목적지", enabled = true, onClick = onOpenDestination)
                StoppedAction(if (state.map3d) "2D 지도" else "3D 지도", enabled = true, onClick = onToggleMap3d)
                StoppedAction("쪽지함", enabled = true, onClick = onOpenInbox)
                StoppedAction("설정", enabled = true, onClick = onOpenSettings)
            }
        }

        VehicleAlert(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 84.dp)
        )

        // 기어 전환(P → D) 안내 배지
        state.gearTransitionBanner?.let { banner ->
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Panel)
                    .border(2.dp, NavTheme.PetAccent, RoundedCornerShape(22.dp))
                    .padding(horizontal = 32.dp, vertical = 20.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(NavTheme.PetAccent)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "P → D",
                            color = Color(0xFF001A3A),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = banner,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }

        // 현재 시뮬레이션/주행 이벤트 안내 배너
        if (state.toastBanner.isNotBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 22.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Panel)
                    .border(1.dp, NavTheme.PetAccent.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text(
                    text = state.toastBanner,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun StoppedAction(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) Panel else Panel.copy(alpha = 0.65f))
            .border(1.dp, if (enabled) NavTheme.PetAccent.copy(alpha = 0.7f) else PanelEdge, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else NavTheme.TextSecondary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 지도가 낮인지 밤인지. 자동일 때는 차량 전조등 점등 여부와 차 위치의 해가 떴는지를 함께 확인해,
 * 터널·야간에 전조등이 켜지거나 해가 지면 저절로 밤 지도로 바뀐다.
 */
@Composable
private fun rememberIsDay(theme: MapTheme, position: GeoPoint, headlampOn: Boolean): Boolean {
    val nowMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(60_000L)
            value = System.currentTimeMillis()
        }
    }
    return remember(theme, position, nowMs, headlampOn) { theme.isDay(position, nowMs, headlampOn) }
}

@Composable
private fun TurnCard(nav: NavUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Panel)
            .border(1.dp, PanelEdge, RoundedCornerShape(24.dp))
            .padding(horizontal = 26.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TurnArrow(
            icon = if (nav.hasRoute) nav.nextTurn else TurnIcon.STRAIGHT,
            modifier = Modifier.size(84.dp)
        )
        Text(
            text = if (nav.hasRoute) formatDistance(nav.distToTurnM) else "···",
            color = Color.White,
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/** 기어, 속도, 방향지시등, 배터리·주행가능거리 표시. */
@Composable
private fun VehicleChips(state: AppUiState, modifier: Modifier = Modifier) {
    val console = state.console
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HudChip {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(NavTheme.PetAccent.copy(alpha = 0.25f))
                    .border(1.dp, NavTheme.PetAccent, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = state.telemetry.gear.label,
                    color = NavTheme.PetAccent,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                text = "${state.telemetry.speedKmh.toInt()}",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text("km/h", color = NavTheme.TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            val cruise = console?.cruiseTargetKph
            if (console?.cruiseOn == true) {
                Text(
                    text = if (cruise != null) "크루즈 $cruise" else "크루즈",
                    color = NavTheme.PetAccent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 방향지시등 점등 또는 매너 방향전환 인증 시 표시
        val signal = state.telemetry.turnSignal
        val phase = state.evaluation.mannerPhase
        if (signal != TurnSignalState.OFF || phase == MannerTurnPhase.VERIFIED) {
            val isVerified = phase == MannerTurnPhase.VERIFIED
            val badgeColor = if (isVerified) TodakColors.Good else Color(0xFFFFCC4D)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Panel)
                    .border(1.5.dp, badgeColor, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isVerified) {
                        "✓ 매너 차선변경 완료 (+15 EXP · +10 P)"
                    } else {
                        "${signal.label} (${String.format(java.util.Locale.US, "%.1f초", state.evaluation.signalHoldSeconds.coerceAtLeast(0.8f))})"
                    },
                    color = badgeColor,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        val battery = console?.batteryPct
        val range = console?.rangeKm
        if (battery != null || range != null) {
            HudChip {
                if (battery != null) {
                    Text("$battery%", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                if (range != null) {
                    Text("${range}km", color = NavTheme.TextSecondary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                if (console.charging) {
                    Text("충전 중", color = NavTheme.PetAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HudChip(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .border(1.dp, PanelEdge, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

/** 켜진 경고등과 배터리 부족. 주행 중에도 한눈에 읽히도록 한 줄로만 띄운다. */
@Composable
private fun VehicleAlert(state: AppUiState, modifier: Modifier = Modifier) {
    val console = state.console ?: return
    val lines = buildList {
        if (console.warnings.isNotEmpty()) add(console.warnings.joinToString(" · "))
        RangeAdvisor.warning(console.batteryPct, console.rangeKm, state.nav.remainingM)?.let(::add)
    }
    if (lines.isEmpty()) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .border(2.dp, NavTheme.Alert, RoundedCornerShape(16.dp))
            .padding(horizontal = 22.dp, vertical = 10.dp)
    ) {
        Text(lines.joinToString("  |  "), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** 기본 내비의 현재 주소 표시와 같은 자리. */
@Composable
private fun AddressChip(address: String, modifier: Modifier = Modifier) {
    if (address.isBlank()) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .border(1.dp, PanelEdge, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(address, color = NavTheme.TextSecondary, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun EtaChip(nav: NavUiState, destination: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .border(1.dp, PanelEdge, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (destination.isNotBlank()) {
            Text(destination, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        }
        Text(
            text = if (nav.hasRoute) formatEta(nav.etaSec) else "--",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(NavTheme.Dot)
        )
        Text(
            text = if (nav.hasRoute) formatDistance(nav.remainingM) else "--",
            color = NavTheme.TextSecondary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 주행 중 쪽지 수신 안내, 운전자 음성 응답("응", "좋아요"), 쪽지 음성 재생 대사, 하트 전달 완료 반응을
 * 제로 아이콘 옆에 직관적인 카드로 표시한다.
 */
@Composable
private fun VoiceInteractionHudCard(
    state: AppUiState,
    modifier: Modifier = Modifier
) {
    val prompt = state.voiceHudPrompt
    val utterance = state.voiceHudUserUtterance
    val held = state.heldNote
    val heartBanner = state.heartReactionBanner
    val showCard = prompt != null || utterance != null || (held != null && state.isTtsSpeaking) || heartBanner != null
    if (!showCard) return

    val borderColor = when {
        heartBanner != null -> Color(0xFFFF6B8B)
        held != null && state.isTtsSpeaking -> TodakColors.Good
        utterance != null -> NavTheme.PetAccent
        else -> NavTheme.PetAccent.copy(alpha = 0.75f)
    }

    Column(
        modifier = modifier
            .widthIn(max = 560.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Panel)
            .border(2.dp, borderColor, RoundedCornerShape(22.dp))
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (prompt != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(NavTheme.PetAccent.copy(alpha = 0.22f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("쪽지 수신 안내", color = NavTheme.PetAccent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    text = "“$prompt”",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (utterance != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(TodakColors.Good.copy(alpha = 0.25f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("운전자 음성", color = TodakColors.Good, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    text = "“$utterance”",
                    color = TodakColors.Good,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        if (held != null && state.isTtsSpeaking) {
            val fullLine = "${NoteInbox.introLine(held.authorPetName)} ${held.displayText}"
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background( Color(0xFF07152B))
                    .border(1.dp, TodakColors.Good.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "음성 쪽지 재생 중 (${held.authorPetName} Lv.${held.authorPetLevel})",
                    color = TodakColors.Good,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "“$fullLine”",
                    color = Color.White,
                    fontSize = 19.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (heartBanner != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x33FF5E84))
                    .border(1.5.dp, Color(0xFFFF6B8B), RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HeartIcon(modifier = Modifier.size(28.dp))
                Text(
                    text = heartBanner,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
private fun HeartIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.88f)
            cubicTo(w * 0.1f, h * 0.62f, w * 0.05f, h * 0.25f, w * 0.28f, h * 0.15f)
            cubicTo(w * 0.42f, h * 0.1f, w * 0.5f, h * 0.22f, w * 0.5f, h * 0.28f)
            cubicTo(w * 0.5f, h * 0.22f, w * 0.58f, h * 0.1f, w * 0.72f, h * 0.15f)
            cubicTo(w * 0.95f, h * 0.25f, w * 0.9f, h * 0.62f, w * 0.5f, h * 0.88f)
            close()
        }
        drawPath(path, Color(0xFFFF5E84))
    }
}

/**
 * 오른쪽 위 펫(제로). 매너운전 기록 시 반짝이는 스파클 이펙트와 보상 배지가 뜨고,
 * 새 쪽지/응원/하트 반응에 따라 9개 그리드 포즈가 바뀐다.
 */
@Composable
private fun PetBadge(
    state: AppUiState,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val accent = NavTheme.PetAccent
    var excited by remember { mutableStateOf(false) }
    val hasIncoming = state.activeIncomingNote != null || state.isTtsSpeaking
    val isMannerSparkle = state.mannerSparkleActive || state.evaluation.mannerPhase == MannerTurnPhase.VERIFIED
    val isHeartActive = state.heartReactionBanner != null

    var firstToast by remember { mutableStateOf(true) }
    LaunchedEffect(state.toastBanner) {
        if (firstToast) {
            firstToast = false
            return@LaunchedEffect
        }
        excited = true
        delay(2_400)
        excited = false
    }

    val badgeBorderColor = when {
        isMannerSparkle -> Color(0xFFFFD54F)
        isHeartActive -> Color(0xFFFF6B8B)
        else -> accent.copy(alpha = 0.65f)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(modifier = Modifier.size(144.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        if (isMannerSparkle) {
                            Brush.radialGradient(
                                listOf(Color(0x55FFD54F), Color(0x3300AAD2), Panel)
                            )
                        } else if (isHeartActive) {
                            Brush.radialGradient(
                                listOf(Color(0x55FF6B8B), Panel)
                            )
                        } else {
                            Brush.verticalGradient(listOf(Panel, Panel))
                        }
                    )
                    .border(
                        width = if (isMannerSparkle || isHeartActive) 2.5.dp else 1.5.dp,
                        color = badgeBorderColor,
                        shape = RoundedCornerShape(26.dp)
                    )
            )
            PetModel(
                accent = accent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                level = state.activePet.level,
                spin = animate && (excited || state.isTtsSpeaking || state.heldNote != null || isMannerSparkle || isHeartActive),
                excited = excited || state.isTtsSpeaking || isMannerSparkle || isHeartActive,
                holdingNote = state.heldNote != null || isHeartActive,
                pose = PetPose.fromState(state, excited = excited || isHeartActive)
            )
            if (isMannerSparkle) {
                SparkleOverlay(modifier = Modifier.fillMaxSize())
            }
            if (hasIncoming) {
                ChatDot(
                    color = if (isHeartActive) Color(0xFFFF5E84) else accent,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
            // 제로 이름 & 레벨 소형 태그
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC061224))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${state.activePet.name} Lv.${state.activePet.level}",
                    color = if (isMannerSparkle) Color(0xFFFFD54F) else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        if (isMannerSparkle) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xEE091E3A))
                    .border(1.5.dp, Color(0xFFFFD54F), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "매너운전 기록! +15 EXP · +10 P",
                    color = Color(0xFFFFD54F),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

/** 매너운전 기록 시 제로 아이콘 주변에 반짝이는 4각 별 스파클 이펙트. */
@Composable
private fun SparkleOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        fun drawStar(cx: Float, cy: Float, r: Float, color: Color) {
            val path = Path().apply {
                moveTo(cx, cy - r)
                quadraticTo(cx, cy, cx + r, cy)
                quadraticTo(cx, cy, cx, cy + r)
                quadraticTo(cx, cy, cx - r, cy)
                quadraticTo(cx, cy, cx, cy - r)
                close()
            }
            drawPath(path, color)
        }
        drawStar(w * 0.16f, h * 0.18f, w * 0.09f, Color(0xFFFFE082))
        drawStar(w * 0.84f, h * 0.22f, w * 0.08f, Color(0xFFFFF59D))
        drawStar(w * 0.18f, h * 0.76f, w * 0.07f, Color(0xFF80DEEA))
        drawStar(w * 0.85f, h * 0.74f, w * 0.09f, Color(0xFFFFD54F))
    }
}

@Composable
private fun ChatDot(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(30.dp)) {
        val r = size.minDimension / 2f
        drawCircle(color = color, radius = r)
        val bubble = Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = size.width * 0.24f, top = size.height * 0.26f,
                    right = size.width * 0.76f, bottom = size.height * 0.62f,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.1f)
                )
            )
            moveTo(size.width * 0.38f, size.height * 0.62f)
            lineTo(size.width * 0.34f, size.height * 0.78f)
            lineTo(size.width * 0.52f, size.height * 0.62f)
            close()
        }
        drawPath(bubble, Color.White)
    }
}

internal fun formatDistance(m: Double): String = when {
    m >= 10_000 -> "${(m / 1000).toInt()}km"
    m >= 1_000 -> String.format(java.util.Locale.US, "%.1fkm", m / 1000)
    else -> "${(Math.round(m / 10.0) * 10).toInt()}m"
}

internal fun formatEta(sec: Int): String {
    val minutes = (sec + 30) / 60
    return if (minutes >= 60) "${minutes / 60}시간 ${minutes % 60}분" else "${minutes}분"
}

/** 회전 방향 화살표. 글자 없이 모양만으로 알려 준다. */
@Composable
fun TurnArrow(icon: TurnIcon, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier = modifier) { drawTurnArrow(icon, color) }
}

private fun DrawScope.drawTurnArrow(icon: TurnIcon, color: Color) {
    val w = size.width
    val h = size.height
    val stroke = Stroke(width = w * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun arrowTo(angleDeg: Float) {
        val path = Path()
        val sharp = kotlin.math.abs(angleDeg) > 100f
        val len = if (sharp) w * 0.44f else w * 0.36f
        val rad = Math.toRadians(angleDeg.toDouble())
        val startX = w * 0.5f - (kotlin.math.sin(rad) * len * 0.5f).toFloat()
        val startY = h * 0.90f
        val elbowY = if (sharp) h * 0.30f else h * 0.52f
        path.moveTo(startX, startY)
        path.lineTo(startX, elbowY)
        val endX = startX + (kotlin.math.sin(rad) * len).toFloat()
        val endY = elbowY - (kotlin.math.cos(rad) * len).toFloat()
        path.lineTo(endX, endY)
        drawPath(path, color, style = stroke)
        val head = if (sharp) w * 0.16f else w * 0.22f
        val back = rad + Math.PI
        val left = back - 0.7
        val right = back + 0.7
        val head1 = Offset(endX + (kotlin.math.sin(left) * head).toFloat(), endY - (kotlin.math.cos(left) * head).toFloat())
        val head2 = Offset(endX + (kotlin.math.sin(right) * head).toFloat(), endY - (kotlin.math.cos(right) * head).toFloat())
        val headPath = Path().apply {
            moveTo(head1.x, head1.y); lineTo(endX, endY); lineTo(head2.x, head2.y)
        }
        drawPath(headPath, color, style = stroke)
    }

    when (icon) {
        TurnIcon.STRAIGHT -> {
            val path = Path().apply { moveTo(w * 0.5f, h * 0.9f); lineTo(w * 0.5f, h * 0.14f) }
            drawPath(path, color, style = stroke)
            val head = Path().apply {
                moveTo(w * 0.28f, h * 0.36f); lineTo(w * 0.5f, h * 0.14f); lineTo(w * 0.72f, h * 0.36f)
            }
            drawPath(head, color, style = stroke)
        }
        TurnIcon.RIGHT -> arrowTo(90f)
        TurnIcon.LEFT -> arrowTo(-90f)
        TurnIcon.SLIGHT_RIGHT -> arrowTo(45f)
        TurnIcon.SLIGHT_LEFT -> arrowTo(-45f)
        TurnIcon.SHARP_RIGHT -> arrowTo(135f)
        TurnIcon.SHARP_LEFT -> arrowTo(-135f)
        TurnIcon.UTURN -> {
            val path = Path().apply {
                moveTo(w * 0.64f, h * 0.9f)
                lineTo(w * 0.64f, h * 0.36f)
                cubicTo(w * 0.64f, h * 0.08f, w * 0.30f, h * 0.08f, w * 0.30f, h * 0.36f)
                lineTo(w * 0.30f, h * 0.62f)
            }
            drawPath(path, color, style = stroke)
            val head = Path().apply {
                moveTo(w * 0.14f, h * 0.48f); lineTo(w * 0.30f, h * 0.66f); lineTo(w * 0.46f, h * 0.48f)
            }
            drawPath(head, color, style = stroke)
        }
        TurnIcon.ROUNDABOUT -> {
            drawCircle(color, radius = w * 0.2f, center = Offset(w * 0.5f, h * 0.42f), style = stroke)
            val path = Path().apply { moveTo(w * 0.5f, h * 0.92f); lineTo(w * 0.5f, h * 0.64f) }
            drawPath(path, color, style = stroke)
            val out = Path().apply { moveTo(w * 0.5f, h * 0.22f); lineTo(w * 0.5f, h * 0.06f) }
            drawPath(out, color, style = stroke)
        }
        TurnIcon.ARRIVE -> {
            val pole = Path().apply { moveTo(w * 0.32f, h * 0.92f); lineTo(w * 0.32f, h * 0.1f) }
            drawPath(pole, color, style = stroke)
            val flag = Path().apply {
                moveTo(w * 0.32f, h * 0.12f); lineTo(w * 0.8f, h * 0.28f); lineTo(w * 0.32f, h * 0.46f); close()
            }
            drawPath(flag, color)
        }
    }
}
