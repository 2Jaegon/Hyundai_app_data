package com.example.hyundai.nav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 경로 좌표를 가져온다.
 *
 * NaviHelper SDK는 회전 정보(TBT)와 도착지는 주지만 경로 폴리라인은 주지 않는다.
 * 그래서 지도에 그릴 선은 OSRM 공개 서버(키 없음, 데모/프로토타입 용도)에서 받는다.
 * 네트워크가 막히면 [fallback]의 오프라인 데모 경로로 대신한다.
 *
 * 프라이버시: 출발/도착 좌표가 제3자 서버로 나간다. 약 10m 단위(소수점 4자리)로 줄여서 보내며,
 * 출시 단계에서는 자체 라우팅 서버로 바꾸거나 사용자 동의를 받아야 한다.
 */
class RouteRepository(
    private val baseUrl: String = "https://router.project-osrm.org"
) {
    init {
        require(baseUrl.startsWith("https://")) { "route server must use https" }
    }

    suspend fun fetch(origin: GeoPoint, destination: GeoPoint): RouteData? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TOTAL_TIMEOUT_MS) { request(origin, destination) }
    }

    private fun request(origin: GeoPoint, destination: GeoPoint): RouteData? {
        val url = String.format(
            Locale.US,
            "%s/route/v1/driving/%.4f,%.4f;%.4f,%.4f?overview=full&geometries=geojson&steps=true",
            baseUrl, origin.lng, origin.lat, destination.lng, destination.lat
        )
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 12_000
                setRequestProperty("User-Agent", "TodakGil-Prototype")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            if (conn.contentLengthLong > MAX_BODY_BYTES) return null
            val body = conn.inputStream.use { readLimited(it) } ?: return null
            OsrmRouteParser.parse(body)
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** 응답을 [MAX_BODY_BYTES]까지만 읽는다. 넘으면 null. */
    private fun readLimited(input: java.io.InputStream): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_BODY_BYTES) return null
            out.write(buffer, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    companion object {
        private const val MAX_BODY_BYTES = 5 * 1024 * 1024
        private const val TOTAL_TIMEOUT_MS = 20_000L

        /** 성수대교 동단 근처 출발 (앱 기본 위치). */
        val DEFAULT_ORIGIN = GeoPoint(37.5445, 127.0440)

        /** 판교역 (출퇴근 밴드 기본 목적지). */
        val DEFAULT_DESTINATION = GeoPoint(37.3947, 127.1112)

        /**
         * 오프라인 데모용 경로. 도로에 맞춘 선이 아니라 화면 구성을 보여 주기 위한 대략적인 선이다.
         * 시작점에서 동남쪽으로 약 3.4km, 중간에 우회전과 좌회전이 한 번씩 있다.
         */
        fun fallback(origin: GeoPoint = DEFAULT_ORIGIN): RouteData {
            val a = origin
            val b = GeoMath.destination(a, 95.0, 1_200.0)
            val c = GeoMath.destination(b, 170.0, 1_000.0)
            val d = GeoMath.destination(c, 95.0, 1_200.0)
            val track = RouteTrack(listOf(a, b, c, d))
            val steps = listOf(
                RouteStep(GeoMath.distanceM(a, b), TurnIcon.RIGHT, ""),
                RouteStep(GeoMath.distanceM(a, b) + GeoMath.distanceM(b, c), TurnIcon.LEFT, ""),
                RouteStep(track.totalM, TurnIcon.ARRIVE, "")
            )
            return RouteData(track, steps)
        }
    }
}
