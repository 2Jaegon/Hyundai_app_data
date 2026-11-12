package com.example.hyundai.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.hyundai.nav.GeoPoint
import com.example.hyundai.nav.NavUiState
import com.example.hyundai.nav.TurnIcon
import com.example.hyundai.ui.drive.DriveScreen
import com.example.hyundai.ui.drive.TurnArrow
import com.example.hyundai.ui.parked.ParkedScreen
import com.example.hyundai.ui.theme.TodakColors
import com.example.hyundai.viewmodel.AppUiState

@Preview(widthDp = 760, heightDp = 160, backgroundColor = 0xFF00234C, showBackground = true)
@Composable
fun TurnArrowsPreview() {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        listOf(
            TurnIcon.STRAIGHT, TurnIcon.RIGHT, TurnIcon.LEFT, TurnIcon.SLIGHT_RIGHT,
            TurnIcon.SHARP_LEFT, TurnIcon.UTURN, TurnIcon.ROUNDABOUT, TurnIcon.ARRIVE
        ).forEach { TurnArrow(it, Modifier.size(72.dp)) }
    }
}

@Preview(widthDp = 1280, heightDp = 560, showBackground = true)
@Composable
fun DriveScreenPreview() {
    val state = AppUiState(
        nav = NavUiState(
            hasRoute = true,
            carPosition = GeoPoint(37.5445, 127.0440),
            nextTurn = TurnIcon.RIGHT,
            distToTurnM = 300.0,
            remainingM = 8_400.0,
            etaSec = 12 * 60
        ),
        activeIncomingNote = null
    )
    DriveScreen(
        state = state,
        map = { m ->
            Box(
                modifier = m.background(
                    Brush.verticalGradient(listOf(Color(0xFF00305F), Color(0xFF001F42), Color(0xFF001A3A)))
                )
            )
        }
    )
}

@Preview(widthDp = 1280, heightDp = 560, showBackground = true)
@Composable
fun ParkedScreenPreview() {
    Box(modifier = Modifier.fillMaxSize().background(TodakColors.Background)) {
        ParkedScreen(
            state = AppUiState(),
            onOpenRewardShop = {},
            onOpenParkingReport = {},
            onOpenInbox = {},
            onOpenSettings = {},
            onOpenVoiceComposer = {},
            onListenNote = {},
            onStopTts = {}
        )
    }
}
