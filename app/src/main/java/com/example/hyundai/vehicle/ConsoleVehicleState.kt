package com.example.hyundai.vehicle

import com.example.hyundai.model.GearState
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Pleos Connect 가상 콘솔(에뮬레이터 안 `127.0.0.1:8088`)이 내보내는 차량 신호 중 내비 화면에 쓰는 것.
 *
 * 콘솔은 아무도 조작하지 않은 값도 기본값(`source: "default"`)으로 채워서 보낸다.
 * 그런 값까지 믿으면 시연 주행 속도가 0으로 덮이거나 배터리 100%가 계속 보이므로,
 * 영역마다 실제로 조작된 값만 남기고 나머지는 null로 둔다.
 */
data class ConsoleVehicleState(
    val speedKph: Float? = null,
    val gear: GearState? = null,
    val cruiseOn: Boolean = false,
    val cruiseTargetKph: Int? = null,
    val batteryPct: Int? = null,
    val rangeKm: Int? = null,
    val charging: Boolean = false,
    /** 켜진 경고등의 짧은 한국어 이름. 정보용 표시등(방향지시등, 전조등)은 넣지 않는다. */
    val warnings: List<String> = emptyList(),
    val toll: TollEvent? = null
)

/** 하이패스(ETCS) 마지막 결제. [key]가 바뀌면 새 결제다. */
data class TollEvent(
    val key: String,
    val gateName: String,
    val amountWon: Int?,
    val balanceWon: Int?
)

object ConsoleStateParser {

    fun parse(json: String): ConsoleVehicleState? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val controls = root.optJSONObject("controls")
        val drivingMode = root.optJSONObject("driving_mode")
        val ev = root.optJSONObject("ev_battery")
        val odometer = root.optJSONObject("odometer")

        val cruiseState = drivingMode?.takeIf { it.isLive() }?.optString("ad_mode_state").orEmpty()
        val cruiseOn = cruiseState.isNotEmpty() && cruiseState.lowercase() !in CRUISE_IDLE_STATES
        val targetMps = drivingMode?.optDouble("target_speed_mps", Double.NaN) ?: Double.NaN

        return ConsoleVehicleState(
            speedKph = controls?.takeIf { it.isLive() }
                ?.optDouble("speed_kph", Double.NaN)
                ?.takeIf { it.isFinite() && it >= 0.0 }
                ?.toFloat(),
            gear = parseGear(root.optJSONObject("gear")),
            cruiseOn = cruiseOn,
            cruiseTargetKph = if (cruiseOn && targetMps.isFinite() && targetMps > 0) (targetMps * 3.6).roundToInt() else null,
            batteryPct = ev?.takeIf { it.isLive() }
                ?.optDouble("state_of_charge", Double.NaN)
                ?.takeIf { it.isFinite() }
                ?.roundToInt()
                ?.coerceIn(0, 100),
            rangeKm = odometer?.takeIf { it.isLive() }
                ?.optDouble("remaining_range_m", Double.NaN)
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.let { (it / 1000.0).roundToInt() },
            charging = ev?.takeIf { it.isLive() }?.optBoolean("charging_activated") == true,
            warnings = parseWarnings(root.optJSONObject("telltales")),
            toll = parseToll(root.optJSONObject("etcs"))
        )
    }

    /** 콘솔 이름(Park/Reverse/Neutral/Drive) → 앱 기어. 모르는 값은 null. */
    fun mapGear(name: String?): GearState? = when (name?.trim()?.lowercase()) {
        "park", "p" -> GearState.P
        "reverse", "r" -> GearState.R
        "neutral", "n" -> GearState.N
        "drive", "d" -> GearState.D
        else -> null
    }

    private fun parseGear(gear: JSONObject?): GearState? {
        val actual = gear?.opt("actual") ?: return null
        val name = when (actual) {
            is JSONObject -> actual.optString("value").ifBlank { actual.optString("gear") }
            is String -> actual
            else -> null
        }
        return mapGear(name)
    }

    /** `actual`은 경고등 이름을 키로, 값은 `{state: "On"}` 또는 `"On"`으로 온다. 켜짐(On)·깜빡임(Blink)만 경고로 본다. */
    private fun parseWarnings(telltales: JSONObject?): List<String> {
        val actual = telltales?.optJSONObject("actual") ?: return emptyList()
        val out = ArrayList<String>()
        for (type in WARNING_LABELS.keys) {
            val value = actual.opt(type) ?: continue
            val state = when (value) {
                is JSONObject -> value.optString("state")
                is String -> value
                else -> ""
            }
            if (state.equals("On", ignoreCase = true) || state.equals("Blink", ignoreCase = true)) {
                out.add(WARNING_LABELS.getValue(type))
            }
        }
        return out
    }

    private fun parseToll(etcs: JSONObject?): TollEvent? {
        val tx = etcs?.optJSONObject("last_transaction") ?: return null
        val gate = tx.optString("tollgate_name")
            .ifBlank { tx.optString("tollgate_label") }
            .ifBlank { tx.optString("tollgate_id") }
            .ifBlank { "요금소" }
        val amount = tx.optDouble("amount_paid", Double.NaN).takeIf { it.isFinite() }?.roundToInt()
        val balance = etcs.optDouble("current_balance", Double.NaN).takeIf { it.isFinite() }?.roundToInt()
        // 결제마다 바뀌는 값(시각, 금액, 잔액)을 묶어 같은 결제를 두 번 알리지 않게 한다.
        val stamp = tx.optString("timestamp_ms").ifBlank { tx.optString("timestamp") }
        val key = listOf(stamp, gate, tx.optString("tollgate_type"), amount?.toString().orEmpty(), balance?.toString().orEmpty())
            .joinToString("|")
        return TollEvent(key = key, gateName = gate, amountWon = amount, balanceWon = balance)
    }

    /** 콘솔에서 실제로 조작했거나 에뮬레이션이 켜져 값이 흐르는 영역인지. */
    private fun JSONObject.isLive(): Boolean =
        optBoolean("emulator_active") || optBoolean("input_active") ||
            optString("source", "default").let { it.isNotEmpty() && it != "default" }

    private val CRUISE_IDLE_STATES = setOf("off", "ready", "standby", "unavailable", "none", "disabled")

    /** 운전자가 바로 알아야 하는 경고등만. 순서가 화면에 보이는 순서다. */
    private val WARNING_LABELS: Map<String, String> = linkedMapOf(
        "SeatBelt" to "안전벨트",
        "DoorOpen" to "문 열림",
        "ParkingBrake" to "주차 브레이크",
        "ElectronicParkingBrake" to "주차 브레이크",
        "Airbag" to "에어백 점검",
        "ABS" to "ABS 점검",
        "ESC" to "차체 자세 제어",
        "MDPS" to "조향 장치 점검",
        "TPMS" to "타이어 공기압",
        "EngineOilPressure" to "엔진오일 압력",
        "EngineTemperature" to "냉각수 온도",
        "LowVoltBattery" to "보조 배터리",
        "EVSystem" to "전기차 시스템",
        "MasterWarning" to "차량 점검"
    )
}
