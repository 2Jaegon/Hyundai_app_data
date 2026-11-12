package com.example.hyundai.data

import android.content.Context
import com.example.hyundai.nav.MapTheme

/** 설정 화면의 값을 기기에 저장해, 앱을 다시 켜도 그대로 유지한다. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("ongil_settings", Context.MODE_PRIVATE)

    data class Values(
        val autoPlayTts: Boolean,
        val commuteBandOnly: Boolean,
        val mapTheme: MapTheme
    )

    fun load(): Values = Values(
        autoPlayTts = prefs.getBoolean(KEY_AUTO_PLAY, true),
        commuteBandOnly = prefs.getBoolean(KEY_COMMUTE_ONLY, false),
        mapTheme = MapTheme.fromName(prefs.getString(KEY_MAP_THEME, null))
    )

    fun saveAutoPlay(on: Boolean) = prefs.edit().putBoolean(KEY_AUTO_PLAY, on).apply()

    fun saveCommuteBandOnly(on: Boolean) = prefs.edit().putBoolean(KEY_COMMUTE_ONLY, on).apply()

    fun saveMapTheme(theme: MapTheme) = prefs.edit().putString(KEY_MAP_THEME, theme.name).apply()

    private companion object {
        const val KEY_AUTO_PLAY = "auto_play_tts"
        const val KEY_COMMUTE_ONLY = "commute_band_only"
        const val KEY_MAP_THEME = "map_theme"
    }
}
