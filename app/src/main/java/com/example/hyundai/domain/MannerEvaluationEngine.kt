package com.example.hyundai.domain

import com.example.hyundai.model.DrivingEvaluationState
import com.example.hyundai.model.MannerTurnPhase
import com.example.hyundai.model.TurnSignalState
import kotlin.math.abs

/**
 * 0점에서부터 쌓이는 긍정 강화형(Positive Reinforcement) 안전·매너운전 평가 엔진.
 *
 * 어뷰징 방지(Anti-Abuse) 핵심 설계:
 * 1. Safe Drive: 단순 시간 경과가 아니라 실제 주행거리(Odometer 변화량) 1.0km 도달 시마다 +10 EXP / +5 P 지급.
 * 2. Smooth Drive: 2.0km 구간 윈도우 동안 급가속(> +7km/h/s)·급제동(< -10km/h/s + Brake ON)이 0회일 때 완주 보너스 +20 EXP / +10 P 지급.
 *    위험 행동 발생 시 기존 점수를 깎지 않고(감점 0), 해당 2km 윈도우의 보너스만 리셋.
 * 3. Manner Drive: 깜빡이를 단순 점멸(ON/OFF 반복)하는 어뷰징을 막기 위해
 *    [깜빡이 ON 1.5초 이상 유지] + [실제 차량 방위각(Heading) 25° 이상 일치 회전 또는 차선변경]이 모두 확인될 때만
 *    +15 EXP / +10 P 지급 후 45초 쿨타임 적용.
 */
class MannerEvaluationEngine {

    data class EvaluationStepResult(
        val updatedState: DrivingEvaluationState,
        val expGained: Int = 0,
        val pointsGained: Int = 0,
        val ticketGained: Int = 0,
        val eventBanner: String? = null,
        val petReactionSpeech: String? = null
    )

    private var signalStartHeadingDeg: Float? = null
    private var activeSignalDirection: TurnSignalState = TurnSignalState.OFF
    private var kmTowardsNextTicket: Float = 4.2f // 10km 마다 +1 티켓

    fun onDistanceDriven(
        currentState: DrivingEvaluationState,
        deltaKm: Float,
        currentSpeedKmh: Float
    ): EvaluationStepResult {
        if (deltaKm <= 0f || currentSpeedKmh < 5f) {
            return EvaluationStepResult(currentState)
        }

        var exp = 0
        var points = 0
        var tickets = 0
        val logs = currentState.recentEvaluationLogs.toMutableList()
        var banner: String? = null
        var petSpeech: String? = null

        // 1. Safe Drive (1.0 km 단위)
        var newSafeProgress = currentState.safeBlockProgressKm + deltaKm
        var newSafeBlocks = currentState.completedSafeBlocks
        while (newSafeProgress >= currentState.safeBlockTargetKm) {
            newSafeProgress -= currentState.safeBlockTargetKm
            newSafeBlocks += 1
            exp += 10
            points += 5
            val msg = "[Safe] 1.0km 안정 주행 완주 (#$newSafeBlocks) → +10 EXP, +5 P"
            logs.add(0, msg)
            banner = "안전 주행 1km 달성! (+10 EXP, +5 P)"
            petSpeech = "차분하게 달려 주셔서 마음이 편해요."
        }

        // 2. Smooth Drive (2.0 km 구간 보너스)
        var newSmoothProgress = currentState.smoothWindowProgressKm + deltaKm
        var newSmoothWindows = currentState.completedSmoothWindows
        var harshInWindow = currentState.harshEventsInCurrentWindow
        var smoothFeedback = currentState.lastSmoothFeedback

        if (newSmoothProgress >= currentState.smoothWindowTargetKm) {
            newSmoothProgress -= currentState.smoothWindowTargetKm
            if (harshInWindow == 0) {
                newSmoothWindows += 1
                exp += 20
                points += 10
                val msg = "[Smooth] 2.0km 무급제동·무급가속 완주 보너스 (#$newSmoothWindows) → +20 EXP, +10 P"
                logs.add(0, msg)
                banner = "Smooth Drive 2km 보너스 획득! (+20 EXP, +10 P)"
                petSpeech = "물컵의 물도 안 흔들릴 만큼 부드러워요."
                smoothFeedback = "2.0km 구간 보너스 달성 완료! 새 2km 구간을 시작합니다."
            } else {
                smoothFeedback = "이번 2km 구간은 급가감속(${harshInWindow}회)으로 보너스만 쉬어갑니다 (감점 없음!)."
                harshInWindow = 0
            }
        }

        // 3. 10km 주행 시 추가 쪽지 작성권 +1 지급
        kmTowardsNextTicket += deltaKm
        if (kmTowardsNextTicket >= 10.0f) {
            kmTowardsNextTicket -= 10.0f
            tickets += 1
            logs.add(0, "[Ticket] 누적 10km 안전주행 달성 → 쪽지 작성권 +1개 충전!")
            banner = "10km 주행 달성! 쪽지 작성권이 1장 충전되었어요 💌"
        }

        return EvaluationStepResult(
            updatedState = currentState.copy(
                safeBlockProgressKm = newSafeProgress,
                completedSafeBlocks = newSafeBlocks,
                smoothWindowProgressKm = newSmoothProgress,
                completedSmoothWindows = newSmoothWindows,
                harshEventsInCurrentWindow = harshInWindow,
                lastSmoothFeedback = smoothFeedback,
                recentEvaluationLogs = logs.take(8)
            ),
            expGained = exp,
            pointsGained = points,
            ticketGained = tickets,
            eventBanner = banner,
            petReactionSpeech = petSpeech
        )
    }

    fun onHarshDrivingDetected(
        currentState: DrivingEvaluationState,
        isHarshBrake: Boolean,
        deltaSpeedKmhPerSec: Float
    ): EvaluationStepResult {
        val logs = currentState.recentEvaluationLogs.toMutableList()
        val kind = if (isHarshBrake) "급제동 감지(${deltaSpeedKmhPerSec.toInt()}km/h/s)" else "급가속 감지(+${deltaSpeedKmhPerSec.toInt()}km/h/s)"
        logs.add(0, "[Smooth 안내] $kind — 감점은 없으며 현재 2km 구간을 다시 시작합니다.")

        return EvaluationStepResult(
            updatedState = currentState.copy(
                smoothWindowProgressKm = 0f,
                harshEventsInCurrentWindow = currentState.harshEventsInCurrentWindow + 1,
                totalHarshEventsToday = currentState.totalHarshEventsToday + 1,
                lastSmoothFeedback = "$kind 감지! 감점 없이 0km부터 부드러운 주행 보너스를 다시 채웁니다.",
                recentEvaluationLogs = logs.take(8)
            ),
            eventBanner = "$kind: 감점은 없어요! 다음 2km 구간에서 보너스에 다시 도전해요 🌱",
            petReactionSpeech = "앗, 놀랐어요! 조금만 여유 있게 가 볼까요?"
        )
    }

    fun onTurnSignalAndHeadingChanged(
        currentState: DrivingEvaluationState,
        newSignal: TurnSignalState,
        currentHeadingDeg: Float,
        holdDurationSec: Float
    ): EvaluationStepResult {
        val logs = currentState.recentEvaluationLogs.toMutableList()

        // 쿨타임 중일 때
        if (currentState.cooldownRemainingSec > 0 && newSignal != TurnSignalState.OFF) {
            return EvaluationStepResult(
                updatedState = currentState.copy(
                    mannerPhase = MannerTurnPhase.COOLDOWN,
                    signalHoldSeconds = holdDurationSec
                )
            )
        }

        // 깜빡이를 켠 상태
        if (newSignal == TurnSignalState.LEFT || newSignal == TurnSignalState.RIGHT) {
            if (activeSignalDirection != newSignal) {
                activeSignalDirection = newSignal
                signalStartHeadingDeg = currentHeadingDeg
            }
            val startHeading = signalStartHeadingDeg ?: currentHeadingDeg
            val deltaHeading = computeSignedHeadingDelta(startHeading, currentHeadingDeg)
            val absDelta = abs(deltaHeading)

            val isDirectionMatching = (newSignal == TurnSignalState.LEFT && deltaHeading <= -25f) ||
                (newSignal == TurnSignalState.RIGHT && deltaHeading >= 25f) ||
                absDelta >= 25f

            return if (holdDurationSec >= 1.5f && isDirectionMatching) {
                // 정당한 매너 방향전환 인증!
                activeSignalDirection = TurnSignalState.OFF
                signalStartHeadingDeg = null
                val newCount = currentState.completedMannerTurns + 1
                logs.add(
                    0,
                    "[Manner] ${newSignal.label} (${String.format("%.1f", holdDurationSec)}초) + 방위각 ${absDelta.toInt()}° 변화 일치 → +15 EXP, +10 P"
                )
                EvaluationStepResult(
                    updatedState = currentState.copy(
                        mannerPhase = MannerTurnPhase.VERIFIED,
                        signalHoldSeconds = holdDurationSec,
                        headingDeltaDeg = absDelta,
                        completedMannerTurns = newCount,
                        cooldownRemainingSec = 45,
                        recentEvaluationLogs = logs.take(8)
                    ),
                    expGained = 15,
                    pointsGained = 10,
                    eventBanner = "매너 방향지시등 인증 완료! (+15 EXP, +10 P)",
                    petReactionSpeech = "미리 깜빡이를 켜 주셔서 뒤차도 고마워했을 거예요."
                )
            } else {
                val phase = if (holdDurationSec >= 1.5f) {
                    MannerTurnPhase.READY_FOR_HEADING
                } else {
                    MannerTurnPhase.SIGNAL_ON_HOLDING
                }
                EvaluationStepResult(
                    updatedState = currentState.copy(
                        mannerPhase = phase,
                        signalHoldSeconds = holdDurationSec,
                        headingDeltaDeg = absDelta
                    )
                )
            }
        }

        // 깜빡이를 끈 순간: 실제 방향 변화 없이 껐다면 어뷰징 방지 안내
        if (newSignal == TurnSignalState.OFF && activeSignalDirection != TurnSignalState.OFF) {
            val startHeading = signalStartHeadingDeg ?: currentHeadingDeg
            val absDelta = abs(computeSignedHeadingDelta(startHeading, currentHeadingDeg))
            activeSignalDirection = TurnSignalState.OFF
            signalStartHeadingDeg = null

            if (absDelta < 20f) {
                val blocked = currentState.abuseBlockedCount + 1
                logs.add(
                    0,
                    "[어뷰징 방지] 깜빡이 점멸 후 실제 진행방향 변화(${absDelta.toInt()}° < 25°) 미감지 → EXP 미지급"
                )
                return EvaluationStepResult(
                    updatedState = currentState.copy(
                        mannerPhase = MannerTurnPhase.ABUSE_BLOCKED,
                        signalHoldSeconds = 0f,
                        headingDeltaDeg = absDelta,
                        abuseBlockedCount = blocked,
                        recentEvaluationLogs = logs.take(8)
                    ),
                    eventBanner = "어뷰징 방지 작동: 실제 방향전환(≥25°)이 확인되지 않아 EXP가 적립되지 않았습니다.",
                    petReactionSpeech = "깜빡이만 켜서는 포인트가 쌓이지 않아요."
                )
            }
        }

        return EvaluationStepResult(
            updatedState = currentState.copy(
                mannerPhase = if (currentState.cooldownRemainingSec > 0) MannerTurnPhase.COOLDOWN else MannerTurnPhase.IDLE,
                signalHoldSeconds = 0f
            )
        )
    }

    fun tickCooldown(currentState: DrivingEvaluationState): DrivingEvaluationState {
        if (currentState.cooldownRemainingSec <= 0) return currentState
        val next = (currentState.cooldownRemainingSec - 1).coerceAtLeast(0)
        return currentState.copy(
            cooldownRemainingSec = next,
            mannerPhase = if (next == 0) MannerTurnPhase.IDLE else currentState.mannerPhase
        )
    }

    private fun computeSignedHeadingDelta(startDeg: Float, currentDeg: Float): Float {
        var diff = (currentDeg - startDeg) % 360f
        if (diff > 180f) diff -= 360f
        if (diff < -180f) diff += 360f
        return diff
    }
}
