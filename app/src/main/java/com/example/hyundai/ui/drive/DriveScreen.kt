package com.example.hyundai.ui.drive

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.example.hyundai.nav.GeoPoint
import com.example.hyundai.nav.MapTheme
import com.example.hyundai.nav.NavUiState
import com.example.hyundai.nav.TurnIcon
import com.example.hyundai.ui.map.NavMap
import com.example.hyundai.ui.map.NavTheme
import com.example.hyundai.ui.pet.ZeroPet
import com.example.hyundai.viewmodel.AppUiState
import kotlinx.coroutines.delay

private val Panel = NavTheme.Panel
private val PanelEdge = NavTheme.PanelEdge

/**
 * 주행 중(D 기어) 화면.
 * 지도가 화면 전체이고, 글자는 거리/시간 숫자뿐이다. 쪽지·응원은 펫이 알려 준다.
 */
@Composable
fun DriveScreen(
    state: AppUiState,
    modifier: Modifier = Modifier,
    onOpenInbox: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    /** 다른 화면에 가려 안 보일 때는 false로 두어 펫을 그리지 않는다. */
    petAnimated: Boolean = true,
    /** 미리보기처럼 지도를 대신할 때만 넘긴다. */
    map: (@Composable (Modifier) -> Unit)? = null
) {
    val isDay = rememberIsDay(state.mapTheme, state.nav.carPosition)
    Box(modifier = modifier.fillMaxSize()) {
        if (map != null) {
            map(Modifier.fillMaxSize())
        } else {
            NavMap(nav = state.nav, isDay = isDay, modifier = Modifier.fillMaxSize())
        }

        TurnCard(
            nav = state.nav,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 22.dp)
        )

        EtaChip(
            nav = state.nav,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 24.dp, bottom = 24.dp)
        )

        PetBadge(
            state = state,
            animate = petAnimated,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp, top = 22.dp)
        )

        // 쪽지함과 설정은 차가 완전히 멈추었을 때(신호 대기 등)만 나타난다.
        val stopped = NoteAccess.canBrowse(state.telemetry.gear, state.telemetry.speedKmh)
        AnimatedVisibility(
            visible = stopped,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp, top = 22.dp + 132.dp + 12.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StoppedAction("쪽지함", onOpenInbox)
                StoppedAction("설정", onOpenSettings)
            }
        }
    }
}

@Composable
private fun StoppedAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .border(1.dp, PanelEdge, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * 지도가 낮인지 밤인지. 자동일 때는 차 위치의 해가 뜨었는지 1분마다 다시 확인해, 주행 중에 해가 지면 저절로 밤 지도로 바뀐다.
 */
@Composable
private fun rememberIsDay(theme: MapTheme, position: GeoPoint): Boolean {
    // 시각만 1분마다 갱신하고, 판정은 테마나 위치가 바뀔 때마다 바로 다시 한다(계산이 가벼운다).
    val nowMs by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            delay(60_000L)
            value = System.currentTimeMillis()
        }
    }
    return remember(theme, position, nowMs) { theme.isDay(position, nowMs) }
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

@Composable
private fun EtaChip(nav: NavUiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .border(1.dp, PanelEdge, RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
 * 오른쪽 위 펫. 평소엔 작게 돌고, 새 쪽지/응원이 오면 통통 튀며 말풍선 아이콘이 뜬다.
 */
@Composable
private fun PetBadge(
    state: AppUiState,
    animate: Boolean,
    modifier: Modifier = Modifier
) {
    val pet = state.activePet
    val accent = NavTheme.petAccent(pet.id)
    var excited by remember { mutableStateOf(false) }
    val hasIncoming = state.activeIncomingNote != null || state.isTtsSpeaking

    // 화면이 처음 그려질 때는 튀지 않고, 이후 새 알림이 바뀔 때만 튀게 한다.
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

    // 달리는 중에는 화면을 눌러 조작하지 않는다. 펫은 보여 주기만 하고, 쪽지 승인은 음성으로 한다.
    Box(modifier = modifier.size(132.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(26.dp))
                .background(Panel)
                .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(26.dp))
        )
        ZeroPet(
            level = pet.level,
            accent = accent,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            // 주행 중 펫을 계속 움직이면 화면 전체가 매 프레임 다시 합성되어 지도가 끈긴다(측정: 8fps→25fps).
            // 그래서 쉼 때는 가만히 두고, 쪽지를 읽을 때만 움직인다.
            animate = animate && (excited || state.isTtsSpeaking || state.heldNote != null),
            excited = excited || state.isTtsSpeaking,
            holdingNote = state.heldNote != null
        )
        if (hasIncoming) {
            ChatDot(
                color = accent,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            )
        }
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

    // 방향(도): 0=위, 양수=오른쪽으로 꺾임
    fun arrowTo(angleDeg: Float) {
        val path = Path()
        val sharp = kotlin.math.abs(angleDeg) > 100f
        val len = if (sharp) w * 0.44f else w * 0.36f
        val rad = Math.toRadians(angleDeg.toDouble())
        // 화살표 전체가 카드 가운데에 오도록 줄기를 반대 쪽으로 밀어 둔다.
        val startX = w * 0.5f - (kotlin.math.sin(rad) * len * 0.5f).toFloat()
        val startY = h * 0.90f
        val elbowY = if (sharp) h * 0.30f else h * 0.52f
        path.moveTo(startX, startY)
        path.lineTo(startX, elbowY)
        val endX = startX + (kotlin.math.sin(rad) * len).toFloat()
        val endY = elbowY - (kotlin.math.cos(rad) * len).toFloat()
        path.lineTo(endX, endY)
        drawPath(path, color, style = stroke)
        // 화살촉
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
            // 깃발
            val pole = Path().apply { moveTo(w * 0.32f, h * 0.92f); lineTo(w * 0.32f, h * 0.1f) }
            drawPath(pole, color, style = stroke)
            val flag = Path().apply {
                moveTo(w * 0.32f, h * 0.12f); lineTo(w * 0.8f, h * 0.28f); lineTo(w * 0.32f, h * 0.46f); close()
            }
            drawPath(flag, color)
        }
    }
}
