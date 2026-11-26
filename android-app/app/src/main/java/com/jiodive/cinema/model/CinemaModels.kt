package com.jiodive.cinema.model

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable

enum class TheaterEnvironment(val title: String, val subtitle: String) {
    CINEMA("Cinema Hall", "Curved auditorium"),
    VOID("OLED Void", "Pure black theatre"),
    BED("Bed / Ceiling", "Reclined overhead");

    fun next(): TheaterEnvironment {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}

enum class ScreenPreset(
    val title: String,
    val widthMeters: Float,
    val distanceMeters: Float,
    val curveDegrees: Float,
) {
    IMAX("IMAX", 13.6f, 6.0f, 52f),
    STANDARD("Standard", 9.6f, 6.8f, 36f),
    BALCONY("Balcony", 10.6f, 9.8f, 26f);

    fun next(): ScreenPreset {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}

enum class VideoFormat(val label: String) {
    FLAT_2D("2D"),
    HALF_SBS("Half-SBS"),
    FULL_SBS("Full-SBS"),
    HALF_OU("Half-OU"),
    FULL_OU("Full-OU");

    fun next(): VideoFormat {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}

enum class MediaCategory {
    ALL,
    DEVICE,
    STREAMS,
}

enum class LibrarySortOrder(val label: String) {
    RECENT("Recent"),
    LONGEST("Longest"),
    TITLE_AZ("A-Z"),
}

enum class SubtitleTextSize(
    val label: String,
    val vrPx: Float,
    val previewSp: Float,
) {
    SMALL("Small", 34f, 12f),
    NORMAL("Normal", 42f, 14f),
    LARGE("Large", 50f, 17f),
}

enum class DurationFilterPreset(val label: String) {
    ALL("All lengths"),
    MIN_30M("30m+"),
    MIN_1H("1h+"),
    MIN_2H("2h+"),
    CUSTOM("Custom"),
}

@Immutable
data class DurationFilterState(
    val preset: DurationFilterPreset = DurationFilterPreset.ALL,
    val customMinMinutes: Int = 45,
    val customMaxMinutes: Int? = null,
) {
    fun matches(durationMs: Long): Boolean {
        if (preset == DurationFilterPreset.ALL) return true
        if (durationMs <= 0L) return false
        return when (preset) {
            DurationFilterPreset.ALL -> true
            DurationFilterPreset.MIN_30M -> durationMs >= 30L * 60_000L
            DurationFilterPreset.MIN_1H -> durationMs >= 60L * 60_000L
            DurationFilterPreset.MIN_2H -> durationMs >= 120L * 60_000L
            DurationFilterPreset.CUSTOM -> {
                val minMs = customMinMinutes.coerceAtLeast(0) * 60_000L
                val maxMs = customMaxMinutes?.let { it.coerceAtLeast(customMinMinutes) * 60_000L }
                durationMs >= minMs && (maxMs == null || durationMs <= maxMs)
            }
        }
    }

    fun chipLabel(): String {
        if (preset != DurationFilterPreset.CUSTOM) return preset.label
        val minText = formatMinutesCompact(customMinMinutes)
        val max = customMaxMinutes
        return if (max == null) "$minText+" else "$minText-${formatMinutesCompact(max)}"
    }

    private fun formatMinutesCompact(minutes: Int): String =
        if (minutes >= 60 && minutes % 60 == 0) "${minutes / 60}h" else "${minutes}m"
}

@Immutable
data class WatchHistoryEntry(
    val mediaId: String,
    val uriString: String,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val isStream: Boolean,
    val isDemo: Boolean = false,
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
}

@Immutable
data class RecentStreamEntry(
    val url: String,
    val title: String,
    val addedAtMs: Long = System.currentTimeMillis(),
)

data class SavedMediaEntry(
    val id: String,
    val uriString: String,
    val title: String,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0L,
    val isStream: Boolean = false,
    val folderName: String = "",
    val addedAtMs: Long = System.currentTimeMillis(),
)

@Immutable
data class MediaItem(
    val id: String,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val isStream: Boolean,
    val isDemo: Boolean = false,
    val folderName: String = "",
    val addedAtMs: Long = 0L,
    val thumbnail: Bitmap? = null,
)

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

@Immutable
data class CinemaConfig(
    val darkTheme: Boolean = false,
    val environment: TheaterEnvironment = TheaterEnvironment.CINEMA,
    val screenPreset: ScreenPreset = ScreenPreset.STANDARD,
    val videoFormat: VideoFormat = VideoFormat.FLAT_2D,
    val ipdMm: Float = 62f,
    val fovDeg: Float = 80f,
    val barrelEnabled: Boolean = true,
    val k1: Float = 0.101f,
    val k2: Float = 0.098f,
    val eyeSwap: Boolean = false,
    val thermalCapEnabled: Boolean = true,
    val gazeDwellMs: Int = 1150,
    val sharpenStrength: Float = 0.45f,
    val skipInsertionGuide: Boolean = false,
    val subtitleOffsetMs: Long = 0L,
    val subtitleTextSize: SubtitleTextSize = SubtitleTextSize.NORMAL,
    val bedTiltDeg: Float = 46f,
)
