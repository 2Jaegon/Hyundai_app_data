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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.hyundai.nav.GeoMath
import com.example.hyundai.nav.GeoPoint
import com.example.hyundai.nav.NavUiState
import com.example.hyundai.nav.NoteRouteZone
import com.example.hyundai.nav.RouteTrack
import com.example.hyundai.nav.RouteWindow
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
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.lineBlur
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineColor
import org.maplibre.android.style.layers.PropertyFactory.lineGradient
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineOpacity
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.max
import kotlin.math.min

private const val ROUTE_SRC = "todak-route-src"
private const val GLOW_LAYER = "todak-route-glow"
private const val LINE_LAYER = "todak-route-line"
private const val CORE_LAYER = "todak-route-core"

private const val NOTE_ZONE_SRC = "todak-note-zone-src"
private const val NOTE_ZONE_GLOW_LAYER = "todak-note-zone-glow"
private const val NOTE_ZONE_LINE_LAYER = "todak-note-zone-line"
private const val NOTE_ZONE_CORE_LAYER = "todak-note-zone-core"

/** 기울기를 32°로 완만하게 고정해 주변 건물이나 지형에 의해 시야가 들썩이지 않게 한다. */
private const val CAMERA_TILT = 32.0

/** 건물 높이나 교차로 거리에 상관없이 시야 높이를 항상 동일하게 유지하는 고정 줌 레벨. */
internal const val FIXED_CAMERA_ZOOM = 17.8

/** 위치가 1초마다 갱신되므로, 카메라도 1초에 걸쳐 일정한 속도로 이어서 움직인다. */
private const val CAMERA_TWEEN_MS = 1000.0

/** 차가 화면 아래쪽에 오도록 위쪽 여백을 준다. */
private const val CAMERA_TOP_PADDING_FRACTION = 0.46

private data class CameraPose(
    val position: GeoPoint,
    val bearing: Double,
    /** 경로가 바뀌었는지 알아보는 용도(참조 비교). 바뀌면 보간하지 않고 바로 맞춘다. */
    val track: RouteTrack?,
    /** 경로선을 어디까지 지울지. 카메라와 같은 속도로 이어서 움직인다. */
    val progressM: Double
)

private fun poseOf(nav: NavUiState) =
    CameraPose(nav.carPosition, nav.carBearing.toDouble(), nav.track, nav.progressM)

/** 줌에 따라 같은 비율로 두께가 커지는 선 두께 (도로 폭과 맞춘다). */
private fun zoomWidth(at17: Float, at19: Float): Expression =
    Expression.interpolate(
        Expression.exponential(1.6f), Expression.zoom(),
        Expression.stop(14, at17 * 0.22f),
        Expression.stop(17, at17),
        Expression.stop(19, at19)
    )

/** 선의 앞쪽 [fraction]까지는 투명, 그 뒤는 [color]로 칠한다. 지나간 길만 선이 사라지게 하는 방법이다. */
private fun trimGradient(fraction: Float, color: String): Expression =
    Expression.step(
        Expression.lineProgress(),
        Expression.rgba(0, 0, 0, 0),
        Expression.stop(fraction, Expression.color(color.toColorInt()))
    )

/**
 * 3D 지도. 낮에는 밝은 회청색, 밤에는 어두운 지도로 바뀐다. 건물과 산 지형(음영)은 은은하게, 내 경로만 또렷하게 보인다.
 * 도로 위에 음성 쪽지가 있는 구간(`noteZones`)은 따뜻한 앰버 골드 색상으로 구분해 표시한다.
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
            // 카메라 시야 높이(Zoom)와 기울기(Pitch)를 엔진 레벨에서 완전히 잠가 어떤 상황에서도 출렁이지 않게 한다.
            m.setMinZoomPreference(FIXED_CAMERA_ZOOM)
            m.setMaxZoomPreference(FIXED_CAMERA_ZOOM)
            m.setMinPitchPreference(CAMERA_TILT)
            m.setMaxPitchPreference(CAMERA_TILT)
            map = m
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

    // snapshotFlow가 최신 내비 상태를 볼 수 있게 함수 인자를 상태로 감싼다.
    val latestNav by rememberUpdatedState(nav)

    // 카메라와 경로선: 새 위치가 올 때마다 지금 위치에서 새 위치까지 1초 동안 같은 속도로 이어서 움직인다.
    // 차는 카메라 가운데에 고정된 점으로 그리고, 경로선은 같은 속도로 차 위치까지만 지워서 끊김이 없다.
    LaunchedEffect(map, styleReady, isDay) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        val style = m.style ?: return@LaunchedEffect

        var windowTrack: RouteTrack? = null
        var window = RouteWindow(0.0, 1.0)
        // MapView 레이아웃 크기가 측정된 뒤 한 번 고정한 상단 패딩(px)을 계속 재사용해 시야 흔들림을 막는다.
        var fixedTopPaddingPx = 0.0

        fun updateRoute(progressM: Double) {
            val track = latestNav.track ?: return
            if (track !== windowTrack || window.needsRebuild(progressM)) {
                windowTrack = track
                window = RouteWindow.at(progressM, track.totalM)
                // 구간은 몇 백 m마다 한 번만 올린다. 매초 올리면 지도가 그때마다 선을 다시 만들어 조각이 사라진다.
                style.getSourceAs<GeoJsonSource>(ROUTE_SRC)
                    ?.setGeoJson(routeGeoJson(track.window(window.startM, window.endM)))
            }
            val fraction = window.fraction(progressM)
            val route = lineGradient(trimGradient(fraction, NavTheme.route(isDay)))
            style.getLayerAs<LineLayer>(GLOW_LAYER)?.setProperties(route)
            style.getLayerAs<LineLayer>(LINE_LAYER)?.setProperties(route)
            style.getLayerAs<LineLayer>(CORE_LAYER)?.setProperties(lineGradient(trimGradient(fraction, NavTheme.routeCore(isDay))))

            // 차 앞쪽에 놓인 쪽지 구간들만 잘라 따뜻한 앰버 골드 색상으로 경로 위에 표시한다.
            style.getSourceAs<GeoJsonSource>(NOTE_ZONE_SRC)
                ?.setGeoJson(noteZonesGeoJson(track, latestNav.noteZones, progressM, window.endM))
        }

        fun apply(pose: CameraPose) {
            if (fixedTopPaddingPx <= 0.0 && mapView.height > 0) {
                fixedTopPaddingPx = mapView.height * CAMERA_TOP_PADDING_FRACTION
            }
            val position = CameraPosition.Builder()
                .target(LatLng(pose.position.lat, pose.position.lng))
                .zoom(FIXED_CAMERA_ZOOM)
                .tilt(CAMERA_TILT)
                .bearing(pose.bearing)
                .padding(0.0, fixedTopPaddingPx, 0.0, 0.0)
                .build()
            m.moveCamera(CameraUpdateFactory.newCameraPosition(position))
            updateRoute(pose.progressM)
        }

        var current = poseOf(latestNav)
        apply(current)
        snapshotFlow { poseOf(latestNav) }.collectLatest { target ->
            if (target == current) return@collectLatest
            // 새 경로로 바뀌었거나 시뮬레이션 재시작으로 진행 거리가 뒤로 돌아간 경우 보간하지 않고 즉시 맞춘다.
            if (target.track !== current.track || target.progressM < current.progressM - 10.0) {
                current = target
                apply(current)
                return@collectLatest
            }
            val (fromPosition, fromBearing, _, fromProgress) = current
            val startNanos = withFrameNanos { it }
            var t = 0.0
            while (t < 1.0) {
                withFrameNanos { now ->
                    t = ((now - startNanos) / 1_000_000.0 / CAMERA_TWEEN_MS).coerceAtMost(1.0)
                }
                // 중간에 새 위치가 와서 취소돼도 current가 마지막 보간 위치를 가지고 있어 끊김 없이 이어진다.
                current = CameraPose(
                    GeoMath.lerp(fromPosition, target.position, t),
                    GeoMath.lerpBearingDeg(fromBearing, target.bearing, t),
                    target.track,
                    fromProgress + (target.progressM - fromProgress) * t
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

private fun routeGeoJson(pts: List<GeoPoint>): FeatureCollection {
    if (pts.size < 2) return FeatureCollection.fromFeatures(emptyList<Feature>())
    val line = LineString.fromLngLats(pts.map { Point.fromLngLat(it.lng, it.lat) })
    return FeatureCollection.fromFeature(Feature.fromGeometry(line))
}

/**
 * 경로 위에서 쪽지가 있는 구간([zones]) 중 아직 지나가지 않은 앞쪽 구간만 잘라 GeoJSON FeatureCollection으로 만든다.
 */
internal fun noteZonesGeoJson(
    track: RouteTrack,
    zones: List<NoteRouteZone>,
    progressM: Double,
    windowEndM: Double
): FeatureCollection {
    if (zones.isEmpty()) return FeatureCollection.fromFeatures(emptyList<Feature>())
    val features = ArrayList<Feature>(zones.size)
    for (zone in zones) {
        val segStart = max(zone.startM, progressM)
        val segEnd = min(zone.endM, windowEndM)
        if (segEnd - segStart < 8.0) continue
        val pts = track.window(segStart, segEnd)
        if (pts.size >= 2) {
            val line = LineString.fromLngLats(pts.map { Point.fromLngLat(it.lng, it.lat) })
            features.add(Feature.fromGeometry(line))
        }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun installRouteLayers(style: Style, isDay: Boolean) {
    // 선 그라데이션은 line metrics가 켜진 소스에서만 동작한다. 타일 경계에서 굵은 선이 잘리지 않게 여유(buffer)도 준다.
    style.addSource(
        GeoJsonSource(
            ROUTE_SRC,
            FeatureCollection.fromFeatures(emptyList<Feature>()),
            GeoJsonOptions().withLineMetrics(true).withBuffer(256)
        )
    )
    style.addSource(
        GeoJsonSource(
            NOTE_ZONE_SRC,
            FeatureCollection.fromFeatures(emptyList<Feature>()),
            GeoJsonOptions().withBuffer(256)
        )
    )

    style.addLayer(
        LineLayer(GLOW_LAYER, ROUTE_SRC).withProperties(
            lineGradient(trimGradient(0f, NavTheme.route(isDay))),
            lineOpacity(0.30f), lineWidth(zoomWidth(34f, 72f)), lineBlur(10f),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        LineLayer(LINE_LAYER, ROUTE_SRC).withProperties(
            lineGradient(trimGradient(0f, NavTheme.route(isDay))),
            lineWidth(zoomWidth(16f, 34f)),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        LineLayer(CORE_LAYER, ROUTE_SRC).withProperties(
            lineGradient(trimGradient(0f, NavTheme.routeCore(isDay))),
            lineWidth(zoomWidth(5f, 11f)),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
        )
    )

    // 도로 위에 쪽지가 있는 구간을 다른 색상(따뜻한 앰버 골드)으로 강조하는 레이어
    val zoneColor = NavTheme.noteZone(isDay).toColorInt()
    val zoneCoreColor = NavTheme.NOTE_ZONE_CORE.toColorInt()
    style.addLayer(
        LineLayer(NOTE_ZONE_GLOW_LAYER, NOTE_ZONE_SRC).withProperties(
            lineColor(zoneColor),
            lineOpacity(0.42f), lineWidth(zoomWidth(38f, 80f)), lineBlur(12f),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        LineLayer(NOTE_ZONE_LINE_LAYER, NOTE_ZONE_SRC).withProperties(
            lineColor(zoneColor),
            lineWidth(zoomWidth(18f, 38f)),
            lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
    style.addLayer(
        LineLayer(NOTE_ZONE_CORE_LAYER, NOTE_ZONE_SRC).withProperties(
            lineColor(zoneCoreColor),
            lineWidth(zoomWidth(6f, 13f)),
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
