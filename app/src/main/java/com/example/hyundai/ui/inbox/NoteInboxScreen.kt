package com.example.hyundai.ui.inbox

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hyundai.domain.NoteInbox
import com.example.hyundai.model.VoiceNote
import com.example.hyundai.ui.common.ScreenFrame
import com.example.hyundai.ui.pet.PetModel
import com.example.hyundai.ui.theme.TodakColors
import com.example.hyundai.viewmodel.AppUiState

private enum class InboxTab(val label: String) { RECEIVED("받은 쪽지"), MINE("내가 남긴 쪽지") }

/**
 * 쪽지함. 펫이 길에서 주워 온 쪽지(받은 쪽지)와 내가 남긴 쪽지를 모아 본다.
 * 정차 중에만 열린다. 열려 있는 동안 차가 움직이기 시작하면 상위에서 닫는다.
 */
@Composable
fun NoteInboxScreen(
    state: AppUiState,
    onBack: () -> Unit,
    onListen: (VoiceNote) -> Unit,
    onStop: () -> Unit,
    onLike: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf(InboxTab.RECEIVED) }
    val received = NoteInbox.received(state.notes)
    val mine = NoteInbox.mine(state.notes)
    val shown = if (tab == InboxTab.RECEIVED) received else mine

    ScreenFrame(title = "쪽지함", onBack = onBack, modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PetCorner(
                state = state,
                modifier = Modifier
                    .weight(0.28f)
                    .fillMaxHeight()
            )

            Column(
                modifier = Modifier
                    .weight(0.72f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TabChip("${InboxTab.RECEIVED.label} ${received.size}", tab == InboxTab.RECEIVED) {
                        tab = InboxTab.RECEIVED
                    }
                    TabChip("${InboxTab.MINE.label} ${mine.size}", tab == InboxTab.MINE) {
                        tab = InboxTab.MINE
                    }
                }

                if (shown.isEmpty()) {
                    EmptyInbox(isReceived = tab == InboxTab.RECEIVED, modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(shown, key = { it.id }) { note ->
                            val playing = state.heldNote?.id == note.id && state.isTtsSpeaking
                            InboxCard(
                                note = note,
                                playing = playing,
                                onListenToggle = { if (playing) onStop() else onListen(note) },
                                onLike = { onLike(note.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 왼쪽의 펫. 쪽지를 주워 읽는 동안에는 쪽지를 들고 있다. */
@Composable
private fun PetCorner(state: AppUiState, modifier: Modifier = Modifier) {
    val pet = state.activePet
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(TodakColors.Panel)
            .border(1.dp, TodakColors.Edge, RoundedCornerShape(22.dp))
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.radialGradient(listOf(TodakColors.Route.copy(alpha = 0.28f), TodakColors.PanelDeep))
                )
        ) {
            PetModel(
                accent = TodakColors.Route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                level = pet.level,
                spin = state.heldNote != null,
                holdingNote = state.heldNote != null,
                excited = state.isTtsSpeaking
            )
        }
        Text(
            text = if (state.heldNote != null) "쪽지를 읽고 있어요" else pet.name,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TabChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) TodakColors.RouteDeep else TodakColors.PanelDeep)
            .border(
                1.dp,
                if (selected) TodakColors.Route else TodakColors.Edge,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp)
    ) {
        Text(
            text,
            color = if (selected) Color.White else TodakColors.TextDim,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EmptyInbox(isReceived: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(TodakColors.Panel.copy(alpha = 0.5f))
            .border(1.dp, TodakColors.Edge, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (isReceived) "받은 쪽지가 없어요" else "남긴 쪽지가 없어요",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (isReceived) "달리다 보면 제로가 길에서 쪽지를 주워 와요" else "주차하면 쪽지를 남길 수 있어요",
                color = TodakColors.TextDim,
                fontSize = 17.sp
            )
        }
    }
}

@Composable
private fun InboxCard(
    note: VoiceNote,
    playing: Boolean,
    onListenToggle: () -> Unit,
    onLike: () -> Unit
) {
    val category = Color(note.category.badgeColorHex)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TodakColors.PanelDeep)
            .border(
                if (playing) 1.5.dp else 1.dp,
                if (playing) TodakColors.Route else TodakColors.Edge,
                RoundedCornerShape(18.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(category)
                )
                // 작성자는 이름 대신 펫으로만 보인다.
                Text(
                    "${note.authorPetName} Lv.${note.authorPetLevel}",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(note.category.label, color = TodakColors.TextDim, fontSize = 16.sp)
            }
            Text(note.createdAtLabel, color = TodakColors.TextDim, fontSize = 16.sp)
        }

        Text(
            "“${note.displayText}”",
            color = Color.White,
            fontSize = 19.sp,
            lineHeight = 27.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    note.roadSegmentName,
                    color = TodakColors.TextSecondary,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(note.remainingLifeLabel, color = TodakColors.TextDim, fontSize = 15.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(
                    text = if (playing) "멈추기" else "다시 듣기",
                    active = playing,
                    onClick = onListenToggle
                )
                if (note.isMine) {
                    InfoPill("들은 사람 ${note.listenerCount}")
                    InfoPill("좋아요 ${note.todakCount}")
                } else {
                    ActionButton(
                        text = "좋아요 ${note.todakCount}",
                        active = note.isReactedByMe,
                        onClick = onLike
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) TodakColors.Route.copy(alpha = 0.3f) else TodakColors.Edge)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp)
    ) {
        Text(text, color = TodakColors.Route, fontSize = 17.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, TodakColors.Edge, RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 11.dp)
    ) {
        Text(text, color = TodakColors.TextDim, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
