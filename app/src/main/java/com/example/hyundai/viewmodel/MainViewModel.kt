package com.example.hyundai.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hyundai.data.SettingsStore
import com.example.hyundai.domain.AiNoteModerator
import com.example.hyundai.domain.MannerEvaluationEngine
import com.example.hyundai.domain.NoteAccess
import com.example.hyundai.domain.NoteInbox
import com.example.hyundai.model.AppScreen
import com.example.hyundai.model.DrivingEvaluationState
import com.example.hyundai.model.GearState
import com.example.hyundai.model.ModerationStatus
import com.example.hyundai.model.NoteCategory
import com.example.hyundai.model.ParkingReportSummary
import com.example.hyundai.model.PetCharacter
import com.example.hyundai.model.RewardShopItem
import com.example.hyundai.model.SdkConnectionStatus
import com.example.hyundai.model.TireTrackNode
import com.example.hyundai.model.TurnSignalState
import com.example.hyundai.model.VehicleTelemetry
import com.example.hyundai.model.VoiceNote
import com.example.hyundai.nav.GeoMath
import com.example.hyundai.nav.GeoPoint
import com.example.hyundai.nav.MapTheme
import com.example.hyundai.nav.NavSnapshot
import com.example.hyundai.nav.NavUiState
import com.example.hyundai.nav.RouteData
import com.example.hyundai.nav.RouteRepository
import com.example.hyundai.nav.TurnIcon
import com.example.hyundai.sdk.PleosSdkManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

data class AppUiState(
    val sdkStatus: SdkConnectionStatus = SdkConnectionStatus(),
    val telemetry: VehicleTelemetry = VehicleTelemetry(),
    val nav: NavUiState = NavUiState(),
    val mapTheme: MapTheme = MapTheme.AUTO,
    val evaluation: DrivingEvaluationState = DrivingEvaluationState(),
    val totalExp: Int = 340,
    val warmthPoints: Int = 1850,
    val remainingNoteTickets: Int = 3,
    val maxDailyBaseTickets: Int = 3,
    val bonusTicketsEarnedToday: Int = 1,
    val pet: PetCharacter = initialPet(),
    val notes: List<VoiceNote> = initialVoiceNotes(),
    /** 정차 중에 열린 화면(쪽지함, 설정). 달리기 시작하면 자동으로 HOME이 된다. */
    val screen: AppScreen = AppScreen.HOME,
    /** 펫이 길에서 주워 들고 있는 쪽지. 읽는 동안과 읽은 직후에만 있다. */
    val heldNote: VoiceNote? = null,
    val showOnlyCommuteBand: Boolean = false,
    val showFilteredOppositeLaneNotes: Boolean = true,
    val autoPlayTts: Boolean = false,
    val activeIncomingNote: VoiceNote? = null,
    val isTtsSpeaking: Boolean = false,
    val ttsSpeakingText: String = "",
    val isRecordingStt: Boolean = false,
    val recordingSecondsLeft: Int = 10,
    val draftVoiceText: String = "",
    val lastModerationResult: AiNoteModerator.ModerationResult? = null,
    val showVoiceComposeDialog: Boolean = false,
    val showParkingReportModal: Boolean = false,
    val showRewardShopModal: Boolean = false,
    val showSdkArchitectureModal: Boolean = false,
    val parkingReport: ParkingReportSummary = ParkingReportSummary(),
    val tireTracks: List<TireTrackNode> = initialTireTracks(),
    val rewardItems: List<RewardShopItem> = initialRewardItems(),
    val toastBanner: String = "온길 · 도로 위를 따뜻하게"
) {
    val activePet: PetCharacter
        get() = pet
}

class MainViewModel(application: Application) :
    AndroidViewModel(application),
    PleosSdkManager.Listener {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val evaluationEngine = MannerEvaluationEngine()
    private val sdkManager = PleosSdkManager(application, this)
    private val settingsStore = SettingsStore(application)

    private var recordingJob: Job? = null
    private var ttsTimerJob: Job? = null
    private var releaseJob: Job? = null
    private var lastListenAtMs = 0L
    private var signalHoldJob: Job? = null

    // --- Navigation (route-first map) ---
    private val routeRepository = RouteRepository()
    private var routeData: RouteData? = null
    private var routeIsFallback = false
    private var progressM = 0.0
    private var realLocationSeen = false
    private var lastDestination: GeoPoint? = null
    private var routeJob: Job? = null
    private var liveTurn: TurnIcon? = null
    private var liveTurnDistM = 0.0
    private var liveTurnAtMs = 0L

    init {
        sdkManager.initializeAll()
        startCooldownTicker()
        loadRoute(RouteRepository.DEFAULT_ORIGIN, RouteRepository.DEFAULT_DESTINATION)
        val saved = settingsStore.load()
        // 첫 쪽지 안내를 바로 보여 주기 위해 아직 안 들은 쪽지 하나를 미리 올려 둔다.
        _uiState.update {
            it.copy(
                autoPlayTts = saved.autoPlayTts,
                showOnlyCommuteBand = saved.commuteBandOnly,
                mapTheme = saved.mapTheme,
                activeIncomingNote = NoteInbox.nextIncoming(it.notes, commuteOnly = saved.commuteBandOnly)
            )
        }
        // 달리는 중에는 쪽지함·설정을 열 수 없다. 차가 움직이기 시작하면 열려 있던 화면을 닫는다.
        viewModelScope.launch {
            _uiState.map { NoteAccess.canBrowse(it.telemetry.gear, it.telemetry.speedKmh) }
                .distinctUntilChanged()
                .collect { canBrowse ->
                    if (!canBrowse) closeAllTouchSurfaces()
                }
        }
    }

    private fun canBrowseNow(): Boolean {
        val t = _uiState.value.telemetry
        return NoteAccess.canBrowse(t.gear, t.speedKmh)
    }

    /** 차가 움직이기 시작하면 화면을 직접 눌러야 하는 것(쪽지함, 설정, 작성·리포트·보상 창)을 모두 닫는다. */
    private fun closeAllTouchSurfaces() {
        val s = _uiState.value
        if (s.showVoiceComposeDialog) {
            recordingJob?.cancel()
            sdkManager.stopVoiceNoteCapture()
        }
        _uiState.update {
            it.copy(
                screen = AppScreen.HOME,
                showVoiceComposeDialog = false,
                isRecordingStt = false,
                showParkingReportModal = false,
                showRewardShopModal = false
            )
        }
    }

    private fun startCooldownTicker() {
        viewModelScope.launch {
            while (true) {
                delay(1000L)
                _uiState.update { state ->
                    val updatedEval = evaluationEngine.tickCooldown(state.evaluation)
                    state.copy(evaluation = updatedEval)
                }
                advanceDemoDrive()
            }
        }
    }

    // --- Navigation ---

    private fun loadRoute(origin: GeoPoint, destination: GeoPoint) {
        lastDestination = destination
        // 이전 요청이 늦게 도착해 최신 경로를 덮어쓰지 않게 취소한다.
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            val fetched = routeRepository.fetch(origin, destination)
            // 새 경로 요청이 실패했는데 이미 실제 경로가 있으면 데모 선으로 바꾸지 않는다.
            if (fetched == null && routeData != null && !routeIsFallback) return@launch
            routeIsFallback = fetched == null
            val data = fetched ?: RouteRepository.fallback(origin)
            routeData = data
            progressM = if (realLocationSeen) {
                val t = _uiState.value.telemetry
                data.track.project(GeoPoint(t.latitude, t.longitude))
            } else {
                0.0
            }
            publishNav()
        }
    }

    /** 차량 위치 센서가 없는 환경(에뮬레이터/시연)에서는 D 기어일 때 경로를 따라 자동으로 진행한다. */
    private fun advanceDemoDrive() {
        val t = _uiState.value.telemetry
        val data = routeData
        if (data == null || realLocationSeen || t.gear != GearState.D) return
        if (progressM >= data.track.totalM) return
        progressM = (progressM + t.speedKmh / 3.6).coerceAtMost(data.track.totalM)
        publishNav()
    }

    private fun publishNav() {
        val data = routeData ?: return
        progressM = progressM.coerceIn(0.0, data.track.totalM)
        val current = _uiState.value.telemetry
        val speedMps = (current.speedKmh / 3.6f).toDouble()
        val snap = NavSnapshot.compute(data.track, data.steps, progressM, speedMps)
        val liveFresh = liveTurn != null && System.currentTimeMillis() - liveTurnAtMs < LIVE_TURN_TTL_MS
        val shownTurn = if (liveFresh) liveTurn!! else snap.nextTurn
        val shownDist = if (liveFresh) liveTurnDistM else snap.distToTurnM

        // update 람다 안에서 다시 계산되지 않게 미리 계산한다.
        val pos: GeoPoint
        val bearing: Float
        if (realLocationSeen) {
            pos = GeoPoint(current.latitude, current.longitude)
            bearing = current.headingDeg
        } else {
            pos = data.track.pointAt(progressM)
            bearing = data.track.bearingAt(progressM).toFloat()
        }
        val nav = NavUiState(
            hasRoute = true,
            isFallbackRoute = routeIsFallback,
            carPosition = pos,
            carBearing = bearing,
            // 지나온 구간과 먼 구간은 잘라서, 지도가 매초 다시 그릴 점을 훨씬 적게 한다.
            upcomingRoute = data.track.window(progressM, progressM + ROUTE_WINDOW_M),
            nextTurn = shownTurn,
            distToTurnM = shownDist,
            remainingM = snap.remainingM,
            etaSec = snap.etaSec,
            arrived = snap.arrived
        )
        _uiState.update { state ->
            state.copy(
                telemetry = if (realLocationSeen) {
                    state.telemetry
                } else {
                    state.telemetry.copy(latitude = pos.lat, longitude = pos.lng, headingDeg = bearing)
                },
                nav = nav
            )
        }
    }

    // SDK 콜백이 어느 스레드에서 와도 내비 상태(var)는 항상 메인 스레드에서만 다룬다.
    override fun onNaviTurn(turn: TurnIcon, distanceM: Double) {
        viewModelScope.launch {
            liveTurn = turn
            liveTurnDistM = distanceM
            liveTurnAtMs = System.currentTimeMillis()
            publishNav()
        }
    }

    override fun onNaviDestination(lat: Double, lng: Double, remainDistanceM: Int, remainTimeSec: Int) {
        viewModelScope.launch {
            val dest = GeoPoint(lat, lng)
            val previous = lastDestination
            if (previous != null && GeoMath.distanceM(previous, dest) < NEW_DESTINATION_MIN_M) return@launch
            val t = _uiState.value.telemetry
            loadRoute(GeoPoint(t.latitude, t.longitude), dest)
        }
    }

    /** 시연용: 지도 낮/밤을 자동 → 낮 → 밤 순서로 돌린다. */
    fun cycleMapTheme() {
        setMapTheme(_uiState.value.mapTheme.next())
    }

    /** 런타임 권한(FUSED_LOCATION 등) 승인 창이 끝난 뒤 호출한다. */
    fun onRuntimePermissionsResult() {
        sdkManager.retryLocationInit()
    }

    // --- PleosSdkManager.Listener callbacks ---
    override fun onSdkStatusChanged(status: SdkConnectionStatus) {
        _uiState.update { it.copy(sdkStatus = status) }
    }

    override fun onLocationUpdated(lat: Double, lng: Double, headingDeg: Float?, speedKmh: Float?) {
        viewModelScope.launch {
            val here = GeoPoint(lat, lng)
            val data = routeData
            // 경로에서 너무 떨어진 위치(예: 에뮬레이터 기본 위치)는 무시하고 시연용 자동 주행을 유지한다.
            // 경로가 아직 없거나 경로에서 너무 먼 위치는 실제 위치로 믿지 않는다.
            if (data == null || data.track.distanceToRouteM(here) > REAL_LOCATION_MAX_OFF_ROUTE_M) {
                realLocationSeen = false
                return@launch
            }
            realLocationSeen = true
            progressM = data.track.project(here)
            _uiState.update { state ->
                state.copy(
                    telemetry = state.telemetry.copy(
                        latitude = lat,
                        longitude = lng,
                        // 정차/저속에서는 방위각을 모르므로 이전 값을 유지해 지도가 휠 돌지 않게 한다.
                        headingDeg = headingDeg ?: state.telemetry.headingDeg,
                        speedKmh = speedKmh ?: state.telemetry.speedKmh
                    )
                )
            }
            publishNav()
        }
    }

    override fun onNaviRoadUpdated(roadName: String?, speedLimitKmh: Int?) {
        _uiState.update { state ->
            state.copy(
                telemetry = state.telemetry.copy(
                    roadName = roadName ?: state.telemetry.roadName,
                    speedLimitKmh = speedLimitKmh ?: state.telemetry.speedLimitKmh
                )
            )
        }
    }

    override fun onSttResult(recognizedText: String) {
        val result = AiNoteModerator.analyzeAndFilter(recognizedText)
        _uiState.update {
            it.copy(
                draftVoiceText = recognizedText,
                lastModerationResult = result
            )
        }
    }

    override fun onTtsStateChanged(isSpeaking: Boolean, message: String) {
        // SDK 콜백이 어느 스레드에서 와도 펫이 쪽지를 들고 내리는 상태는 메인 스레드에서만 다룬다.
        viewModelScope.launch {
            // 새 쪽지를 읽기 시작하면 SDK가 이전 쪽지의 종료 신호를 뒤늦게 보내올 수 있다. 시작 직후의 종료 신호는 이전 것으로 보고 무시한다.
            val sinceListen = SystemClock.elapsedRealtime() - lastListenAtMs
            if (!isSpeaking && lastListenAtMs != 0L && sinceListen < STALE_STOP_GRACE_MS) return@launch
            _uiState.update {
                it.copy(
                    isTtsSpeaking = isSpeaking,
                    ttsSpeakingText = message
                )
            }
            if (isSpeaking) {
                releaseJob?.cancel()
            } else {
                // 자연스럽게 끝난 뒤에는 안전 종료 타이머가 나중에 다른 말을 끊지 않게 한다.
                ttsTimerJob?.cancel()
                scheduleHeldNoteRelease()
            }
        }
    }

    // --- Driving & Anti-Abuse Simulation / Verification Actions ---

    /**
     * 1. 안정 주행 (+0.5km 주행 시뮬레이션)
     * Odometer 누적 → 1.0km 도달 시 Safe Drive 보상, 2.0km 도달 시 Smooth Drive 보너스
     */
    fun simulateSafeDrivingStep(deltaKm: Float = 0.5f) {
        val state = _uiState.value
        if (state.telemetry.gear == GearState.P) {
            shiftGear(GearState.D)
        }
        val currentTelemetry = _uiState.value.telemetry
        val stepResult = evaluationEngine.onDistanceDriven(
            currentState = _uiState.value.evaluation,
            deltaKm = deltaKm,
            currentSpeedKmh = currentTelemetry.speedKmh.coerceAtLeast(60f)
        )
        applyEvaluationResult(
            stepResult = stepResult,
            newTelemetry = currentTelemetry.copy(
                odometerKm = currentTelemetry.odometerKm + deltaKm,
                sessionDrivenKm = currentTelemetry.sessionDrivenKm + deltaKm,
                speedKmh = 68f,
                brakePressed = false
            )
        )
        // 시뮬레이터의 안정 주행 버튼은 지도의 차량도 그만큼 앞으로 보낸다.
        progressM += deltaKm * 1000.0
        publishNav()
        presentNextNoteIfIdle()
    }

    /** 시연용: 신호 대기처럼 기어는 D인 채 속도만 0이 된다. 쪽지함과 설정을 여는 조건이다. */
    /**
     * 시연용: 운전자가 "응"이라고 말해 새 쪽지를 듣기로 승인한 것으로 본다.
     * 달리는 중에는 화면을 눌러 승인하지 않으므로, 실제 음성 인식이 연결되면 이 함수를 부른다.
     */
    fun approveIncomingNoteByVoice() {
        val state = _uiState.value
        val incoming = state.activeIncomingNote ?: return
        if (incoming.isListenedByMe || state.isTtsSpeaking) return
        listenToNoteViaTts(incoming)
    }

    fun simulateStopAtLight() {
        _uiState.update { it.copy(telemetry = it.telemetry.copy(gear = GearState.D, speedKmh = 0f, brakePressed = true)) }
    }

    /**
     * 2. 매너 방향지시등 정석 시나리오:
     * 깜빡이 ON (2.0초 유지) + 실제 차량 진행방향(FusedLocation Heading) 30° 회전 일치 → +15 EXP, +10 P
     */
    fun simulateMannerLaneChange(direction: TurnSignalState = TurnSignalState.RIGHT) {
        signalHoldJob?.cancel()
        signalHoldJob = viewModelScope.launch {
            val baseHeading = _uiState.value.telemetry.headingDeg
            // Step 1: Turn Signal ON (0.8s)
            val step1 = evaluationEngine.onTurnSignalAndHeadingChanged(
                currentState = _uiState.value.evaluation,
                newSignal = direction,
                currentHeadingDeg = baseHeading,
                holdDurationSec = 0.8f
            )
            _uiState.update {
                it.copy(
                    telemetry = it.telemetry.copy(turnSignal = direction),
                    evaluation = step1.updatedState,
                    toastBanner = "${direction.label} 점등 감지 (1.5초 이상 유지 확인 중...)"
                )
            }
            delay(700L)

            // Step 2: Hold >= 1.5s reached, waiting for heading change
            val step2 = evaluationEngine.onTurnSignalAndHeadingChanged(
                currentState = _uiState.value.evaluation,
                newSignal = direction,
                currentHeadingDeg = baseHeading,
                holdDurationSec = 1.6f
            )
            _uiState.update {
                it.copy(
                    evaluation = step2.updatedState,
                    toastBanner = "깜빡이 1.6초 유지 확인! FusedLocation 방위각 변화를 감지합니다..."
                )
            }
            delay(700L)

            // Step 3: Heading changes by +30 deg (or -30 deg) matching blinker
            val deltaDeg = if (direction == TurnSignalState.LEFT) -30f else 30f
            val newHeading = (baseHeading + deltaDeg + 360f) % 360f
            val step3 = evaluationEngine.onTurnSignalAndHeadingChanged(
                currentState = _uiState.value.evaluation,
                newSignal = direction,
                currentHeadingDeg = newHeading,
                holdDurationSec = 2.2f
            )
            applyEvaluationResult(
                stepResult = step3,
                newTelemetry = _uiState.value.telemetry.copy(
                    turnSignal = TurnSignalState.OFF,
                    headingDeg = newHeading
                )
            )
        }
    }

    /**
     * 3. 깜빡이 어뷰징(단순 껐다 켜기) 차단 테스트:
     * 주행 중 깜빡이만 켰다가 방위각 변화 없이 바로 끄면 EXP 미지급 안내
     */
    fun simulateBlinkerAbuseAttempt() {
        signalHoldJob?.cancel()
        signalHoldJob = viewModelScope.launch {
            val heading = _uiState.value.telemetry.headingDeg
            val stepOn = evaluationEngine.onTurnSignalAndHeadingChanged(
                currentState = _uiState.value.evaluation,
                newSignal = TurnSignalState.LEFT,
                currentHeadingDeg = heading,
                holdDurationSec = 0.4f
            )
            _uiState.update {
                it.copy(
                    telemetry = it.telemetry.copy(turnSignal = TurnSignalState.LEFT),
                    evaluation = stepOn.updatedState,
                    toastBanner = "좌측 깜빡이 점등 (0.4초)..."
                )
            }
            delay(500L)
            // Turn off immediately with only 2° heading change (< 25° threshold)
            val stepOff = evaluationEngine.onTurnSignalAndHeadingChanged(
                currentState = _uiState.value.evaluation,
                newSignal = TurnSignalState.OFF,
                currentHeadingDeg = heading + 2f,
                holdDurationSec = 0.5f
            )
            applyEvaluationResult(
                stepResult = stepOff,
                newTelemetry = _uiState.value.telemetry.copy(turnSignal = TurnSignalState.OFF)
            )
        }
    }

    /**
     * 4. 급제동 발생 시나리오 (감점 없음! 현재 2km Smooth 보너스 구간만 리셋)
     */
    fun simulateHarshBrakingEvent() {
        val currentTelemetry = _uiState.value.telemetry
        val stepResult = evaluationEngine.onHarshDrivingDetected(
            currentState = _uiState.value.evaluation,
            isHarshBrake = true,
            deltaSpeedKmhPerSec = -14f
        )
        applyEvaluationResult(
            stepResult = stepResult,
            newTelemetry = currentTelemetry.copy(
                speedKmh = (currentTelemetry.speedKmh - 24f).coerceAtLeast(20f),
                brakePressed = true
            )
        )
    }

    /**
     * 5. 기어 전환 (P 전환 시 오늘의 온기 리포트 & 바퀴자취 지도 자동 표시)
     */
    fun shiftGear(newGear: GearState) {
        _uiState.update { state ->
            val newSpeed = if (newGear == GearState.P) 0f else 62f
            state.copy(
                telemetry = state.telemetry.copy(
                    gear = newGear,
                    speedKmh = newSpeed,
                    brakePressed = newGear == GearState.P
                ),
                showParkingReportModal = newGear == GearState.P,
                toastBanner = if (newGear == GearState.P) {
                    "주차(P) 상태 전환 완료! 오늘의 온기 리포트와 바퀴자취 지도가 열렸습니다."
                } else {
                    "${newGear.label}단 전환 · 주행 중에는 음성 쪽지 안전 모드로 동작합니다."
                }
            )
        }
    }

    private fun applyEvaluationResult(
        stepResult: MannerEvaluationEngine.EvaluationStepResult,
        newTelemetry: VehicleTelemetry
    ) {
        _uiState.update { state ->
            val newTotalExp = state.totalExp + stepResult.expGained
            val newPoints = state.warmthPoints + stepResult.pointsGained
            val newTickets = state.remainingNoteTickets + stepResult.ticketGained
            val updatedPet = growPet(state.pet, stepResult.expGained, stepResult.petReactionSpeech)
            state.copy(
                telemetry = newTelemetry,
                evaluation = stepResult.updatedState,
                totalExp = newTotalExp,
                warmthPoints = newPoints,
                remainingNoteTickets = newTickets,
                bonusTicketsEarnedToday = state.bonusTicketsEarnedToday + stepResult.ticketGained,
                pet = updatedPet,
                parkingReport = state.parkingReport.copy(
                    todayDrivenKm = state.parkingReport.todayDrivenKm + (newTelemetry.odometerKm - state.telemetry.odometerKm).coerceAtLeast(0.0),
                    todayMannerTurns = stepResult.updatedState.completedMannerTurns,
                    todaySmoothWindows = stepResult.updatedState.completedSmoothWindows
                ),
                toastBanner = stepResult.eventBanner ?: state.toastBanner
            )
        }
    }

    private fun growPet(pet: PetCharacter, expAdded: Int, reactionSpeech: String?): PetCharacter {
        var exp = pet.currentExp + expAdded
        var lvl = pet.level
        var maxExp = pet.maxExp
        while (exp >= maxExp) {
            exp -= maxExp
            lvl += 1
            maxExp = 100 + (lvl * 40)
        }
        val stage = when {
            lvl >= 4 -> "성체 · 도로의 수호천사"
            lvl == 3 -> "성장기 · 든든한 동행러"
            lvl == 2 -> "새싹기 · 초보 매너러"
            else -> "아기 · 호기심 대장"
        }
        return pet.copy(
            level = lvl,
            stageName = stage,
            currentExp = exp,
            maxExp = maxExp,
            currentSpeech = reactionSpeech ?: pet.currentSpeech
        )
    }

    // --- Voice Note Listening, TTS & 1-Tap Positive Reaction ---

    // --- 정차 중에만 여는 화면 (쪽지함, 설정) ---

    fun openScreen(screen: AppScreen) {
        val t = _uiState.value.telemetry
        if (!NoteAccess.canBrowse(t.gear, t.speedKmh)) {
            _uiState.update { it.copy(toastBanner = "달리는 중에는 열 수 없어요.") }
            return
        }
        _uiState.update { it.copy(screen = screen) }
    }

    fun closeScreen() {
        _uiState.update { it.copy(screen = AppScreen.HOME) }
    }

    // --- 설정 (기기에 저장되어 앱을 다시 켜도 유지된다) ---

    fun toggleAutoPlayTts() {
        val next = !_uiState.value.autoPlayTts
        settingsStore.saveAutoPlay(next)
        _uiState.update { it.copy(autoPlayTts = next) }
    }

    fun toggleCommuteBandFilter() {
        val next = !_uiState.value.showOnlyCommuteBand
        settingsStore.saveCommuteBandOnly(next)
        _uiState.update {
            // 들는 중이 아니면 새 조건에 맞는 다음 쪽지로 알림을 다시 고른다.
            val idle = it.heldNote == null && !it.isTtsSpeaking
            it.copy(
                showOnlyCommuteBand = next,
                activeIncomingNote = if (idle) NoteInbox.nextIncoming(it.notes, commuteOnly = next) else it.activeIncomingNote
            )
        }
    }

    fun setMapTheme(theme: MapTheme) {
        settingsStore.saveMapTheme(theme)
        _uiState.update { it.copy(mapTheme = theme) }
    }

    fun listenToNoteViaTts(note: VoiceNote) {
        if (!note.isSameDirection) {
            _uiState.update { it.copy(toastBanner = "반대 차선 쪽지는 혼선을 막기 위해 재생하지 않아요.") }
            return
        }

        ttsTimerJob?.cancel()
        releaseJob?.cancel()
        lastListenAtMs = SystemClock.elapsedRealtime()
        val speechLine = "${NoteInbox.introLine(note.authorPetName)} ${note.displayText}"
        sdkManager.speakAnonymousNote(speechLine)

        // 펫이 길에서 쪽지를 주워 들고, 읽는 동안 계속 들고 있는다.
        _uiState.update { state ->
            val updatedNotes = state.notes.map {
                if (it.id == note.id) {
                    it.copy(
                        isListenedByMe = true,
                        listenerCount = if (it.isListenedByMe) it.listenerCount else it.listenerCount + 1
                    )
                } else it
            }
            state.copy(
                notes = updatedNotes,
                activeIncomingNote = state.activeIncomingNote?.let { a -> updatedNotes.firstOrNull { it.id == a.id } },
                heldNote = updatedNotes.first { it.id == note.id },
                isTtsSpeaking = true,
                ttsSpeakingText = note.displayText,
                pet = state.pet.copy(currentSpeech = "쪽지를 주웠어요. 같이 들어봐요."),
                toastBanner = "쪽지를 주웠어요"
            )
        }

        // 읽기가 끝났다는 신호(Pleos onDone)를 못 받는 경우를 위한 안전 종료.
        ttsTimerJob = viewModelScope.launch {
            delay(NoteInbox.speechTimeoutMs(speechLine))
            sdkManager.stopSpeaking()
        }
    }

    /** 읽기가 끝난 뒤 잠깐 들고 있다가 내려놓는다. 다시 읽기 시작하면 취소된다. */
    private fun scheduleHeldNoteRelease() {
        if (_uiState.value.heldNote == null) return
        releaseJob?.cancel()
        releaseJob = viewModelScope.launch {
            delay(HELD_NOTE_LINGER_MS)
            _uiState.update { s ->
                s.copy(
                    heldNote = null,
                    // 듣은 쪽지는 쪽지함으로 들어가고, 알림 점도 사라진다.
                    activeIncomingNote = s.activeIncomingNote
                        ?.let { a -> s.notes.firstOrNull { it.id == a.id } }
                        ?.takeIf { !it.isListenedByMe },
                    pet = s.pet.copy(currentSpeech = "잘 들었어요. 쪽지함에 넣어 둘게요.")
                )
            }
        }
    }

    /** 다음 쪽지 구간에 들어오면 알림을 띄우고, 자동 재생이 켜져 있으면 바로 읽는다. */
    private fun presentNextNoteIfIdle() {
        val state = _uiState.value
        if (state.heldNote != null || state.isTtsSpeaking) return
        val incoming = state.activeIncomingNote ?: NoteInbox.nextIncoming(state.notes, commuteOnly = state.showOnlyCommuteBand)?.also { next ->
            _uiState.update { it.copy(activeIncomingNote = next) }
        } ?: return
        if (state.autoPlayTts && state.telemetry.gear == GearState.D) listenToNoteViaTts(incoming)
    }

    fun stopTtsPlayback() {
        ttsTimerJob?.cancel()
        // 직접 멈춘 경우에는 바로 오는 종료 신호를 무시하지 않는다.
        lastListenAtMs = 0L
        sdkManager.stopSpeaking()
    }

    /**
     * 쪽지에 좋아요 하나만 보내는 긍정 반응.
     * 작성자와 청취자 모두에게 EXP/포인트 보상을 주고 쪽지 수명을 연장한다.
     */
    fun sendTodakReaction(noteId: String) {
        val state = _uiState.value
        val target = state.notes.firstOrNull { it.id == noteId } ?: return
        if (target.isReactedByMe) return

        val updatedNotes = state.notes.map { note ->
            if (note.id == noteId) {
                note.copy(
                    todakCount = note.todakCount + 1,
                    isReactedByMe = true,
                    remainingLifeLabel = "${note.remainingLifeLabel} · 연장됨"
                )
            } else note
        }

        val updatedPet = growPet(state.pet, 10, "${target.authorPetName}에게 좋아요를 전했어요.")

        _uiState.update {
            it.copy(
                notes = updatedNotes,
                activeIncomingNote = if (it.activeIncomingNote?.id == noteId) {
                    updatedNotes.firstOrNull { n -> n.id == noteId }
                } else it.activeIncomingNote,
                totalExp = it.totalExp + 10,
                warmthPoints = it.warmthPoints + 15,
                pet = updatedPet,
                parkingReport = it.parkingReport.copy(
                    todayTodakReceived = it.parkingReport.todayTodakReceived + 1
                ),
                toastBanner = "좋아요를 전했어요 (+10 EXP, +15 P)"
            )
        }
    }

    // --- Voice Note Creation (10s STT + AI Safety & Classification) ---

    /** [force]는 숨긴 시연 조작판에서만 쓴다. 일반 화면에서는 달리는 중에 열지 않는다. */
    fun openVoiceNoteComposer(force: Boolean = false) {
        if (!force && !canBrowseNow()) return
        val state = _uiState.value
        if (state.remainingNoteTickets <= 0) {
            _uiState.update {
                it.copy(toastBanner = "오늘의 쪽지 작성권을 모두 사용했어요. 10km 안전주행 시마다 +1장이 충전됩니다!")
            }
            return
        }
        val defaultSample = "성수대교 합류 구간 차들이 많네요. 다들 깜빡이 켜면 조금씩 양보하며 오늘도 기분 좋게 출근해요!"
        val modResult = AiNoteModerator.analyzeAndFilter(defaultSample)
        _uiState.update {
            it.copy(
                showVoiceComposeDialog = true,
                draftVoiceText = defaultSample,
                lastModerationResult = modResult,
                isRecordingStt = false,
                recordingSecondsLeft = 10
            )
        }
    }

    fun closeVoiceNoteComposer() {
        recordingJob?.cancel()
        sdkManager.stopVoiceNoteCapture()
        _uiState.update {
            it.copy(
                showVoiceComposeDialog = false,
                isRecordingStt = false
            )
        }
    }

    fun start10SecSttRecording(presetScenarioText: String? = null) {
        recordingJob?.cancel()
        sdkManager.startVoiceNoteCapture()
        val sampleToRecognize = presetScenarioText
            ?: "강변북로 노을이 정말 예쁘네요. 오늘 하루 고생한 운전자분들 모두 토닥토닥 힘내세요!"

        recordingJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRecordingStt = true,
                    recordingSecondsLeft = 10,
                    draftVoiceText = ""
                )
            }
            for (sec in 9 downTo 7) {
                delay(450L)
                _uiState.update { it.copy(recordingSecondsLeft = sec) }
            }
            sdkManager.stopVoiceNoteCapture()
            val modResult = AiNoteModerator.analyzeAndFilter(sampleToRecognize)
            _uiState.update {
                it.copy(
                    isRecordingStt = false,
                    recordingSecondsLeft = 10,
                    draftVoiceText = sampleToRecognize,
                    lastModerationResult = modResult
                )
            }
        }
    }

    fun selectVoicePresetForAiTest(presetText: String) {
        val modResult = AiNoteModerator.analyzeAndFilter(presetText)
        _uiState.update {
            it.copy(
                draftVoiceText = presetText,
                lastModerationResult = modResult
            )
        }
    }

    fun publishVoiceNote(asCommuteBandNote: Boolean) {
        val state = _uiState.value
        val mod = state.lastModerationResult ?: AiNoteModerator.analyzeAndFilter(state.draftVoiceText)

        if (mod.status == ModerationStatus.BLOCKED) {
            _uiState.update {
                it.copy(toastBanner = mod.noticeMessage)
            }
            return
        }

        val activePet = state.activePet
        val newNote = VoiceNote(
            id = "note_${System.currentTimeMillis()}",
            authorNickname = "나",
            authorPetName = activePet.name,
            authorPetLevel = activePet.level,
            rawText = state.draftVoiceText,
            displayText = mod.cleanedText,
            category = mod.category,
            moderationStatus = mod.status,
            moderationNotice = mod.noticeMessage,
            roadSegmentName = state.telemetry.roadName,
            latitude = state.telemetry.latitude,
            longitude = state.telemetry.longitude,
            headingDeg = state.telemetry.headingDeg,
            distanceMeters = 0,
            headingDiffDeg = 0f,
            createdAtLabel = "방금 전",
            remainingLifeLabel = mod.category.lifespanLabel,
            todakCount = 1,
            listenerCount = 1,
            isReactedByMe = true,
            isListenedByMe = true,
            isCommuteBandOnly = asCommuteBandNote,
            isMine = true
        )

        val updatedPet = growPet(
            pet = state.pet,
            expAdded = 15,
            reactionSpeech = "도로 위에 쪽지를 남겼어요. 다음 운전자가 듣고 미소 지을 거예요."
        )

        _uiState.update {
            it.copy(
                notes = listOf(newNote) + it.notes,
                remainingNoteTickets = (it.remainingNoteTickets - 1).coerceAtLeast(0),
                totalExp = it.totalExp + 15,
                warmthPoints = it.warmthPoints + 20,
                pet = updatedPet,
                showVoiceComposeDialog = false,
                parkingReport = it.parkingReport.copy(
                    todayNotesLeft = it.parkingReport.todayNotesLeft + 1
                ),
                toastBanner = "쪽지 등록 완료! [${mod.category.label} · ${mod.category.lifespanLabel}] (+15 EXP, +20 P 적립)"
            )
        }
    }

    // --- Modals & Hyundai Reward Conversion ---

    fun setParkingReportModalVisible(visible: Boolean) {
        if (visible && !canBrowseNow()) return
        _uiState.update { it.copy(showParkingReportModal = visible) }
    }

    fun setRewardShopModalVisible(visible: Boolean) {
        if (visible && !canBrowseNow()) return
        _uiState.update { it.copy(showRewardShopModal = visible) }
    }

    fun setSdkArchitectureModalVisible(visible: Boolean) {
        _uiState.update { it.copy(showSdkArchitectureModal = visible) }
    }

    fun redeemRewardItem(item: RewardShopItem) {
        val state = _uiState.value
        if (state.warmthPoints < item.pointCost) {
            _uiState.update {
                it.copy(toastBanner = "포인트가 부족합니다 (현재 ${state.warmthPoints} P / 필요 ${item.pointCost} P).")
            }
            return
        }
        _uiState.update {
            it.copy(
                warmthPoints = it.warmthPoints - item.pointCost,
                toastBanner = "[${item.brand}] '${item.title}' 교환 완료! 마이현대 앱 쿠폰함에 즉시 지급되었습니다."
            )
        }
    }

    override fun onCleared() {
        sdkManager.releaseAll()
        super.onCleared()
    }
}

/** 읽기를 시작한 직후 이 시간 안에 오는 종료 신호는 이전 쪽지의 것으로 본다. */
private const val STALE_STOP_GRACE_MS = 700L

/** 쪽지 읽기가 끝난 뒤 펫이 쪽지를 들고 있는 시간. */
private const val HELD_NOTE_LINGER_MS = 1_800L

/** 차량 내비 TBT를 마지막으로 받은 뒤 이 시간이 지나면 우리 경로 계산값으로 돌아간다. */
private const val LIVE_TURN_TTL_MS = 5_000L

/** 이 거리보다 가까운 목적지 변경은 같은 목적지로 보고 경로를 다시 구하지 않는다. */
private const val NEW_DESTINATION_MIN_M = 100.0

/** 실제 위치가 경로에서 이 거리보다 멀면 경로 위의 차량 위치로 쓰지 않는다. */
private const val REAL_LOCATION_MAX_OFF_ROUTE_M = 2_000.0

/** 지도에 그리는 경로는 차 앞 이 거리까지만 남긴다. */
private const val ROUTE_WINDOW_M = 4_000.0

/** 온길의 첫 동행 펫 제로. 다른 펫은 이후 확장한다. */
private fun initialPet(): PetCharacter = PetCharacter(
    id = "pet_zero",
    name = "제로",
    title = "온길의 첫 동행 펫",
    level = 2,
    stageName = "새싹기 · 초보 매너러",
    currentExp = 120,
    maxExp = 180,
    isUnlocked = true,
    badgeColorHex = 0xFF00AAD2,
    description = "깜빡이 매너와 부드러운 브레이크를 가장 좋아하는 든든한 동행 펫",
    currentSpeech = "오늘도 같이 가요. 조금만 더 달리면 Lv.3이 돼요."
)

private fun initialVoiceNotes(): List<VoiceNote> = listOf(
    VoiceNote(
        id = "note_1",
        authorNickname = "성수동 모닝커피",
        authorPetName = "제로",
        authorPetLevel = 3,
        rawText = "성수대교 진입 전 3차선에 작은 포트홀이 있어요. 미리 2차선으로 부드럽게 합류하세요! 오늘도 안전운전 화이팅!",
        displayText = "성수대교 진입 전 3차선에 작은 포트홀이 있어요. 미리 2차선으로 부드럽게 합류하세요! 오늘도 안전운전 화이팅!",
        category = NoteCategory.SAFETY_3H,
        moderationStatus = ModerationStatus.APPROVED,
        roadSegmentName = "강변북로 성수대교 동단",
        latitude = 37.5448,
        longitude = 127.0462,
        headingDeg = 82f,
        distanceMeters = 180,
        headingDiffDeg = 3f, // 진행방향 85°와 3° 차이 → 재생 대상!
        createdAtLabel = "12분 전",
        remainingLifeLabel = "2시간 48분 남음",
        todakCount = 19,
        listenerCount = 47,
        isCommuteBandOnly = true
    ),
    VoiceNote(
        id = "note_2",
        authorNickname = "판교행 아이오닉",
        authorPetName = "제로",
        authorPetLevel = 4,
        rawText = "막히는 출근길이지만 다들 깜빡이 켜주면 조금씩 양보해 주셔서 마음이 따뜻해지네요. 오늘 하루도 좋은 일만 가득하세요!",
        displayText = "막히는 출근길이지만 다들 깜빡이 켜주면 조금씩 양보해 주셔서 마음이 따뜻해지네요. 오늘 하루도 좋은 일만 가득하세요!",
        category = NoteCategory.CHEER_24H,
        moderationStatus = ModerationStatus.APPROVED,
        roadSegmentName = "성수대교 남단 분기점",
        latitude = 37.5390,
        longitude = 127.0470,
        headingDeg = 105f,
        distanceMeters = 420,
        headingDiffDeg = 20f, // ±45° 이내 → 재생 가능
        createdAtLabel = "38분 전",
        remainingLifeLabel = "23시간 22분 남음",
        todakCount = 34,
        listenerCount = 82,
        isCommuteBandOnly = true
    ),
    VoiceNote(
        id = "note_3",
        authorNickname = "뚝섬 노을수집가",
        authorPetName = "제로",
        authorPetLevel = 2,
        rawText = "서울숲 주차장 들어가실 분들 동문 쪽 공영주차장이 지금 훨씬 여유로워요! 바로 옆 베이커리 소금빵도 추천합니다.",
        displayText = "서울숲 주차장 들어가실 분들 동문 쪽 공영주차장이 지금 훨씬 여유로워요! 바로 옆 베이커리 소금빵도 추천합니다.",
        category = NoteCategory.TIP_30D,
        moderationStatus = ModerationStatus.APPROVED,
        roadSegmentName = "성수동 서울숲 나들목",
        latitude = 37.5430,
        longitude = 127.0415,
        headingDeg = 68f,
        distanceMeters = 650,
        headingDiffDeg = 17f, // ±45° 이내 → 재생 가능
        createdAtLabel = "2일 전",
        remainingLifeLabel = "28일 남음",
        todakCount = 61,
        listenerCount = 210,
        isCommuteBandOnly = false,
        isListenedByMe = true
    ),
    VoiceNote(
        id = "note_opposite",
        authorNickname = "일산행 캐스퍼",
        authorPetName = "제로",
        authorPetLevel = 2,
        rawText = "강변북로 일산 방향 한강철교 부근 햇빛이 눈부시니 선글라스 챙기세요!",
        displayText = "강변북로 일산 방향 한강철교 부근 햇빛이 눈부시니 선글라스 챙기세요!",
        category = NoteCategory.VIBE_7D,
        moderationStatus = ModerationStatus.APPROVED,
        roadSegmentName = "강변북로 (반대 차선 · 서향)",
        latitude = 37.5450,
        longitude = 127.0445,
        headingDeg = 265f,
        distanceMeters = 120,
        headingDiffDeg = 180f, // 반대 차선 (180° 차이 > 45°) → 자동 필터링됨!
        createdAtLabel = "25분 전",
        remainingLifeLabel = "6일 23시간 남음",
        todakCount = 14,
        listenerCount = 39,
        isCommuteBandOnly = false
    ),
    VoiceNote(
        id = "note_mine_1",
        authorNickname = "나",
        authorPetName = "제로",
        authorPetLevel = 2,
        rawText = "터널 나가면 노면이 젖어 있어요. 천천히 가세요.",
        displayText = "터널 나가면 노면이 젖어 있어요. 천천히 가세요.",
        category = NoteCategory.SAFETY_3H,
        moderationStatus = ModerationStatus.APPROVED,
        roadSegmentName = "강변북로 성수대교 동단",
        latitude = 37.5446,
        longitude = 127.0452,
        headingDeg = 85f,
        distanceMeters = 0,
        headingDiffDeg = 0f,
        createdAtLabel = "1시간 전",
        remainingLifeLabel = "1시간 52분 남음",
        todakCount = 3,
        listenerCount = 8,
        isReactedByMe = true,
        isListenedByMe = true,
        isMine = true
    )
)

private fun initialTireTracks(): List<TireTrackNode> = listOf(
    TireTrackNode("자양동 출발", "08:10", "Safe 1km 달성", 0, 0, 0.14f, 0.68f),
    TireTrackNode("영동대교 북단", "08:18", "Smooth 2km 완주", 1, 4, 0.36f, 0.52f),
    TireTrackNode("성수대교 동단", "08:27", "Manner 깜빡이 3회", 1, 5, 0.62f, 0.44f),
    TireTrackNode("성수동 카페거리", "08:35", "무급제동 완벽 도착", 2, 9, 0.84f, 0.30f)
)

private fun initialRewardItems(): List<RewardShopItem> = listOf(
    RewardShopItem(
        id = "rw_bluemembers",
        brand = "Hyundai Blue Members",
        title = "블루멤버스 1,000 포인트 즉시 전환",
        pointCost = 500,
        categoryLabel = "포인트 전환",
        emoji = "💎"
    ),
    RewardShopItem(
        id = "rw_bluehands",
        brand = "Blue Hands 정비혜택",
        title = "블루핸즈 에어컨 필터·와이퍼 교체 5,000원 할인권",
        pointCost = 800,
        categoryLabel = "차량 정비",
        emoji = "🔧"
    ),
    RewardShopItem(
        id = "rw_myhyundai_shop",
        brand = "마이현대 Shop",
        title = "현대 순정 발수코팅 워셔액 2개입 교환권",
        pointCost = 600,
        categoryLabel = "차량 용품",
        emoji = "🧴"
    ),
    RewardShopItem(
        id = "rw_ubi_insurance",
        brand = "현대해상 하이카 UBI",
        title = "온길 매너운전 인증서 발급 (보험료 최대 12% 특약 연동)",
        pointCost = 300,
        categoryLabel = "보험 연계",
        emoji = "🛡️"
    )
)
