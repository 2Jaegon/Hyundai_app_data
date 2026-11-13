package com.example.hyundai.ui.pet

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import com.example.hyundai.R
import com.example.hyundai.model.GearState
import com.example.hyundai.model.MannerTurnPhase
import com.example.hyundai.viewmodel.AppUiState

/**
 * 첨부된 3×3 픽셀아트 푸들 시트의 9가지 포즈.
 * - 우측 열(Col 2): 매너점수(레벨)에 따른 성장 단계 ([PUPPY] Lv.1 → [ADULT] Lv.2~3 → [SENIOR] Lv.4+)
 * - 좌측·중앙 열(Col 0, 1): 주행 및 상호작용 상황에 따른 포즈 ([PLAY], [SLEEP], [RUN], [EAT], [BEG], [HAPPY])
 */
enum class PetPose(
    val label: String,
    @DrawableRes val drawableRes: Int,
    val gridRow: Int,
    val gridCol: Int
) {
    /** (0, 0) Alternative Poses: 공을 굴리며 노는 평온·여유 포즈 */
    PLAY("공놀이", R.drawable.pet_pose_play, 0, 0),

    /** (0, 1) Sleeping: 쿠션 침대 위에서 Z Z 꿀잠 자는 휴식·신호대기 포즈 */
    SLEEP("꿀잠", R.drawable.pet_pose_sleep, 0, 1),

    /** (0, 2) Puppy: Lv.1 아기 강아지 기본 포즈 */
    PUPPY("아기 제로", R.drawable.pet_pose_puppy, 0, 2),

    /** (1, 0) Running: 주행 중 차와 함께 힘차게 달리는 포즈 */
    RUN("신나게 질주", R.drawable.pet_pose_run, 1, 0),

    /** (1, 1) Eating: 매너 방향전환 인증·보상 획득 시 맛있게 간식을 먹는 포즈 */
    EAT("간식 냠냠", R.drawable.pet_pose_eat, 1, 1),

    /** (1, 2) Adult: Lv.2~3 성견 강아지 기본 포즈 */
    ADULT("성견 제로", R.drawable.pet_pose_adult, 1, 2),

    /** (2, 0) Begging: 깜빡이 유지 대기·음성 녹음 경청·쪽지 승인 대기 시 두 발로 서서 기다리는 포즈 */
    BEG("귀 쫑긋 대기", R.drawable.pet_pose_beg, 2, 0),

    /** (2, 1) Happy: 쪽지 청취·공감(좋아요)·레벨업 시 하트와 별·꽃이 피어나는 행복 포즈 */
    HAPPY("행복 만점", R.drawable.pet_pose_happy, 2, 1),

    /** (2, 2) Senior: Lv.4 이상 안경과 수염을 단 베테랑 매너견 포즈 */
    SENIOR("시니어 제로", R.drawable.pet_pose_senior, 2, 2);

    companion object {
        /** 매너점수(레벨)에 따른 기본 성장 단계 포즈 (3×3 그리드 우측 열). */
        fun forLevel(level: Int): PetPose = when {
            level <= 1 -> PUPPY
            level in 2..3 -> ADULT
            else -> SENIOR
        }

        /**
         * 현재 주행/정차 상황과 매너점수(레벨)를 종합해 9개 그리드 중 알맞은 포즈를 결정한다.
         */
        fun resolve(
            level: Int,
            gear: GearState = GearState.P,
            speedKmh: Float = 0f,
            headlampOn: Boolean = false,
            mannerPhase: MannerTurnPhase = MannerTurnPhase.IDLE,
            holdingNote: Boolean = false,
            excited: Boolean = false,
            waitingForVoiceApproval: Boolean = false,
            isRecordingStt: Boolean = false,
            playingWithBall: Boolean = false
        ): PetPose = when {
            holdingNote || excited -> HAPPY
            mannerPhase == MannerTurnPhase.VERIFIED -> EAT
            isRecordingStt || waitingForVoiceApproval ||
                mannerPhase == MannerTurnPhase.SIGNAL_ON_HOLDING ||
                mannerPhase == MannerTurnPhase.READY_FOR_HEADING -> BEG
            playingWithBall || mannerPhase == MannerTurnPhase.COOLDOWN -> PLAY
            (gear == GearState.D && speedKmh < 1f) || (gear != GearState.D && headlampOn) -> SLEEP
            gear == GearState.D && speedKmh >= 1f -> RUN
            else -> forLevel(level)
        }

        /** [AppUiState]에서 현재 상황 및 매너점수(레벨)에 맞는 포즈를 계산한다. */
        fun fromState(
            state: AppUiState,
            excited: Boolean = false,
            playingWithBall: Boolean = false
        ): PetPose = resolve(
            level = state.activePet.level,
            gear = state.telemetry.gear,
            speedKmh = state.telemetry.speedKmh,
            headlampOn = state.telemetry.headlampOn,
            mannerPhase = state.evaluation.mannerPhase,
            holdingNote = state.heldNote != null || state.isTtsSpeaking,
            excited = excited || state.isTtsSpeaking || state.mannerSparkleActive,
            waitingForVoiceApproval = state.telemetry.gear == GearState.D &&
                (state.voiceHudPrompt != null || (!state.autoPlayTts && state.activeIncomingNote != null)) &&
                !state.isTtsSpeaking && !state.mannerSparkleActive,
            isRecordingStt = state.isRecordingStt || state.showVoiceComposeDialog,
            playingWithBall = playingWithBall
        )
    }
}

/** 레벨에 따라 결정되는 성장 단계 및 장식 상태. */
internal data class PetLook(
    val scarf: Boolean,
    val blush: Boolean,
    val crown: Boolean,
    val stagePose: PetPose = PetPose.PUPPY
) {
    companion object {
        fun forLevel(level: Int) = PetLook(
            scarf = level >= 2,
            blush = level >= 3,
            crown = level >= 4,
            stagePose = PetPose.forLevel(level)
        )
    }
}

/**
 * 온길의 마스코트 강아지 '제로(Zero)'.
 * 9개 그리드 픽셀아트 푸들 디자인을 사용하여 매너점수(레벨 1 아기 → 레벨 2~3 성견 → 레벨 4+ 시니어)와
 * 주행·쪽지·보상 상황(공놀이, 꿀잠, 질주, 간식 먹기, 귀 쫑긋 대기, 행복 만점)에 따라 모습이 바뀐다.
 *
 * [spin]이나 [excited]가 true일 때만 부드럽게 통통 튀며,
 * 주행 중 평상시에는 지도 프레임 저하를 막기 위해 정지 상태로 선명하게 그린다.
 */
@Composable
fun PetModel(
    accent: Color,
    modifier: Modifier = Modifier,
    level: Int = 2,
    spin: Boolean = true,
    excited: Boolean = false,
    holdingNote: Boolean = false,
    pose: PetPose? = null
) {
    val resolvedPose = pose ?: PetPose.resolve(
        level = level,
        holdingNote = holdingNote,
        excited = excited
    )

    val bitmap = ImageBitmap.imageResource(id = resolvedPose.drawableRes)

    if (!spin && !excited) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Image(
                bitmap = bitmap,
                contentDescription = resolvedPose.label,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.None,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    val transition = rememberInfiniteTransition(label = "pet_zero_grid")
    val bob = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (excited) 340 else 1200, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "bob"
    )

    val bobPx = kotlin.math.round(if (excited) -8f * bob.value else -4f * bob.value)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            bitmap = bitmap,
            contentDescription = resolvedPose.label,
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.None,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = bobPx
                }
        )
    }
}
