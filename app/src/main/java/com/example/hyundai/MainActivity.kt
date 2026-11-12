package com.example.hyundai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hyundai.domain.NoteAccess
import com.example.hyundai.model.AppScreen
import com.example.hyundai.model.GearState
import com.example.hyundai.model.TurnSignalState
import com.example.hyundai.ui.HyundaiRewardShopModal
import com.example.hyundai.ui.ParkingReportAndTireTrackModal
import com.example.hyundai.ui.VoiceNoteComposeModal
import com.example.hyundai.ui.debug.SimulatorDrawer
import com.example.hyundai.ui.drive.DriveScreen
import com.example.hyundai.ui.inbox.NoteInboxScreen
import com.example.hyundai.ui.parked.ParkedScreen
import com.example.hyundai.ui.settings.SettingsScreen
import com.example.hyundai.ui.theme.TodakColors
import com.example.hyundai.viewmodel.AppUiState
import com.example.hyundai.viewmodel.MainViewModel

private const val CAR_FONT_SCALE = 1.2f

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.onRuntimePermissionsResult() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestRuntimePermissions()
        // 차량 화면은 주행 중 꺼지면 안 된다.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            // 차량 화면(2560px, 160dpi)에서는 일반 글자 크기가 작게 보여서 글자만 조금 키운다.
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, base.fontScale * CAR_FONT_SCALE)
            ) {
                MaterialTheme(
                    colorScheme = darkColorScheme(
                        primary = TodakColors.Route,
                        secondary = TodakColors.Good,
                        background = TodakColors.Background,
                        surface = TodakColors.Panel
                    )
                ) {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    TodakGilDashboard(
                        state = state,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    /**
     * Pleos FusedLocation(dangerous), 위치, 마이크는 실행 중 승인을 받아야 한다.
     * 이미 승인된 것은 다시 묻지 않는다.
     */
    private fun requestRuntimePermissions() {
        val wanted = arrayOf(
            "pleos.car.permission.FUSED_LOCATION",
            Manifest.permission.ACCESS_FINE_LOCATION,
            // Android 12 이상은 FINE을 요청할 때 COARSE를 같이 요청해야 한다.
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.RECORD_AUDIO
        )
        val missing = wanted.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }
}

/**
 * D 기어(주행 중)에는 지도와 경로만, 그 밖(P/N/R)에는 펫·평가·쪽지 대시보드를 보여 준다.
 */
@Composable
fun TodakGilDashboard(
    state: AppUiState,
    viewModel: MainViewModel
) {
    val isDriving = state.telemetry.gear == GearState.D
    // 쪽지함·설정은 차가 멈췄을 때만 보인다. 상태가 바뀔 직후 한 프레임도 열려 있지 않도록 여기서도 막는다.
    val canBrowse = NoteAccess.canBrowse(state.telemetry.gear, state.telemetry.speedKmh)
    // 정차 대시보드는 기어가 D가 아니고 실제로 멈췄을 때만 덮는다.
    val showParked = !isDriving && canBrowse
    // 다른 화면이 덮고 있으면 아래 지도 화면의 펫은 움직이지 않게 해 그리기 비용을 아낀다.
    val driveCovered = showParked || (canBrowse && state.screen != AppScreen.HOME)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = TodakColors.Background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 지도는 항상 만들어 두고, 정차 대시보드가 그 위를 덮는다.
            // 기어를 바꿀 때마다 지도를 부수고 다시 만들면 흰 화면과 타일 재요청이 생긴다.
            DriveScreen(
                state = state,
                onOpenInbox = { viewModel.openScreen(AppScreen.INBOX) },
                onOpenSettings = { viewModel.openScreen(AppScreen.SETTINGS) },
                petAnimated = !driveCovered
            )
            if (showParked) {
                ParkedScreen(
                    state = state,
                    onOpenRewardShop = { viewModel.setRewardShopModalVisible(true) },
                    onOpenParkingReport = { viewModel.setParkingReportModalVisible(true) },
                    onOpenInbox = { viewModel.openScreen(AppScreen.INBOX) },
                    onOpenSettings = { viewModel.openScreen(AppScreen.SETTINGS) },
                    onOpenVoiceComposer = viewModel::openVoiceNoteComposer,
                    onListenNote = viewModel::listenToNoteViaTts,
                    onStopTts = viewModel::stopTtsPlayback,
                    // 대시보드가 덮고 있는 동안 아래 지도 화면의 버튼이 눌리지 않도록 터치를 막는다.
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
                )
            }

            if (canBrowse && state.screen == AppScreen.INBOX) {
                NoteInboxScreen(
                    state = state,
                    onBack = viewModel::closeScreen,
                    onListen = viewModel::listenToNoteViaTts,
                    onStop = viewModel::stopTtsPlayback,
                    onLike = viewModel::sendTodakReaction
                )
            }
            if (canBrowse && state.screen == AppScreen.SETTINGS) {
                SettingsScreen(
                    state = state,
                    onBack = viewModel::closeScreen,
                    onToggleAutoPlay = viewModel::toggleAutoPlayTts,
                    onToggleCommuteOnly = viewModel::toggleCommuteBandFilter,
                    onSelectMapTheme = viewModel::setMapTheme
                )
            }

            SimulatorDrawer(
                state = state,
                onSafeDriveStep = { viewModel.simulateSafeDrivingStep(0.5f) },
                onMannerTurnSuccess = { viewModel.simulateMannerLaneChange(TurnSignalState.RIGHT) },
                onBlinkerAbuseTest = viewModel::simulateBlinkerAbuseAttempt,
                onHarshBrakeTest = viewModel::simulateHarshBrakingEvent,
                onToggleGearPark = {
                    val nextGear = if (state.telemetry.gear == GearState.P) GearState.D else GearState.P
                    viewModel.shiftGear(nextGear)
                },
                onDemoNote = { text ->
                    viewModel.openVoiceNoteComposer(force = true)
                    viewModel.selectVoicePresetForAiTest(text)
                },
                onCycleMapTheme = viewModel::cycleMapTheme,
                onToggleHeadlamp = viewModel::toggleHeadlampSimulation,
                onStopAtLight = viewModel::simulateStopAtLight,
                onVoiceApprove = viewModel::approveIncomingNoteByVoice,
                onRestartSimulation = viewModel::restartSimulation,
                modifier = Modifier.align(Alignment.BottomCenter)
            )

            if (state.showVoiceComposeDialog) {
                VoiceNoteComposeModal(
                    state = state,
                    onStartStt = viewModel::start10SecSttRecording,
                    onPublish = viewModel::publishVoiceNote,
                    onDismiss = viewModel::closeVoiceNoteComposer
                )
            }

            if (state.showParkingReportModal) {
                ParkingReportAndTireTrackModal(
                    state = state,
                    onDismiss = { viewModel.setParkingReportModalVisible(false) }
                )
            }

            if (state.showRewardShopModal) {
                HyundaiRewardShopModal(
                    state = state,
                    onRedeem = viewModel::redeemRewardItem,
                    onDismiss = { viewModel.setRewardShopModalVisible(false) }
                )
            }
        }
    }
}
