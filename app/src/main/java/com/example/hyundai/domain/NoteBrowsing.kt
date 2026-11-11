package com.example.hyundai.domain

import com.example.hyundai.model.GearState
import com.example.hyundai.model.VoiceNote

/**
 * 쪽지함·설정처럼 화면을 직접 눌러야 하는 곳은 차가 멈춰 있을 때만 열 수 있다.
 */
object NoteAccess {
    /** 이 속도 미만이면 멈춘 것으로 본다. 속도 센서의 작은 떨림은 정차로 처리한다. */
    const val STOPPED_BELOW_KMH = 0.5f

    fun canBrowse(gear: GearState, speedKmh: Float): Boolean =
        gear == GearState.P || speedKmh < STOPPED_BELOW_KMH
}

/** 쪽지함에 보여 줄 목록과 다음 쪽지를 고르는 규칙. */
object NoteInbox {
    /** 내가 들은 다른 운전자의 쪽지. */
    fun received(notes: List<VoiceNote>): List<VoiceNote> =
        notes.filter { it.isListenedByMe && !it.isMine }

    /** 내가 남긴 쪽지. */
    fun mine(notes: List<VoiceNote>): List<VoiceNote> = notes.filter { it.isMine }

    /**
     * 아직 안 들었고 같은 방향인 쪽지 중 가장 앞의 것.
     * [commuteOnly]가 true면 출퇴근 길 쪽지만 고른다.
     */
    fun nextIncoming(
        notes: List<VoiceNote>,
        excludeId: String? = null,
        commuteOnly: Boolean = false
    ): VoiceNote? = notes.firstOrNull {
        it.isSameDirection && !it.isListenedByMe && it.id != excludeId && (!commuteOnly || it.isCommuteBandOnly)
    }

    /**
     * 읽기가 끝났다는 신호를 못 받았을 때를 위한 안전 시간.
     * 글자당 0.25초로 넉넉히 잡고, 너무 짧거나 길지 않게 6~30초로 제한한다.
     */
    fun speechTimeoutMs(text: String): Long = (text.length * 250L).coerceIn(6_000L, 30_000L)

    /** "제로가 남긴 쪽지예요." 처럼 이름 받침에 맞춰 이/가를 고른다. */
    fun introLine(petName: String): String {
        val last = petName.lastOrNull()
        val hasBatchim = last != null && last in '가'..'힣' && (last.code - 0xAC00) % 28 != 0
        return "$petName${if (hasBatchim) "이" else "가"} 남긴 쪽지예요."
    }
}
