package com.jiodive.cinema.data

import android.content.Context
import android.content.SharedPreferences
import com.jiodive.cinema.model.CinemaConfig
import com.jiodive.cinema.model.DurationFilterPreset
import com.jiodive.cinema.model.DurationFilterState
import com.jiodive.cinema.model.LibrarySortOrder
import com.jiodive.cinema.model.MediaItem
import com.jiodive.cinema.model.RecentStreamEntry
import com.jiodive.cinema.model.SavedMediaEntry
import com.jiodive.cinema.model.ScreenPreset
import com.jiodive.cinema.model.SubtitleTextSize
import com.jiodive.cinema.model.TheaterEnvironment
import com.jiodive.cinema.model.VideoFormat
import com.jiodive.cinema.model.WatchHistoryEntry
import org.json.JSONArray
import org.json.JSONObject

class PrefsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("jiodive_cinema_prefs", Context.MODE_PRIVATE)

    fun loadConfig(): CinemaConfig {
        val envName = prefs.getString(KEY_ENV, TheaterEnvironment.CINEMA.name)
        val sizeName = prefs.getString(KEY_PRESET, ScreenPreset.STANDARD.name)
        val formatName = prefs.getString(KEY_FORMAT, VideoFormat.FLAT_2D.name)
        val subSizeName = prefs.getString(KEY_SUB_TEXT_SIZE, SubtitleTextSize.NORMAL.name)

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
            subtitleOffsetMs = prefs.getLong(KEY_SUB_OFFSET_MS, 0L).coerceIn(-5_000L, 5_000L),
            subtitleTextSize = runCatching { SubtitleTextSize.valueOf(subSizeName!!) }
                .getOrDefault(SubtitleTextSize.NORMAL),
            bedTiltDeg = prefs.getFloat(KEY_BED_TILT_DEG, 46f).coerceIn(20f, 90f),
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
            .putLong(KEY_SUB_OFFSET_MS, config.subtitleOffsetMs.coerceIn(-5_000L, 5_000L))
            .putString(KEY_SUB_TEXT_SIZE, config.subtitleTextSize.name)
            .putFloat(KEY_BED_TILT_DEG, config.bedTiltDeg.coerceIn(20f, 90f))
            .apply()
    }

    fun loadSortOrder(): LibrarySortOrder {
        val raw = prefs.getString(KEY_SORT_ORDER, LibrarySortOrder.RECENT.name)
        return runCatching { LibrarySortOrder.valueOf(raw!!) }.getOrDefault(LibrarySortOrder.RECENT)
    }

    fun saveSortOrder(order: LibrarySortOrder) {
        prefs.edit().putString(KEY_SORT_ORDER, order.name).apply()
    }

    fun saveLastStreamUrl(url: String) {
        prefs.edit().putString(KEY_LAST_STREAM, url).apply()
    }

    fun getLastStreamUrl(): String = prefs.getString(KEY_LAST_STREAM, "") ?: ""

    fun saveLastSubtitle(uriString: String, label: String) {
        prefs.edit()
            .putString(KEY_LAST_SUB_URI, uriString)
            .putString(KEY_LAST_SUB_NAME, label)
            .apply()
    }

    fun getLastSubtitle(): Pair<String, String>? {
        val uri = prefs.getString(KEY_LAST_SUB_URI, null)?.trim().orEmpty()
        val label = prefs.getString(KEY_LAST_SUB_NAME, null)?.trim().orEmpty()
        return if (uri.isNotEmpty() && label.isNotEmpty()) uri to label else null
    }

    fun clearLastSubtitle() {
        prefs.edit()
            .remove(KEY_LAST_SUB_URI)
            .remove(KEY_LAST_SUB_NAME)
            .apply()
    }

    fun getRecentStreams(): List<RecentStreamEntry> {
        val raw = prefs.getString(KEY_RECENT_STREAMS, null)
        if (raw.isNullOrBlank()) {
            val legacyLast = getLastStreamUrl().trim()
            return if (legacyLast.startsWith("http")) {
                listOf(RecentStreamEntry(url = legacyLast, title = LibraryCatalog.suggestStreamTitle(legacyLast)))
            } else {
                emptyList()
            }
        }
        return runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<RecentStreamEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val url = obj.optString("url", "")
                val title = obj.optString("title", "")
                if (url.isNotBlank()) {
                    list.add(
                        RecentStreamEntry(
                            url = url,
                            title = title.ifBlank { LibraryCatalog.suggestStreamTitle(url) },
                            addedAtMs = obj.optLong("addedAtMs", 0L),
                        ),
                    )
                }
            }
            list
        }.getOrDefault(emptyList())
    }

    fun recordRecentStream(url: String, title: String) {
        val clean = url.trim()
        if (clean.isBlank()) return
        val current = getRecentStreams().toMutableList()
        current.removeAll { it.url == clean }
        current.add(
            0,
            RecentStreamEntry(
                url = clean,
                title = title.ifBlank { LibraryCatalog.suggestStreamTitle(clean) },
                addedAtMs = System.currentTimeMillis(),
            ),
        )
        saveRecentStreamsList(current.take(MAX_RECENT_STREAMS))
    }

    fun removeRecentStream(url: String) {
        val updated = getRecentStreams().filterNot { it.url == url }
        saveRecentStreamsList(updated)
        if (getLastStreamUrl().trim() == url.trim()) {
            prefs.edit().remove(KEY_LAST_STREAM).apply()
        }
    }

    private fun saveRecentStreamsList(items: List<RecentStreamEntry>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("url", item.url)
                    .put("title", item.title)
                    .put("addedAtMs", item.addedAtMs),
            )
        }
        prefs.edit().putString(KEY_RECENT_STREAMS, arr.toString()).apply()
    }

    fun hasCompletedOnboarding(): Boolean = prefs.getBoolean(KEY_ONBOARDING_DONE, false)

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, completed).apply()
    }

    fun loadDurationFilter(): DurationFilterState {
        val presetName = prefs.getString(KEY_DURATION_PRESET, DurationFilterPreset.ALL.name)
        val preset = if (presetName == "UNDER_30M") {
            DurationFilterPreset.ALL
        } else {
            runCatching { DurationFilterPreset.valueOf(presetName!!) }.getOrDefault(DurationFilterPreset.ALL)
        }
        val minMin = prefs.getInt(KEY_DURATION_CUSTOM_MIN, 45).coerceIn(0, 360)
        val maxRaw = prefs.getInt(KEY_DURATION_CUSTOM_MAX, -1)
        val maxMin = if (maxRaw >= minMin && maxRaw > 0) maxRaw else null
        return DurationFilterState(
            preset = preset,
            customMinMinutes = minMin,
            customMaxMinutes = maxMin,
        )
    }

    fun saveDurationFilter(state: DurationFilterState) {
        prefs.edit()
            .putString(KEY_DURATION_PRESET, state.preset.name)
            .putInt(KEY_DURATION_CUSTOM_MIN, state.customMinMinutes)
            .putInt(KEY_DURATION_CUSTOM_MAX, state.customMaxMinutes ?: -1)
            .apply()
    }

    fun getWatchHistory(): List<WatchHistoryEntry> {
        val raw = prefs.getString(KEY_WATCH_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<WatchHistoryEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val uri = obj.optString("uri", "")
                val title = obj.optString("title", "")
                if (uri.isNotBlank() && title.isNotBlank()) {
                    list.add(
                        WatchHistoryEntry(
                            mediaId = obj.optString("mediaId", "hist_${uri.hashCode()}"),
                            uriString = uri,
                            title = title,
                            positionMs = obj.optLong("positionMs", 0L).coerceAtLeast(0L),
                            durationMs = obj.optLong("durationMs", 0L).coerceAtLeast(0L),
                            isStream = obj.optBoolean("isStream", false),
                            isDemo = obj.optBoolean("isDemo", false),
                            updatedAtMs = obj.optLong("updatedAtMs", 0L),
                        ),
                    )
                }
            }
            list.sortedByDescending { it.updatedAtMs }
        }.getOrDefault(emptyList())
    }

    fun getResumePositionMs(uriString: String): Long {
        if (uriString.isBlank()) return 0L
        val entry = getWatchHistory().firstOrNull { it.uriString == uriString } ?: return 0L
        val dur = entry.durationMs
        val pos = entry.positionMs
        // Reset to start only if the user essentially finished the video
        return if (pos > 2_000L && (dur <= 0L || pos < (dur - 8_000L).coerceAtLeast(dur * 95L / 100L))) {
            pos
        } else {
            0L
        }
    }

    fun upsertWatchProgress(
        mediaId: String,
        uriString: String,
        title: String,
        positionMs: Long,
        durationMs: Long,
        isStream: Boolean,
        isDemo: Boolean,
    ) {
        if (uriString.isBlank() || title.isBlank()) return
        val current = getWatchHistory().toMutableList()
        val existing = current.firstOrNull { it.uriString == uriString }
        val resolvedDur = durationMs.takeIf { it > 0L } ?: (existing?.durationMs ?: 0L)
        val normalizedPos = if (resolvedDur > 20_000L && positionMs >= resolvedDur - 6_000L) {
            0L
        } else {
            positionMs.coerceAtLeast(0L)
        }
        val resolvedMediaId = mediaId.ifBlank { existing?.mediaId ?: "hist_${uriString.hashCode()}" }

        current.removeAll { it.uriString == uriString }
        current.add(
            0,
            WatchHistoryEntry(
                mediaId = resolvedMediaId,
                uriString = uriString,
                title = title,
                positionMs = normalizedPos,
                durationMs = resolvedDur,
                isStream = isStream,
                isDemo = isDemo,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
        saveWatchHistoryList(current.take(MAX_HISTORY_ITEMS))
    }

    fun removeWatchHistory(uriString: String) {
        val updated = getWatchHistory().filterNot { it.uriString == uriString }
        saveWatchHistoryList(updated)
    }

    fun clearWatchHistory() {
        prefs.edit().remove(KEY_WATCH_HISTORY).apply()
    }

    private fun saveWatchHistoryList(items: List<WatchHistoryEntry>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("mediaId", item.mediaId)
                    .put("uri", item.uriString)
                    .put("title", item.title)
                    .put("positionMs", item.positionMs)
                    .put("durationMs", item.durationMs)
                    .put("isStream", item.isStream)
                    .put("isDemo", item.isDemo)
                    .put("updatedAtMs", item.updatedAtMs),
            )
        }
        prefs.edit().putString(KEY_WATCH_HISTORY, arr.toString()).apply()
    }

    fun getSavedMediaEntries(): List<SavedMediaEntry> {
        val raw = prefs.getString(KEY_SAVED_PICKS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            val list = ArrayList<SavedMediaEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val uri = obj.optString("uri", "")
                val title = obj.optString("title", "")
                if (uri.isNotBlank() && title.isNotBlank()) {
                    val isStream = obj.optBoolean("isStream", false)
                    val rawFolder = obj.optString("folderName", "")
                    list.add(
                        SavedMediaEntry(
                            id = obj.optString("id", "saved_${uri.hashCode()}"),
                            uriString = uri,
                            title = title,
                            durationMs = obj.optLong("durationMs", 0L).coerceAtLeast(0L),
                            width = obj.optInt("width", 0).coerceAtLeast(0),
                            height = obj.optInt("height", 0).coerceAtLeast(0),
                            sizeBytes = obj.optLong("sizeBytes", 0L).coerceAtLeast(0L),
                            isStream = isStream,
                            folderName = if (!isStream) rawFolder.ifBlank { "Picked Files" } else rawFolder,
                            addedAtMs = obj.optLong("addedAtMs", 0L),
                        ),
                    )
                }
            }
            list
        }.getOrDefault(emptyList())
    }

    fun saveCustomMedia(item: MediaItem) {
        if (item.isDemo) return
        val uriStr = item.uri.toString()
        val current = getSavedMediaEntries().toMutableList()
        current.removeAll { it.uriString == uriStr }
        current.add(
            0,
            SavedMediaEntry(
                id = item.id,
                uriString = uriStr,
                title = item.title,
                durationMs = item.durationMs,
                width = item.width,
                height = item.height,
                sizeBytes = item.sizeBytes,
                isStream = item.isStream,
                folderName = item.folderName,
                addedAtMs = item.addedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
            ),
        )
        saveCustomMediaList(current.take(MAX_SAVED_PICKS))
    }

    fun removeSavedMedia(uriString: String) {
        val updated = getSavedMediaEntries().filterNot { it.uriString == uriString }
        saveCustomMediaList(updated)
        if (getLastStreamUrl().trim() == uriString.trim()) {
            prefs.edit().remove(KEY_LAST_STREAM).apply()
        }
    }

    private fun saveCustomMediaList(items: List<SavedMediaEntry>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("uri", item.uriString)
                    .put("title", item.title)
                    .put("durationMs", item.durationMs)
                    .put("width", item.width)
                    .put("height", item.height)
                    .put("sizeBytes", item.sizeBytes)
                    .put("isStream", item.isStream)
                    .put("folderName", item.folderName)
                    .put("addedAtMs", item.addedAtMs),
            )
        }
        prefs.edit().putString(KEY_SAVED_PICKS, arr.toString()).apply()
    }

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
        const val KEY_ONBOARDING_DONE = "onboarding_completed"
        const val KEY_SUB_OFFSET_MS = "subtitle_offset_ms"
        const val KEY_SUB_TEXT_SIZE = "subtitle_text_size"
        const val KEY_BED_TILT_DEG = "bed_tilt_deg"
        const val KEY_SORT_ORDER = "library_sort_order"
        const val KEY_LAST_STREAM = "last_stream_url"
        const val KEY_LAST_SUB_URI = "last_subtitle_uri"
        const val KEY_LAST_SUB_NAME = "last_subtitle_name"
        const val KEY_RECENT_STREAMS = "recent_streams_json"
        const val KEY_WATCH_HISTORY = "watch_history_json"
        const val KEY_SAVED_PICKS = "saved_media_picks_json"
        const val KEY_DURATION_PRESET = "duration_filter_preset"
        const val KEY_DURATION_CUSTOM_MIN = "duration_filter_custom_min"
        const val KEY_DURATION_CUSTOM_MAX = "duration_filter_custom_max"

        const val MAX_HISTORY_ITEMS = 15
        const val MAX_SAVED_PICKS = 20
        const val MAX_RECENT_STREAMS = 8
    }
}
