package com.example.hyundai.viewmodel

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hyundai.data.SettingsStore
import com.example.hyundai.domain.AiNoteModerator
import com.example.hyundai.domain.GleoAiAssistant
import com.example.hyundai.domain.MannerEvaluationEngine
import com.example.hyundai.domain.NoteAccess
import com.example.hyundai.domain.NoteInbox
import com.example.hyundai.domain.RangeAdvisor
import com.example.hyundai.domain.VehicleSignalTracker
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
import com.example.hyundai.nav.NoteRouteZone
import com.example.hyundai.nav.RouteData
import com.example.hyundai.nav.RouteRepository
import com.example.hyundai.nav.RouteTrack
import com.example.hyundai.nav.TurnIcon
import com.example.hyundai.nav.DriveView
import com.example.hyundai.nav.NavPlace
import com.example.hyundai.nav.PlaceKind
import com.example.hyundai.nav.PlaceListMode
import com.example.hyundai.nav.PlaceRepository
import com.example.hyundai.nav.PlaceSearchUi
import com.example.hyundai.nav.SavedPlaces
import com.example.hyundai.sdk.PleosSdkManager
import com.example.hyundai.sdk.VirtualConsoleClient
import com.example.hyundai.vehicle.ConsoleVehicleState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val autoPlayTts: Boolean = true,
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
    val parkingReport: ParkingReportSummary = ParkingReportSummary(),
    val tireTracks: List<TireTrackNode> = initialTireTracks(),
    val rewardItems: List<RewardShopItem> = initialRewardItems(),
    val toastBanner: String = "온길 · 도로 위를 따뜻하게",
    /** 지도를 기울인 3D로 볼지(false면 위에서 내려다보는 2D). */
    val map3d: Boolean = true,
    val driveView: DriveView = DriveView.MAP,
    /** 지금 안내 중인 목적지 이름. */
    val destinationName: String = "판교역",
    /** 기본 내비 즐겨찾기와 이 앱에 저장한 장소를 합친 것. */
    val places: SavedPlaces = SavedPlaces(),
    val placeSearch: PlaceSearchUi = PlaceSearchUi(),
    /** 가상 콘솔 차량 신호. 콘솔에 연결되지 않았으면 null. */
    val console: ConsoleVehicleState? = null,
    /** 시나리오 3: 매너운전 달성 시 제로 아이콘 반짝임 효과 */
    val mannerSparkleActive: Boolean = false,
    /** 시나리오 4·5·6: 주행 중 음성 안내/대화 HUD 프롬프트 */
    val voiceHudPrompt: String? = null,
    /** 시나리오 4·5·6: 운전자 발화("응", "좋아요", "쪽지 남겨줘") 표시 */
    val voiceHudUserUtterance: String? = null,
    /** 시나리오 5·6: 하트 전달 또는 음성 쪽지 등록 완료 배너 */
    val heartReactionBanner: String? = null,
    /** 시나리오 8: 제로 성장(레벨업) 배너 */
    val levelUpBanner: String? = null,
    /** 시나리오 2: 기어 P -> D 전환 안내 배너 */
    val gearTransitionBanner: String? = null,
    /** 시나리오 9-1: 쪽지함 진입 시 '내가 남긴 쪽지' 탭 바로 열기 */
    val initialInboxTabMine: Boolean = false,
    /** 캡처/녹화 시 하단 시뮬레이터 제어바 숨김 여부 */
    val hideSimulatorDrawer: Boolean = false
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
    private val signalTracker = VehicleSignalTracker()
    private val sdkManager = PleosSdkManager(application, this)
    private val settingsStore = SettingsStore(application)

    private var recordingJob: Job? = null
    private var ttsTimerJob: Job? = null
    private var releaseJob: Job? = null
    private var lastListenAtMs = 0L
    private var signalHoldJob: Job? = null
    private var showcaseJob: Job? = null
    private var sparkleClearJob: Job? = null
    private var bannerClearJob: Job? = null

    // --- Navigation (route-first map) ---
    private val routeRepository = RouteRepository()
    private var routeData: RouteData? = null
    private var routeIsFallback = false
    private var progressM = 0.0
    private var realLocationSeen = false
    /** 차량 주행거리(Odometer)로 마지막으로 거리를 적립한 시각. 이 동안에는 시연 주행 거리를 겹쳐 적립하지 않는다. */
    private var lastOdometerCreditAtMs = 0L
    private var lastDestination: GeoPoint? = null
    private var routeJob: Job? = null
    private var liveTurn: TurnIcon? = null
    private var liveTurnDistM = 0.0
    private var liveTurnAtMs = 0L

    // --- 목적지·가상 콘솔 ---
    private val placeRepository = PlaceRepository()
    private var searchJob: Job? = null
    /** 이 앱에서 직접 저장한 장소(기기에 저장). */
    private var localPlaces = SavedPlaces()
    /** 기본 내비(NaviHelper)에서 받은 장소(저장하지 않음). */
    private var naviPlaces = SavedPlaces()
    private val consoleClient = VirtualConsoleClient(viewModelScope, ::onConsoleState)

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
                map3d = saved.map3d,
                driveView = saved.driveView,
                places = saved.places,
                activeIncomingNote = NoteInbox.nextIncoming(it.notes, commuteOnly = saved.commuteBandOnly)
            )
        }
        localPlaces = saved.places
        consoleClient.start()
        // 달리는 중에는 쪽지함·설정을 열 수 없다. 차가 움직이기 시작하면 열려 있던 화면을 닫는다.
        viewModelScope.launch {
            _uiState.map { NoteAccess.canBrowse(it.telemetry.gear, it.telemetry.speedKmh) }
                .distinctUntilChanged()
                .collect { canBrowse ->
                    if (!canBrowse) closeAllTouchSurfaces()
                }
        }
        // 안내 배너는 잠깐만 보여 준다. 지난 안내가 계속 떠 있으면 지금 상황으로 오해한다.
        viewModelScope.launch {
            _uiState.map { it.toastBanner }
                .distinctUntilChanged()
                .collectLatest { text ->
                    if (text.isBlank()) return@collectLatest
                    delay(TOAST_VISIBLE_MS)
                    _uiState.update { if (it.toastBanner == text) it.copy(toastBanner = "") else it }
                }
        }
    }

    private fun canBrowseNow(): Boolean {
        val t = _uiState.value.telemetry
        return NoteAccess.canBrowse(t.gear, t.speedKmh)
    }

    /** 차가 움직이기 시작하면 화면을 직접 눌러야 하는 것(쪽지함, 설정, 리포트·보상 창)을 모두 닫는다. */
    private fun closeAllTouchSurfaces() {
        _uiState.update {
            it.copy(
                screen = AppScreen.HOME,
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
                evaluateHeldTurnSignal()
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

    /**
     * 차량 위치 센서가 없는 환경(에뮬레이터/시연)에서는 D 기어일 때 경로를 따라 자동으로 진행한다.
     * N 기어로 굴러가는 동안에도 차는 움직이므로 같이 진행한다(속도는 가상 콘솔 값을 따른다).
     */
    private fun advanceDemoDrive() {
        val t = _uiState.value.telemetry
        val data = routeData
        if (data == null || realLocationSeen || (t.gear != GearState.D && t.gear != GearState.N)) return
        if (progressM >= data.track.totalM) return
        val before = progressM
        progressM = (progressM + t.speedKmh / 3.6).coerceAtMost(data.track.totalM)
        publishNav()
        creditDemoDistance(progressM - before)
        checkNoteZoneProximity()
    }

    /**
     * 시연 주행으로 간 거리도 Safe/Smooth 평가에 넣는다. GPS가 경로 위에 있으면 위치 콜백이,
     * 차량 주행거리(Odometer)가 늘고 있으면 그 콜백이 이미 적립하므로 그때는 건너뛴다.
     */
    private fun creditDemoDistance(deltaM: Double) {
        if (deltaM <= 0.0) return
        if (SystemClock.elapsedRealtime() - lastOdometerCreditAtMs < ODOMETER_FRESH_MS) return
        val deltaKm = (deltaM / 1000.0).toFloat()
        val current = _uiState.value
        val step = evaluationEngine.onDistanceDriven(
            currentState = current.evaluation,
            deltaKm = deltaKm,
            currentSpeedKmh = current.telemetry.speedKmh
        )
        applyEvaluationResult(
            stepResult = step,
            newTelemetry = current.telemetry.copy(
                odometerKm = current.telemetry.odometerKm + deltaKm,
                sessionDrivenKm = current.telemetry.sessionDrivenKm + deltaKm
            )
        )
    }

    /**
     * 차량 신호로 깜빡이가 켜져 있는 동안 1초마다 유지 시간과 진행 방향 변화를 다시 본다.
     * 깜빡이가 바뀔 때만 평가하면 유지 시간이 늘지 않아 매너 운전 인증이 나오지 않는다.
     * GPS가 경로 위에 있으면 위치 콜백이 같은 평가를 하므로 건너뛴다.
     */
    private fun evaluateHeldTurnSignal() {
        if (realLocationSeen) return
        val signal = signalTracker.activeSignal
        if (signal != TurnSignalState.LEFT && signal != TurnSignalState.RIGHT) return
        val current = _uiState.value
        val step = evaluationEngine.onTurnSignalAndHeadingChanged(
            currentState = current.evaluation,
            newSignal = signal,
            currentHeadingDeg = current.telemetry.headingDeg,
            holdDurationSec = signalTracker.currentSignalHoldSeconds(SystemClock.elapsedRealtime())
        )
        applyEvaluationResult(step, current.telemetry)
        if (step.expGained > 0) triggerMannerSparkle()
    }

    /**
     * 주행 중 앞쪽 쪽지 구간(180m 이내)에 진입했을 때 쪽지를 자동으로 띄우고,
     * 자동 읽기(`autoPlayTts`)가 켜져 있으면 즉시 음성(TTS)으로 읽어 준다.
     */
    private fun checkNoteZoneProximity() {
        val state = _uiState.value
        if (state.telemetry.gear != GearState.D || state.isTtsSpeaking || state.heldNote != null) return
        val zones = state.nav.noteZones
        val enteredZone = zones.firstOrNull { z ->
            progressM >= (z.startM - 40.0) && progressM <= z.endM
        } ?: return
        val note = state.notes.firstOrNull { it.id == enteredZone.noteId && !it.isListenedByMe && it.isSameDirection }
            ?: return
        if (state.activeIncomingNote?.id != note.id) {
            _uiState.update { it.copy(activeIncomingNote = note) }
        }
        if (state.autoPlayTts) {
            listenToNoteViaTts(note)
        }
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
        val zones = computeNoteRouteZones(
            track = data.track,
            notes = _uiState.value.notes,
            commuteOnly = _uiState.value.showOnlyCommuteBand
        )
        val nav = NavUiState(
            hasRoute = true,
            isFallbackRoute = routeIsFallback,
            carPosition = pos,
            carBearing = bearing,
            track = data.track,
            progressM = progressM,
            noteZones = zones,
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

    // --- 가상 콘솔 차량 신호 (읽기 전용) ---

    /** 처음 연결했을 때 이미 있던 하이패스 결제는 새 결제로 알리지 않는다. */
    private var lastTollKey: String? = null
    private var tollSeeded = false

    private fun onConsoleState(state: ConsoleVehicleState?) {
        val before = _uiState.value
        val prev = before.console
        if (state == prev) return

        // 콘솔에서 기어를 바꾼 순간에만 따라간다. 같은 값을 매초 덮어쓰면 시연 조작판의 기어 버튼이 먹히지 않는다.
        val gear = state?.gear
        if (gear != null && gear != prev?.gear && gear != before.telemetry.gear) shiftGear(gear)

        var tollBanner: String? = null
        val toll = state?.toll
        if (state != null) {
            if (tollSeeded && toll != null && toll.key != lastTollKey) {
                tollBanner = buildString {
                    append("하이패스 ").append(toll.gateName)
                    toll.amountWon?.let { append(String.format(java.util.Locale.KOREA, " %,d원 결제", it)) }
                    toll.balanceWon?.let { append(String.format(java.util.Locale.KOREA, " · 잔액 %,d원", it)) }
                }
            }
            lastTollKey = toll?.key
            tollSeeded = true
        }

        _uiState.update { s ->
            val speed = state?.speedKph
            s.copy(
                console = state,
                telemetry = if (speed != null) s.telemetry.copy(speedKmh = speed) else s.telemetry,
                toastBanner = tollBanner ?: s.toastBanner
            )
        }
    }

    // --- 목적지 (정차 중에만 연다) ---

    private fun carPoint(): GeoPoint {
        val t = _uiState.value.telemetry
        return GeoPoint(t.latitude, t.longitude)
    }

    fun openDestinationScreen() {
        openScreen(AppScreen.DESTINATION)
        if (_uiState.value.screen != AppScreen.DESTINATION) return
        sdkManager.refreshNaviPlaces()
        showPlaceList(PlaceListMode.RECENT)
    }

    /** 최근·즐겨찾기·충전소 목록으로 바꾼다. 검색 목록은 [searchPlaces]가 만든다. */
    fun showPlaceList(mode: PlaceListMode) {
        if (mode == PlaceListMode.CHARGERS) {
            searchChargers()
            return
        }
        searchJob?.cancel()
        val places = _uiState.value.places
        val (list, empty) = when (mode) {
            PlaceListMode.FAVORITES ->
                (listOfNotNull(places.home, places.work) + places.favorites) to "즐겨찾기가 없어요. 검색한 곳을 저장해 보세요"
            PlaceListMode.SEARCH -> _uiState.value.placeSearch.results to "가고 싶은 곳을 검색해 보세요"
            else -> places.recents to "최근 목적지가 없어요"
        }
        val here = carPoint()
        _uiState.update {
            it.copy(
                placeSearch = it.placeSearch.copy(
                    mode = mode,
                    results = list.map { p -> p.copy(distanceM = GeoMath.distanceM(here, p.point)) },
                    loading = false,
                    offline = false,
                    message = if (list.isEmpty()) empty else null
                )
            )
        }
    }

    fun searchPlaces(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        searchJob?.cancel()
        _uiState.update {
            it.copy(placeSearch = PlaceSearchUi(mode = PlaceListMode.SEARCH, query = q, loading = true))
        }
        searchJob = viewModelScope.launch {
            val result = placeRepository.search(q, carPoint())
            _uiState.update {
                it.copy(
                    placeSearch = it.placeSearch.copy(
                        results = result.places,
                        loading = false,
                        offline = result.offline,
                        message = when {
                            result.places.isNotEmpty() -> null
                            result.offline -> "인터넷에 연결되지 않아 찾지 못했어요"
                            else -> "찾는 곳이 없어요"
                        }
                    )
                )
            }
        }
    }

    fun searchChargers() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(placeSearch = it.placeSearch.copy(mode = PlaceListMode.CHARGERS, results = emptyList(), loading = true, message = null))
        }
        searchJob = viewModelScope.launch {
            val found = placeRepository.nearbyChargers(carPoint())
            _uiState.update {
                it.copy(
                    placeSearch = it.placeSearch.copy(
                        results = found.orEmpty(),
                        loading = false,
                        offline = found == null,
                        message = when {
                            found == null -> "충전소 정보를 받지 못했어요"
                            found.isEmpty() -> "5km 안에 충전소가 없어요"
                            else -> null
                        }
                    )
                )
            }
        }
    }

    /** 집·회사 바로가기. 저장된 곳이 없으면 저장하는 방법을 알려 준다. */
    fun goToSavedPlace(kind: PlaceKind) {
        val places = _uiState.value.places
        val place = when (kind) {
            PlaceKind.HOME -> places.home
            PlaceKind.WORK -> places.work
            else -> null
        }
        if (place != null) {
            startGuidance(place)
            return
        }
        val label = if (kind == PlaceKind.HOME) "집" else "회사"
        _uiState.update {
            it.copy(placeSearch = it.placeSearch.copy(message = "${label}이 아직 없어요. 검색한 곳에서 '${label}로'를 눌러 저장해요"))
        }
    }

    /** 온길 지도에 [place]까지 경로를 그리고 안내를 시작한다. */
    fun startGuidance(place: NavPlace) {
        if (!canBrowseNow()) return
        loadRoute(carPoint(), place.point)
        rememberRecent(place)
        _uiState.update {
            it.copy(
                destinationName = place.name,
                screen = AppScreen.HOME,
                toastBanner = "${place.name}까지 안내를 시작해요"
            )
        }
    }

    /** 기본 내비에도 같은 목적지로 길안내를 넘긴다. 온길 지도는 기본 내비의 회전 안내(TBT)를 받아 따라간다. */
    fun handOffToNavi(place: NavPlace) {
        if (!canBrowseNow()) return
        val ok = sdkManager.requestNaviRoute(place)
        if (!ok) {
            _uiState.update { it.copy(placeSearch = it.placeSearch.copy(message = "기본 내비에 연결하지 못했어요")) }
            return
        }
        startGuidance(place)
        _uiState.update { it.copy(toastBanner = "기본 내비에도 ${place.name} 안내를 넘겼어요") }
    }

    fun openNaviSearch(query: String) {
        if (!sdkManager.openNaviSearch(query.trim())) {
            _uiState.update { it.copy(placeSearch = it.placeSearch.copy(message = "기본 내비를 열지 못했어요")) }
        }
    }

    fun openNaviChargers() {
        if (!sdkManager.openNaviChargers()) {
            _uiState.update { it.copy(placeSearch = it.placeSearch.copy(message = "기본 내비를 열지 못했어요")) }
        }
    }

    /** 검색한 곳을 집·회사·즐겨찾기로 저장한다. */
    fun savePlace(place: NavPlace, kind: PlaceKind) {
        val stored = place.copy(kind = kind, distanceM = null)
        localPlaces = when (kind) {
            PlaceKind.HOME -> localPlaces.copy(home = stored)
            PlaceKind.WORK -> localPlaces.copy(work = stored)
            else -> localPlaces.copy(favorites = SavedPlaces.dedupe(listOf(stored) + localPlaces.favorites))
        }
        settingsStore.savePlaces(localPlaces)
        refreshPlaces()
        val label = when (kind) {
            PlaceKind.HOME -> "집으로"
            PlaceKind.WORK -> "회사로"
            else -> "즐겨찾기에"
        }
        _uiState.update { it.copy(placeSearch = it.placeSearch.copy(message = "${place.name}을(를) ${label} 저장했어요")) }
    }

    private fun rememberRecent(place: NavPlace) {
        val recent = place.copy(kind = PlaceKind.RECENT, distanceM = null)
        localPlaces = localPlaces.copy(
            recents = SavedPlaces.dedupe(listOf(recent) + localPlaces.recents).take(SavedPlaces.MAX_RECENTS)
        )
        settingsStore.savePlaces(localPlaces)
        refreshPlaces()
    }

    private fun refreshPlaces() {
        _uiState.update { it.copy(places = SavedPlaces.merge(naviPlaces, localPlaces)) }
    }

    override fun onNaviPlaces(places: SavedPlaces) {
        viewModelScope.launch {
            naviPlaces = places
            refreshPlaces()
            val s = _uiState.value
            val mode = s.placeSearch.mode
            if (s.screen == AppScreen.DESTINATION && (mode == PlaceListMode.RECENT || mode == PlaceListMode.FAVORITES)) {
                showPlaceList(mode)
            }
        }
    }

    // --- 지도 시점 ---

    fun toggleMap3d() {
        if (!canBrowseNow()) return
        val next = !_uiState.value.map3d
        settingsStore.saveMap3d(next)
        _uiState.update { it.copy(map3d = next) }
    }

    fun setDriveView(view: DriveView) {
        settingsStore.saveDriveView(view)
        _uiState.update { it.copy(driveView = view) }
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

            val nowMs = SystemClock.elapsedRealtime()
            val curState = _uiState.value
            val speedSample = signalTracker.onLocationSample(
                lat = lat,
                lng = lng,
                timestampMs = nowMs,
                brakePressed = curState.telemetry.brakePressed
            )
            val effectiveSpeed = speedKmh ?: speedSample?.speedKmh ?: curState.telemetry.speedKmh
            val effectiveHeading = headingDeg ?: curState.telemetry.headingDeg

            var updatedTelemetry = curState.telemetry.copy(
                latitude = lat,
                longitude = lng,
                // 정차/저속에서는 방위각을 모르므로 이전 값을 유지해 지도가 휙 돌지 않게 한다.
                headingDeg = effectiveHeading,
                speedKmh = effectiveSpeed
            )

            // 1) 급가속/급제동 실시간 감지 및 Smooth 보너스 평가
            if (speedSample != null && (speedSample.isHarshBrake || speedSample.isHarshAccel)) {
                val harshStep = evaluationEngine.onHarshDrivingDetected(
                    currentState = _uiState.value.evaluation,
                    isHarshBrake = speedSample.isHarshBrake,
                    deltaSpeedKmhPerSec = speedSample.accelKmhPerSec
                )
                applyEvaluationResult(harshStep, updatedTelemetry)
            } else if (speedSample != null && speedSample.deltaKm > 0f) {
                // Odometer 콜백이 오지 않는 환경에서도 실제 GPS 이동 거리로 Safe/Smooth 구간을 누적한다.
                val distStep = evaluationEngine.onDistanceDriven(
                    currentState = _uiState.value.evaluation,
                    deltaKm = speedSample.deltaKm,
                    currentSpeedKmh = effectiveSpeed
                )
                updatedTelemetry = updatedTelemetry.copy(
                    odometerKm = updatedTelemetry.odometerKm + speedSample.deltaKm,
                    sessionDrivenKm = updatedTelemetry.sessionDrivenKm + speedSample.deltaKm
                )
                applyEvaluationResult(distStep, updatedTelemetry)
            } else {
                _uiState.update { it.copy(telemetry = updatedTelemetry) }
            }

            // 2) 방향지시등이 켜져 있는 동안 방위각 변화(>= 25°) 실시간 검증
            val activeSig = _uiState.value.telemetry.turnSignal
            if (activeSig == TurnSignalState.LEFT || activeSig == TurnSignalState.RIGHT) {
                val heldSec = signalTracker.currentSignalHoldSeconds(nowMs)
                val turnStep = evaluationEngine.onTurnSignalAndHeadingChanged(
                    currentState = _uiState.value.evaluation,
                    newSignal = activeSig,
                    currentHeadingDeg = effectiveHeading,
                    holdDurationSec = heldSec
                )
                applyEvaluationResult(turnStep, _uiState.value.telemetry)
            }

            publishNav()
            presentNextNoteIfIdle()
        }
    }

    override fun onVehicleGearChanged(gear: GearState) {
        viewModelScope.launch {
            shiftGear(gear)
        }
    }

    override fun onVehicleTurnSignalChanged(signal: TurnSignalState) {
        viewModelScope.launch {
            val nowMs = SystemClock.elapsedRealtime()
            val heldSec = signalTracker.onTurnSignalChanged(signal, nowMs)
            val current = _uiState.value
            val step = evaluationEngine.onTurnSignalAndHeadingChanged(
                currentState = current.evaluation,
                newSignal = signal,
                currentHeadingDeg = current.telemetry.headingDeg,
                holdDurationSec = heldSec
            )
            applyEvaluationResult(
                stepResult = step,
                newTelemetry = current.telemetry.copy(turnSignal = signal)
            )
        }
    }

    override fun onVehicleBrakeChanged(pressed: Boolean) {
        viewModelScope.launch {
            _uiState.update { state ->
                state.copy(telemetry = state.telemetry.copy(brakePressed = pressed))
            }
        }
    }

    override fun onVehicleOdometerChanged(odometerKm: Double) {
        viewModelScope.launch {
            val deltaKm = signalTracker.onOdometerUpdated(odometerKm)
            val current = _uiState.value
            if (deltaKm > 0f) {
                lastOdometerCreditAtMs = SystemClock.elapsedRealtime()
                val step = evaluationEngine.onDistanceDriven(
                    currentState = current.evaluation,
                    deltaKm = deltaKm,
                    currentSpeedKmh = current.telemetry.speedKmh
                )
                applyEvaluationResult(
                    stepResult = step,
                    newTelemetry = current.telemetry.copy(
                        odometerKm = odometerKm,
                        sessionDrivenKm = current.telemetry.sessionDrivenKm + deltaKm
                    )
                )
                presentNextNoteIfIdle()
            } else {
                _uiState.update { state ->
                    state.copy(telemetry = state.telemetry.copy(odometerKm = odometerKm))
                }
            }
        }
    }

    override fun onVehicleHeadlampChanged(headlampOn: Boolean) {
        viewModelScope.launch {
            _uiState.update { state ->
                val newTelemetry = state.telemetry.copy(headlampOn = headlampOn)
                val speech = GleoAiAssistant.generateContextualPetSpeech(
                    roadName = newTelemetry.roadName,
                    speedKmh = newTelemetry.speedKmh,
                    gear = newTelemetry.gear,
                    isNightOrHeadlampOn = headlampOn
                )
                state.copy(
                    telemetry = newTelemetry,
                    pet = state.pet.copy(currentSpeech = speech),
                    toastBanner = if (headlampOn) {
                        "차량 전조등(LowBeam) ON 감지 · 자동 야간 지도로 전환합니다."
                    } else {
                        "차량 전조등 OFF 감지 · 주간/태양고도 기준 지도로 복귀합니다."
                    }
                )
            }
        }
    }

    /** 시연용: 차량 전조등(LowBeam) ON/OFF 토글 */
    fun toggleHeadlampSimulation() {
        onVehicleHeadlampChanged(!_uiState.value.telemetry.headlampOn)
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
        viewModelScope.launch {
            val state = _uiState.value
            val intent = GleoAiAssistant.parseVoiceCommand(
                utterance = recognizedText,
                isComposeDialogOpen = state.showVoiceComposeDialog,
                hasIncomingOrHeldNote = state.activeIncomingNote != null || state.heldNote != null,
                isSpeakingTts = state.isTtsSpeaking
            )
            when (intent) {
                GleoAiAssistant.VoiceCommandIntent.APPROVE_LISTEN -> {
                    approveIncomingNoteByVoice()
                    return@launch
                }
                GleoAiAssistant.VoiceCommandIntent.SEND_LIKE -> {
                    val targetId = state.heldNote?.id ?: state.activeIncomingNote?.id
                    if (targetId != null) {
                        sendTodakReaction(targetId)
                        return@launch
                    }
                }
                GleoAiAssistant.VoiceCommandIntent.STOP_OR_SKIP -> {
                    stopTtsPlayback()
                    return@launch
                }
                GleoAiAssistant.VoiceCommandIntent.START_COMPOSE -> {
                    openVoiceNoteComposer(force = true)
                    return@launch
                }
                GleoAiAssistant.VoiceCommandIntent.NOTE_DICTATION -> Unit
            }

            val result = AiNoteModerator.analyzeAndFilter(recognizedText)
            _uiState.update {
                it.copy(
                    draftVoiceText = recognizedText,
                    lastModerationResult = result
                )
            }
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
            triggerMannerSparkle()
        }
    }

    private fun triggerMannerSparkle() {
        sparkleClearJob?.cancel()
        _uiState.update {
            it.copy(
                mannerSparkleActive = true,
                pet = it.pet.copy(currentSpeech = "방향지시등 매너운전 성공! 제로가 반짝반짝 기뻐해요 ✨")
            )
        }
        sparkleClearJob = viewModelScope.launch {
            delay(4000L)
            _uiState.update { it.copy(mannerSparkleActive = false) }
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
     * 시연용: 시뮬레이션을 출발 지점(0m)과 초기 상태로 되돌린다.
     * 사용자 설정(자동 TTS 재생, 출퇴근 밴드 필터, 지도 테마)은 그대로 유지한다.
     */
    fun restartSimulation() {
        stopTtsPlayback()
        recordingJob?.cancel()
        sdkManager.stopVoiceNoteCapture()
        signalHoldJob?.cancel()
        releaseJob?.cancel()

        progressM = 0.0
        realLocationSeen = false
        liveTurn = null
        liveTurnDistM = 0.0
        liveTurnAtMs = 0L

        val initialNotes = initialVoiceNotes()
        _uiState.update { current ->
            AppUiState(
                sdkStatus = current.sdkStatus,
                mapTheme = current.mapTheme,
                autoPlayTts = current.autoPlayTts,
                showOnlyCommuteBand = current.showOnlyCommuteBand,
                map3d = current.map3d,
                driveView = current.driveView,
                destinationName = current.destinationName,
                places = current.places,
                console = current.console,
                notes = initialNotes,
                activeIncomingNote = NoteInbox.nextIncoming(
                    initialNotes,
                    commuteOnly = current.showOnlyCommuteBand
                ),
                toastBanner = "시뮬레이션을 출발 지점부터 다시 시작합니다."
            )
        }
        publishNav()
    }

    /**
     * 5. 기어 전환 (P 전환 시 리포트 모달을 강제로 띄우지 않고, 오늘의 주행 기록과 바퀴자취만 자동 저장해 두며
     *    운전자가 정차 화면 상단의 리포트 버튼을 눌렀을 때만 열리도록 한다.)
     */
    fun shiftGear(newGear: GearState) {
        val justParked = newGear == GearState.P && _uiState.value.telemetry.gear != GearState.P
        _uiState.update { state ->
            val newSpeed = if (newGear == GearState.P) 0f else 62f
            val updatedTracks = if (newGear == GearState.P && state.telemetry.gear != GearState.P) {
                val gradeLabel = when {
                    state.evaluation.completedMannerTurns > 0 -> "Manner 깜빡이 ${state.evaluation.completedMannerTurns}회"
                    state.evaluation.completedSmoothWindows > 0 -> "Smooth ${state.evaluation.completedSmoothWindows * 2}km 완주"
                    else -> "Safe ${state.evaluation.completedSafeBlocks}km 달성"
                }
                val newTrack = TireTrackNode(
                    segmentName = "${state.telemetry.roadName} 주차 완료",
                    timeLabel = "방금",
                    mannerGrade = gradeLabel,
                    noteCount = state.parkingReport.todayNotesLeft,
                    todakReceived = state.parkingReport.todayTodakReceived,
                    xFraction = 0.88f,
                    yFraction = 0.24f
                )
                if (state.tireTracks.lastOrNull()?.segmentName == newTrack.segmentName) {
                    state.tireTracks
                } else {
                    state.tireTracks + newTrack
                }
            } else {
                state.tireTracks
            }
            val updatedReport = if (newGear == GearState.P) {
                state.parkingReport.copy(
                    todayMannerTurns = state.evaluation.completedMannerTurns,
                    todaySmoothWindows = state.evaluation.completedSmoothWindows
                )
            } else {
                state.parkingReport
            }
            state.copy(
                telemetry = state.telemetry.copy(
                    gear = newGear,
                    speedKmh = newSpeed,
                    brakePressed = newGear == GearState.P
                ),
                // 주차 시 리포트를 강제로 팝업하지 않고 백그라운드에 기록만 해 둔다.
                showParkingReportModal = false,
                parkingReport = updatedReport,
                tireTracks = updatedTracks,
                toastBanner = if (newGear == GearState.P) {
                    "주차(P) 전환 · 오늘의 주행 리포트가 기록되었습니다. 상단 [리포트]에서 언제든 확인하세요."
                } else {
                    "${newGear.label}단 전환 · 주행 중에는 음성 쪽지 안전 모드로 동작합니다."
                }
            )
        }
        if (justParked) suggestChargersIfLow()
    }

    /** 배터리나 주행가능거리가 모자란 채로 주차하면, 주행 중 경고에서 약속한 대로 가까운 충전소 목록을 연다. */
    private fun suggestChargersIfLow() {
        val state = _uiState.value
        val console = state.console ?: return
        if (!RangeAdvisor.needsCharge(console.batteryPct, console.rangeKm, state.nav.remainingM)) return
        openScreen(AppScreen.DESTINATION)
        if (_uiState.value.screen != AppScreen.DESTINATION) return
        showPlaceList(PlaceListMode.CHARGERS)
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
            // 달리는 중에는 화면 글자를 띄우지 않고 음성으로만 알린다. 쪽지를 읽는 중이면 끊지 않는다.
            if (!_uiState.value.isTtsSpeaking) sdkManager.speakAnonymousNote(DRIVING_LOCK_VOICE)
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
                heartReactionBanner = "♥ ${target.authorPetName}에게 하트가 전달되었어요! (+10 EXP · +15 P)",
                toastBanner = "좋아요를 전했어요 (+10 EXP, +15 P)"
            )
        }
        bannerClearJob?.cancel()
        bannerClearJob = viewModelScope.launch {
            delay(4500L)
            _uiState.update { it.copy(heartReactionBanner = null) }
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
        publishNav()
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
                toastBanner = "[${item.brand}] '${item.title}' 교환 시연 완료 (예시 화면 · 제휴 연계 예정)"
            )
        }
    }

    /**
     * 발표/시연 자료(1~9번) 촬영 및 검증을 위한 통합 시나리오 실행기.
     * `adb shell am start -n com.example.hyundai/.MainActivity --es scene <scene_id>` 또는 제어바에서 호출한다.
     */
    fun runShowcaseScenario(scene: String, hideDrawer: Boolean = false) {
        showcaseJob?.cancel()
        recordingJob?.cancel()
        signalHoldJob?.cancel()
        ttsTimerJob?.cancel()
        releaseJob?.cancel()
        sparkleClearJob?.cancel()
        bannerClearJob?.cancel()
        sdkManager.stopSpeaking()
        sdkManager.stopVoiceNoteCapture()

        val baseNotes = initialVoiceNotes()
        when (scene) {
            "1_home" -> {
                _uiState.update {
                    it.copy(
                        hideSimulatorDrawer = hideDrawer,
                        screen = AppScreen.HOME,
                        telemetry = it.telemetry.copy(gear = GearState.P, speedKmh = 0f, turnSignal = TurnSignalState.OFF, brakePressed = true),
                        pet = initialPet(),
                        totalExp = 340,
                        warmthPoints = 1850,
                        remainingNoteTickets = 3,
                        maxDailyBaseTickets = 3,
                        bonusTicketsEarnedToday = 1,
                        notes = baseNotes,
                        activeIncomingNote = baseNotes.first(),
                        heldNote = null,
                        isTtsSpeaking = false,
                        showVoiceComposeDialog = false,
                        showParkingReportModal = false,
                        showRewardShopModal = false,
                        mannerSparkleActive = false,
                        voiceHudPrompt = null,
                        voiceHudUserUtterance = null,
                        heartReactionBanner = null,
                        levelUpBanner = null,
                        gearTransitionBanner = null
                    )
                }
            }

            "2_drive_start" -> {
                showcaseJob = viewModelScope.launch {
                    // 1) 주차(P) 홈 화면에서 시작
                    _uiState.update {
                        it.copy(
                            hideSimulatorDrawer = hideDrawer,
                            autoPlayTts = false,
                            screen = AppScreen.HOME,
                            telemetry = it.telemetry.copy(gear = GearState.P, speedKmh = 0f, turnSignal = TurnSignalState.OFF, brakePressed = true),
                            pet = initialPet(),
                            remainingNoteTickets = 3,
                            heldNote = null,
                            isTtsSpeaking = false,
                            showVoiceComposeDialog = false,
                            showParkingReportModal = false,
                            showRewardShopModal = false,
                            mannerSparkleActive = false,
                            voiceHudPrompt = null,
                            voiceHudUserUtterance = null,
                            heartReactionBanner = null,
                            levelUpBanner = null,
                            gearTransitionBanner = null
                        )
                    }
                    delay(2200L)
                    // 2) 기어 P -> D 변경 및 주행 화면 전환
                    _uiState.update {
                        it.copy(
                            telemetry = it.telemetry.copy(gear = GearState.D, speedKmh = 62f, brakePressed = false),
                            gearTransitionBanner = "기어 P → D 변경 · 주행 중 최소 정보 화면으로 전환"
                        )
                    }
                    delay(2200L)
                    // 3) 배너 닫고 간결한 주행 화면(제로 아이콘 + 핵심 내비만 표시) 유지
                    _uiState.update {
                        it.copy(gearTransitionBanner = null)
                    }
                }
            }

            "3_manner_drive" -> {
                showcaseJob = viewModelScope.launch {
                    val baseHeading = _uiState.value.telemetry.headingDeg
                    _uiState.update {
                        it.copy(
                            hideSimulatorDrawer = hideDrawer,
                            autoPlayTts = false,
                            screen = AppScreen.HOME,
                            telemetry = it.telemetry.copy(gear = GearState.D, speedKmh = 64f, turnSignal = TurnSignalState.OFF, brakePressed = false),
                            pet = initialPet().copy(currentSpeech = "안전하게 주행 중이에요."),
                            heldNote = null,
                            isTtsSpeaking = false,
                            showVoiceComposeDialog = false,
                            showParkingReportModal = false,
                            showRewardShopModal = false,
                            mannerSparkleActive = false,
                            voiceHudPrompt = null,
                            voiceHudUserUtterance = null,
                            heartReactionBanner = null,
                            gearTransitionBanner = null
                        )
                    }
                    delay(1200L)
                    // 방향지시등 우측 점등
                    val step1 = evaluationEngine.onTurnSignalAndHeadingChanged(
                        currentState = _uiState.value.evaluation,
                        newSignal = TurnSignalState.RIGHT,
                        currentHeadingDeg = baseHeading,
                        holdDurationSec = 0.8f
                    )
                    _uiState.update {
                        it.copy(
                            telemetry = it.telemetry.copy(turnSignal = TurnSignalState.RIGHT),
                            evaluation = step1.updatedState,
                            pet = it.pet.copy(currentSpeech = "우측 방향지시등 점등 감지! 차선 변경을 확인하고 있어요.")
                        )
                    }
                    delay(1300L)
                    val step2 = evaluationEngine.onTurnSignalAndHeadingChanged(
                        currentState = _uiState.value.evaluation,
                        newSignal = TurnSignalState.RIGHT,
                        currentHeadingDeg = baseHeading,
                        holdDurationSec = 1.7f
                    )
                    _uiState.update {
                        it.copy(
                            evaluation = step2.updatedState,
                            pet = it.pet.copy(currentSpeech = "깜빡이 1.7초 유지 완료! 부드럽게 방향을 바꾸고 있어요.")
                        )
                    }
                    delay(1200L)
                    // 방향 전환 완료 -> 매너운전 기록 및 제로 반짝임 반응
                    val newHeading = (baseHeading + 30f) % 360f
                    val step3 = evaluationEngine.onTurnSignalAndHeadingChanged(
                        currentState = _uiState.value.evaluation,
                        newSignal = TurnSignalState.RIGHT,
                        currentHeadingDeg = newHeading,
                        holdDurationSec = 2.3f
                    )
                    _uiState.update {
                        it.copy(
                            telemetry = it.telemetry.copy(turnSignal = TurnSignalState.RIGHT, headingDeg = newHeading),
                            evaluation = step3.updatedState,
                            totalExp = it.totalExp + 15,
                            warmthPoints = it.warmthPoints + 10,
                            mannerSparkleActive = true,
                            pet = it.pet.copy(
                                currentExp = (it.pet.currentExp + 15).coerceAtMost(it.pet.maxExp - 1),
                                currentSpeech = "매너운전 기록 완료! 제로가 반짝반짝 기뻐해요 ✨ (+15 EXP · +10 P)"
                            )
                        )
                    }
                }
            }

            "3_manner_static" -> {
                val baseHeading = _uiState.value.telemetry.headingDeg
                val step1 = evaluationEngine.onTurnSignalAndHeadingChanged(
                    currentState = DrivingEvaluationState(),
                    newSignal = TurnSignalState.RIGHT,
                    currentHeadingDeg = baseHeading,
                    holdDurationSec = 1.6f
                )
                val step2 = evaluationEngine.onTurnSignalAndHeadingChanged(
                    currentState = step1.updatedState,
                    newSignal = TurnSignalState.RIGHT,
                    currentHeadingDeg = (baseHeading + 30f) % 360f,
                    holdDurationSec = 2.2f
                )
                _uiState.update {
                    it.copy(
                        hideSimulatorDrawer = hideDrawer,
                        autoPlayTts = false,
                        screen = AppScreen.HOME,
                        telemetry = it.telemetry.copy(gear = GearState.D, speedKmh = 64f, turnSignal = TurnSignalState.RIGHT, brakePressed = false),
                        evaluation = step2.updatedState,
                        mannerSparkleActive = true,
                        pet = initialPet().copy(
                            currentExp = 135,
                            currentSpeech = "매너운전 기록 완료! 제로가 반짝반짝 기뻐해요 ✨ (+15 EXP · +10 P)"
                        ),
                        voiceHudPrompt = null,
                        voiceHudUserUtterance = null,
                        heartReactionBanner = null,
                        gearTransitionBanner = null
                    )
                }
            }

            "4_5_note_flow", "4_note_receive", "5_note_like" -> {
                showcaseJob = viewModelScope.launch {
                    val targetNote = baseNotes.first()
                    progressM = 130.0
                    publishNav()
                    _uiState.update {
                        it.copy(
                            hideSimulatorDrawer = hideDrawer,
                            autoPlayTts = false,
                            screen = AppScreen.HOME,
                            telemetry = it.telemetry.copy(gear = GearState.D, speedKmh = 60f, turnSignal = TurnSignalState.OFF, brakePressed = false),
                            notes = baseNotes,
                            activeIncomingNote = targetNote,
                            heldNote = null,
                            isTtsSpeaking = false,
                            showVoiceComposeDialog = false,
                            showParkingReportModal = false,
                            showRewardShopModal = false,
                            mannerSparkleActive = false,
                            voiceHudPrompt = null,
                            voiceHudUserUtterance = null,
                            heartReactionBanner = null,
                            gearTransitionBanner = null,
                            pet = initialPet().copy(currentSpeech = "전방에 다른 운전자가 남긴 쪽지 구간이 있어요.")
                        )
                    }
                    delay(1000L)
                    // 1) 쪽지 구간 진입 안내
                    progressM = 165.0
                    publishNav()
                    val promptText = "이 구간에 다른 운전자가 남긴 쪽지가 있어요. 들어볼까요?"
                    sdkManager.speakAnonymousNote(promptText)
                    _uiState.update {
                        it.copy(
                            voiceHudPrompt = promptText,
                            voiceHudUserUtterance = null,
                            pet = it.pet.copy(currentSpeech = promptText)
                        )
                    }
                    delay(2600L)
                    // 2) 운전자 "응" 응답
                    _uiState.update {
                        it.copy(voiceHudUserUtterance = "응")
                    }
                    delay(1200L)
                    // 3) 쪽지 음성 재생 ("제로가 남긴 쪽지예요. 여기만 지나면 금방 풀려요. 오늘도 힘내세요!")
                    val fullSpeech = "${NoteInbox.introLine(targetNote.authorPetName)} ${targetNote.displayText}"
                    sdkManager.speakAnonymousNote(fullSpeech)
                    val listenedNote = targetNote.copy(isListenedByMe = true, listenerCount = targetNote.listenerCount + 1)
                    _uiState.update { state ->
                        state.copy(
                            notes = state.notes.map { if (it.id == targetNote.id) listenedNote else it },
                            activeIncomingNote = listenedNote,
                            heldNote = listenedNote,
                            isTtsSpeaking = true,
                            ttsSpeakingText = targetNote.displayText,
                            voiceHudPrompt = "“$fullSpeech”",
                            voiceHudUserUtterance = "응",
                            pet = state.pet.copy(currentSpeech = fullSpeech)
                        )
                    }
                    delay(4200L)
                    if (scene == "4_note_receive") {
                        _uiState.update {
                            it.copy(
                                isTtsSpeaking = false,
                                voiceHudPrompt = "쪽지 재생 완료 · “$fullSpeech”"
                            )
                        }
                        return@launch
                    }
                    // 4) 재생 종료 후 운전자 "좋아요" 반응 (5번 시나리오)
                    _uiState.update {
                        it.copy(
                            isTtsSpeaking = false,
                            voiceHudPrompt = "쪽지를 다 들었어요. 마음을 전하려면 말씀해 주세요.",
                            voiceHudUserUtterance = "좋아요"
                        )
                    }
                    delay(1200L)
                    sendTodakReaction(targetNote.id)
                    sdkManager.speakAnonymousNote("제로에게 따뜻한 하트를 전달했어요.")
                    _uiState.update {
                        it.copy(
                            voiceHudPrompt = "하트 전달 완료! 작성자와 나에게 경험치와 포인트가 적립되었어요.",
                            voiceHudUserUtterance = "좋아요",
                            heartReactionBanner = "♥ 제로에게 하트가 전달되었어요! (+10 EXP · +15 P)"
                        )
                    }
                }
            }

            "6_voice_compose" -> {
                showcaseJob = viewModelScope.launch {
                    val sampleNote = "비 오니까 다들 천천히 가요!"
                    _uiState.update {
                        it.copy(
                            hideSimulatorDrawer = hideDrawer,
                            autoPlayTts = false,
                            screen = AppScreen.HOME,
                            telemetry = it.telemetry.copy(gear = GearState.D, speedKmh = 56f, turnSignal = TurnSignalState.OFF, brakePressed = false),
                            showVoiceComposeDialog = false,
                            showParkingReportModal = false,
                            showRewardShopModal = false,
                            voiceHudPrompt = "주행 중 음성 명령 대기 중",
                            voiceHudUserUtterance = "쪽지 남겨줘",
                            heartReactionBanner = null
                        )
                    }
                    delay(1200L)
                    // 음성 녹음 시작
                    _uiState.update {
                        it.copy(
                            showVoiceComposeDialog = true,
                            isRecordingStt = true,
                            recordingSecondsLeft = 9,
                            draftVoiceText = "비 오니까...",
                            voiceHudPrompt = "음성으로 쪽지를 작성하고 있어요",
                            voiceHudUserUtterance = "쪽지 남겨줘"
                        )
                    }
                    delay(1300L)
                    _uiState.update {
                        it.copy(
                            isRecordingStt = true,
                            recordingSecondsLeft = 8,
                            draftVoiceText = sampleNote
                        )
                    }
                    delay(1400L)
                    // AI 안전 필터 통과 및 검수 완료 화면
                    val mod = AiNoteModerator.analyzeAndFilter(sampleNote)
                    _uiState.update {
                        it.copy(
                            isRecordingStt = false,
                            recordingSecondsLeft = 10,
                            draftVoiceText = sampleNote,
                            lastModerationResult = mod
                        )
                    }
                    delay(1800L)
                    // 등록 완료 안내
                    publishVoiceNote(asCommuteBandNote = true)
                    sdkManager.speakAnonymousNote("쪽지 등록이 완료되었어요. 비 오니까 다들 천천히 가요!")
                    _uiState.update {
                        it.copy(
                            voiceHudPrompt = "음성 쪽지 등록 완료 · “$sampleNote”",
                            voiceHudUserUtterance = "쪽지 남겨줘",
                            heartReactionBanner = "✓ 쪽지 등록 완료! “$sampleNote” (+15 EXP · +20 P)"
                        )
                    }
                }
            }

            "6a_composing" -> {
                val sampleNote = "비 오니까 다들 천천히 가요!"
                _uiState.update {
                    it.copy(
                        hideSimulatorDrawer = hideDrawer,
                        autoPlayTts = false,
                        screen = AppScreen.HOME,
                        telemetry = it.telemetry.copy(gear = GearState.D, speedKmh = 56f, turnSignal = TurnSignalState.OFF, brakePressed = false),
                        showVoiceComposeDialog = true,
                        isRecordingStt = true,
                        recordingSecondsLeft = 8,
                        draftVoiceText = sampleNote,
                        voiceHudPrompt = "음성 쪽지 녹음 중",
                        voiceHudUserUtterance = "쪽지 남겨줘",
                        heartReactionBanner = null
                    )
                }
            }

            "6b_published" -> {
                val sampleNote = "비 오니까 다들 천천히 가요!"
                val mod = AiNoteModerator.analyzeAndFilter(sampleNote)
                _uiState.update {
                    it.copy(
                        hideSimulatorDrawer = hideDrawer,
                        autoPlayTts = false,
                        screen = AppScreen.HOME,
                        telemetry = it.telemetry.copy(gear = GearState.D, speedKmh = 56f, turnSignal = TurnSignalState.OFF, brakePressed = false),
                        showVoiceComposeDialog = false,
                        isRecordingStt = false,
                        draftVoiceText = sampleNote,
                        lastModerationResult = mod,
                        remainingNoteTickets = 2,
                        voiceHudPrompt = "음성 쪽지 등록 완료! 도로 위 안전 정보(3시간 유지)로 공유되었어요.",
                        voiceHudUserUtterance = "비 오니까 다들 천천히 가요!",
                        heartReactionBanner = "✓ 음성 쪽지 등록 완료! “$sampleNote” (+15 EXP · +20 P)"
                    )
                }
            }

            "7_driving_report" -> {
                _uiState.update {
                    it.copy(
                        hideSimulatorDrawer = hideDrawer,
                        screen = AppScreen.HOME,
                        telemetry = it.telemetry.copy(gear = GearState.P, speedKmh = 0f, turnSignal = TurnSignalState.OFF, brakePressed = true),
                        evaluation = DrivingEvaluationState(
                            completedSafeBlocks = 8,
                            completedSmoothWindows = 3,
                            completedMannerTurns = 4
                        ),
                        parkingReport = ParkingReportSummary(
                            todayDrivenKm = 12.4,
                            todayMannerTurns = 4,
                            todaySmoothWindows = 3,
                            todayNotesLeft = 2,
                            todayTodakReceived = 19
                        ),
                        showParkingReportModal = true,
                        showRewardShopModal = false,
                        showVoiceComposeDialog = false
                    )
                }
            }

            "8_pet_growth" -> {
                showcaseJob = viewModelScope.launch {
                    // Lv.1 아기 제로 (PUPPY)에서 시작
                    val lv1Pet = initialPet().copy(
                        level = 1,
                        stageName = "아기 · 호기심 대장",
                        currentExp = 115,
                        maxExp = 140,
                        currentSpeech = "오늘 주행으로 모은 경험치가 들어오고 있어요! (115 / 140 EXP)"
                    )
                    _uiState.update {
                        it.copy(
                            hideSimulatorDrawer = hideDrawer,
                            screen = AppScreen.HOME,
                            telemetry = it.telemetry.copy(gear = GearState.P, speedKmh = 0f, brakePressed = true),
                            pet = lv1Pet,
                            totalExp = 295,
                            levelUpBanner = null,
                            showParkingReportModal = false,
                            showRewardShopModal = false,
                            showVoiceComposeDialog = false
                        )
                    }
                    delay(1800L)
                    // 경험치 상승 (135 / 140)
                    _uiState.update {
                        it.copy(
                            pet = lv1Pet.copy(
                                currentExp = 135,
                                currentSpeech = "매너운전 +20 EXP 획득! 거의 다 모았어요!"
                            ),
                            totalExp = 315
                        )
                    }
                    delay(1400L)
                    // Lv.2 성견 제로 (ADULT) 성장!
                    val lv2Pet = initialPet().copy(
                        level = 2,
                        stageName = "새싹기 · 초보 매너러",
                        currentExp = 45,
                        maxExp = 180,
                        currentSpeech = "짜잔! 주행 경험치가 쌓여 Lv.2 성견 제로로 성장했어요! 🎉"
                    )
                    _uiState.update {
                        it.copy(
                            pet = lv2Pet,
                            totalExp = 365,
                            levelUpBanner = "🎉 LEVEL UP! Lv.1 아기 제로 → Lv.2 성견 제로 성장 완료!"
                        )
                    }
                }
            }

            "8a_before" -> {
                val lv1Pet = initialPet().copy(
                    level = 1,
                    stageName = "아기 · 호기심 대장",
                    currentExp = 125,
                    maxExp = 140,
                    currentSpeech = "조금만 더 매너운전 경험치를 모으면 Lv.2 성견 제로로 성장해요!"
                )
                _uiState.update {
                    it.copy(
                        hideSimulatorDrawer = hideDrawer,
                        screen = AppScreen.HOME,
                        telemetry = it.telemetry.copy(gear = GearState.P, speedKmh = 0f, brakePressed = true),
                        pet = lv1Pet,
                        totalExp = 305,
                        levelUpBanner = null,
                        showParkingReportModal = false,
                        showRewardShopModal = false,
                        showVoiceComposeDialog = false
                    )
                }
            }

            "8b_after" -> {
                val lv2Pet = initialPet().copy(
                    level = 2,
                    stageName = "새싹기 · 초보 매너러",
                    currentExp = 45,
                    maxExp = 180,
                    currentSpeech = "짜잔! 주행 경험치가 쌓여 Lv.2 성견 제로로 성장했어요! 🎉"
                )
                _uiState.update {
                    it.copy(
                        hideSimulatorDrawer = hideDrawer,
                        screen = AppScreen.HOME,
                        telemetry = it.telemetry.copy(gear = GearState.P, speedKmh = 0f, brakePressed = true),
                        pet = lv2Pet,
                        totalExp = 365,
                        levelUpBanner = "🎉 LEVEL UP! Lv.1 아기 제로 → Lv.2 성견 제로 성장 완료!",
                        showParkingReportModal = false,
                        showRewardShopModal = false,
                        showVoiceComposeDialog = false
                    )
                }
            }

            "9a_note_reaction" -> {
                _uiState.update {
                    it.copy(
                        hideSimulatorDrawer = hideDrawer,
                        screen = AppScreen.INBOX,
                        initialInboxTabMine = true,
                        telemetry = it.telemetry.copy(gear = GearState.P, speedKmh = 0f, brakePressed = true),
                        notes = baseNotes,
                        showParkingReportModal = false,
                        showRewardShopModal = false,
                        showVoiceComposeDialog = false
                    )
                }
            }

            "9b_reward_shop" -> {
                _uiState.update {
                    it.copy(
                        hideSimulatorDrawer = hideDrawer,
                        screen = AppScreen.HOME,
                        telemetry = it.telemetry.copy(gear = GearState.P, speedKmh = 0f, brakePressed = true),
                        warmthPoints = 1850,
                        showRewardShopModal = true,
                        showParkingReportModal = false,
                        showVoiceComposeDialog = false
                    )
                }
            }
        }
    }

    override fun onCleared() {
        consoleClient.stop()
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

/** 주행 화면 안내 배너가 떠 있는 시간. */
private const val TOAST_VISIBLE_MS = 6_000L

/** 차량 주행거리 콜백이 이 시간 안에 거리를 늘렸으면 시연 주행 거리는 적립하지 않는다. */
private const val ODOMETER_FRESH_MS = 5_000L

/** 이 거리보다 가까운 목적지 변경은 같은 목적지로 보고 경로를 다시 구하지 않는다. */
private const val NEW_DESTINATION_MIN_M = 100.0

/** 실제 위치가 경로에서 이 거리보다 멀면 경로 위의 차량 위치로 쓰지 않는다. */
private const val REAL_LOCATION_MAX_OFF_ROUTE_M = 2_000.0

/** 쪽지 하나가 경로 위에서 다른 색상으로 강조 표시되는 구간의 기본 반경(앞뒤 각 30m = 총 60m 구간). */
private const val NOTE_ZONE_HALF_LENGTH_M = 30.0

/** 달리는 중에 쪽지함·설정을 눌렀을 때 음성으로만 들려 주는 안내. */
private const val DRIVING_LOCK_VOICE = "달리는 중에는 열 수 없어요. 정차한 뒤에 눌러 주세요."

/**
 * 현재 경로([track]) 위에서 같은 방향(`isSameDirection`) 쪽지들이 놓인 구간([NoteRouteZone])을 계산한다.
 * - 실제 GPS 좌표가 경로에서 600m 이내에 있으면 `track.project(pos)` 위치에 매핑하고,
 * - 오프라인 데모 경로처럼 좌표가 다를 때는 `distanceMeters`를 기준으로 경로 위에 자연스럽게 배치한다.
 */
internal fun computeNoteRouteZones(
    track: RouteTrack,
    notes: List<VoiceNote>,
    commuteOnly: Boolean
): List<NoteRouteZone> {
    if (track.totalM < 20.0) return emptyList()
    val filtered = notes.filter {
        it.isSameDirection && (!commuteOnly || it.isCommuteBandOnly)
    }
    return filtered.mapIndexedNotNull { idx, note ->
        val p = GeoPoint(note.latitude, note.longitude)
        val distToRoute = track.distanceToRouteM(p)
        val centerM = when {
            note.distanceMeters > 0 && distToRoute > 120.0 -> note.distanceMeters.toDouble()
            distToRoute <= 600.0 -> track.project(p).let { proj ->
                // 출발점(0m) 바로 위에 몰려 있으면 distanceMeters나 인덱스 간격으로 펼쳐 보여 준다.
                if (proj < 30.0 && note.distanceMeters > 0) note.distanceMeters.toDouble() else proj
            }
            note.distanceMeters > 0 -> note.distanceMeters.toDouble()
            else -> ((idx + 1) * 220.0)
        }.coerceIn(30.0, (track.totalM - 30.0).coerceAtLeast(30.0))

        val startM = (centerM - NOTE_ZONE_HALF_LENGTH_M).coerceAtLeast(0.0)
        val endM = (centerM + NOTE_ZONE_HALF_LENGTH_M).coerceAtMost(track.totalM)
        if (endM - startM < 10.0) null
        else NoteRouteZone(
            noteId = note.id,
            startM = startM,
            endM = endM,
            isSafety = note.category == NoteCategory.SAFETY_3H
        )
    }
}

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
        rawText = "여기만 지나면 금방 풀려요. 오늘도 힘내세요!",
        displayText = "여기만 지나면 금방 풀려요. 오늘도 힘내세요!",
        category = NoteCategory.CHEER_24H,
        moderationStatus = ModerationStatus.APPROVED,
        roadSegmentName = "강변북로 성수대교 동단",
        latitude = 37.5448,
        longitude = 127.0462,
        headingDeg = 82f,
        distanceMeters = 180,
        headingDiffDeg = 3f, // 진행방향 85°와 3° 차이 → 재생 대상!
        createdAtLabel = "12분 전",
        remainingLifeLabel = "23시간 48분 남음",
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
        id = "note_mine_rain",
        authorNickname = "나",
        authorPetName = "제로",
        authorPetLevel = 2,
        rawText = "비 오니까 다들 천천히 가요!",
        displayText = "비 오니까 다들 천천히 가요!",
        category = NoteCategory.SAFETY_3H,
        moderationStatus = ModerationStatus.APPROVED,
        roadSegmentName = "강변북로 성수대교 동단",
        latitude = 37.5447,
        longitude = 127.0458,
        headingDeg = 85f,
        distanceMeters = 0,
        headingDiffDeg = 0f,
        createdAtLabel = "방금 전",
        remainingLifeLabel = "2시간 58분 남음 · 연장됨",
        todakCount = 12,
        listenerCount = 34,
        isReactedByMe = true,
        isListenedByMe = true,
        isMine = true
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
        roadSegmentName = "강변북로 영동대교 북단",
        latitude = 37.5446,
        longitude = 127.0452,
        headingDeg = 85f,
        distanceMeters = 0,
        headingDiffDeg = 0f,
        createdAtLabel = "1시간 전",
        remainingLifeLabel = "1시간 52분 남음 · 연장됨",
        todakCount = 7,
        listenerCount = 19,
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
