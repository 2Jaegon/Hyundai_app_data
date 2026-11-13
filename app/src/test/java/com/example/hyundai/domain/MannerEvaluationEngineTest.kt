package com.example.hyundai.domain

import com.example.hyundai.model.DrivingEvaluationState
import com.example.hyundai.model.MannerTurnPhase
import com.example.hyundai.model.TurnSignalState
import org.junit.Assert.assertEquals
import org.junit.Test

class MannerEvaluationEngineTest {

    @Test
    fun signalHeldThroughARouteTurnIsVerifiedByOnceASecondChecks() {
        // 시연 주행에서는 깜빡이를 켠 뒤 1초마다 유지 시간과 진행 방향을 다시 넣는다.
        val engine = MannerEvaluationEngine()
        var state = DrivingEvaluationState()
        state = engine.onTurnSignalAndHeadingChanged(state, TurnSignalState.LEFT, 90f, 0f).updatedState
        state = engine.onTurnSignalAndHeadingChanged(state, TurnSignalState.LEFT, 90f, 1f).updatedState
        assertEquals(MannerTurnPhase.SIGNAL_ON_HOLDING, state.mannerPhase)
        assertEquals(1f, state.signalHoldSeconds)

        state = engine.onTurnSignalAndHeadingChanged(state, TurnSignalState.LEFT, 90f, 2f).updatedState
        assertEquals(MannerTurnPhase.READY_FOR_HEADING, state.mannerPhase)

        // 경로가 왼쪽으로 35도 꺾이는 지점을 지난다.
        val turned = engine.onTurnSignalAndHeadingChanged(state, TurnSignalState.LEFT, 55f, 3f)
        assertEquals(MannerTurnPhase.VERIFIED, turned.updatedState.mannerPhase)
        assertEquals(15, turned.expGained)
        assertEquals(state.completedMannerTurns + 1, turned.updatedState.completedMannerTurns)
    }

    @Test
    fun signalHeldOnAStraightRoadEarnsNothing() {
        val engine = MannerEvaluationEngine()
        var state = DrivingEvaluationState()
        for (sec in 0..5) {
            val step = engine.onTurnSignalAndHeadingChanged(state, TurnSignalState.RIGHT, 180f, sec.toFloat())
            assertEquals(0, step.expGained)
            state = step.updatedState
        }
        assertEquals(MannerTurnPhase.READY_FOR_HEADING, state.mannerPhase)
    }
}
