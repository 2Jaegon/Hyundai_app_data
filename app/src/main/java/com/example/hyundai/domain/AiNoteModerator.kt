package com.example.hyundai.domain

import com.example.hyundai.model.ModerationStatus
import com.example.hyundai.model.NoteCategory

/**
 * 도로 위 음성 쪽지 AI 관리 엔진
 * 1) 안전성 필터링: 욕설·비방, 차량번호/전화번호(개인정보), 위험 행동 유도 탐지 및 순화/차단
 * 2) 메시지 자동 분류: 상황·안전(3시간), 따뜻한 응원(24시간), 감성·풍경(7일), 길·맛집 꿀팁(30일)
 */
object AiNoteModerator {

    data class ModerationResult(
        val status: ModerationStatus,
        val cleanedText: String,
        val category: NoteCategory,
        val noticeMessage: String
    )

    private val licensePlateRegex = Regex("""\d{2,3}\s?[가-힣]\s?\d{4}""")
    private val phoneRegex = Regex("""01[016789][-.\s]?\d{3,4}[-.\s]?\d{4}""")
    private val severeAbuseKeywords = listOf("욕설", "꺼져", "박아버려", "보복운전", "죽어", "미친")
    private val mildComplaintKeywords = listOf("짜증", "답답", "왜 저래", "느려터진")

    private val safetyKeywords = listOf("사고", "정체", "공사", "낙하물", "조심", "빗길", "안개", "블랙아이스", "차선", "합류", "포트홀")
    private val tipKeywords = listOf("맛집", "커피", "카페", "휴게소", "주차", "꿀팁", "우회", "신호", "베이커리", "국밥")
    private val vibeKeywords = listOf("노을", "하늘", "벚꽃", "단풍", "바다", "강변", "풍경", "노래", "야경", "날씨")

    fun analyzeAndFilter(rawInput: String): ModerationResult {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return ModerationResult(
                status = ModerationStatus.BLOCKED,
                cleanedText = "",
                category = NoteCategory.CHEER_24H,
                noticeMessage = "음성 내용이 비어 있어요. 다시 말씀해 주세요."
            )
        }

        // 1. 명백한 비방/보복운전 유도 차단
        if (severeAbuseKeywords.any { trimmed.contains(it) }) {
            return ModerationResult(
                status = ModerationStatus.BLOCKED,
                cleanedText = trimmed,
                category = NoteCategory.SAFETY_3H,
                noticeMessage = "AI 안전 필터: 비방·위협 표현이 감지되어 등록이 차단되었습니다."
            )
        }

        var processedText = trimmed
        var wasPurified = false
        val reasons = mutableListOf<String>()

        // 2. 차량번호 / 전화번호 자동 마스킹 (익명성 및 개인정보 보호)
        if (licensePlateRegex.containsMatchIn(processedText)) {
            processedText = licensePlateRegex.replace(processedText, "[차량번호 보호됨]")
            wasPurified = true
            reasons.add("특정 차량번호 마스킹")
        }
        if (phoneRegex.containsMatchIn(processedText)) {
            processedText = phoneRegex.replace(processedText, "[연락처 보호됨]")
            wasPurified = true
            reasons.add("개인 연락처 마스킹")
        }

        // 3. 거친 불평 표현을 다정한 도로 안내 문장으로 AI 순화
        if (mildComplaintKeywords.any { processedText.contains(it) }) {
            processedText = "앞 구간 흐름이 조금 더딘니다. 서로 양보하며 안전하게 가요."
            wasPurified = true
            reasons.add("감정 표현을 다정한 배려 문장으로 순화")
        }

        // 4. 카테고리 및 유효 수명 자동 분류
        val category = classifyCategory(processedText)

        val status = if (wasPurified) ModerationStatus.PURIFIED else ModerationStatus.APPROVED
        val notice = if (wasPurified) {
            "AI 다정 필터 작동 (${reasons.joinToString(", ")}) · [${category.label} / ${category.lifespanLabel}]로 분류됨"
        } else {
            "AI 안전 검증 완료 · [${category.label} / ${category.lifespanLabel}]로 자동 분류됨"
        }

        return ModerationResult(
            status = status,
            cleanedText = processedText,
            category = category,
            noticeMessage = notice
        )
    }

    private fun classifyCategory(text: String): NoteCategory {
        return when {
            safetyKeywords.any { text.contains(it) } -> NoteCategory.SAFETY_3H
            tipKeywords.any { text.contains(it) } -> NoteCategory.TIP_30D
            vibeKeywords.any { text.contains(it) } -> NoteCategory.VIBE_7D
            else -> NoteCategory.CHEER_24H
        }
    }
}
