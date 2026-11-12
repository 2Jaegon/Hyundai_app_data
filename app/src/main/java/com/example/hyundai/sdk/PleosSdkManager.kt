package com.example.hyundai.sdk

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.BookmarkInfo
import ai.pleos.playground.navi.data.CameraAlert
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.ChargerOperator
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RecentDestinationInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.data.TripInfo
import ai.pleos.playground.navi.data.WaypointArrivedInfo
import ai.pleos.playground.navi.data.WaypointChangedInfo
import ai.pleos.playground.navi.data.WaypointInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType as SttErrorType
import ai.pleos.playground.stt.constant.Mode as SttMode
import ai.pleos.playground.stt.listener.OnServerConnectionListener as SttServerListener
import ai.pleos.playground.stt.listener.ResultListener as SttResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener as TtsEventListener
import ai.pleos.playground.tts.listener.OnServerConnectionListener as TtsServerListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech as AndroidTts
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.example.hyundai.domain.VehicleSignalTracker
import com.example.hyundai.model.GearState
import com.example.hyundai.model.SdkConnectionStatus
import com.example.hyundai.model.TurnSignalState
import com.example.hyundai.nav.RouteRepository
import com.example.hyundai.nav.TurnIcon
import com.example.hyundai.nav.TurnMapping
import java.util.Locale

/**
 * Pleos Connect 5대 공식 SDK 통합 매니저
 * 1. Vehicle SDK (ai.pleos.playground:Vehicle:2.3.2) - Gear, TurnSignal, Brake, Odometer, Light(전조등) 실시간 리스너 연동
 * 2. FusedLocation SDK (ai.pleos.playground:FusedLocation:1.1.3) - 실시간 좌표·방위각(Heading) 및 근접 알림
 * 3. TextToSpeech SDK (ai.pleos.playground:TextToSpeech:3.3.1) - 익명 도로 쪽지 음성 재생 + AudioFocus Ducking + Server/On-Device 지원
 * 4. SpeechToText SDK (ai.pleos.playground:SpeechToText:3.3.1) - 주행 중 10초 핸즈프리 쪽지 녹음 및 음성 승인 명령 인식
 * 5. NaviHelper SDK (ai.pleos.playground:NaviHelper:3.0.5) - 현재 도로명, 제한속도, TBT 회전 안내, 목적지 연동
 */
class PleosSdkManager(
    context: Context,
    private val listener: Listener,
    private val serverClientId: String = "",
    private val serverClientSecret: String = ""
) {
    interface Listener {
        fun onSdkStatusChanged(status: SdkConnectionStatus)
        fun onLocationUpdated(lat: Double, lng: Double, headingDeg: Float?, speedKmh: Float?)
        fun onNaviRoadUpdated(roadName: String?, speedLimitKmh: Int?)
        fun onSttResult(recognizedText: String)
        fun onTtsStateChanged(isSpeaking: Boolean, message: String)

        /** NaviHelper가 알려 주는 다음 회전(TBT). 차량 내비가 안내 중일 때만 호출된다. */
        fun onNaviTurn(turn: TurnIcon, distanceM: Double) {}

        /** 차량 내비에 목적지가 설정/변경되었을 때. */
        fun onNaviDestination(lat: Double, lng: Double, remainDistanceM: Int, remainTimeSec: Int) {}

        /** FusedLocation 근접 알림(ENTER/EXIT) 이벤트. */
        fun onProximityTransition(alertId: String, transition: String) {}

        /** Vehicle SDK 실시간 기어(P/R/N/D) 변경 콜백. */
        fun onVehicleGearChanged(gear: GearState) {}

        /** Vehicle SDK 실시간 방향지시등(OFF/LEFT/RIGHT) 변경 콜백. */
        fun onVehicleTurnSignalChanged(signal: TurnSignalState) {}

        /** Vehicle SDK 실시간 브레이크 페달 조작 콜백. */
        fun onVehicleBrakeChanged(pressed: Boolean) {}

        /** Vehicle SDK 실시간 누적 주행거리(km) 콜백. */
        fun onVehicleOdometerChanged(odometerKm: Double) {}

        /** Vehicle SDK 실시간 전조등(LowBeam) 점등 상태 콜백. */
        fun onVehicleHeadlampChanged(headlampOn: Boolean) {}
    }

    private val appContext = context.applicationContext
    private val tag = "PleosSdkManager"

    private var status = SdkConnectionStatus()

    // --- AudioFocus 관리 (주행 중 라디오/음악 재생 시 쪽지 음성이 또렷하게 들리도록 Ducking 처리) ---
    private val audioManager: AudioManager? =
        appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val audioFocusRequest: AudioFocusRequest = AudioFocusRequest.Builder(
        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
    ).setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
    ).setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener { /* Ducking handled by Android Car Audio Service */ }
        .build()

    private fun requestSpeechAudioFocus() {
        runCatching { audioManager?.requestAudioFocus(audioFocusRequest) }
    }

    private fun abandonSpeechAudioFocus() {
        runCatching { audioManager?.abandonAudioFocusRequest(audioFocusRequest) }
    }

    // 1. Vehicle SDK & 실시간 차량 신호 리스너
    private var vehicle: Vehicle? = null

    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) {
            if (released || gear == null) return
            val mapped = VehicleSignalTracker.mapVehicleGear(gear.name)
            Log.i(tag, "Vehicle Gear updated: ${gear.name} -> $mapped")
            listener.onVehicleGearChanged(mapped)
        }

        override fun onFailed(e: Exception) {
            Log.w(tag, "Vehicle Gear listener failed: ${e.message}")
        }
    }

    private val turnSignalListener = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
            if (released || state == null) return
            val mapped = VehicleSignalTracker.mapTurnSignal(state.name)
            Log.i(tag, "Vehicle TurnSignal updated: ${state.name} -> $mapped")
            listener.onVehicleTurnSignalChanged(mapped)
        }

        override fun onFailed(e: Exception) {
            Log.w(tag, "Vehicle TurnSignal listener failed: ${e.message}")
        }
    }

    private val brakeListener = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) {
            if (released || status == null) return
            Log.i(tag, "Vehicle BrakePedal updated: $status")
            listener.onVehicleBrakeChanged(status)
        }

        override fun onFailed(e: Exception) {
            Log.w(tag, "Vehicle BrakePedal listener failed: ${e.message}")
        }
    }

    private val distanceListener = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(value: Float?) {
            if (released || value == null || !value.isFinite()) return
            Log.i(tag, "Vehicle Odometer updated: $value km")
            listener.onVehicleOdometerChanged(value.toDouble())
        }

        override fun onFailed(e: Exception) {
            Log.w(tag, "Vehicle DistanceDriven listener failed: ${e.message}")
        }
    }

    private val lowBeamLightListener = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) {
            if (released || state == null) return
            val isOn = VehicleSignalTracker.isHeadlampOn(state.name)
            Log.i(tag, "Vehicle LowBeam updated: ${state.name} (headlampOn=$isOn)")
            listener.onVehicleHeadlampChanged(isOn)
        }

        override fun onFailed(e: Exception) {
            Log.w(tag, "Vehicle LowBeam listener failed: ${e.message}")
        }
    }

    // 2. FusedLocation SDK
    private var fusedLocation: FusedLocation? = null
    // SDK는 위치를 안드로이드 Location이 아니라 LocationData(위도, 경도, heading, confidence)로 준다. 속도는 들어 있지 않다.
    private val fusedLocationListener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            val loc = location ?: return
            Log.i(
                tag,
                "FusedLocation: lat=${loc.latitude} lng=${loc.longitude} heading=${loc.heading} confidence=${loc.confidence}"
            )
            // 방위각을 모를 때(NaN, 음수)는 null로 넘겨 이전 방위각을 유지하게 한다.
            val heading = loc.heading.takeIf { it.isFinite() && it >= 0f }
            listener.onLocationUpdated(
                lat = loc.latitude,
                lng = loc.longitude,
                headingDeg = heading,
                speedKmh = null
            )
        }

        override fun onError(exception: Exception) {
            Log.w(tag, "FusedLocation error: ${exception.message}")
            // 권한 오류가 콜백으로만 올 수 있어서, 연결 표시를 실제 상태로 돌리고 승인 뒤 재시도하게 한다.
            if (released) return
            status = status.copy(
                locationConnected = false,
                locationDetail = "FusedLocation 1.1.3 오류: ${exception.message}"
            )
            publishStatus()
        }
    }

    private val proximityListener = object : ProximityAlertListener {
        override fun onRegistered(alertId: String) {
            proximityAlertId = alertId
            Log.i(tag, "ProximityAlert registered: $alertId")
        }

        override fun onTransition(event: ProximityAlertEvent) {
            Log.i(tag, "ProximityAlert transition: alert=${event.alertId} transition=${event.transition}")
            listener.onProximityTransition(event.alertId, event.transition.name)
        }

        override fun onError(exception: Exception) {
            Log.w(tag, "ProximityAlert error: ${exception.message}")
        }
    }

    // 3. TextToSpeech SDK + Android TTS Fallback
    private var pleosTts: TextToSpeech? = null
    @Volatile private var isPleosTtsReady = false
    private var androidTts: AndroidTts? = null
    private var isAndroidTtsReady = false
    @Volatile private var lastSpokenText = ""
    @Volatile private var proximityAlertId: String? = null

    /** 한 문장당 안드로이드 TTS 대체는 한 번만 한다(onError가 연속으로 와도 중복해서 읽지 않게). */
    @Volatile private var fallbackUsed = false

    /** releaseAll() 이후에는 예약된 재시도와 늦게 오는 SDK 콜백이 아무 것도 하지 않는다. */
    @Volatile private var released = false

    // 4. SpeechToText SDK
    private var pleosStt: SpeechToText? = null

    // 5. NaviHelper SDK
    private var naviHelper: NaviHelper? = null
    private val naviListener = object : NaviHelperEventListener {
        override fun onRouteStarted(routeStartInfo: RouteStartInfo) {}
        @Deprecated("Deprecated by NaviHelperEventListener")
        override fun onRouteEnded() {}
        override fun onRoutingCompleted() {}
        override fun onReRouteEnded() {}
        override fun onRouteOptionChanged(routeOption: RouteOption) {}
        override fun onRouteCancelled() {}
        override fun onWaypointChanged(waypointChangedInfo: WaypointChangedInfo) {}
        override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) {}
        override fun onWayPointArrived(waypointArrivedInfo: WaypointArrivedInfo) {}
        override fun onError(errorCode: NaviErrorCode) {
            Log.w(tag, "NaviHelper error: $errorCode")
        }
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            listener.onNaviRoadUpdated(currentLocationInfo.address.ifBlank { null }, null)
        }
        override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) {}
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            listener.onNaviDestination(
                destinationInfo.latitude,
                destinationInfo.longitude,
                destinationInfo.remainDistance,
                destinationInfo.remainTime
            )
        }
        override fun onWayPointInfo(waypointInfoList: List<WaypointInfo>) {}
        override fun onRecentDestinationInfo(recentDestinationInfoList: List<RecentDestinationInfo>) {}
        override fun onBookmarkInfo(bookmarkInfoList: List<BookmarkInfo>) {}
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) {
            listener.onNaviRoadUpdated(null, speedLimit)
        }
        override fun onCameraAlert(cameraAlertList: List<CameraAlert>) {}
        override fun onDrivingInfo(drivingInfo: DrivingInfo) {}
        override fun onBookMarkChanged() {}
        override fun onDestinationChanged() {}
        override fun onNavigationStatus(status: NavigationStatus) {}
        override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
            tbtInfoList.firstOrNull()?.let {
                listener.onNaviTurn(TurnMapping.fromPleos(it.type), it.distance)
            }
        }
        override fun onChargerOperatorInfo(chargerOperatorList: List<ChargerOperator>) {}
        override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) {}
        override fun onTripInfo(tripInfo: TripInfo) {}
    }

    fun initializeAll() {
        initVehicleSdk()
        initFusedLocationSdk()
        initTtsSdk()
        initSttSdk()
        initNaviHelperSdk()
        listener.onSdkStatusChanged(status)
    }

    private fun initVehicleSdk() {
        try {
            if (appContext.checkSelfPermission("pleos.car.permission.CAR_DRIVE") !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                status = status.copy(
                    vehicleConnected = false,
                    vehicleDetail = "Vehicle 2.3.2 미지원 환경 (시뮬레이터 주행 모드)"
                )
                return
            }
            val v = Vehicle(appContext)
            v.initialize()
            vehicle = v

            // 1) 실시간 기어(P/R/N/D) 조회 및 리스너 등록
            runCatching {
                val gearApi = v.getGear()
                gearApi.getCurrentGearSelectionState(
                    onComplete = { g -> g?.let { gearListener.onSelected(it) } },
                    onFailed = { e -> Log.w(tag, "getCurrentGearSelectionState failed: ${e.message}") }
                )
                gearApi.registerGearSelection(gearListener)
            }

            // 2) 실시간 방향지시등(OFF/LEFT/RIGHT) 조회 및 리스너 등록
            runCatching {
                val turnApi = v.getTurnSignal()
                turnApi.getCurrentTurnSignalState(
                    onComplete = { s -> s?.let { turnSignalListener.onTurnSignalStateUpdated(it) } },
                    onFailed = { e -> Log.w(tag, "getCurrentTurnSignalState failed: ${e.message}") }
                )
                turnApi.registerTurnSignalState(turnSignalListener)
            }

            // 3) 실시간 브레이크 페달 조작 조회 및 리스너 등록
            runCatching {
                val brakeApi = v.getBrake()
                brakeApi.getCurrentBrakePedalStatus(
                    onComplete = { b -> b?.let { brakeListener.onBrakePedalStatusUpdated(it) } },
                    onFailed = { e -> Log.w(tag, "getCurrentBrakePedalStatus failed: ${e.message}") }
                )
                brakeApi.registerBrakePedalStatus(brakeListener)
            }

            // 4) 실시간 주행거리(Odometer km) 조회 및 리스너 등록
            runCatching {
                val odoApi = v.getOdometer()
                odoApi.getCurrentDistanceDriven(
                    onComplete = { d -> d?.let { distanceListener.onDistanceDrivenUpdated(it) } },
                    onFailed = { e -> Log.w(tag, "getCurrentDistanceDriven failed: ${e.message}") }
                )
                odoApi.registerDistanceDriven(distanceListener)
            }

            // 5) 실시간 전조등(LowBeam) 점등 상태 조회 및 리스너 등록 (주/야간 지도 자동 연동)
            runCatching {
                val lightApi = v.getLight()
                lightApi.getCurrentLowBeamLightState(
                    onComplete = { l -> l?.let { lowBeamLightListener.onLightStateUpdated(it) } },
                    onFailed = { e -> Log.w(tag, "getCurrentLowBeamLightState failed: ${e.message}") }
                )
                lightApi.registerLowBeamLightState(lowBeamLightListener)
            }

            status = status.copy(
                vehicleConnected = true,
                vehicleDetail = "Vehicle 2.3.2 (Gear·TurnSignal·Brake·Odometer·Light 실시간 연동)"
            )
        } catch (e: Throwable) {
            Log.w(tag, "Vehicle SDK init failed: ${e.message}")
            vehicle = null
            status = status.copy(
                vehicleConnected = false,
                vehicleDetail = "Vehicle 2.3.2 초기화 실패: ${e.message}"
            )
        }
    }

    private fun publishStatus() {
        listener.onSdkStatusChanged(status)
    }

    /**
     * FusedLocation의 pleos.car.permission.FUSED_LOCATION은 dangerous 권한이라 사용자 승인 뒤에야 동작한다.
     * 승인 결과가 나오면 아직 연결되지 않은 위치 SDK만 다시 초기화한다.
     */
    fun retryLocationInit() {
        if (status.locationConnected) return
        initFusedLocationSdk()
        publishStatus()
    }

    private fun initFusedLocationSdk() {
        try {
            val fl = FusedLocation(appContext)
            val supported = runCatching { fl.isSupported() }.getOrDefault(false)
            if (!supported) {
                status = status.copy(
                    locationConnected = false,
                    locationDetail = "FusedLocation 1.1.3 미지원 단말 (데모 주행 모드)"
                )
                return
            }
            if (appContext.checkSelfPermission("pleos.car.permission.FUSED_LOCATION") !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                status = status.copy(
                    locationConnected = false,
                    locationDetail = "FusedLocation 1.1.3 권한 승인 대기 중"
                )
                return
            }
            fl.initialize()
            fl.registerFusedLocationCallback(fusedLocationListener)
            fusedLocation = fl
            // 시험용 근접 알림: 기본 출발지 반경 200m의 진입/이탈. 에뮬레이터 위치를 옮겨 동작을 확인한다.
            fl.addProximityAlert(
                ProximityAlertRequest(
                    latitude = RouteRepository.DEFAULT_ORIGIN.lat,
                    longitude = RouteRepository.DEFAULT_ORIGIN.lng,
                    radiusMeters = 200f,
                    transitionTypes = setOf(ProximityTransition.ENTER, ProximityTransition.EXIT)
                ),
                proximityListener
            )
            status = status.copy(
                locationConnected = true,
                locationDetail = "FusedLocation 1.1.3 (isSupported=$supported · 콜백·근접알림 등록)"
            )
        } catch (e: Throwable) {
            Log.w(tag, "FusedLocation SDK init failed: ${e.message}")
            status = status.copy(
                locationConnected = false,
                locationDetail = "FusedLocation 1.1.3 초기화 실패: ${e.message}"
            )
        }
    }

    /** Pleos TTS가 speak() 호출 뒤 이 시간(ms) 안에 onStart를 주지 않으면 데몬 미응답으로 보고 Android TTS로 전환한다. */
    private val pleosTtsWatchdogRunnable = Runnable {
        if (released) return@Runnable
        val text = lastSpokenText
        if (text.isNotBlank() && !fallbackUsed) {
            Log.w(tag, "Pleos TTS did not start within watchdog window; switching to Android TTS fallback")
            fallbackUsed = true
            runCatching { pleosTts?.stop() }
            speakWithAndroidTts(text)
        }
    }

    private fun initTtsSdk() {
        runCatching {
            androidTts = AndroidTts(appContext) { code ->
                if (code == AndroidTts.SUCCESS) {
                    val langResult = androidTts?.setLanguage(Locale.KOREAN) ?: AndroidTts.LANG_NOT_SUPPORTED
                    if (langResult == AndroidTts.LANG_MISSING_DATA || langResult == AndroidTts.LANG_NOT_SUPPORTED) {
                        androidTts?.setLanguage(Locale.getDefault())
                    }
                    androidTts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    androidTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            listener.onTtsStateChanged(true, lastSpokenText)
                        }
                        override fun onDone(utteranceId: String?) {
                            lastSpokenText = ""
                            abandonSpeechAudioFocus()
                            listener.onTtsStateChanged(false, "")
                        }
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            lastSpokenText = ""
                            abandonSpeechAudioFocus()
                            listener.onTtsStateChanged(false, "")
                        }
                    })
                    isAndroidTtsReady = true
                    // 초기화 전에 요청된 문장이 대기 중이면 즉시 읽는다.
                    val pending = pendingAndroidSpeech
                    if (!pending.isNullOrBlank()) {
                        pendingAndroidSpeech = null
                        speakWithAndroidTts(pending)
                    }
                }
            }
        }
        try {
            if (appContext.checkSelfPermission("pleos.car.permission.TTS_SERVICE") !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                status = status.copy(
                    ttsConnected = false,
                    ttsDetail = "TextToSpeech 3.3.1 미지원 환경 (안드로이드 시스템 TTS 사용)"
                )
                return
            }
            val useServer = serverClientId.isNotBlank() && serverClientSecret.isNotBlank()
            val ttsMode = if (useServer) TtsMode.SERVER else TtsMode.ON_DEVICE
            val tts = TextToSpeech(appContext, ttsMode)
            status = status.copy(ttsConnected = false, ttsDetail = "TextToSpeech 3.3.1 (${ttsMode.name} 연결 중…)")
            // initialize(리스너) 버전은 이 SDK에서 내부 lateinit 오류(실측)가 나서, 리스너 없는 initialize()를 쓴다.
            tts.initialize()
            pleosTts = tts
            if (useServer) {
                runCatching {
                    tts.registerApp(
                        serverClientId,
                        serverClientSecret,
                        object : TtsServerListener {
                            override fun onConnected() {
                                Log.i(tag, "Pleos TTS Server connected")
                            }
                            override fun onFailed(errorType: TtsErrorType) {
                                Log.w(tag, "Pleos TTS Server connection failed: $errorType")
                            }
                        }
                    )
                }
            }
            attachTtsListener(tts, attempt = 0)
        } catch (e: Throwable) {
            Log.w(tag, "Pleos TTS init failed", e)
            pleosTts = null
            status = status.copy(ttsConnected = false, ttsDetail = "TextToSpeech 3.3.1 초기화 실패: ${e.message}")
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var pendingAndroidSpeech: String? = null

    /** 이벤트 리스너는 SDK 내부 준비가 끝난 뒤에만 붙일 수 있어서, 실패하면 0.5초 뒤 다시 시도한다. */
    private fun attachTtsListener(tts: TextToSpeech, attempt: Int) {
        if (released) return
        try {
            tts.addEventListener(ttsEventListener)
            runCatching { tts.setLocale(Locale.KOREA) }
            Log.i(tag, "Pleos TTS listener attached (attempt=$attempt)")
        } catch (e: Throwable) {
            if (attempt < 12) {
                mainHandler.postDelayed({ attachTtsListener(tts, attempt + 1) }, 500L)
            } else {
                Log.w(tag, "Pleos TTS addEventListener gave up", e)
                status = status.copy(ttsConnected = false, ttsDetail = "TextToSpeech 3.3.1 리스너 연결 실패: ${e.message}")
                publishStatus()
            }
        }
    }

    private val ttsEventListener = object : TtsEventListener {
        override fun onStart() {
            Log.i(tag, "Pleos TTS onStart")
            mainHandler.removeCallbacks(pleosTtsWatchdogRunnable)
            listener.onTtsStateChanged(true, lastSpokenText)
        }

        override fun onReady() {
            Log.i(tag, "Pleos TTS onReady")
            isPleosTtsReady = true
            status = status.copy(ttsConnected = true, ttsDetail = "TextToSpeech 3.3.1 (준비 완료)")
            publishStatus()
        }

        override fun onDone() {
            Log.i(tag, "Pleos TTS onDone")
            mainHandler.removeCallbacks(pleosTtsWatchdogRunnable)
            lastSpokenText = ""
            abandonSpeechAudioFocus()
            listener.onTtsStateChanged(false, "")
        }

        override fun onError(error: TtsErrorType) {
            Log.w(tag, "Pleos TTS onError: $error")
            mainHandler.removeCallbacks(pleosTtsWatchdogRunnable)
            status = status.copy(ttsConnected = false, ttsDetail = "TextToSpeech 3.3.1 오류: $error (안드로이드 TTS로 대체)")
            publishStatus()
            // Pleos TTS가 실패했으면 같은 문장을 안드로이드 TTS로 한 번만 대신 읽는다.
            val text = lastSpokenText
            if (text.isNotBlank() && !fallbackUsed) {
                fallbackUsed = true
                speakWithAndroidTts(text)
            } else {
                abandonSpeechAudioFocus()
                listener.onTtsStateChanged(false, "")
            }
        }

        override fun onStop() {
            Log.i(tag, "Pleos TTS onStop")
            mainHandler.removeCallbacks(pleosTtsWatchdogRunnable)
            lastSpokenText = ""
            abandonSpeechAudioFocus()
            listener.onTtsStateChanged(false, "")
        }

        override fun onUpdatedRms(rms: Double) {}
    }

    private fun initSttSdk() {
        try {
            if (appContext.checkSelfPermission("pleos.car.permission.STT_SERVICE") !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                status = status.copy(
                    sttConnected = false,
                    sttDetail = "SpeechToText 3.3.1 미지원 환경 (시뮬레이터 음성 프리셋 사용)"
                )
                return
            }
            val useServer = serverClientId.isNotBlank() && serverClientSecret.isNotBlank()
            val sttMode = if (useServer) SttMode.SERVER else SttMode.ON_DEVICE
            val stt = SpeechToText(appContext, sttMode)
            stt.initialize()
            stt.addListener(sttResultListener)
            if (useServer) {
                runCatching {
                    stt.registerApp(
                        serverClientId,
                        serverClientSecret,
                        object : SttServerListener {
                            override fun onConnected() {
                                Log.i(tag, "Pleos STT Server connected")
                            }
                            override fun onFailed(errorType: SttErrorType) {
                                Log.w(tag, "Pleos STT Server connection failed: $errorType")
                            }
                        }
                    )
                }
            }
            // 온디바이스 STT 서비스는 국가가 없는 "ko"를 unsupported locale로 거부한다(실측). ko_KR을 써야 한다.
            runCatching { stt.setLocale(Locale.KOREA) }
            pleosStt = stt
            // 엔진이 실제로 준비되면(onReady) 그때 연결됨으로 표시한다.
            status = status.copy(sttConnected = false, sttDetail = "SpeechToText 3.3.1 (${sttMode.name} 연결 중…)")
        } catch (e: Throwable) {
            Log.w(tag, "Pleos STT init failed: ${e.message}")
            pleosStt = null
            status = status.copy(sttConnected = false, sttDetail = "SpeechToText 3.3.1 초기화 실패: ${e.message}")
        }
    }

    private val sttResultListener = object : SttResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            Log.i(tag, "Pleos STT onUpdated(final=$isFinal): $text")
            if (isFinal && text.isNotBlank()) listener.onSttResult(text)
        }

        override fun onUpdatedEpdData(start: Long, end: Long) {}

        override fun onStartedRecognition() {
            Log.i(tag, "Pleos STT onStartedRecognition")
        }

        override fun onEndedRecognition() {
            Log.i(tag, "Pleos STT onEndedRecognition")
            abandonSpeechAudioFocus()
        }

        override fun onError(error: SttErrorType) {
            Log.w(tag, "Pleos STT onError: $error")
            abandonSpeechAudioFocus()
        }

        override fun onReady() {
            Log.i(tag, "Pleos STT onReady")
            status = status.copy(sttConnected = true, sttDetail = "SpeechToText 3.3.1 (준비 완료)")
            publishStatus()
        }

        override fun onUpdatedRms(rms: Double) {}
    }

    private fun initNaviHelperSdk() {
        try {
            if (appContext.checkSelfPermission("pleos.car.permission.NAVI_ROUTE") !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                status = status.copy(
                    naviConnected = false,
                    naviDetail = "NaviHelper 3.0.5 미지원 환경 (OSRM/데모 경로 모드)"
                )
                return
            }
            val helper = NaviHelper(appContext)
            helper.initialize()
            helper.addListener(naviListener)
            runCatching { helper.getCurrentLocationInfo() }
            runCatching { helper.getRouteStateInfo() }
            naviHelper = helper
            status = status.copy(
                naviConnected = true,
                naviDetail = "NaviHelper 3.0.5 (출퇴근 밴드·도로명 연동)"
            )
        } catch (e: Throwable) {
            Log.w(tag, "NaviHelper SDK init failed: ${e.message}")
            naviHelper = null
            status = status.copy(
                naviConnected = false,
                naviDetail = "NaviHelper 3.0.5 초기화 실패: ${e.message}"
            )
        }
    }

    fun speakAnonymousNote(text: String) {
        lastSpokenText = text
        fallbackUsed = false
        mainHandler.removeCallbacks(pleosTtsWatchdogRunnable)
        requestSpeechAudioFocus()
        val tts = pleosTts
        // Pleos TTS가 실제로 onReady된 상태일 때만 먼저 호출하고, 450ms 안에 onStart가 오지 않으면
        // 에뮬레이터/단말에서 음성이 묻히지 않도록 즉시 Android 시스템 TTS로 전환한다.
        if (tts != null && isPleosTtsReady) {
            val ok = runCatching { tts.speak(text, true) }
                .onFailure { Log.w(tag, "Pleos TTS speak failed: ${it.message}") }
                .isSuccess
            if (ok) {
                Log.i(tag, "TTS Speaking (pleos=true): $text")
                listener.onTtsStateChanged(true, text)
                mainHandler.postDelayed(pleosTtsWatchdogRunnable, 450L)
                return
            }
        }
        fallbackUsed = true
        speakWithAndroidTts(text)
    }

    private fun speakWithAndroidTts(text: String) {
        listener.onTtsStateChanged(true, text)
        if (isAndroidTtsReady) {
            val res = runCatching {
                androidTts?.speak(text, AndroidTts.QUEUE_FLUSH, null, "todak_note_tts")
            }.getOrDefault(AndroidTts.ERROR)
            Log.i(tag, "TTS Speaking (android fallback, result=$res): $text")
        } else {
            pendingAndroidSpeech = text
            Log.i(tag, "Android TTS not ready yet; queued speech: $text")
        }
    }

    fun stopSpeaking() {
        // 이 SDK는 onReady를 주지 않아 준비 플래그가 false일 수 있으므로, 조건 없이 항상 멈춘다.
        mainHandler.removeCallbacks(pleosTtsWatchdogRunnable)
        pendingAndroidSpeech = null
        lastSpokenText = ""
        runCatching { pleosTts?.stop() }
        runCatching { androidTts?.stop() }
        abandonSpeechAudioFocus()
        listener.onTtsStateChanged(false, "")
    }

    fun startVoiceNoteCapture() {
        requestSpeechAudioFocus()
        runCatching { pleosStt?.request() }
            .onSuccess {
                // onReady를 주지 않는 SDK라서, 요청 호출이 예외 없이 끝난 것을 연결 신호로 본다.
                status = status.copy(sttConnected = true, sttDetail = "SpeechToText 3.3.1 (ON_DEVICE 요청 성공)")
                publishStatus()
            }
            .onFailure {
                abandonSpeechAudioFocus()
                Log.w(tag, "Pleos STT request failed: ${it.message}")
            }
    }

    fun stopVoiceNoteCapture() {
        runCatching { pleosStt?.stop() }
        abandonSpeechAudioFocus()
    }

    fun releaseAll() {
        released = true
        mainHandler.removeCallbacksAndMessages(null)
        abandonSpeechAudioFocus()
        proximityAlertId?.let { id -> runCatching { fusedLocation?.removeProximityAlert(id) } }
        runCatching { fusedLocation?.unregisterFusedLocationCallback(fusedLocationListener) }
        runCatching { fusedLocation?.release() }
        runCatching { naviHelper?.removeListener(naviListener) }
        runCatching { naviHelper?.release() }
        runCatching { vehicle?.getGear()?.unregisterGearSelection(gearListener) }
        runCatching { vehicle?.getTurnSignal()?.unregisterTurnSignalState(turnSignalListener) }
        runCatching { vehicle?.getBrake()?.unregisterBrakePedalStatus(brakeListener) }
        runCatching { vehicle?.getOdometer()?.unregisterDistanceDriven(distanceListener) }
        runCatching { vehicle?.getLight()?.unregisterLowBeamLightState(lowBeamLightListener) }
        runCatching { vehicle?.release() }
        runCatching { pleosTts?.removeEventListener(ttsEventListener) }
        runCatching { pleosTts?.release() }
        runCatching { pleosStt?.removeListener(sttResultListener) }
        runCatching { pleosStt?.release() }
        runCatching { androidTts?.shutdown() }
    }
}
