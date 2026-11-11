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
import ai.pleos.playground.stt.listener.ResultListener as SttResultListener
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType as TtsErrorType
import ai.pleos.playground.tts.constant.Mode as TtsMode
import ai.pleos.playground.tts.listener.EventListener as TtsEventListener
import ai.pleos.playground.vehicle.Vehicle
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech as AndroidTts
import android.util.Log
import com.example.hyundai.model.SdkConnectionStatus
import com.example.hyundai.nav.RouteRepository
import com.example.hyundai.nav.TurnIcon
import com.example.hyundai.nav.TurnMapping
import java.util.Locale

/**
 * Pleos Connect 5대 공식 SDK 통합 매니저
 * 1. Vehicle SDK (ai.pleos.playground:Vehicle:2.3.2) - Odometer, Brake, TurnSignal, DrivingMode
 * 2. FusedLocation SDK (ai.pleos.playground:FusedLocation:1.1.3) - 실시간 좌표 및 차량 방위각(Heading)
 * 3. TextToSpeech SDK (ai.pleos.playground:TextToSpeech:3.3.1) - 익명 도로 쪽지 음성 재생
 * 4. SpeechToText SDK (ai.pleos.playground:SpeechToText:3.3.1) - 주행 중 10초 핸즈프리 쪽지 녹음
 * 5. NaviHelper SDK (ai.pleos.playground:NaviHelper:3.0.5) - 현재 도로명, 제한속도, 출퇴근 밴드 경로 연동
 */
class PleosSdkManager(
    context: Context,
    private val listener: Listener
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
    }

    private val appContext = context.applicationContext
    private val tag = "PleosSdkManager"

    private var status = SdkConnectionStatus()

    // 1. Vehicle SDK
    private var vehicle: Vehicle? = null

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
            val v = Vehicle(appContext)
            v.initialize()
            // Verify domain linkage: Odometer, Brake, TurnSignal
            v.getOdometer()
            v.getBrake()
            v.getTurnSignal()
            vehicle = v
            // initialize()가 예외 없이 끝난 것뿐, 실제 차량 값을 받는지는 아직 확인하지 않았다.
            status = status.copy(
                vehicleConnected = true,
                vehicleDetail = "Vehicle 2.3.2 (초기화 호출 성공 · 차량 값은 아직 사용 안 함)"
            )
        } catch (e: Throwable) {
            Log.w(tag, "Vehicle SDK init failed: ${e.message}")
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
            val supported = runCatching { fl.isSupported() }.getOrDefault(true)
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

    private fun initTtsSdk() {
        runCatching {
            androidTts = AndroidTts(appContext) { code ->
                if (code == AndroidTts.SUCCESS) {
                    androidTts?.language = Locale.KOREAN
                    isAndroidTtsReady = true
                }
            }
        }
        try {
            val tts = TextToSpeech(appContext, TtsMode.ON_DEVICE)
            pleosTts = tts
            status = status.copy(ttsConnected = false, ttsDetail = "TextToSpeech 3.3.1 (연결 중…)")
            // initialize(리스너) 버전은 이 SDK에서 내부 lateinit 오류(실측)가 나서, 리스너 없는 initialize()를 쓴다.
            // 준비 완료는 이벤트 리스너의 onReady로 받는다.
            tts.initialize()
            attachTtsListener(tts, attempt = 0)
        } catch (e: Throwable) {
            Log.w(tag, "Pleos TTS init failed", e)
            status = status.copy(ttsConnected = false, ttsDetail = "TextToSpeech 3.3.1 초기화 실패: ${e.message}")
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

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
            listener.onTtsStateChanged(true, lastSpokenText)
        }

        override fun onReady() {
            Log.i(tag, "Pleos TTS onReady")
            isPleosTtsReady = true
            status = status.copy(ttsConnected = true, ttsDetail = "TextToSpeech 3.3.1 (ON_DEVICE 준비 완료)")
            publishStatus()
        }

        override fun onDone() {
            Log.i(tag, "Pleos TTS onDone")
            lastSpokenText = ""
            listener.onTtsStateChanged(false, "")
        }

        override fun onError(error: TtsErrorType) {
            Log.w(tag, "Pleos TTS onError: $error")
            status = status.copy(ttsConnected = false, ttsDetail = "TextToSpeech 3.3.1 오류: $error (안드로이드 TTS로 대체)")
            publishStatus()
            // Pleos TTS가 실패했으면 같은 문장을 안드로이드 TTS로 한 번만 대신 읽는다.
            val text = lastSpokenText
            if (text.isNotBlank() && !fallbackUsed) {
                fallbackUsed = true
                speakWithAndroidTts(text)
            } else {
                listener.onTtsStateChanged(false, "")
            }
        }

        override fun onStop() {
            Log.i(tag, "Pleos TTS onStop")
            lastSpokenText = ""
            listener.onTtsStateChanged(false, "")
        }

        override fun onUpdatedRms(rms: Double) {}
    }

    private fun initSttSdk() {
        try {
            val stt = SpeechToText(appContext, SttMode.ON_DEVICE)
            stt.addListener(sttResultListener)
            stt.initialize()
            // 온디바이스 STT 서비스는 국가가 없는 "ko"를 unsupported locale로 거부한다(실측). ko_KR을 써야 한다.
            runCatching { stt.setLocale(Locale.KOREA) }
            pleosStt = stt
            // 엔진이 실제로 준비되면(onReady) 그때 연결됨으로 표시한다.
            status = status.copy(sttConnected = false, sttDetail = "SpeechToText 3.3.1 (연결 중…)")
        } catch (e: Throwable) {
            Log.w(tag, "Pleos STT init failed: ${e.message}")
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
        }

        override fun onError(error: SttErrorType) {
            Log.w(tag, "Pleos STT onError: $error")
        }

        override fun onReady() {
            Log.i(tag, "Pleos STT onReady")
            status = status.copy(sttConnected = true, sttDetail = "SpeechToText 3.3.1 (ON_DEVICE 준비 완료)")
            publishStatus()
        }

        override fun onUpdatedRms(rms: Double) {}
    }

    private fun initNaviHelperSdk() {
        try {
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
            status = status.copy(
                naviConnected = false,
                naviDetail = "NaviHelper 3.0.5 초기화 실패: ${e.message}"
            )
        }
    }

    fun speakAnonymousNote(text: String) {
        lastSpokenText = text
        fallbackUsed = false
        val tts = pleosTts
        // 이 SDK는 서비스가 연결돼도 onReady를 주지 않는다(실측). 그래서 준비 신호를 기다리지 말고
        // 일단 Pleos TTS로 읽어 보고, 예외나 onError가 오면 안드로이드 TTS로 한 번만 대신 읽는다.
        if (tts != null) {
            val ok = runCatching { tts.speak(text, true) }
                .onFailure { Log.w(tag, "Pleos TTS speak failed: ${it.message}") }
                .isSuccess
            if (ok) {
                Log.i(tag, "TTS Speaking (pleos=true): $text")
                listener.onTtsStateChanged(true, text)
                status = status.copy(ttsConnected = true, ttsDetail = "TextToSpeech 3.3.1 (ON_DEVICE 호출 성공)")
                publishStatus()
                return
            }
        }
        speakWithAndroidTts(text)
    }

    private fun speakWithAndroidTts(text: String) {
        listener.onTtsStateChanged(true, text)
        if (isAndroidTtsReady) {
            runCatching {
                androidTts?.speak(text, AndroidTts.QUEUE_FLUSH, null, "todak_note_tts")
            }
        }
        Log.i(tag, "TTS Speaking (pleos=false, android fallback): $text")
    }

    fun stopSpeaking() {
        // 이 SDK는 onReady를 주지 않아 준비 플래그가 false일 수 있으므로, 조건 없이 항상 멈춘다.
        lastSpokenText = ""
        runCatching { pleosTts?.stop() }
        runCatching { androidTts?.stop() }
        listener.onTtsStateChanged(false, "")
    }

    fun startVoiceNoteCapture() {
        runCatching { pleosStt?.request() }
            .onSuccess {
                // onReady를 주지 않는 SDK라서, 요청 호출이 예외 없이 끝난 것을 연결 신호로 본다.
                status = status.copy(sttConnected = true, sttDetail = "SpeechToText 3.3.1 (ON_DEVICE 요청 성공)")
                publishStatus()
            }
            .onFailure { Log.w(tag, "Pleos STT request failed: ${it.message}") }
    }

    fun stopVoiceNoteCapture() {
        runCatching { pleosStt?.stop() }
    }

    fun releaseAll() {
        released = true
        mainHandler.removeCallbacksAndMessages(null)
        proximityAlertId?.let { id -> runCatching { fusedLocation?.removeProximityAlert(id) } }
        runCatching { fusedLocation?.unregisterFusedLocationCallback(fusedLocationListener) }
        runCatching { fusedLocation?.release() }
        runCatching { naviHelper?.removeListener(naviListener) }
        runCatching { naviHelper?.release() }
        runCatching { vehicle?.release() }
        runCatching { pleosTts?.removeEventListener(ttsEventListener) }
        runCatching { pleosTts?.release() }
        runCatching { pleosStt?.removeListener(sttResultListener) }
        runCatching { pleosStt?.release() }
        runCatching { androidTts?.shutdown() }
    }
}
