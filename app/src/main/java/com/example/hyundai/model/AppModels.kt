package com.example.hyundai.model

enum class GearState(val label: String) {
    P("P"),
    R("R"),
    N("N"),
    D("D")
}

enum class TurnSignalState(val label: String) {
    OFF("OFF"),
    LEFT("좌측 점등 ◀"),
    RIGHT("우측 점등 ▶"),
    HAZARD("비상등 ⚠")
}

data class SdkConnectionStatus(
    val vehicleConnected: Boolean = false,
    val vehicleDetail: String = "초기화 대기",
    val locationConnected: Boolean = false,
    val locationDetail: String = "초기화 대기",
    val ttsConnected: Boolean = false,
    val ttsDetail: String = "초기화 대기",
    val sttConnected: Boolean = false,
    val sttDetail: String = "초기화 대기",
    val naviConnected: Boolean = false,
    val naviDetail: String = "초기화 대기"
)

data class VehicleTelemetry(
    val speedKmh: Float = 62f,
    val odometerKm: Double = 14520.4,
    val sessionDrivenKm: Double = 4.2,
    val brakePressed: Boolean = false,
    val turnSignal: TurnSignalState = TurnSignalState.OFF,
    val gear: GearState = GearState.D,
    val headingDeg: Float = 85f, // 동쪽(성수대교/강변북로 방향)
    val latitude: Double = 37.5445,
    val longitude: Double = 127.0440,
    val roadName: String = "강변북로 성수대교 동단",
    val commuteBandName: String = "성수-판교 오전 출근 밴드 (84명 동행 중)",
    val speedLimitKmh: Int = 80
)

enum class MannerTurnPhase(val description: String) {
    IDLE("방향지시등 대기 중"),
    SIGNAL_ON_HOLDING("깜빡이 점등 감지 (1.5초 유지 확인 중)"),
    READY_FOR_HEADING("유지 완료! 실제 차선변경/회전(±25°) 대기 중"),
    VERIFIED("매너 방향전환 인증 완료! (+15 EXP, +10 P)"),
    ABUSE_BLOCKED("단순 깜빡이 껐다 켜기 감지 (실제 방향전환 미확인·보상 미지급)"),
    COOLDOWN("연속 적립 방지 쿨타임 진행 중")
}

data class DrivingEvaluationState(
    // 1. Safe Drive: 1.0 km 단위 정속/안정 주행 적립
    val safeBlockProgressKm: Float = 0.65f,
    val safeBlockTargetKm: Float = 1.0f,
    val completedSafeBlocks: Int = 4,
    // 2. Smooth Drive: 2.0 km 무급감속/무급가속 구간 보너스 (감점 없음)
    val smoothWindowProgressKm: Float = 1.35f,
    val smoothWindowTargetKm: Float = 2.0f,
    val harshEventsInCurrentWindow: Int = 0,
    val totalHarshEventsToday: Int = 0,
    val completedSmoothWindows: Int = 2,
    val lastSmoothFeedback: String = "현재 구간 급가속·급제동 0회! 부드러운 주행 유지 중",
    // 3. Manner Drive: 깜빡이(1.5초+) + 실제 조향/방위각 변화(>=25도) + 45초 쿨타임
    val mannerPhase: MannerTurnPhase = MannerTurnPhase.IDLE,
    val signalHoldSeconds: Float = 0f,
    val headingDeltaDeg: Float = 0f,
    val cooldownRemainingSec: Int = 0,
    val completedMannerTurns: Int = 5,
    val abuseBlockedCount: Int = 0,
    val recentEvaluationLogs: List<String> = listOf(
        "[Manner] 성수대교 분기점 우측 깜빡이 + 32° 진입 확인 → +15 EXP, +10 P",
        "[Smooth] 2.0km 연속 무급제동 구간 완주 보너스 → +20 EXP, +10 P",
        "[Safe] 1.0km 안정 주행 달성 → +10 EXP, +5 P"
    )
)

/** 정차 중에만 여는 화면. HOME은 따로 연 화면이 없는 상태다. */
enum class AppScreen { HOME, INBOX, SETTINGS }

data class PetCharacter(
    val id: String,
    val name: String,
    val title: String,
    val level: Int,
    val stageName: String,
    val currentExp: Int,
    val maxExp: Int,
    val isUnlocked: Boolean,
    val badgeColorHex: Long,
    val description: String,
    val currentSpeech: String
)

enum class NoteCategory(
    val label: String,
    val lifespanLabel: String,
    val badgeColorHex: Long
) {
    // 색은 포인트(액티브 블루)와 샌드 두 가지만 쓴다. 중요한 안전 쪽지만 포인트로 눈에 띄게 한다.
    SAFETY_3H("상황·안전", "3시간 유지", 0xFF00AAD2),
    CHEER_24H("따뜻한 응원", "24시간 유지", 0xFFE4DCD3),
    VIBE_7D("감성·풍경", "7일 유지", 0xFFE4DCD3),
    TIP_30D("길·맛집 꿀팁", "30일 유지", 0xFFE4DCD3)
}

enum class ModerationStatus(val label: String) {
    APPROVED("AI 안전 검증 통과"),
    PURIFIED("AI 다정한 순화 완료"),
    BLOCKED("작성 제한 (개인정보/비방 차단)")
}

data class VoiceNote(
    val id: String,
    val authorNickname: String,
    val authorPetName: String,
    val authorPetLevel: Int,
    val rawText: String,
    val displayText: String,
    val category: NoteCategory,
    val moderationStatus: ModerationStatus,
    val moderationNotice: String? = null,
    val roadSegmentName: String,
    val latitude: Double,
    val longitude: Double,
    val headingDeg: Float,
    val distanceMeters: Int,
    val headingDiffDeg: Float,
    val createdAtLabel: String,
    val remainingLifeLabel: String,
    val todakCount: Int,
    val listenerCount: Int,
    val isReactedByMe: Boolean = false,
    val isListenedByMe: Boolean = false,
    val isCommuteBandOnly: Boolean = false,
    val isMine: Boolean = false
) {
    // 진행 방향 ±45° 이내일 때만 재생 허용 (반대 차선 쪽지 필터링)
    val isSameDirection: Boolean
        get() = headingDiffDeg <= 45f
}

data class TireTrackNode(
    val segmentName: String,
    val timeLabel: String,
    val mannerGrade: String,
    val noteCount: Int,
    val todakReceived: Int,
    val xFraction: Float,
    val yFraction: Float
)

data class RewardShopItem(
    val id: String,
    val brand: String,
    val title: String,
    val pointCost: Int,
    val categoryLabel: String,
    val emoji: String
)

data class ParkingReportSummary(
    val todayDrivenKm: Double = 18.4,
    val todayNotesLeft: Int = 2,
    val todayListenersReached: Int = 23,
    val todayTodakReceived: Int = 9,
    val todayMannerTurns: Int = 5,
    val todaySmoothWindows: Int = 2,
    val monthlyWarmthRank: String = "상위 8% 다정한 드라이버",
    val monthlyTotalTodak: Int = 142
)
