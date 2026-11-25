package com.jiodive.cinema.vr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.jiodive.cinema.data.MediaRepository
import com.jiodive.cinema.model.CinemaConfig
import kotlin.math.roundToInt

enum class DockAction {
    NONE,
    SCRUB,
    TOGGLE_PLAY,
    SEEK_BACK,
    SEEK_FWD,
    TOGGLE_MUTE,
    TOGGLE_CC,
    CYCLE_ROOM,
    RECENTER,
    TOGGLE_SETTINGS,
    EXIT_VR,
    IPD_MINUS,
    IPD_PLUS,
    FOV_MINUS,
    FOV_PLUS,
    CYCLE_FORMAT,
    TOGGLE_SWAP,
    TOGGLE_OPTICS,
    TOGGLE_THERMAL,
}

data class DockHotspot(
    val action: DockAction,
    val rectNorm: RectF,
    val label: String,
    val primaryCta: Boolean = false,
    val active: Boolean = false,
)

/**
 * Renders the floating 3D VR remote control dock onto a high-contrast Canvas bitmap
 * (JioImmerse OLED dark charcoal + Turquoise primary CTA + Emerald/Gold accents).
 */
class VrDockRenderer {
    val widthPx = 1024
    val heightPx = 300
    val bitmap: Bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)

    var showSettings: Boolean = false
    var dockHidden: Boolean = false

    private val hotspots = ArrayList<DockHotspot>(16)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(232, 22, 27, 29) // Matte charcoal
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.argb(140, 45, 212, 191) // Turquoise hairline
    }
    private val btnIdlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 33, 40, 43)
    }
    private val btnHoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 19, 78, 82)
    }
    private val btnActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 16, 128, 120)
    }
    private val ctaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 45, 212, 191) // Turquoise primary CTA
    }
    private val ctaHoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 94, 234, 212)
    }
    private val trackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 52, 62, 66)
    }
    private val trackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 45, 212, 191)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 26f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(230, 178, 196, 202)
        textSize = 22f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    fun redraw(
        isPlaying: Boolean,
        isMuted: Boolean,
        subtitlesLoaded: Boolean,
        subtitlesEnabled: Boolean,
        positionMs: Long,
        durationMs: Long,
        config: CinemaConfig,
        hoveredAction: DockAction?,
        hoverScrubNorm: Float,
    ) {
        hotspots.clear()
        bitmap.eraseColor(Color.TRANSPARENT)

        val outer = RectF(4f, 4f, widthPx - 4f, heightPx - 4f)
        canvas.drawRoundRect(outer, 36f, 36f, bgPaint)
        canvas.drawRoundRect(outer, 36f, 36f, borderPaint)

        // Top timeline scrub bar + time readout
        val barRectPx = RectF(42f, 34f, widthPx - 230f, 54f)
        canvas.drawRoundRect(barRectPx, 10f, 10f, trackBgPaint)
        val frac = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
        if (frac > 0.005f) {
            val fill = RectF(barRectPx.left, barRectPx.top, barRectPx.left + barRectPx.width() * frac, barRectPx.bottom)
            canvas.drawRoundRect(fill, 10f, 10f, trackFillPaint)
        }
        val scrubDotX = barRectPx.left + barRectPx.width() * frac
        canvas.drawCircle(scrubDotX, barRectPx.centerY(), 13f, ctaPaint)

        // Hover preview notch on seekbar
        if (hoveredAction == DockAction.SCRUB) {
            val hx = barRectPx.left + barRectPx.width() * hoverScrubNorm.coerceIn(0f, 1f)
            canvas.drawCircle(hx, barRectPx.centerY(), 9f, subTextPaint)
        }

        val scrubHit = RectF(barRectPx.left - 8f, 16f, barRectPx.right + 8f, 76f)
        hotspots.add(
            DockHotspot(
                action = DockAction.SCRUB,
                rectNorm = toNorm(scrubHit),
                label = "Seek",
            ),
        )

        subTextPaint.textAlign = Paint.Align.RIGHT
        val timeText = "${MediaRepository.formatDuration(positionMs)} / ${MediaRepository.formatDuration(durationMs)}"
        canvas.drawText(timeText, widthPx - 40f, 52f, subTextPaint)

        if (!showSettings) {
            drawMainRow(
                isPlaying = isPlaying,
                isMuted = isMuted,
                subtitlesLoaded = subtitlesLoaded,
                subtitlesEnabled = subtitlesEnabled,
                config = config,
                hoveredAction = hoveredAction,
            )
        } else {
            drawSettingsRows(config = config, hoveredAction = hoveredAction)
        }
    }

    private fun drawMainRow(
        isPlaying: Boolean,
        isMuted: Boolean,
        subtitlesLoaded: Boolean,
        subtitlesEnabled: Boolean,
        config: CinemaConfig,
        hoveredAction: DockAction?,
    ) {
        data class BtnDef(
            val action: DockAction,
            val label: String,
            val weight: Float,
            val primaryCta: Boolean = false,
            val active: Boolean = false,
        )

        val buttons = listOf(
            BtnDef(DockAction.SEEK_BACK, "-10s", 0.9f),
            BtnDef(DockAction.TOGGLE_PLAY, if (isPlaying) "Pause" else "Play", 1.35f, primaryCta = true),
            BtnDef(DockAction.SEEK_FWD, "+10s", 0.9f),
            BtnDef(DockAction.TOGGLE_MUTE, if (isMuted) "Unmute" else "Audio", 0.95f, active = !isMuted),
            BtnDef(
                DockAction.TOGGLE_CC,
                if (!subtitlesLoaded) "CC --" else if (subtitlesEnabled) "CC On" else "CC Off",
                0.95f,
                active = subtitlesLoaded && subtitlesEnabled,
            ),
            BtnDef(DockAction.CYCLE_ROOM, config.environment.title.substringBefore(' '), 1.05f),
            BtnDef(DockAction.RECENTER, "Center", 0.95f),
            BtnDef(DockAction.TOGGLE_SETTINGS, "Optics", 0.95f),
            BtnDef(DockAction.EXIT_VR, "Exit", 0.85f),
        )

        drawButtonRow(buttons.map { Triple(it.action, it.label, it.weight to (it.primaryCta to it.active)) }, 118f, 250f, hoveredAction)
    }

    private fun drawSettingsRows(config: CinemaConfig, hoveredAction: DockAction?) {
        val row1 = listOf(
            Triple(DockAction.IPD_MINUS, "IPD -", 0.9f to (false to false)),
            Triple(DockAction.IPD_PLUS, "IPD ${config.ipdMm.roundToInt()}+", 1.0f to (false to true)),
            Triple(DockAction.FOV_MINUS, "FOV -", 0.9f to (false to false)),
            Triple(DockAction.FOV_PLUS, "FOV ${config.fovDeg.roundToInt()}°+", 1.0f to (false to true)),
            Triple(DockAction.CYCLE_FORMAT, config.videoFormat.label, 1.15f to (false to (config.videoFormat.ordinal > 0))),
            Triple(DockAction.TOGGLE_SETTINGS, "Back", 0.95f to (true to false)),
        )
        val row2 = listOf(
            Triple(DockAction.TOGGLE_SWAP, if (config.eyeSwap) "L/R Flip" else "L/R Norm", 1.1f to (false to config.eyeSwap)),
            Triple(DockAction.TOGGLE_OPTICS, if (config.barrelEnabled) "Lens On" else "Lens Off", 1.05f to (false to config.barrelEnabled)),
            Triple(DockAction.TOGGLE_THERMAL, if (config.thermalCapEnabled) "60% Cap" else "Max Lum", 1.1f to (false to config.thermalCapEnabled)),
            Triple(DockAction.RECENTER, "Recenter", 1.0f to (false to false)),
            Triple(DockAction.EXIT_VR, "Exit VR", 0.95f to (false to false)),
        )
        drawButtonRow(row1, 96f, 174f, hoveredAction)
        drawButtonRow(row2, 188f, 266f, hoveredAction)
    }

    private fun drawButtonRow(
        items: List<Triple<DockAction, String, Pair<Float, Pair<Boolean, Boolean>>>>,
        topPx: Float,
        bottomPx: Float,
        hoveredAction: DockAction?,
    ) {
        val marginX = 36f
        val gap = 12f
        val totalWeight = items.sumOf { it.third.first.toDouble() }.toFloat()
        val availWidth = widthPx - marginX * 2f - gap * (items.size - 1)
        var cursorX = marginX

        for ((action, label, meta) in items) {
            val weight = meta.first
            val primaryCta = meta.second.first
            val active = meta.second.second
            val btnW = availWidth * (weight / totalWeight)
            val rect = RectF(cursorX, topPx, cursorX + btnW, bottomPx)
            val hovered = hoveredAction == action

            val fillPaint = when {
                primaryCta && hovered -> ctaHoverPaint
                primaryCta -> ctaPaint
                hovered -> btnHoverPaint
                active -> btnActivePaint
                else -> btnIdlePaint
            }
            canvas.drawRoundRect(rect, 22f, 22f, fillPaint)
            if (hovered && !primaryCta) {
                canvas.drawRoundRect(rect, 22f, 22f, borderPaint)
            }

            textPaint.color = if (primaryCta) Color.rgb(4, 42, 39) else Color.WHITE
            textPaint.textSize = if (showSettings) 22f else 24f
            val textY = rect.centerY() - (textPaint.descent() + textPaint.ascent()) * 0.5f
            canvas.drawText(label, rect.centerX(), textY, textPaint)

            hotspots.add(
                DockHotspot(
                    action = action,
                    rectNorm = toNorm(rect),
                    label = label,
                    primaryCta = primaryCta,
                    active = active,
                ),
            )
            cursorX += btnW + gap
        }
    }

    /**
     * Hit tests normalized `(u, v)` on `[0..1, 0..1]` where `u` is left->right and `v` is top->bottom.
     */
    fun hitTest(u: Float, v: Float): Pair<DockAction, Float>? {
        if (dockHidden || u !in 0f..1f || v !in 0f..1f) return null
        for (spot in hotspots) {
            if (spot.rectNorm.contains(u, v)) {
                val scrubFrac = if (spot.action == DockAction.SCRUB) {
                    ((u - spot.rectNorm.left) / spot.rectNorm.width().coerceAtLeast(0.001f)).coerceIn(0f, 1f)
                } else {
                    0f
                }
                return spot.action to scrubFrac
            }
        }
        return DockAction.NONE to 0f
    }

    private fun toNorm(r: RectF): RectF = RectF(
        r.left / widthPx.toFloat(),
        r.top / heightPx.toFloat(),
        r.right / widthPx.toFloat(),
        r.bottom / heightPx.toFloat(),
    )
}
