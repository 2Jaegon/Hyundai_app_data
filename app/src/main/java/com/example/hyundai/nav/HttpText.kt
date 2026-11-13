package com.example.hyundai.nav

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** 작은 JSON 응답을 받아 오는 HTTP 도우미. 실패하거나 응답이 너무 크면 null을 준다. 호출하는 쪽에서 IO 스레드로 옮긴다. */
internal object HttpText {

    const val USER_AGENT = "OnGil-Prototype/1.0"

    fun get(
        url: String,
        maxBytes: Int = DEFAULT_MAX_BYTES,
        connectTimeoutMs: Int = 8_000,
        readTimeoutMs: Int = 12_000
    ): String? = request(url, null, maxBytes, connectTimeoutMs, readTimeoutMs)

    /** `application/x-www-form-urlencoded` 본문으로 POST 한다. */
    fun postForm(url: String, form: String, maxBytes: Int = DEFAULT_MAX_BYTES): String? =
        request(url, form, maxBytes, 8_000, 15_000)

    private fun request(
        url: String,
        form: String?,
        maxBytes: Int,
        connectTimeoutMs: Int,
        readTimeoutMs: Int
    ): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
                if (form != null) {
                    requestMethod = "POST"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
                    outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
                }
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            if (conn.contentLengthLong > maxBytes) return null
            conn.inputStream.use { readLimited(it, maxBytes) }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** 응답을 [maxBytes]까지만 읽는다. 넘으면 null. */
    private fun readLimited(input: InputStream, maxBytes: Int): String? {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > maxBytes) return null
            out.write(buffer, 0, n)
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private const val DEFAULT_MAX_BYTES = 5 * 1024 * 1024
}
