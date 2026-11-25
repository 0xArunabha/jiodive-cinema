package com.jiodive.cinema.model

import android.graphics.Bitmap
import android.net.Uri

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
    val thumbnail: Bitmap? = null,
)

data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String,
)

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
)
