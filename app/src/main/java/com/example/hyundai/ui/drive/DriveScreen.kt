package com.example.hyundai.ui.drive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
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
import com.example.hyundai.ui.map.LocalNavSkin
import com.example.hyundai.ui.map.NavMap
import com.example.hyundai.ui.map.NavTheme
import com.example.hyundai.ui.pet.NoteLetter
import com.example.hyundai.ui.pet.PetModel
import com.example.hyundai.ui.pet.PetPose
import com.example.hyundai.ui.theme.TodakColors
import com.example.hyundai.viewmodel.AppUiState
import kotlinx.coroutines.delay

private val CardShape = RoundedCornerShape(26.dp)
private val ChipShape = RoundedCornerShape(22.dp)

/**
 * 주행 중(D 기어) 화면.
 * 지도 위에는 회전 안내, 속도, 도착 정보, 오른쪽 위의 제로만 둔다. 카드는 낮에는 크림, 밤에는 남색이다.
 * 쪽지가 오면 제로가 쪽지를 들고 말풍선으로 알려 주고, 읽는 동안만 살짝 움직인다(지도 프레임을 지키기 위해).
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

    CompositionLocalProvider(LocalNavSkin provides NavTheme.skin(isDay && !highway)) {
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
                    .padding(start = 22.dp, top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TurnCard(nav = state.nav)
                VehicleChips(state = state)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 22.dp, bottom = 22.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EtaChip(nav = state.nav, destination = state.destinationName)
                AddressChip(address = state.telemetry.roadName)
            }

            // 오른쪽 위: 제로와 말풍선
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 22.dp, top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                PetSpeechBubble(state = state, modifier = Modifier.padding(top = 26.dp))
                DrivePet(state = state, animate = petAnimated)
            }

            // 정차 중일 때만 터치 메뉴 버튼을 보여 주고, 주행 중에는 숨겨 화면을 간결하게 유지한다.
            if (stopped) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 22.dp, top = 20.dp + PET_AREA + 14.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StoppedAction("목적지", onClick = onOpenDestination)
                    StoppedAction(if (state.map3d) "2D 지도" else "3D 지도", onClick = onToggleMap3d)
                    StoppedAction("쪽지함", onClick = onOpenInbox)
                    StoppedAction("설정", onClick = onOpenSettings)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 현재 시뮬레이션/주행 이벤트 안내. 제로의 말풍선이 떠 있으면 겹치지 않게 말풍선만 보인다.
                if (state.toastBanner.isNotBlank() && !bubbleVisible(state)) {
                    Card(modifier = Modifier.widthIn(max = 560.dp)) {
                        Text(
                            text = state.toastBanner,
                            color = LocalNavSkin.current.text,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                VehicleAlert(state = state)
            }

            // 기어 전환(P → D) 안내
            state.gearTransitionBanner?.let { banner ->
                val skin = LocalNavSkin.current
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .shadow(10.dp, CardShape, ambientColor = skin.shadow, spotColor = skin.shadow)
                        .clip(CardShape)
                        .background(skin.card)
                        .padding(horizontal = 28.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(TodakColors.Sky)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("P → D", color = TodakColors.OnColor, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text(banner, color = skin.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

/** 지도 위 카드 공통 모양: 둥근 모서리, 옅은 그림자, 낮/밤 색. */
@Composable
private fun Card(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = ChipShape,
    border: Color? = null,
    content: @Composable () -> Unit
) {
    val skin = LocalNavSkin.current
    Box(
        modifier = modifier
            .shadow(6.dp, shape, ambientColor = skin.shadow, spotColor = skin.shadow)
            .clip(shape)
            .background(skin.card)
            .then(if (border != null) Modifier.border(2.dp, border, shape) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) { content() }
}

@Composable
private fun StoppedAction(text: String, onClick: () -> Unit) {
    val skin = LocalNavSkin.current
    Box(
        modifier = Modifier
            .shadow(6.dp, ChipShape, ambientColor = skin.shadow, spotColor = skin.shadow)
            .clip(ChipShape)
            .background(skin.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 14.dp)
    ) {
        Text(text = text, color = skin.sky, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
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

/** 다음 회전. 하늘색 칸 안의 흰 화살표와 남은 거리. */
@Composable
private fun TurnCard(nav: NavUiState, modifier: Modifier = Modifier) {
    val skin = LocalNavSkin.current
    Row(
        modifier = modifier
            .shadow(8.dp, CardShape, ambientColor = skin.shadow, spotColor = skin.shadow)
            .clip(CardShape)
            .background(skin.card)
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 26.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(TodakColors.Sky),
            contentAlignment = Alignment.Center
        ) {
            TurnArrow(
                icon = if (nav.hasRoute) nav.nextTurn else TurnIcon.STRAIGHT,
                modifier = Modifier.size(66.dp)
            )
        }
        Text(
            text = if (nav.hasRoute) formatDistance(nav.distToTurnM) else "···",
            color = skin.text,
            fontSize = 38.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/** 기어, 속도, 방향지시등, 배터리·주행가능거리 표시. */
@Composable
private fun VehicleChips(state: AppUiState, modifier: Modifier = Modifier) {
    val skin = LocalNavSkin.current
    val console = state.console
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(skin.skySoft)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(state.telemetry.gear.label, color = skin.sky, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text("${state.telemetry.speedKmh.toInt()}", color = skin.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text("km/h", color = skin.textSub, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                val cruise = console?.cruiseTargetKph
                if (console?.cruiseOn == true) {
                    Text(
                        text = if (cruise != null) "크루즈 $cruise" else "크루즈",
                        color = skin.sky,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // 방향지시등 점등 또는 매너 방향전환 인증 시 표시
        val signal = state.telemetry.turnSignal
        val phase = state.evaluation.mannerPhase
        if (signal != TurnSignalState.OFF || phase == MannerTurnPhase.VERIFIED) {
            val isVerified = phase == MannerTurnPhase.VERIFIED
            val tone = if (isVerified) skin.sky else skin.apricot
            Card(border = tone) {
                Text(
                    text = if (isVerified) {
                        "매너 차선변경 완료 +15 EXP"
                    } else {
                        "${signal.label} ${String.format(java.util.Locale.US, "%.1f초", state.evaluation.signalHoldSeconds.coerceAtLeast(0.8f))}"
                    },
                    color = tone,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        val battery = console?.batteryPct
        val range = console?.rangeKm
        if (battery != null || range != null) {
            Card {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (battery != null) {
                        Text("$battery%", color = skin.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    if (range != null) {
                        Text("${range}km", color = skin.textSub, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    if (console.charging) {
                        Text("충전 중", color = skin.sky, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
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
    val skin = LocalNavSkin.current
    Card(modifier = modifier, border = skin.apricot) {
        Text(lines.joinToString("  |  "), color = skin.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** 기본 내비의 현재 주소 표시와 같은 자리. */
@Composable
private fun AddressChip(address: String, modifier: Modifier = Modifier) {
    if (address.isBlank()) return
    val skin = LocalNavSkin.current
    Card(modifier = modifier) {
        Text(address, color = skin.textSub, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun EtaChip(nav: NavUiState, destination: String, modifier: Modifier = Modifier) {
    val skin = LocalNavSkin.current
    Card(modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (destination.isNotBlank()) {
                Text(destination, color = skin.text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
            Text(
                text = if (nav.hasRoute) formatEta(nav.etaSec) else "--",
                color = skin.sky,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(NavTheme.Dot)
            )
            Text(
                text = if (nav.hasRoute) formatDistance(nav.remainingM) else "--",
                color = skin.textSub,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** 펫 자리(방석 + 이름표)의 높이. 정차 메뉴를 이 아래에 둔다. */
private val PET_AREA: Dp = 196.dp
private val PET_SIZE: Dp = 164.dp

/**
 * 오른쪽 위의 제로. 상자 없이 둥근 방석 위에 앉아 있다.
 * 쪽지가 오면 한 번 폴짝 뛰며 쪽지를 들고, 읽는 동안 쪽지를 살랑 흔든다. 다른 때는 움직이지 않는다.
 */
@Composable
private fun DrivePet(state: AppUiState, animate: Boolean, modifier: Modifier = Modifier) {
    val skin = LocalNavSkin.current
    val incoming = state.activeIncomingNote
    val reading = state.heldNote != null && state.isTtsSpeaking
    val holdingLetter = (incoming != null && !incoming.isListenedByMe) || reading
    val sparkle = state.mannerSparkleActive || state.evaluation.mannerPhase == MannerTurnPhase.VERIFIED
    val hearts = state.heartReactionBanner != null
    val pose = PetPose.forDrive(state, holdingLetter)

    // 쪽지가 새로 올 때마다 한 번 폴짝. 값은 그리기 단계에서만 읽어 지도 위에서 화면을 다시 구성하지 않는다.
    val hop = remember { Animatable(0f) }
    val noteKey = if (holdingLetter) (state.heldNote?.id ?: incoming?.id) else null
    LaunchedEffect(noteKey, animate) {
        if (noteKey == null || !animate) return@LaunchedEffect
        hop.snapTo(0f)
        hop.animateTo(1f, tween(170, easing = FastOutSlowInEasing))
        hop.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
    }

    Column(modifier = modifier.size(width = PET_SIZE, height = PET_AREA), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(PET_SIZE)) {
            // 방석
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(PET_SIZE - 10.dp)
                    .shadow(8.dp, CircleShape, ambientColor = skin.shadow, spotColor = skin.shadow)
                    .clip(CircleShape)
                    .background(if (holdingLetter || hearts) skin.apricotSoft else skin.card)
                    .border(3.dp, if (holdingLetter || hearts) skin.apricot else skin.cardEdge, CircleShape)
            )
            PetModel(
                accent = TodakColors.Apricot,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 22.dp)
                    .graphicsLayer {
                        translationY = -18.dp.toPx() * hop.value
                    },
                level = state.activePet.level,
                spin = animate && (hearts || sparkle),
                excited = animate && hearts,
                pose = pose
            )
            if (sparkle) SparkleOverlay(modifier = Modifier.fillMaxSize())
            LetterPop(
                // 하트를 보낼 때는 쪽지를 내려놓고 기뻐한다.
                visible = holdingLetter && !hearts,
                reading = reading,
                animate = animate,
                hop = { hop.value },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 4.dp, y = (-6).dp)
            )
        }
        // 이름표
        Box(
            modifier = Modifier
                .offset(y = (-8).dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (sparkle) TodakColors.Sky else skin.card)
                .border(1.5.dp, if (sparkle) TodakColors.Sky else skin.cardEdge, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 3.dp)
        ) {
            Text(
                text = "${state.activePet.name} Lv.${state.activePet.level}",
                color = if (sparkle) TodakColors.OnColor else skin.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
    }
}

/** 쪽지는 톡 튀어나오듯 커지며 나타나고, 다 읽으면 작아지며 사라진다. [hop]은 그리기 단계에서만 읽는다. */
@Composable
private fun LetterPop(
    visible: Boolean,
    reading: Boolean,
    animate: Boolean,
    hop: () -> Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), initialScale = 0.2f) +
            fadeIn(tween(140)),
        exit = scaleOut(tween(200), targetScale = 0.4f) + fadeOut(tween(200))
    ) {
        HeldLetter(
            reading = reading,
            animate = animate,
            modifier = Modifier
                .size(width = 66.dp, height = 54.dp)
                .graphicsLayer { translationY = -18.dp.toPx() * hop() }
        )
    }
}

/** 제로가 든 쪽지. 읽는 동안에는 편지지가 나와 있고 좌우로 살랑인다. */
@Composable
private fun HeldLetter(reading: Boolean, animate: Boolean, modifier: Modifier = Modifier) {
    if (reading && animate) {
        val sway = rememberInfiniteTransition(label = "letter_sway")
        val angle by sway.animateFloat(
            initialValue = -7f,
            targetValue = 5f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "letter_angle"
        )
        NoteLetter(
            open = true,
            modifier = modifier.graphicsLayer {
                transformOrigin = TransformOrigin(0.3f, 1f)
                rotationZ = angle
            }
        )
    } else {
        NoteLetter(open = reading, modifier = modifier.graphicsLayer { rotationZ = -8f })
    }
}

/**
 * 제로의 말풍선. 쪽지 수신 안내, 운전자 음성 응답("응", "좋아요"), 읽고 있는 쪽지, 하트 전달을 보여 준다.
 * 꼬리가 오른쪽의 제로를 가리킨다.
 */
@Composable
private fun PetSpeechBubble(state: AppUiState, modifier: Modifier = Modifier) {
    val skin = LocalNavSkin.current
    val prompt = state.voiceHudPrompt
    val utterance = state.voiceHudUserUtterance
    val held = state.heldNote
    val heartBanner = state.heartReactionBanner?.removePrefix("♥")?.trim()
    val reading = held != null && state.isTtsSpeaking

    AnimatedVisibility(
        visible = bubbleVisible(state),
        modifier = modifier,
        enter = fadeIn(tween(200)) + slideInHorizontally(tween(260)) { it / 6 } +
            scaleIn(tween(260), initialScale = 0.92f, transformOrigin = TransformOrigin(1f, 0.2f)),
        exit = fadeOut(tween(180)) + slideOutHorizontally(tween(200)) { it / 8 } +
            scaleOut(tween(200), targetScale = 0.95f, transformOrigin = TransformOrigin(1f, 0.2f))
    ) {
        val bubbleShape = remember { SpeechBubbleShape(corner = 26.dp, tail = BUBBLE_TAIL, tailCenterY = 42.dp) }
        Column(
            modifier = Modifier
                .widthIn(min = 260.dp, max = 520.dp)
                .shadow(8.dp, bubbleShape, ambientColor = skin.shadow, spotColor = skin.shadow)
                .clip(bubbleShape)
                .background(skin.card)
                .padding(start = 22.dp, top = 16.dp, bottom = 16.dp, end = 22.dp + BUBBLE_TAIL),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                heartBanner != null -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        HeartIcon(modifier = Modifier.size(28.dp))
                        Text(heartBanner, color = skin.text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                reading -> {
                    Tag("쪽지 읽는 중", skin.apricotSoft, skin.apricot)
                    Text(
                        text = "${NoteInbox.introLine(held!!.authorPetName)} ${held.displayText}",
                        color = skin.text,
                        fontSize = 20.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                else -> {
                    if (prompt != null) {
                        Text(prompt, color = skin.text, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                    }
                    if (utterance != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Tag("내 목소리", skin.skySoft, skin.sky)
                            Text("“$utterance”", color = skin.sky, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }
    }
}

private val BUBBLE_TAIL: Dp = 16.dp

private fun bubbleVisible(state: AppUiState): Boolean =
    state.voiceHudPrompt != null || state.voiceHudUserUtterance != null ||
        (state.heldNote != null && state.isTtsSpeaking) || state.heartReactionBanner != null

/** 둥근 말풍선. 오른쪽 위에서 [tail]만큼 꼬리가 나와 제로를 가리킨다. 꼬리 폭만큼 오른쪽 안쪽 여백을 더 준다. */
private class SpeechBubbleShape(
    private val corner: Dp,
    private val tail: Dp,
    private val tailCenterY: Dp
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tailPx = with(density) { tail.toPx() }
        val bodyRight = size.width - tailPx
        val r = minOf(with(density) { corner.toPx() }, size.height / 2f)
        val ty = minOf(with(density) { tailCenterY.toPx() }, size.height - r)
        val half = tailPx * 0.7f
        val path = Path().apply {
            addRoundRect(RoundRect(0f, 0f, bodyRight, size.height, CornerRadius(r, r)))
            moveTo(bodyRight - 1f, ty - half)
            quadraticTo(bodyRight + tailPx * 0.35f, ty - half * 0.2f, size.width, ty - half * 0.1f)
            quadraticTo(bodyRight + tailPx * 0.25f, ty + half * 0.6f, bodyRight - 1f, ty + half)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
private fun Tag(text: String, background: Color, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
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
        drawPath(path, TodakColors.Apricot)
    }
}

/** 매너운전 기록 시 제로 주변에 반짝이는 4각 별. */
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
        drawStar(w * 0.14f, h * 0.18f, w * 0.08f, TodakColors.Apricot)
        drawStar(w * 0.86f, h * 0.20f, w * 0.07f, TodakColors.Sky)
        drawStar(w * 0.16f, h * 0.78f, w * 0.06f, TodakColors.Sky)
        drawStar(w * 0.86f, h * 0.76f, w * 0.08f, TodakColors.Apricot)
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
