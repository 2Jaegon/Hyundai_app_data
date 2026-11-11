package com.example.hyundai.ui.map

import android.view.Gravity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.hyundai.nav.GeoMath
import com.example.hyundai.nav.GeoPoint
import com.example.hyundai.nav.NavUiState
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory.lineBlur
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private const val ROUTE_SRC = "todak-route-src"

/** 기울기를 낮추고 더 가깝게 봐서, 차 앞 가까운 길만 크게 보이게 한다. */
private const val CAMERA_TILT = 45.0

/** 위치가 1초마다 갱신되므로, 카메라도 1초에 걸쳐 일정한 속도로 이어서 움직인다. */
private const val CAMERA_TWEEN_MS = 1000.0

/** 차가 화면 아래쪽에 오도록 위쪽 여백을 준다. */
private const val CAMERA_TOP_PADDING_FRACTION = 0.46

/**
 * 카카오맵처럼 도로가 크게 보이는 가까운 시점. 교차로가 가까워지면 더 확대해 어느 길로 들어가는지 보여 준다.
 */
private fun zoomFor(nav: NavUiState): Double = when {
    nav.distToTurnM < 200 -> 19.0
    nav.distToTurnM < 600 -> 18.6
    else -> 18.2
}

private data class CameraPose(val position: GeoPoint, val bearing: Double, val zoom: Double)

private fun poseOf(nav: NavUiState) =
    CameraPose(nav.carPosition, nav.carBearing.toDouble(), zoomFor(nav))

/** 줌에 따라 같은 비율로 두께가 커지는 선 두께 (도로 폭과 맞춘다). */
private fun zoomWidth(at17: Float, at19: Float): Expression =
    Expression.interpolate(
        Expression.exponential(1.6f), Expression.zoom(),
        Expression.stop(14, at17 * 0.22f),
        Expression.stop(17, at17),
        Expression.stop(19, at19)
    )

/**
 * 3D 지도. 낮에는 밝은 회청색, 밤에는 어두운 지도로 바뀐다. 건물과 산 지형(음영)은 은은하게, 내 경로만 또렷하게 보인다.
 * 지도 데이터는 키가 필요 없는 OpenFreeMap(건물/도로)과 AWS 공개 고도 타일(산 음영)을 쓴다.
 */
@Composable
fun NavMap(
    nav: NavUiState,
    isDay: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val nightJson = remember(context) {
        context.assets.open("todak_map_style.json").bufferedReader().use { it.readText() }
    }
    val dayJson = remember(context) {
        context.assets.open("todak_map_day.json").bufferedReader().use { it.readText() }
    }
    val styleJson = if (isDay) dayJson else nightJson
    // 고도 타일(산 음영) 형식을 이 기기의 지도 엔진이 못 읽으면 true가 된다. 낮/밤 두 스타일이 같은 고도 타일을 써서
    // 테마와 상관없이 유지하고, 스타일을 불러오는 곳은 아래 하나로 합친다.
    var reliefFailed by remember { mutableStateOf(false) }

    val mapView = remember(context) {
        MapLibre.getInstance(context)
        MapView(context).also { it.onCreate(null) }
    }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        // addObserver는 현재 상태까지의 이벤트(ON_START, ON_RESUME)를 즉시 다시 보내 주므로 수동 호출은 필요 없다.
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            m.uiSettings.apply {
                setAllGesturesEnabled(false)
                isCompassEnabled = false
                isLogoEnabled = false
                isAttributionEnabled = true
                attributionGravity = Gravity.BOTTOM or Gravity.END
            }
            map = m
        }
        // TEMP-PERF: 프레임 수 측정. 검증이 끝나면 지운다.
        var frames = 0
        var windowStart = android.os.SystemClock.elapsedRealtime()
        mapView.addOnDidFinishRenderingFrameListener { _, _, _ ->
            frames++
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - windowStart >= 3000) {
                android.util.Log.d("NavPerf", "fps=${frames * 1000 / (now - windowStart)}")
                frames = 0; windowStart = now
            }
        }
        // 고도 타일 형식을 못 읽으면 플래그만 켜고, 실제 다시 불러오는 일은 아래 효과가 한다.
        mapView.addOnDidFailLoadingMapListener { message ->
            if (message.contains("elevation-tiles", ignoreCase = true)) reliefFailed = true
        }
    }

    // 스타일을 불러오는 유일한 곳. 낮/밤이 바뀌거나 음영을 빼야 할 때 통째로 바꾸고 경로 선을 다시 올린다.
    LaunchedEffect(map, isDay, reliefFailed) {
        val m = map ?: return@LaunchedEffect
        styleReady = false
        val json = if (reliefFailed) withoutRelief(styleJson) else styleJson
        m.setStyle(Style.Builder().fromJson(json)) { style ->
            installRouteLayers(style, isDay)
            styleReady = true
        }
    }

    LaunchedEffect(nav.upcomingRoute, styleReady) {
        if (!styleReady) return@LaunchedEffect
        map?.style?.getSourceAs<GeoJsonSource>(ROUTE_SRC)?.setGeoJson(routeGeoJson(nav))
    }

    // snapshotFlow가 최신 내비 상태를 볼 수 있게 함수 인자를 상태로 감싼다.
    val latestNav by rememberUpdatedState(nav)

    // 카메라: 새 위치가 올 때마다 지금 위치에서 새 위치까지 1초 동안 같은 속도로 이어서 움직인다.
    // 차는 카메라 가운데에 고정된 점으로 그리므로, 점과 카메라가 따로 움직여 흔들리는 일이 없다.
    LaunchedEffect(map, styleReady) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect

        fun apply(pose: CameraPose) {
            val position = CameraPosition.Builder()
                .target(LatLng(pose.position.lat, pose.position.lng))
                .zoom(pose.zoom)
                .tilt(CAMERA_TILT)
                .bearing(pose.bearing)
                .padding(0.0, mapView.height * CAMERA_TOP_PADDING_FRACTION, 0.0, 0.0)
                .build()
            m.moveCamera(CameraUpdateFactory.newCameraPosition(position))
        }

        var current = poseOf(latestNav)
        apply(current)
        snapshotFlow { poseOf(latestNav) }.collectLatest { target ->
            if (target == current) return@collectLatest
            val from = current
            val startNanos = withFrameNanos { it }
            var t = 0.0
            while (t < 1.0) {
                withFrameNanos { now ->
                    t = ((now - startNanos) / 1_000_000.0 / CAMERA_TWEEN_MS).coerceAtMost(1.0)
                }
                // 중간에 새 위치가 와서 취소돼도 current가 마지막 보간 위치를 가지고 있어 끊김 없이 이어진다.
                current = CameraPose(
                    GeoMath.lerp(from.position, target.position, t),
                    GeoMath.lerpBearingDeg(from.bearing, target.bearing, t),
                    from.zoom + (target.zoom - from.zoom) * t
                )
                apply(current)
            }
        }
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        CarMarker(isDay)
    }
}

/** 카메라 가운데(위쪽 여백을 뺀 영역의 가운데)에 고정된 차량 표시. */
@Composable
private fun CarMarker(isDay: Boolean) {
    val accent = Color(NavTheme.route(isDay).toColorInt())
    val ring = Color(NavTheme.carRing(isDay).toColorInt())
    Canvas(Modifier.fillMaxSize()) {
        val center = androidx.compose.ui.geometry.Offset(
            size.width / 2f,
            size.height * (CAMERA_TOP_PADDING_FRACTION + (1 - CAMERA_TOP_PADDING_FRACTION) / 2).toFloat()
        )
        drawCircle(accent.copy(alpha = 0.28f), radius = 26.dp.toPx(), center = center)
        drawCircle(ring, radius = 11.dp.toPx(), center = center)
        drawCircle(Color.White, radius = 7.dp.toPx(), center = center)
    }
}

private fun routeGeoJson(nav: NavUiState): FeatureCollection {
    val pts = nav.upcomingRoute
    if (pts.size < 2) return FeatureCollection.fromFeatures(emptyList<Feature>())
    val line = LineString.fromLngLats(pts.map { Point.fromLngLat(it.lng, it.lat) })
    return FeatureCollection.fromFeature(Feature.fromGeometry(line))
}

private fun installRouteLayers(style: Style, isDay: Boolean) {
    style.addSource(GeoJsonSource(ROUTE_SRC, FeatureCollection.fromFeatures(emptyList<Feature>())))

    style.addLayer(
        LineLayer("todak-route-glow", ROUTE_SRC).withProperties(
            lineColor(NavTheme.route(isDay)), lineOpacity(0.30f), lineWidth(zoomWidth(34f, 72f)), lineBlur(10f),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        LineLayer("todak-route-line", ROUTE_SRC).withProperties(
            lineColor(NavTheme.route(isDay)), lineWidth(zoomWidth(16f, 34f)),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        LineLayer("todak-route-core", ROUTE_SRC).withProperties(
            lineColor(NavTheme.routeCore(isDay)), lineWidth(zoomWidth(5f, 11f)),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
}

private fun withoutRelief(styleJson: String): String {
    val root = JSONObject(styleJson)
    root.getJSONObject("sources").remove("dem")
    val layers = root.getJSONArray("layers")
    val kept = org.json.JSONArray()
    for (i in 0 until layers.length()) {
        val layer = layers.getJSONObject(i)
        if (layer.optString("id") != "relief") kept.put(layer)
    }
    root.put("layers", kept)
    return root.toString()
}
