package com.example.hyundai.ui.drive

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.hyundai.model.GearState
import com.example.hyundai.sdk.VirtualConsoleClient
import com.example.hyundai.ui.map.NavTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Pleos 가상 콘솔의 3D 고속도로 장면. 콘솔이 도는 에뮬레이터에서만 보인다.
 * 장면은 콘솔 서버에서 불러오고, 차가 얼마나 빨리 가는지는 온길의 주행 속도를 그대로 넘긴다.
 * [active]가 false(다른 화면이 덮음)면 WebView를 멈춰 지도·펫과 GPU를 나눠 쓰지 않게 한다.
 *
 * WebView는 처음 만들 때 엔진을 메인 스레드에서 불러온다. 에뮬레이터에서 이게 5초를 넘겨 ANR이 났다
 * (측정: provider 로드 약 3초 + Chromium 시작 약 2초). 그래서 provider는 백그라운드에서 먼저 불러 두고,
 * 끝난 뒤에만 WebView를 만든다.
 */
@Composable
fun Highway3dView(
    speedKmh: Float,
    gear: GearState,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var engineReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            // WebViewFactory가 provider APK를 읽고 클래스를 올리는 부분이 이 스레드에서 끝난다.
            runCatching { WebSettings.getDefaultUserAgent(context.applicationContext) }
        }
        engineReady = true
    }
    if (engineReady) {
        HighwayWebView(speedKmh, gear, active, modifier)
    } else {
        Box(modifier.background(Color(0xFF050A12))) { StatusBubble("3D 도로를 불러오는 중", Modifier.align(Alignment.Center)) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HighwayWebView(
    speedKmh: Float,
    gear: GearState,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>("3D 도로를 불러오는 중") }
    val html = remember(context) {
        context.assets.open("highway3d.html").bufferedReader().use { it.readText() }
    }
    val webView = remember(context) {
        WebView(context).apply {
            // AndroidView 기본값(WRAP_CONTENT)이면 WebView가 내용 높이 모드가 되어 CSS height:100%가 0이 되고 장면이 안 보인다(실측).
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            setBackgroundColor(android.graphics.Color.rgb(5, 10, 18))
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                    val text = message.message()
                    if (text.startsWith(STATUS_PREFIX)) {
                        status = text.removePrefix(STATUS_PREFIX).ifBlank { null }
                    } else if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR ||
                        message.messageLevel() == ConsoleMessage.MessageLevel.WARNING
                    ) {
                        // 장면(three.js) 쪽 오류는 로그로만 남겨 원인을 찾을 수 있게 한다.
                        Log.w(LOG_TAG, "${message.sourceId()}:${message.lineNumber()} $text")
                    }
                    return true
                }
            }
            webViewClient = object : WebViewClient() {
                // 장면 안에서 다른 페이지로 넘어가지 않는다.
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.url.path?.endsWith("highway-view.js") == true || request.isForMainFrame) {
                        status = "가상 콘솔에 연결하지 못했어요"
                    }
                }
            }
            loadDataWithBaseURL(VirtualConsoleClient.BASE_URL, html, "text/html", "utf-8", null)
        }
    }
    DisposableEffect(webView) {
        onDispose { webView.destroy() }
    }
    val latestSpeed by rememberUpdatedState(speedKmh)
    val latestGear by rememberUpdatedState(gear)
    LaunchedEffect(webView, active) {
        if (!active) {
            webView.onPause()
            return@LaunchedEffect
        }
        webView.onResume()
        // 장면은 페이지가 늦게 준비되고 속도는 한동안 같을 수 있어서, 보이는 동안 1초마다 다시 넣는다.
        while (true) {
            val js = String.format(
                Locale.US, "window.ongilSet && window.ongilSet(%.1f,'%s',0)",
                latestSpeed, consoleGear(latestGear)
            )
            webView.evaluateJavascript(js, null)
            delay(1_000L)
        }
    }

    Box(modifier) {
        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
        status?.let { StatusBubble(it, Modifier.align(Alignment.Center)) }
    }
}

@Composable
private fun StatusBubble(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NavTheme.Panel)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Text(text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

private const val STATUS_PREFIX = "ONGIL:"
private const val LOG_TAG = "Highway3d"

private fun consoleGear(gear: GearState): String = when (gear) {
    GearState.P -> "Park"
    GearState.R -> "Reverse"
    GearState.N -> "Neutral"
    GearState.D -> "Drive"
}
