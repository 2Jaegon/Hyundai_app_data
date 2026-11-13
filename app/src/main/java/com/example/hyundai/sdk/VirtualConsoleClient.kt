package com.example.hyundai.sdk

import com.example.hyundai.vehicle.ConsoleStateParser
import com.example.hyundai.vehicle.ConsoleVehicleState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pleos Connect 가상 콘솔(`http://127.0.0.1:8088`)의 차량 상태를 1초마다 읽는다. 읽기만 하고 아무 값도 쓰지 않는다.
 *
 * 콘솔은 에뮬레이터 안에서만 돈다. 실제 차나 콘솔이 꺼진 에뮬레이터에서는 연결이 안 되므로,
 * 세 번 연달아 실패하면 5초 간격으로 늦춰 배터리와 CPU를 아낀다.
 * [onState]는 메인 스레드에서 불리며, 연결이 안 되면 null을 받는다.
 */
class VirtualConsoleClient(
    private val scope: CoroutineScope,
    private val onState: (ConsoleVehicleState?) -> Unit,
    private val stateUrl: String = DEFAULT_STATE_URL
) {
    init {
        // 평문 HTTP는 이 기기 자신에게만 허용한다(network_security_config와 같은 범위).
        require(stateUrl.startsWith("http://127.0.0.1:")) { "virtual console must be on this device" }
    }

    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var failures = 0
            while (isActive) {
                val state = withContext(Dispatchers.IO) { fetch() }
                failures = if (state == null) failures + 1 else 0
                // 처음 실패 한두 번은 일시적일 수 있어서, 세 번째부터 "연결 안 됨"으로 알린다.
                if (state != null || failures >= FAILURES_BEFORE_OFFLINE) onState(state)
                delay(if (failures >= FAILURES_BEFORE_OFFLINE) SLOW_POLL_MS else POLL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun fetch(): ConsoleVehicleState? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(stateUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 700
                readTimeout = 900
                useCaches = false
                instanceFollowRedirects = false
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > MAX_BYTES) return null
                String(bytes, Charsets.UTF_8)
            }
            ConsoleStateParser.parse(body)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        const val BASE_URL = "http://127.0.0.1:8088/"
        private const val DEFAULT_STATE_URL = "http://127.0.0.1:8088/vehicle/state"
        private const val POLL_MS = 1_000L
        private const val SLOW_POLL_MS = 5_000L
        private const val FAILURES_BEFORE_OFFLINE = 3
        private const val MAX_BYTES = 512 * 1024
    }
}
