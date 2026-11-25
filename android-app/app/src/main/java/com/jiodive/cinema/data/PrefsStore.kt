package com.jiodive.cinema.data

import android.content.Context
import android.content.SharedPreferences
import com.jiodive.cinema.model.CinemaConfig
import com.jiodive.cinema.model.ScreenPreset
import com.jiodive.cinema.model.TheaterEnvironment
import com.jiodive.cinema.model.VideoFormat

class PrefsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("jiodive_cinema_prefs", Context.MODE_PRIVATE)

    fun loadConfig(): CinemaConfig {
        val envName = prefs.getString(KEY_ENV, TheaterEnvironment.CINEMA.name)
        val sizeName = prefs.getString(KEY_PRESET, ScreenPreset.STANDARD.name)
        val formatName = prefs.getString(KEY_FORMAT, VideoFormat.FLAT_2D.name)

        return CinemaConfig(
            darkTheme = prefs.getBoolean(KEY_DARK_THEME, false),
            environment = runCatching { TheaterEnvironment.valueOf(envName!!) }.getOrDefault(TheaterEnvironment.CINEMA),
            screenPreset = runCatching { ScreenPreset.valueOf(sizeName!!) }.getOrDefault(ScreenPreset.STANDARD),
            videoFormat = runCatching { VideoFormat.valueOf(formatName!!) }.getOrDefault(VideoFormat.FLAT_2D),
            ipdMm = prefs.getFloat(KEY_IPD, 62f).coerceIn(54f, 72f),
            fovDeg = prefs.getFloat(KEY_FOV, 80f).coerceIn(65f, 95f),
            barrelEnabled = prefs.getBoolean(KEY_BARREL, true),
            k1 = prefs.getFloat(KEY_K1, 0.101f),
            k2 = prefs.getFloat(KEY_K2, 0.098f),
            eyeSwap = prefs.getBoolean(KEY_EYE_SWAP, false),
            thermalCapEnabled = prefs.getBoolean(KEY_THERMAL_CAP, true),
            gazeDwellMs = prefs.getInt(KEY_DWELL_MS, 1150).coerceIn(750, 2000),
            sharpenStrength = prefs.getFloat(KEY_SHARPEN, 0.45f).coerceIn(0f, 1f),
            skipInsertionGuide = prefs.getBoolean(KEY_SKIP_GUIDE, false),
        )
    }

    fun saveConfig(config: CinemaConfig) {
        prefs.edit()
            .putBoolean(KEY_DARK_THEME, config.darkTheme)
            .putString(KEY_ENV, config.environment.name)
            .putString(KEY_PRESET, config.screenPreset.name)
            .putString(KEY_FORMAT, config.videoFormat.name)
            .putFloat(KEY_IPD, config.ipdMm)
            .putFloat(KEY_FOV, config.fovDeg)
            .putBoolean(KEY_BARREL, config.barrelEnabled)
            .putFloat(KEY_K1, config.k1)
            .putFloat(KEY_K2, config.k2)
            .putBoolean(KEY_EYE_SWAP, config.eyeSwap)
            .putBoolean(KEY_THERMAL_CAP, config.thermalCapEnabled)
            .putInt(KEY_DWELL_MS, config.gazeDwellMs)
            .putFloat(KEY_SHARPEN, config.sharpenStrength)
            .putBoolean(KEY_SKIP_GUIDE, config.skipInsertionGuide)
            .apply()
    }

    fun saveLastStreamUrl(url: String) {
        prefs.edit().putString(KEY_LAST_STREAM, url).apply()
    }

    fun getLastStreamUrl(): String = prefs.getString(KEY_LAST_STREAM, "") ?: ""

    private companion object {
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_ENV = "env"
        const val KEY_PRESET = "preset"
        const val KEY_FORMAT = "format"
        const val KEY_IPD = "ipd_mm"
        const val KEY_FOV = "fov_deg"
        const val KEY_BARREL = "barrel_enabled"
        const val KEY_K1 = "k1"
        const val KEY_K2 = "k2"
        const val KEY_EYE_SWAP = "eye_swap"
        const val KEY_THERMAL_CAP = "thermal_cap"
        const val KEY_DWELL_MS = "dwell_ms"
        const val KEY_SHARPEN = "sharpen"
        const val KEY_SKIP_GUIDE = "skip_guide"
        const val KEY_LAST_STREAM = "last_stream_url"
    }
}
