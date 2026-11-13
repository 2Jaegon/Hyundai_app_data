package com.example.hyundai.data

import android.content.Context
import com.example.hyundai.nav.DriveView
import com.example.hyundai.nav.GeoPoint
import com.example.hyundai.nav.MapTheme
import com.example.hyundai.nav.NavPlace
import com.example.hyundai.nav.PlaceKind
import com.example.hyundai.nav.SavedPlaces
import org.json.JSONArray
import org.json.JSONObject

/** 설정 화면의 값을 기기에 저장해, 앱을 다시 켜도 그대로 유지한다. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("ongil_settings", Context.MODE_PRIVATE)

    data class Values(
        val autoPlayTts: Boolean,
        val commuteBandOnly: Boolean,
        val mapTheme: MapTheme,
        val map3d: Boolean,
        val driveView: DriveView,
        val places: SavedPlaces
    )

    fun load(): Values = Values(
        autoPlayTts = prefs.getBoolean(KEY_AUTO_PLAY, true),
        commuteBandOnly = prefs.getBoolean(KEY_COMMUTE_ONLY, false),
        mapTheme = MapTheme.fromName(prefs.getString(KEY_MAP_THEME, null)),
        map3d = prefs.getBoolean(KEY_MAP_3D, true),
        driveView = runCatching { DriveView.valueOf(prefs.getString(KEY_DRIVE_VIEW, null).orEmpty()) }
            .getOrDefault(DriveView.MAP),
        places = SavedPlaces(
            home = prefs.getString(KEY_HOME, null)?.let(::decodePlace),
            work = prefs.getString(KEY_WORK, null)?.let(::decodePlace),
            favorites = decodeList(prefs.getString(KEY_FAVORITES, null)),
            recents = decodeList(prefs.getString(KEY_RECENTS, null))
        )
    )

    fun saveAutoPlay(on: Boolean) = prefs.edit().putBoolean(KEY_AUTO_PLAY, on).apply()

    fun saveCommuteBandOnly(on: Boolean) = prefs.edit().putBoolean(KEY_COMMUTE_ONLY, on).apply()

    fun saveMapTheme(theme: MapTheme) = prefs.edit().putString(KEY_MAP_THEME, theme.name).apply()

    fun saveMap3d(on: Boolean) = prefs.edit().putBoolean(KEY_MAP_3D, on).apply()

    fun saveDriveView(view: DriveView) = prefs.edit().putString(KEY_DRIVE_VIEW, view.name).apply()

    /** 이 앱에서 직접 저장한 장소만 쓴다. 기본 내비에서 받은 장소는 저장하지 않는다. */
    fun savePlaces(places: SavedPlaces) = prefs.edit()
        .putString(KEY_HOME, places.home?.let { encodePlace(it).toString() })
        .putString(KEY_WORK, places.work?.let { encodePlace(it).toString() })
        .putString(KEY_FAVORITES, JSONArray(places.favorites.map(::encodePlace)).toString())
        .putString(KEY_RECENTS, JSONArray(places.recents.map(::encodePlace)).toString())
        .apply()

    private fun encodePlace(p: NavPlace): JSONObject = JSONObject()
        .put("name", p.name)
        .put("address", p.address)
        .put("lat", p.point.lat)
        .put("lng", p.point.lng)
        .put("kind", p.kind.name)

    private fun decodePlace(raw: String): NavPlace? = runCatching { decodePlace(JSONObject(raw)) }.getOrNull()

    private fun decodePlace(o: JSONObject): NavPlace? {
        val lat = o.optDouble("lat", Double.NaN)
        val lng = o.optDouble("lng", Double.NaN)
        val name = o.optString("name")
        if (!lat.isFinite() || !lng.isFinite() || name.isBlank()) return null
        val kind = runCatching { PlaceKind.valueOf(o.optString("kind")) }.getOrDefault(PlaceKind.FAVORITE)
        return NavPlace(name, o.optString("address"), GeoPoint(lat, lng), kind)
    }

    private fun decodeList(raw: String?): List<NavPlace> {
        val arr = raw?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optJSONObject(i)?.let(::decodePlace) }
    }

    private companion object {
        const val KEY_AUTO_PLAY = "auto_play_tts"
        const val KEY_COMMUTE_ONLY = "commute_band_only"
        const val KEY_MAP_THEME = "map_theme"
        const val KEY_MAP_3D = "map_3d"
        const val KEY_DRIVE_VIEW = "drive_view"
        const val KEY_HOME = "place_home"
        const val KEY_WORK = "place_work"
        const val KEY_FAVORITES = "place_favorites"
        const val KEY_RECENTS = "place_recents"
    }
}
