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
import com.jiodive.cinema.model.TheaterEnvironment
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class DockAction {
    NONE,
    SCRUB,
    TOGGLE_PLAY,
    SEEK_BACK,
    SEEK_FWD,
    TOGGLE_MUTE,
    VOL_DOWN,
    VOL_UP,
    TOGGLE_CC,
    CYCLE_ROOM,
    RECENTER,
    TOGGLE_SETTINGS,
    TOGGLE_LOCK,
    EXIT_VR,
    IPD_MINUS,
    IPD_PLUS,
    FOV_MINUS,
    FOV_PLUS,
    CYCLE_FORMAT,
    TOGGLE_SWAP,
    TOGGLE_OPTICS,
    TOGGLE_THERMAL,
    CYCLE_BED_TILT,
}

data class DockHotspot(
    val action: DockAction,
    val rectNorm: RectF,
    val label: String,
    val primaryCta: Boolean = false,
    val active: Boolean = false,
)

/**
 * Renders a restrained, icon-first translucent cinema bar onto an RGBA Canvas bitmap.
 * Replaces blocky labeled rectangles with vector glyphs, single primary Play/Pause focal
 * point, quiet micro-labels, and a calm dark frosted optical panel.
 */
class VrDockRenderer {
    val widthPx = 1120
    val heightPx = 348
    val bitmap: Bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(bitmap)

    @Volatile
    var showSettings: Boolean = false
    @Volatile
    var dockHidden: Boolean = false
    @Volatile
    var dockLocked: Boolean = false

    private val hotspots = ArrayList<DockHotspot>(16)

    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 14, 18, 21)
    }
    private val panelRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.argb(46, 255, 255, 255)
    }
    private val hoverDiscPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(56, 255, 255, 255)
    }
    private val hoverExitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(72, 239, 68, 68)
    }
    private val ctaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 56, 198, 189)
    }
    private val ctaHoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 118, 232, 224)
    }
    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(28, 255, 255, 255)
    }
    private val pillActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(60, 56, 198, 189)
    }
    private val pillHoverPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(74, 255, 255, 255)
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(38, 255, 255, 255)
        strokeWidth = 2f
    }
    private val trackBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(55, 255, 255, 255)
    }
    private val trackFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(240, 56, 198, 189)
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val glyphStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.argb(242, 240, 246, 248)
    }
    private val glyphFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(242, 240, 246, 248)
    }
    private val activeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(255, 56, 198, 189)
    }
    private val dwellRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(245, 56, 198, 189)
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(185, 208, 218, 224)
        textSize = 18f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val tinyNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(242, 240, 246, 248)
        textSize = 16f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val headerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(235, 240, 246, 248)
        textSize = 23f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(196, 212, 222, 228)
        textSize = 21f
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    fun redraw(
        isPlaying: Boolean,
        isMuted: Boolean,
        volumeFraction: Float,
        subtitlesLoaded: Boolean,
        subtitlesEnabled: Boolean,
        positionMs: Long,
        durationMs: Long,
        config: CinemaConfig,
        hoveredAction: DockAction?,
        hoverScrubNorm: Float,
        dwellProgress: Float = 0f,
        effectiveBedTiltDeg: Float = config.bedTiltDeg,
    ) {
        hotspots.clear()
        bitmap.eraseColor(Color.TRANSPARENT)

        if (dockLocked) {
            drawLockedPill(
                hovered = hoveredAction == DockAction.TOGGLE_LOCK,
                dwellProgress = dwellProgress,
            )
            return
        }

        val panelRect = RectF(22f, 18f, widthPx - 22f, heightPx - 18f)
        canvas.drawRoundRect(panelRect, 44f, 44f, panelPaint)
        canvas.drawRoundRect(panelRect, 44f, 44f, panelRimPaint)

        if (!showSettings) {
            drawTimeline(
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                hoveredAction = hoveredAction,
                hoverScrubNorm = hoverScrubNorm,
            )
            drawMainIconBar(
                isPlaying = isPlaying,
                isMuted = isMuted,
                subtitlesLoaded = subtitlesLoaded,
                subtitlesEnabled = subtitlesEnabled,
                config = config,
                hoveredAction = hoveredAction,
                dwellProgress = dwellProgress,
            )
            drawComfortBottomStrip(
                isMuted = isMuted,
                volumeFraction = volumeFraction,
                hoveredAction = hoveredAction,
                dwellProgress = dwellProgress,
            )
        } else {
            drawOpticsHeader(hoveredAction = hoveredAction, dwellProgress = dwellProgress)
            drawSettingsPanel(
                config = config,
                hoveredAction = hoveredAction,
                dwellProgress = dwellProgress,
                effectiveBedTiltDeg = effectiveBedTiltDeg,
            )
        }
    }

    private fun drawLockedPill(hovered: Boolean, dwellProgress: Float) {
        val pill = RectF(widthPx * 0.5f - 195f, 232f, widthPx * 0.5f + 195f, 314f)
        canvas.drawRoundRect(pill, 41f, 41f, panelPaint)
        canvas.drawRoundRect(pill, 41f, 41f, panelRimPaint)
        if (hovered) {
            canvas.drawRoundRect(pill, 41f, 41f, pillHoverPaint)
            if (dwellProgress > 0.01f) {
                val fillW = (pill.width() - 44f) * dwellProgress.coerceIn(0f, 1f)
                canvas.drawLine(pill.left + 22f, pill.bottom - 7f, pill.left + 22f + fillW, pill.bottom - 7f, dwellRingPaint)
            }
        }
        drawLockGlyph(pill.left + 58f, pill.centerY(), locked = true)
        val prevAlign = timePaint.textAlign
        val prevColor = timePaint.color
        timePaint.textAlign = Paint.Align.LEFT
        timePaint.color = Color.WHITE
        val textY = pill.centerY() - (timePaint.descent() + timePaint.ascent()) * 0.5f
        val text = if (hovered && dwellProgress > 0.05f) "Hold Gaze to Unlock" else "Dock Locked · Unlock"
        canvas.drawText(text, pill.left + 92f, textY, timePaint)
        timePaint.textAlign = prevAlign
        timePaint.color = prevColor

        hotspots.add(
            DockHotspot(
                action = DockAction.TOGGLE_LOCK,
                rectNorm = toNorm(RectF(pill.left - 16f, pill.top - 18f, pill.right + 16f, pill.bottom + 14f)),
                label = "Unlock Dock",
                active = true,
            ),
        )
    }

    private fun drawTimeline(
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        hoveredAction: DockAction?,
        hoverScrubNorm: Float,
    ) {
        val barRect = RectF(58f, 56f, widthPx - 246f, 65f)
        canvas.drawRoundRect(barRect, 4.5f, 4.5f, trackBgPaint)
        val frac = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
        if (frac > 0.004f) {
            val fill = RectF(barRect.left, barRect.top, barRect.left + barRect.width() * frac, barRect.bottom)
            canvas.drawRoundRect(fill, 4.5f, 4.5f, trackFillPaint)
        }
        val dotX = barRect.left + barRect.width() * frac
        canvas.drawCircle(dotX, barRect.centerY(), if (hoveredAction == DockAction.SCRUB) 11f else 8.5f, thumbPaint)

        if (hoveredAction == DockAction.SCRUB) {
            val hx = barRect.left + barRect.width() * hoverScrubNorm.coerceIn(0f, 1f)
            canvas.drawCircle(hx, barRect.centerY(), 7f, hoverDiscPaint)
        }

        hotspots.add(
            DockHotspot(
                action = DockAction.SCRUB,
                rectNorm = toNorm(RectF(barRect.left - 12f, 26f, barRect.right + 12f, 94f)),
                label = "Seek",
            ),
        )

        val timeText = "${MediaRepository.formatDuration(positionMs)} / ${MediaRepository.formatDuration(durationMs)}"
        canvas.drawText(timeText, widthPx - 54f, 68f, timePaint)

        // Subtle live emerald/cyan playback dot beside time readout
        val statusDotX = widthPx - 54f - timePaint.measureText(timeText) - 16f
        activeDotPaint.alpha = if (isPlaying) 245 else 95
        canvas.drawCircle(statusDotX, 60.5f, 4.2f, activeDotPaint)
        activeDotPaint.alpha = 255
    }

    private fun drawMainIconBar(
        isPlaying: Boolean,
        isMuted: Boolean,
        subtitlesLoaded: Boolean,
        subtitlesEnabled: Boolean,
        config: CinemaConfig,
        hoveredAction: DockAction?,
        dwellProgress: Float,
    ) {
        val rowCy = 148f
        val hitTop = 98f
        val hitBottom = 224f

        // Left Transport Group: -10s | Play/Pause Medallion | +10s
        drawIconControl(
            action = DockAction.SEEK_BACK,
            cx = 102f,
            cy = rowCy,
            hitTop = hitTop,
            hitBottom = hitBottom,
            hitHalfW = 48f,
            caption = "",
            hovered = hoveredAction == DockAction.SEEK_BACK,
            active = false,
            dwellProgress = dwellProgress,
            drawGlyph = { x, y -> drawSkipGlyph(x, y, forward = false) },
        )

        // Hero Play / Pause Medallion (only bright accent fill on the entire dock)
        val playCx = 204f
        val playHovered = hoveredAction == DockAction.TOGGLE_PLAY
        val playR = if (playHovered) 42f else 39f
        canvas.drawCircle(playCx, rowCy + 2f, playR, if (playHovered) ctaHoverPaint else ctaPaint)
        if (playHovered && dwellProgress > 0.01f) {
            val arcRect = RectF(playCx - 46f, rowCy + 2f - 46f, playCx + 46f, rowCy + 2f + 46f)
            canvas.drawArc(arcRect, -90f, dwellProgress * 360f, false, dwellRingPaint)
        }
        drawPlayPauseGlyph(playCx, rowCy + 2f, isPlaying)
        hotspots.add(
            DockHotspot(
                action = DockAction.TOGGLE_PLAY,
                rectNorm = toNorm(RectF(playCx - 50f, hitTop, playCx + 50f, hitBottom)),
                label = if (isPlaying) "Pause" else "Play",
                primaryCta = true,
            ),
        )

        drawIconControl(
            action = DockAction.SEEK_FWD,
            cx = 306f,
            cy = rowCy,
            hitTop = hitTop,
            hitBottom = hitBottom,
            hitHalfW = 48f,
            caption = "",
            hovered = hoveredAction == DockAction.SEEK_FWD,
            active = false,
            dwellProgress = dwellProgress,
            drawGlyph = { x, y -> drawSkipGlyph(x, y, forward = true) },
        )

        // Hairline vertical separator
        canvas.drawLine(372f, 110f, 372f, 212f, dividerPaint)

        // Right Theater & Utility Group (quiet icon + micro-caption, generous spacing)
        val utilStartX = 436f
        val utilStep = 114f

        drawIconControl(
            action = DockAction.TOGGLE_MUTE,
            cx = utilStartX + utilStep * 0f,
            cy = rowCy - 10f,
            hitTop = hitTop,
            hitBottom = hitBottom,
            hitHalfW = 50f,
            caption = if (isMuted) "Muted" else "Audio",
            hovered = hoveredAction == DockAction.TOGGLE_MUTE,
            active = isMuted,
            dwellProgress = dwellProgress,
            drawGlyph = { x, y -> drawSpeakerGlyph(x, y, muted = isMuted) },
        )

        val ccActive = subtitlesLoaded && subtitlesEnabled
        drawIconControl(
            action = DockAction.TOGGLE_CC,
            cx = utilStartX + utilStep * 1f,
            cy = rowCy - 10f,
            hitTop = hitTop,
            hitBottom = hitBottom,
            hitHalfW = 50f,
            caption = "CC",
            hovered = hoveredAction == DockAction.TOGGLE_CC,
            active = ccActive,
            dwellProgress = dwellProgress,
            drawGlyph = { x, y -> drawCcGlyph(x, y, dim = !subtitlesLoaded) },
        )

        drawIconControl(
            action = DockAction.CYCLE_ROOM,
            cx = utilStartX + utilStep * 2f,
            cy = rowCy - 10f,
            hitTop = hitTop,
            hitBottom = hitBottom,
            hitHalfW = 50f,
            caption = when (config.environment) {
                TheaterEnvironment.CINEMA -> "Hall"
                TheaterEnvironment.VOID -> "Void"
                TheaterEnvironment.BED -> "Bed"
            },
            hovered = hoveredAction == DockAction.CYCLE_ROOM,
            active = false,
            dwellProgress = dwellProgress,
            drawGlyph = { x, y -> drawRoomGlyph(x, y, config.environment) },
        )

        drawIconControl(
            action = DockAction.RECENTER,
            cx = utilStartX + utilStep * 3f,
            cy = rowCy - 10f,
            hitTop = hitTop,
            hitBottom = hitBottom,
            hitHalfW = 50f,
            caption = "Center",
            hovered = hoveredAction == DockAction.RECENTER,
            active = false,
            dwellProgress = dwellProgress,
            drawGlyph = { x, y -> drawRecenterGlyph(x, y) },
        )

        drawIconControl(
            action = DockAction.TOGGLE_SETTINGS,
            cx = utilStartX + utilStep * 4f,
            cy = rowCy - 10f,
            hitTop = hitTop,
            hitBottom = hitBottom,
            hitHalfW = 50f,
            caption = "Optics",
            hovered = hoveredAction == DockAction.TOGGLE_SETTINGS,
            active = false,
            dwellProgress = dwellProgress,
            drawGlyph = { x, y -> drawGearGlyph(x, y) },
        )

        drawIconControl(
            action = DockAction.EXIT_VR,
            cx = utilStartX + utilStep * 5f,
            cy = rowCy - 10f,
            hitTop = hitTop,
            hitBottom = hitBottom,
            hitHalfW = 50f,
            caption = "Exit",
            hovered = hoveredAction == DockAction.EXIT_VR,
            active = false,
            isDestructive = true,
            dwellProgress = dwellProgress,
            drawGlyph = { x, y -> drawExitGlyph(x, y) },
        )
    }

    private fun drawComfortBottomStrip(
        isMuted: Boolean,
        volumeFraction: Float,
        hoveredAction: DockAction?,
        dwellProgress: Float,
    ) {
        canvas.drawLine(58f, 228f, widthPx - 58f, 228f, dividerPaint)

        val rowTop = 240f
        val rowBottom = 304f
        val cy = (rowTop + rowBottom) * 0.5f

        val volDownRect = RectF(58f, rowTop, 188f, rowBottom)
        drawActionPill(
            action = DockAction.VOL_DOWN,
            rect = volDownRect,
            label = "Vol −",
            hovered = hoveredAction == DockAction.VOL_DOWN,
            active = false,
            dwellProgress = dwellProgress,
        )

        // Volume meter readout pill
        val meterRect = RectF(198f, rowTop, 524f, rowBottom)
        canvas.drawRoundRect(meterRect, 18f, 18f, pillBgPaint)
        val barLeft = meterRect.left + 20f
        val barRight = meterRect.right - 92f
        val barTrack = RectF(barLeft, cy - 4.5f, barRight, cy + 4.5f)
        canvas.drawRoundRect(barTrack, 4.5f, 4.5f, trackBgPaint)
        val effVol = if (isMuted) 0f else volumeFraction.coerceIn(0f, 1f)
        if (effVol > 0.01f) {
            val fill = RectF(barLeft, barTrack.top, barLeft + barTrack.width() * effVol, barTrack.bottom)
            canvas.drawRoundRect(fill, 4.5f, 4.5f, trackFillPaint)
        }
        val pctLabel = if (isMuted) "Muted" else "${(effVol * 100f).roundToInt()}%"
        val pctY = cy - (tinyNumPaint.descent() + tinyNumPaint.ascent()) * 0.5f
        val prevSize = tinyNumPaint.textSize
        tinyNumPaint.textSize = 19f
        canvas.drawText(pctLabel, meterRect.right - 46f, pctY, tinyNumPaint)
        tinyNumPaint.textSize = prevSize

        val volUpRect = RectF(534f, rowTop, 664f, rowBottom)
        drawActionPill(
            action = DockAction.VOL_UP,
            rect = volUpRect,
            label = "Vol +",
            hovered = hoveredAction == DockAction.VOL_UP,
            active = false,
            dwellProgress = dwellProgress,
        )

        val lockRect = RectF(widthPx - 342f, rowTop, widthPx - 58f, rowBottom)
        val lockHovered = hoveredAction == DockAction.TOGGLE_LOCK
        canvas.drawRoundRect(
            lockRect,
            18f,
            18f,
            if (lockHovered) pillHoverPaint else pillBgPaint,
        )
        if (lockHovered && dwellProgress > 0.01f) {
            val fillW = (lockRect.width() - 24f) * dwellProgress.coerceIn(0f, 1f)
            canvas.drawLine(lockRect.left + 12f, lockRect.bottom - 5f, lockRect.left + 12f + fillW, lockRect.bottom - 5f, dwellRingPaint)
        }
        drawLockGlyph(lockRect.centerX() - 62f, cy, locked = false)
        val prevTimeAlign = timePaint.textAlign
        val prevTimeColor = timePaint.color
        timePaint.textAlign = Paint.Align.LEFT
        timePaint.color = Color.WHITE
        val lockTextY = cy - (timePaint.descent() + timePaint.ascent()) * 0.5f
        canvas.drawText("Lock Dock", lockRect.centerX() - 34f, lockTextY, timePaint)
        timePaint.textAlign = prevTimeAlign
        timePaint.color = prevTimeColor

        hotspots.add(
            DockHotspot(
                action = DockAction.TOGGLE_LOCK,
                rectNorm = toNorm(lockRect),
                label = "Lock Dock",
            ),
        )
    }

    private fun drawActionPill(
        action: DockAction,
        rect: RectF,
        label: String,
        hovered: Boolean,
        active: Boolean,
        dwellProgress: Float,
    ) {
        val bg = when {
            hovered -> pillHoverPaint
            active -> pillActivePaint
            else -> pillBgPaint
        }
        canvas.drawRoundRect(rect, 18f, 18f, bg)
        if (hovered && dwellProgress > 0.01f) {
            val fillW = (rect.width() - 24f) * dwellProgress.coerceIn(0f, 1f)
            canvas.drawLine(rect.left + 12f, rect.bottom - 5f, rect.left + 12f + fillW, rect.bottom - 5f, dwellRingPaint)
        }
        val prevAlign = timePaint.textAlign
        val prevColor = timePaint.color
        timePaint.textAlign = Paint.Align.CENTER
        timePaint.color = Color.WHITE
        val ty = rect.centerY() - (timePaint.descent() + timePaint.ascent()) * 0.5f
        canvas.drawText(label, rect.centerX(), ty, timePaint)
        timePaint.textAlign = prevAlign
        timePaint.color = prevColor

        hotspots.add(
            DockHotspot(
                action = action,
                rectNorm = toNorm(rect),
                label = label,
                active = active,
            ),
        )
    }

    private inline fun drawIconControl(
        action: DockAction,
        cx: Float,
        cy: Float,
        hitTop: Float,
        hitBottom: Float,
        hitHalfW: Float,
        caption: String,
        hovered: Boolean,
        active: Boolean,
        isDestructive: Boolean = false,
        dwellProgress: Float = 0f,
        drawGlyph: (Float, Float) -> Unit,
    ) {
        if (hovered) {
            canvas.drawCircle(cx, cy, 34f, if (isDestructive) hoverExitPaint else hoverDiscPaint)
            if (dwellProgress > 0.01f) {
                val oval = RectF(cx - 34f, cy - 34f, cx + 34f, cy + 34f)
                canvas.drawArc(oval, -90f, dwellProgress * 360f, false, dwellRingPaint)
            }
        }
        drawGlyph(cx, cy)

        if (caption.isNotEmpty()) {
            captionPaint.color = when {
                hovered -> Color.WHITE
                active -> Color.argb(245, 56, 198, 189)
                else -> Color.argb(180, 196, 208, 214)
            }
            canvas.drawText(caption, cx, cy + 50f, captionPaint)
            if (active) {
                canvas.drawCircle(cx, cy + 62f, 3.2f, activeDotPaint)
            }
        }

        hotspots.add(
            DockHotspot(
                action = action,
                rectNorm = toNorm(RectF(cx - hitHalfW, hitTop, cx + hitHalfW, hitBottom)),
                label = caption.ifEmpty { action.name },
                active = active,
            ),
        )
    }

    private fun drawOpticsHeader(hoveredAction: DockAction?, dwellProgress: Float) {
        canvas.drawText("Headset & Optics", 54f, 68f, headerTitlePaint)

        val doneRect = RectF(widthPx - 168f, 38f, widthPx - 48f, 82f)
        val hovered = hoveredAction == DockAction.TOGGLE_SETTINGS
        canvas.drawRoundRect(doneRect, 22f, 22f, if (hovered) ctaHoverPaint else ctaPaint)
        if (hovered && dwellProgress > 0.01f) {
            val fillEnd = doneRect.left + doneRect.width() * dwellProgress.coerceIn(0f, 1f)
            canvas.drawLine(doneRect.left + 12f, doneRect.bottom - 5f, fillEnd - 6f, doneRect.bottom - 5f, dwellRingPaint)
        }
        tinyNumPaint.color = Color.rgb(4, 34, 32)
        tinyNumPaint.textSize = 20f
        val textY = doneRect.centerY() - (tinyNumPaint.descent() + tinyNumPaint.ascent()) * 0.5f
        canvas.drawText("Done", doneRect.centerX(), textY, tinyNumPaint)
        tinyNumPaint.color = Color.argb(242, 240, 246, 248)
        tinyNumPaint.textSize = 16f

        hotspots.add(
            DockHotspot(
                action = DockAction.TOGGLE_SETTINGS,
                rectNorm = toNorm(doneRect),
                label = "Done",
                primaryCta = true,
            ),
        )
    }

    private fun drawSettingsPanel(
        config: CinemaConfig,
        hoveredAction: DockAction?,
        dwellProgress: Float,
        effectiveBedTiltDeg: Float,
    ) {
        data class PillItem(
            val action: DockAction,
            val label: String,
            val weight: Float,
            val active: Boolean = false,
        )

        val row1 = listOf(
            PillItem(DockAction.IPD_MINUS, "IPD −", 0.9f),
            PillItem(DockAction.IPD_PLUS, "${config.ipdMm.roundToInt()} mm +", 1.15f, active = true),
            PillItem(DockAction.FOV_MINUS, "FOV −", 0.9f),
            PillItem(DockAction.FOV_PLUS, "${config.fovDeg.roundToInt()}° +", 1.15f, active = true),
            PillItem(DockAction.CYCLE_FORMAT, config.videoFormat.label, 1.25f, active = config.videoFormat.ordinal > 0),
        )
        val row2 = if (config.environment == TheaterEnvironment.BED) {
            listOf(
                PillItem(DockAction.TOGGLE_SWAP, if (config.eyeSwap) "Swap Eyes" else "Normal Eyes", 1.10f, active = config.eyeSwap),
                PillItem(DockAction.TOGGLE_OPTICS, if (config.barrelEnabled) "Lens Warp" else "Flat Lens", 1.05f, active = config.barrelEnabled),
                PillItem(DockAction.TOGGLE_THERMAL, if (config.thermalCapEnabled) "60% Cap" else "Max Bright", 1.05f, active = config.thermalCapEnabled),
                PillItem(DockAction.CYCLE_BED_TILT, "Tilt ${effectiveBedTiltDeg.roundToInt()}°", 1.00f, active = true),
                PillItem(DockAction.RECENTER, "Center", 0.90f),
                PillItem(DockAction.EXIT_VR, "Exit", 0.75f),
            )
        } else {
            listOf(
                PillItem(DockAction.TOGGLE_SWAP, if (config.eyeSwap) "Eyes Swapped" else "Normal Eyes", 1.2f, active = config.eyeSwap),
                PillItem(DockAction.TOGGLE_OPTICS, if (config.barrelEnabled) "Lens Warp On" else "Flat Lens", 1.2f, active = config.barrelEnabled),
                PillItem(DockAction.TOGGLE_THERMAL, if (config.thermalCapEnabled) "60% Thermal Cap" else "Max Bright", 1.3f, active = config.thermalCapEnabled),
                PillItem(DockAction.RECENTER, "Reset View", 1.0f),
                PillItem(DockAction.EXIT_VR, "Exit", 0.85f),
            )
        }

        drawFrostedPillRow(row1.map { Triple(it.action, it.label, it.weight to it.active) }, 112f, 196f, hoveredAction, dwellProgress)
        drawFrostedPillRow(row2.map { Triple(it.action, it.label, it.weight to it.active) }, 212f, 296f, hoveredAction, dwellProgress)
    }

    private fun drawFrostedPillRow(
        items: List<Triple<DockAction, String, Pair<Float, Boolean>>>,
        topPx: Float,
        bottomPx: Float,
        hoveredAction: DockAction?,
        dwellProgress: Float,
    ) {
        val marginX = 48f
        val gap = if (items.size > 5) 10f else 12f
        val totalWeight = items.sumOf { it.third.first.toDouble() }.toFloat()
        val availWidth = widthPx - marginX * 2f - gap * (items.size - 1)
        var cursorX = marginX

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = if (items.size > 5) 18.5f else 20f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        for ((action, label, meta) in items) {
            val weight = meta.first
            val active = meta.second
            val btnW = availWidth * (weight / totalWeight)
            val rect = RectF(cursorX, topPx, cursorX + btnW, bottomPx)
            val hovered = hoveredAction == action

            val bg = when {
                hovered -> pillHoverPaint
                active -> pillActivePaint
                else -> pillBgPaint
            }
            canvas.drawRoundRect(rect, 18f, 18f, bg)
            if (hovered && dwellProgress > 0.01f) {
                val fillW = (rect.width() - 24f) * dwellProgress.coerceIn(0f, 1f)
                canvas.drawLine(rect.left + 12f, rect.bottom - 5f, rect.left + 12f + fillW, rect.bottom - 5f, dwellRingPaint)
            }

            labelPaint.color = if (active && !hovered) Color.argb(255, 165, 243, 236) else Color.WHITE
            val textY = rect.centerY() - (labelPaint.descent() + labelPaint.ascent()) * 0.5f
            canvas.drawText(label, rect.centerX(), textY, labelPaint)

            hotspots.add(
                DockHotspot(
                    action = action,
                    rectNorm = toNorm(rect),
                    label = label,
                    active = active,
                ),
            )
            cursorX += btnW + gap
        }
    }

    // --- Crisp Vector Canvas Glyphs ---

    private fun drawPlayPauseGlyph(cx: Float, cy: Float, isPlaying: Boolean) {
        glyphFillPaint.color = Color.rgb(4, 34, 32)
        if (isPlaying) {
            val w = 8.5f
            val h = 30f
            canvas.drawRoundRect(cx - 13f, cy - h * 0.5f, cx - 13f + w, cy + h * 0.5f, 4f, 4f, glyphFillPaint)
            canvas.drawRoundRect(cx + 4.5f, cy - h * 0.5f, cx + 4.5f + w, cy + h * 0.5f, 4f, 4f, glyphFillPaint)
        } else {
            val p = Path().apply {
                moveTo(cx - 10f, cy - 16f)
                lineTo(cx + 16f, cy)
                lineTo(cx - 10f, cy + 16f)
                close()
            }
            canvas.drawPath(p, glyphFillPaint)
        }
        glyphFillPaint.color = Color.argb(242, 240, 246, 248)
    }

    private fun drawSkipGlyph(cx: Float, cy: Float, forward: Boolean) {
        val r = 20f
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        glyphStrokePaint.strokeWidth = 3.4f
        if (forward) {
            canvas.drawArc(oval, -60f, 290f, false, glyphStrokePaint)
            // Arrow head near top-right (-60 deg)
            canvas.drawLine(cx + 10f, cy - 17f, cx + 19f, cy - 13f, glyphStrokePaint)
            canvas.drawLine(cx + 10f, cy - 17f, cx + 8f, cy - 26f, glyphStrokePaint)
        } else {
            canvas.drawArc(oval, -120f, -290f, false, glyphStrokePaint)
            // Arrow head near top-left (-120 deg)
            canvas.drawLine(cx - 10f, cy - 17f, cx - 19f, cy - 13f, glyphStrokePaint)
            canvas.drawLine(cx - 10f, cy - 17f, cx - 8f, cy - 26f, glyphStrokePaint)
        }
        glyphStrokePaint.strokeWidth = 3.8f
        val numY = cy - (tinyNumPaint.descent() + tinyNumPaint.ascent()) * 0.5f + 2f
        canvas.drawText("10", cx, numY, tinyNumPaint)
    }

    private fun drawSpeakerGlyph(cx: Float, cy: Float, muted: Boolean) {
        val body = Path().apply {
            moveTo(cx - 17f, cy - 6f)
            lineTo(cx - 9f, cy - 6f)
            lineTo(cx + 1f, cy - 15f)
            lineTo(cx + 1f, cy + 15f)
            lineTo(cx - 9f, cy + 6f)
            lineTo(cx - 17f, cy + 6f)
            close()
        }
        canvas.drawPath(body, glyphFillPaint)
        glyphStrokePaint.strokeWidth = 3.4f
        if (muted) {
            canvas.drawLine(cx + 7f, cy - 7f, cx + 19f, cy + 7f, glyphStrokePaint)
            canvas.drawLine(cx + 19f, cy - 7f, cx + 7f, cy + 7f, glyphStrokePaint)
        } else {
            canvas.drawArc(RectF(cx - 2f, cy - 10f, cx + 12f, cy + 10f), -48f, 96f, false, glyphStrokePaint)
            canvas.drawArc(RectF(cx - 5f, cy - 16f, cx + 19f, cy + 16f), -52f, 104f, false, glyphStrokePaint)
        }
        glyphStrokePaint.strokeWidth = 3.8f
    }

    private fun drawCcGlyph(cx: Float, cy: Float, dim: Boolean) {
        val alpha = if (dim) 115 else 242
        glyphStrokePaint.alpha = alpha
        tinyNumPaint.alpha = alpha
        canvas.drawRoundRect(RectF(cx - 20f, cy - 14f, cx + 20f, cy + 14f), 6f, 6f, glyphStrokePaint)
        val ty = cy - (tinyNumPaint.descent() + tinyNumPaint.ascent()) * 0.5f
        canvas.drawText("CC", cx, ty, tinyNumPaint)
        glyphStrokePaint.alpha = 242
        tinyNumPaint.alpha = 242
    }

    private fun drawRoomGlyph(cx: Float, cy: Float, env: TheaterEnvironment) {
        glyphStrokePaint.strokeWidth = 3.4f
        when (env) {
            TheaterEnvironment.CINEMA -> {
                // Curved cinema screen + base
                val arc = Path().apply {
                    moveTo(cx - 18f, cy - 10f)
                    quadTo(cx, cy - 15f, cx + 18f, cy - 10f)
                    lineTo(cx + 18f, cy + 10f)
                    quadTo(cx, cy + 6f, cx - 18f, cy + 10f)
                    close()
                }
                canvas.drawPath(arc, glyphStrokePaint)
            }
            TheaterEnvironment.VOID -> {
                // Quiet crescent moon / void icon
                canvas.drawArc(RectF(cx - 15f, cy - 15f, cx + 15f, cy + 15f), 35f, 250f, false, glyphStrokePaint)
                canvas.drawArc(RectF(cx - 5f, cy - 14f, cx + 16f, cy + 7f), 65f, 195f, false, glyphStrokePaint)
            }
            TheaterEnvironment.BED -> {
                // Reclined ceiling viewer horizon line + pillow
                canvas.drawLine(cx - 18f, cy + 10f, cx + 18f, cy + 10f, glyphStrokePaint)
                canvas.drawRoundRect(RectF(cx - 16f, cy - 2f, cx - 2f, cy + 6f), 4f, 4f, glyphStrokePaint)
                canvas.drawArc(RectF(cx - 14f, cy - 16f, cx + 14f, cy - 4f), 205f, 130f, false, glyphStrokePaint)
            }
        }
        glyphStrokePaint.strokeWidth = 3.8f
    }

    private fun drawRecenterGlyph(cx: Float, cy: Float) {
        glyphStrokePaint.strokeWidth = 3.4f
        canvas.drawCircle(cx, cy, 13f, glyphStrokePaint)
        canvas.drawLine(cx - 20f, cy, cx - 10f, cy, glyphStrokePaint)
        canvas.drawLine(cx + 10f, cy, cx + 20f, cy, glyphStrokePaint)
        canvas.drawLine(cx, cy - 20f, cx, cy - 10f, glyphStrokePaint)
        canvas.drawLine(cx, cy + 10f, cx, cy + 20f, glyphStrokePaint)
        canvas.drawCircle(cx, cy, 3.5f, glyphFillPaint)
        glyphStrokePaint.strokeWidth = 3.8f
    }

    private fun drawGearGlyph(cx: Float, cy: Float) {
        glyphStrokePaint.strokeWidth = 3.3f
        canvas.drawCircle(cx, cy, 6.5f, glyphStrokePaint)
        val path = Path()
        val teeth = 8
        for (i in 0 until teeth * 2) {
            val angle = i * PI / teeth
            val r = if (i % 2 == 0) 17.5f else 12.5f
            val px = cx + (cos(angle) * r).toFloat()
            val py = cy + (sin(angle) * r).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        canvas.drawPath(path, glyphStrokePaint)
        glyphStrokePaint.strokeWidth = 3.8f
    }

    private fun drawExitGlyph(cx: Float, cy: Float) {
        glyphStrokePaint.strokeWidth = 3.4f
        // Doorframe + outward arrow
        val frame = Path().apply {
            moveTo(cx + 2f, cy - 15f)
            lineTo(cx - 14f, cy - 15f)
            lineTo(cx - 14f, cy + 15f)
            lineTo(cx + 2f, cy + 15f)
        }
        canvas.drawPath(frame, glyphStrokePaint)
        canvas.drawLine(cx - 4f, cy, cx + 16f, cy, glyphStrokePaint)
        canvas.drawLine(cx + 10f, cy - 6f, cx + 16f, cy, glyphStrokePaint)
        canvas.drawLine(cx + 10f, cy + 6f, cx + 16f, cy, glyphStrokePaint)
        glyphStrokePaint.strokeWidth = 3.8f
    }

    private fun drawLockGlyph(cx: Float, cy: Float, locked: Boolean) {
        glyphStrokePaint.strokeWidth = 3.0f
        val body = RectF(cx - 10f, cy - 2f, cx + 10f, cy + 12f)
        canvas.drawRoundRect(body, 4f, 4f, glyphStrokePaint)
        val shackle = RectF(
            if (locked) cx - 6.5f else cx - 9f,
            cy - 13f,
            if (locked) cx + 6.5f else cx + 4f,
            cy,
        )
        canvas.drawArc(shackle, 180f, if (locked) 180f else 145f, false, glyphStrokePaint)
        glyphStrokePaint.strokeWidth = 3.8f
    }

    /**
     * Hit tests normalized `(u, v)` on `[0..1, 0..1]` where `u` is left->right and `v` is top->bottom.
     */
    fun hitTest(u: Float, v: Float): Pair<DockAction, Float>? {
        if ((dockHidden && !dockLocked) || u !in 0f..1f || v !in 0f..1f) return null
        if (dockLocked) {
            val unlockSpot = hotspots.firstOrNull { it.action == DockAction.TOGGLE_LOCK }
            return if (unlockSpot != null && hotspots.size == 1 && unlockSpot.rectNorm.contains(u, v)) {
                DockAction.TOGGLE_LOCK to 0f
            } else {
                null
            }
        }
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
