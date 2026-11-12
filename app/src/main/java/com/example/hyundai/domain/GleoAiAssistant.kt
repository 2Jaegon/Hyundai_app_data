package com.example.hyundai.domain

import com.example.hyundai.model.GearState
import com.example.hyundai.model.NoteCategory
import com.example.hyundai.model.VoiceNote

/**
 * 주행 중 핸즈프리 음성 명령(Gleo AI / STT) 해석 및
 * 도로·날씨·주행 맥락 맞춤형 펫('제로') 대사 생성 엔진.
 *
 * 1. 주행 중 화면 터치 없이 음성만으로 수신 쪽지 재생("응", "들려줘", "읽어줘"),
 *    공감 보내기("좋아요", "토닥", "고마워"), 거절/다음으로 넘기기("다음", "괜찮아")를 인식한다.
 * 2. 온디바이스 Gleo AI 맥락 프롬프트 규칙을 통해 시간대·도로명·주행 스타일에 맞는
 *    자연스러운 동행 대사를 생성한다.
 */
object GleoAiAssistant {

    enum class VoiceCommandIntent {
        /** 수신된 쪽지 재생 승인 ("응", "그래", "들려줘", "읽어줘", "재생") */
        APPROVE_LISTEN,
        /** 방금 들은 쪽지 또는 현재 쪽지에 좋아요/토닥 공감 ("좋아요", "토닥", "고마워", "공감") */
        SEND_LIKE,
        /** 현재 재생 중지 또는 쪽지 건너뛰기 ("그만", "멈춰", "다음", "패스") */
        STOP_OR_SKIP,
        /** 새 음성 쪽지 남기기 시작 ("쪽지 남기기", "쪽지 작성", "길말 남기기") */
        START_COMPOSE,
        /** 일반 쪽지 본문 발화 (명령어가 아님) */
        NOTE_DICTATION
    }

    private val approveKeywords = listOf(
        "응", "어", "그래", "네", "예", "좋아", "들려줘", "들어볼래", "들어보자",
        "읽어줘", "재생", "플레이", "틀어줘", "오케이", "ok"
    )
    private val likeKeywords = listOf(
        "좋아요", "토닥", "토닥토닥", "고마워", "고마워요", "감사", "공감", "하트", "추천"
    )
    private val stopKeywords = listOf(
        "그만", "멈춰", "정지", "스톱", "다음", "패스", "넘어가", "안 들을래", "괜찮아"
    )
    private val composeKeywords = listOf(
        "쪽지 남기기", "쪽지 쓸래", "쪽지 작성", "길말 남기기", "음성 쪽지"
    )

    /**
     * STT로 인식된 짧은 발화에서 주행 중 핸즈프리 제어 의도를 판별한다.
     * 쪽지 작성 모달이 열려 있을 때는 모든 발화를 본문(`NOTE_DICTATION`)으로 취급한다.
     */
    fun parseVoiceCommand(
        utterance: String,
        isComposeDialogOpen: Boolean,
        hasIncomingOrHeldNote: Boolean,
        isSpeakingTts: Boolean
    ): VoiceCommandIntent {
        if (isComposeDialogOpen) return VoiceCommandIntent.NOTE_DICTATION
        val normalized = utterance.trim().lowercase()
        if (normalized.isEmpty()) return VoiceCommandIntent.NOTE_DICTATION

        // 짧은 제어 명령(18자 이하)일 때만 명령어로 우선 해석
        if (normalized.length <= 18) {
            if (composeKeywords.any { normalized.contains(it) }) {
                return VoiceCommandIntent.START_COMPOSE
            }
            if ((isSpeakingTts || hasIncomingOrHeldNote) && stopKeywords.any { normalized.contains(it) }) {
                return VoiceCommandIntent.STOP_OR_SKIP
            }
            if (hasIncomingOrHeldNote && likeKeywords.any { normalized.contains(it) }) {
                return VoiceCommandIntent.SEND_LIKE
            }
            if (hasIncomingOrHeldNote && approveKeywords.any { normalized == it || normalized.startsWith("$it ") || normalized.contains(it) }) {
                return VoiceCommandIntent.APPROVE_LISTEN
            }
        }
        return VoiceCommandIntent.NOTE_DICTATION
    }

    /**
     * 도로 구간, 주행 속도, 전조등(야간 여부), 쪽지 카테고리에 맞춰
     * 동행 펫 '제로'의 상황 맞춤형 한마디를 생성한다.
     */
    fun generateContextualPetSpeech(
        roadName: String,
        speedKmh: Float,
        gear: GearState,
        isNightOrHeadlampOn: Boolean,
        note: VoiceNote? = null
    ): String {
        if (gear == GearState.P) {
            return "오늘도 안전하게 도착했어요! 따뜻한 운전 덕분에 제가 한 뼘 더 자랐어요."
        }
        if (note != null) {
            return when (note.category) {
                NoteCategory.SAFETY_3H -> "${roadName} 앞쪽 안전 쪽지예요. 미리 확인하고 여유 있게 지나가요!"
                NoteCategory.CHEER_24H -> "같은 길을 달리는 이웃이 따뜻한 응원 쪽지를 건넸어요."
                NoteCategory.VIBE_7D -> "이 길의 멋진 풍경 소식이에요. 마음까지 시원해지네요!"
                NoteCategory.TIP_30D -> "알아두면 든든한 동네 길 꿀팁을 주웠어요!"
            }
        }
        return when {
            isNightOrHeadlampOn -> "밤길 전조등이 켜졌네요. 야간 시야에 맞춰 지도도 편안한 다크 모드로 바꿨어요."
            speedKmh < 15f -> "${roadName} 구간이 조금 막히네요. 깊게 숨 한 번 쉬고 여유롭게 가요."
            else -> "${roadName} 위를 기분 좋게 달리는 중이에요. 오늘도 매너운전 함께해요!"
        }
    }
}
