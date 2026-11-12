package com.example.hyundai.domain

import com.example.hyundai.model.GearState
import com.example.hyundai.model.TurnSignalState
import com.example.hyundai.nav.GeoMath
import com.example.hyundai.nav.GeoPoint

/**
 * Pleos Vehicle SDK(기어, 방향지시등, 브레이크, 주행거리, 전조등) 및
 * FusedLocation SDK(위도, 경도, 방위각) 원시 신호를 실시간 주행 텔레메트리와
 * 안전·매너 평가 지표(속도, 가감속도, 깜빡이 유지 시간)로 변환하는 순수 도메인 추적기.
 *
 * 특징:
 * 1. FusedLocation `LocationData`에는 속도(speed) 필드가 없으므로 연속 좌표 간 변위($\Delta d / \Delta t$)로
 *    실시간 속도(km/h)와 초당 속도 변화율(가감속도 km/h/s)을 산출한다.
 * 2. GPS 튀는 값(Outlier > 200 km/h)과 정차 미세 흔들림(< 1.5 m)을 걸러낸다.
 * 3. 방향지시등 점등 시각을 기록해 실제 유지 시간(초)을 계산한다.
 */
class VehicleSignalTracker {

    data class SpeedSample(
        val speedKmh: Float,
        val accelKmhPerSec: Float,
        val deltaKm: Float,
        val isHarshAccel: Boolean,
        val isHarshBrake: Boolean
    )

    private var lastPoint: GeoPoint? = null
    private var lastLocationAtMs: Long = 0L
    private var lastSpeedKmh: Float = 0f

    private var activeSignal: TurnSignalState = TurnSignalState.OFF
    private var signalStartedAtMs: Long = 0L

    private var lastOdometerKm: Double? = null

    /**
     * 연속된 위치 좌표와 시각(ms)으로 실시간 속도(km/h), 가속도(km/h/s), 이동 거리(km)를 계산한다.
     * 첫 위치 샘플이거나 시간 간격이 너무 짧거나 길면(`0.2s .. 30s` 밖) null을 반환한다.
     */
    fun onLocationSample(
        lat: Double,
        lng: Double,
        timestampMs: Long,
        brakePressed: Boolean
    ): SpeedSample? {
        val current = GeoPoint(lat, lng)
        val prev = lastPoint
        val prevTime = lastLocationAtMs
        lastPoint = current
        lastLocationAtMs = timestampMs

        if (prev == null || prevTime <= 0L) return null
        val dtSec = (timestampMs - prevTime) / 1000.0
        if (dtSec < 0.2 || dtSec > 30.0) return null

        val distM = GeoMath.distanceM(prev, current)
        // 정차 중 GPS 오차(1.2m 미만 이동)는 속도 0으로 처리
        val rawSpeedKmh = if (distM < 1.2) {
            0f
        } else {
            ((distM / dtSec) * 3.6).toFloat()
        }

        // 비정상적인 GPS 점프(> 200 km/h)는 무시하고 직전 속도를 유지
        if (rawSpeedKmh > 200f) return null

        // 급격한 노이즈 완화를 위한 경량 지수 평활(EMA)
        val smoothedSpeedKmh = if (rawSpeedKmh == 0f) {
            0f
        } else {
            (lastSpeedKmh * 0.3f + rawSpeedKmh * 0.7f)
        }

        val accelKmhPerSec = ((smoothedSpeedKmh - lastSpeedKmh) / dtSec.toFloat())
        lastSpeedKmh = smoothedSpeedKmh

        val deltaKm = (distM / 1000.0).toFloat()
        val isHarshAccel = accelKmhPerSec >= HARSH_ACCEL_THRESHOLD_KMH_PER_S
        val isHarshBrake = accelKmhPerSec <= HARSH_BRAKE_THRESHOLD_KMH_PER_S &&
            (brakePressed || accelKmhPerSec <= -12f)

        return SpeedSample(
            speedKmh = smoothedSpeedKmh,
            accelKmhPerSec = accelKmhPerSec,
            deltaKm = deltaKm,
            isHarshAccel = isHarshAccel,
            isHarshBrake = isHarshBrake
        )
    }

    /**
     * 차량 Odometer(누적 주행거리 km) 수신 시 직전 값과의 차이($\Delta \text{km}$)를 반환한다.
     */
    fun onOdometerUpdated(odometerKm: Double): Float {
        val prev = lastOdometerKm
        lastOdometerKm = odometerKm
        if (prev == null || odometerKm <= prev) return 0f
        val delta = (odometerKm - prev).toFloat()
        // 단일 콜백에서 비정상적인 점프(> 5km)는 초기 동기화로 간주하여 적립에서 제외
        return if (delta <= 5.0f) delta else 0f
    }

    /**
     * 방향지시등 상태 변경 시 호출되어, 현재까지 유지된 시간(초)을 반환한다.
     */
    fun onTurnSignalChanged(newSignal: TurnSignalState, nowMs: Long): Float {
        if (newSignal == TurnSignalState.LEFT || newSignal == TurnSignalState.RIGHT) {
            if (activeSignal != newSignal) {
                activeSignal = newSignal
                signalStartedAtMs = nowMs
                return 0f
            }
            return ((nowMs - signalStartedAtMs).coerceAtLeast(0L)) / 1000f
        } else {
            val heldSec = if (activeSignal != TurnSignalState.OFF && signalStartedAtMs > 0L) {
                ((nowMs - signalStartedAtMs).coerceAtLeast(0L)) / 1000f
            } else {
                0f
            }
            activeSignal = TurnSignalState.OFF
            signalStartedAtMs = 0L
            return heldSec
        }
    }

    /**
     * 현재 방향지시등이 켜져 있을 때 경과 시간(초)을 조회한다.
     */
    fun currentSignalHoldSeconds(nowMs: Long): Float {
        if (activeSignal == TurnSignalState.OFF || signalStartedAtMs <= 0L) return 0f
        return ((nowMs - signalStartedAtMs).coerceAtLeast(0L)) / 1000f
    }

    companion object {
        const val HARSH_ACCEL_THRESHOLD_KMH_PER_S = 7.5f
        const val HARSH_BRAKE_THRESHOLD_KMH_PER_S = -10.0f

        /** Pleos VehicleGear 이름 또는 상수를 앱의 [GearState]로 변환한다. */
        fun mapVehicleGear(gearName: String?): GearState = when (gearName?.uppercase()) {
            "GEAR_PARK", "P", "PARK" -> GearState.P
            "GEAR_REVERSE", "R", "REVERSE" -> GearState.R
            "GEAR_NEUTRAL", "N", "NEUTRAL" -> GearState.N
            "GEAR_DRIVE", "D", "DRIVE",
            "GEAR_1", "GEAR_2", "GEAR_3", "GEAR_4", "GEAR_5",
            "GEAR_6", "GEAR_7", "GEAR_8", "GEAR_9" -> GearState.D
            else -> GearState.D
        }

        /** Pleos TurnSignalValue 이름을 앱의 [TurnSignalState]로 변환한다. */
        fun mapTurnSignal(signalName: String?): TurnSignalState = when (signalName?.uppercase()) {
            "LEFT" -> TurnSignalState.LEFT
            "RIGHT" -> TurnSignalState.RIGHT
            "OFF", null -> TurnSignalState.OFF
            else -> TurnSignalState.HAZARD
        }

        /** Pleos LightState(전조등)가 켜져 있는지 판별한다 (`ON`일 때 true). */
        fun isHeadlampOn(lightStateName: String?): Boolean =
            lightStateName?.uppercase() == "ON"
    }
}
