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
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.example.hyundai.nav.GeoMath
import com.example.hyundai.nav.GeoPoint
import com.example.hyundai.nav.NavUiState
import com.example.hyundai.nav.RouteGradient
import com.example.hyundai.nav.RouteStop
import com.example.hyundai.nav.RouteTrack
import com.example.hyundai.nav.RouteWindowSwitcher
import com.example.hyundai.ui.theme.TodakColors
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
import org.maplibre.android.style.layers.PropertyFactory.lineCap
import org.maplibre.android.style.layers.PropertyFactory.lineGradient
import org.maplibre.android.style.layers.PropertyFactory.lineJoin
import org.maplibre.android.style.layers.PropertyFactory.lineWidth
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/** 경로선은 두 칸(0, 1)에 번갈아 올린다([RouteWindowSwitcher]). 칸마다 소스 하나, 테두리와 속 레이어 둘. */
private fun routeSource(slot: Int) = "todak-route-src-$slot"
private fun casingLayer(slot: Int) = "todak-route-casing-$slot"
private fun fillLayer(slot: Int) = "todak-route-fill-$slot"

/** 차 뒤에서 선이 서서히 옅어지는 길이(m). 차 표시 아래에 대부분 가려진다. */
private const val ROUTE_TAIL_M = 12.0

/** 3D 시점 기울기. 32°로 완만하게 고정해 주변 건물이나 지형에 의해 시야가 들썩이지 않게 한다. */
private const val CAMERA_TILT_3D = 32.0

/** 2D 시점: 위에서 곧게 내려다본다(진행 방향은 계속 위쪽). */
private const val CAMERA_TILT_2D = 0.0

/** 건물 높이나 교차로 거리에 상관없이 시야 높이를 항상 동일하게 유지하는 고정 줌 레벨. */
internal const val FIXED_CAMERA_ZOOM = 18.6

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

/** [RouteGradient]가 정한 점들로 선 색을 칠한다. 점 사이는 이어서 섞어, 지워지는 끝이 칸 단위로 튀지 않는다. */
private fun routeGradient(stops: List<RouteStop>, base: String, note: String): Expression {
    val baseColor = base.toColorInt()
    val noteColor = note.toColorInt()
    if (stops.size < 2) return transparentGradient()
    return Expression.interpolate(
        Expression.linear(),
        Expression.lineProgress(),
        *stops.map { s ->
            val c = if (s.onNote) noteColor else baseColor
            Expression.stop(
                s.position,
                Expression.rgba(
                    android.graphics.Color.red(c), android.graphics.Color.green(c), android.graphics.Color.blue(c),
                    s.alpha
                )
            )
        }.toTypedArray()
    )
}

/**
 * 숨긴 칸의 선. 레이어를 끄지 않고 투명하게만 칠해, 지도 엔진이 그 칸의 선 모양을 미리 처리해 두게 한다
 * (꺼 둔 레이어의 소스는 처리하지 않을 수 있다).
 */
private fun transparentGradient(): Expression =
    Expression.interpolate(
        Expression.linear(),
        Expression.lineProgress(),
        Expression.stop(0f, Expression.rgba(0, 0, 0, 0f)),
        Expression.stop(1f, Expression.rgba(0, 0, 0, 0f))
    )

/**
 * 3D 지도. 낮에는 밝은 회청색, 밤에는 어두운 지도로 바뀐다. 건물과 산 지형(음영)은 은은하게, 내 경로만 또렷하게 보인다.
 * 도로 위에 음성 쪽지가 있는 구간(`noteZones`)은 따뜻한 앰버 골드 색상으로 구분해 표시한다.
 */
@Composable
fun NavMap(
    nav: NavUiState,
    isDay: Boolean,
    modifier: Modifier = Modifier,
    /** 다른 화면이 지도를 덮고 있으면 true. 안 보이는 지도를 프레임마다 갱신하지 않는다. */
    covered: Boolean = false,
    /** false면 기울이지 않은 2D 지도. */
    tilted: Boolean = true
) {
    val tilt = if (tilted) CAMERA_TILT_3D else CAMERA_TILT_2D
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
            m.setMinPitchPreference(tilt)
            m.setMaxPitchPreference(tilt)
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
            installRouteLayers(style)
            styleReady = true
        }
    }

    // snapshotFlow가 최신 내비 상태를 볼 수 있게 함수 인자를 상태로 감싼다.
    val latestNav by rememberUpdatedState(nav)
    val latestCovered by rememberUpdatedState(covered)

    // 카메라와 경로선: 새 위치가 올 때마다 지금 위치에서 새 위치까지 1초 동안 같은 속도로 이어서 움직인다.
    // 차는 카메라 가운데에 고정된 점으로 그리고, 경로선은 같은 속도로 차 위치까지만 지워서 끊김이 없다.
    LaunchedEffect(map, styleReady, isDay, tilt) {
        val m = map ?: return@LaunchedEffect
        if (!styleReady) return@LaunchedEffect
        // 기울기를 바꿀 때는 잠금 범위를 먼저 넓혔다가 새 값으로 다시 잠근다(최솟값이 최댓값보다 커지지 않게).
        m.setMinPitchPreference(CAMERA_TILT_2D)
        m.setMaxPitchPreference(tilt)
        m.setMinPitchPreference(tilt)
        val style = m.style ?: return@LaunchedEffect

        var windowTrack: RouteTrack? = null
        val switcher = RouteWindowSwitcher()
        var shownSlot = -1
        // MapView 레이아웃 크기가 측정된 뒤 한 번 고정한 상단 패딩(px)을 계속 재사용해 시야 흔들림을 막는다.
        var fixedTopPaddingPx = 0.0
        val casingColor = NavTheme.routeCasing(isDay)
        val noteCasingColor = NavTheme.noteCasing(isDay)

        fun updateRoute(progressM: Double) {
            val track = latestNav.track ?: return
            if (track !== windowTrack) {
                windowTrack = track
                switcher.reset()
            }
            // 구간은 몇 백 m마다 한 번만, 그것도 숨긴 칸에 미리 올린다. 매초 올리면 지도가 선을 다시 만들며 조각이 사라진다.
            for (action in switcher.update(progressM, track.totalM, SystemClock.uptimeMillis())) {
                when (action) {
                    is RouteWindowSwitcher.Action.Upload -> style.getSourceAs<GeoJsonSource>(routeSource(action.slot))
                        ?.setGeoJson(routeGeoJson(track.window(action.window.startM, action.window.endM)))
                    is RouteWindowSwitcher.Action.Show -> if (shownSlot != action.slot) {
                        // 바뀌는 순간 두 칸의 색을 같은 프레임에 바꾼다. 옛 칸은 투명하게만 둔다.
                        val hidden = lineGradient(transparentGradient())
                        style.getLayerAs<LineLayer>(casingLayer(1 - action.slot))?.setProperties(hidden)
                        style.getLayerAs<LineLayer>(fillLayer(1 - action.slot))?.setProperties(hidden)
                        shownSlot = action.slot
                    }
                }
            }
            val window = switcher.active ?: return
            val stops = RouteGradient.stops(window, progressM, latestNav.noteZones, ROUTE_TAIL_M)
            style.getLayerAs<LineLayer>(casingLayer(shownSlot))
                ?.setProperties(lineGradient(routeGradient(stops, casingColor, noteCasingColor)))
            style.getLayerAs<LineLayer>(fillLayer(shownSlot))
                ?.setProperties(lineGradient(routeGradient(stops, NavTheme.ROUTE_FILL, NavTheme.NOTE_FILL)))
        }

        fun apply(pose: CameraPose) {
            if (fixedTopPaddingPx <= 0.0 && mapView.height > 0) {
                fixedTopPaddingPx = mapView.height * CAMERA_TOP_PADDING_FRACTION
            }
            val position = CameraPosition.Builder()
                .target(LatLng(pose.position.lat, pose.position.lng))
                .zoom(FIXED_CAMERA_ZOOM)
                .tilt(tilt)
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
            if (latestCovered) {
                // 가려진 동안에는 보간 없이 한 번만 맞춘다. 다시 보이면 이미 최신 위치다.
                current = target
                apply(current)
                return@collectLatest
            }
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
        CarMarker()
    }
}

/** 카메라 가운데(위쪽 여백을 뺀 영역의 가운데)에 고정된 차량 표시. 흰 테두리의 하늘색 원 안에 진행 방향 화살표. */
@Composable
private fun CarMarker() {
    val sky = TodakColors.Sky
    val ink = TodakColors.Ink
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(
            size.width / 2f,
            size.height * (CAMERA_TOP_PADDING_FRACTION + (1 - CAMERA_TOP_PADDING_FRACTION) / 2).toFloat()
        )
        val r = 17.dp.toPx()
        drawCircle(ink.copy(alpha = 0.16f), radius = r + 3.dp.toPx(), center = center + Offset(0f, 2.dp.toPx()))
        drawCircle(Color.White, radius = r, center = center)
        drawCircle(sky, radius = r - 4.dp.toPx(), center = center)
        val a = r * 0.55f
        val arrow = Path().apply {
            moveTo(center.x, center.y - a)
            lineTo(center.x + a * 0.78f, center.y + a * 0.72f)
            lineTo(center.x, center.y + a * 0.30f)
            lineTo(center.x - a * 0.78f, center.y + a * 0.72f)
            close()
        }
        drawPath(arrow, Color.White)
    }
}

private fun routeGeoJson(pts: List<GeoPoint>): FeatureCollection {
    if (pts.size < 2) return FeatureCollection.fromFeatures(emptyList<Feature>())
    val line = LineString.fromLngLats(pts.map { Point.fromLngLat(it.lng, it.lat) })
    return FeatureCollection.fromFeature(Feature.fromGeometry(line))
}

/**
 * 경로선 레이어. 빛 번짐(blur) 없이, 진한 하늘색 테두리 위에 하늘색으로 속을 꽉 채운 선 하나다.
 * 쪽지 구간은 같은 선 위에서 살구색으로 바뀐다([RouteGradient]).
 */
private fun installRouteLayers(style: Style) {
    for (slot in 0..1) {
        // 선 그라데이션은 line metrics가 켜진 소스에서만 동작한다. 타일 경계에서 굵은 선이 잘리지 않게 여유(buffer)도 준다.
        style.addSource(
            GeoJsonSource(
                routeSource(slot),
                FeatureCollection.fromFeatures(emptyList<Feature>()),
                GeoJsonOptions().withLineMetrics(true).withBuffer(256)
            )
        )
        style.addLayer(
            LineLayer(casingLayer(slot), routeSource(slot)).withProperties(
                lineGradient(transparentGradient()),
                lineWidth(zoomWidth(20f, 42f)),
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
        style.addLayer(
            LineLayer(fillLayer(slot), routeSource(slot)).withProperties(
                lineGradient(transparentGradient()),
                lineWidth(zoomWidth(15f, 32f)),
                lineCap(Property.LINE_CAP_ROUND), lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }
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
