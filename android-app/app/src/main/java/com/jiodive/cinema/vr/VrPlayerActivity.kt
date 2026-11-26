package com.jiodive.cinema.vr

import android.annotation.SuppressLint
import android.media.AudioManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.jiodive.cinema.R
import com.jiodive.cinema.data.MediaRepository
import com.jiodive.cinema.data.PrefsStore
import com.jiodive.cinema.data.SrtVttParser
import com.jiodive.cinema.model.SubtitleCue
import com.jiodive.cinema.ui.components.DisplayRefreshHelper
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Full-screen landscape stereoscopic VR playback Activity (JioImmerse PlayerActivity /
 * JmrUnityActivity orientation & thermal contract):
 * - Enforces JioDive 60% thermal window brightness cap when enabled
 * - Connects Media3 ExoPlayer -> OpenGL ES 3.0 SurfaceTexture
 * - Handles gaze-dwell clicks, screen capacitive button taps, touch drag look, & Bluetooth controllers
 */
class VrPlayerActivity : ComponentActivity() {
    private lateinit var prefs: PrefsStore
    private lateinit var audioManager: AudioManager
    private lateinit var glView: GLSurfaceView
    private lateinit var headTracker: HeadTracker
    private lateinit var dockRenderer: VrDockRenderer
    private lateinit var renderer: StereoGlRenderer

    private var exoPlayer: ExoPlayer? = null
    private var decoderSurface: Surface? = null
    private var subtitleCues: List<SubtitleCue> = emptyList()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var touchMovedDistance = 0f
    private var touchDownElapsedMs = 0L

    private var playbackMediaId = ""
    private var playbackUriStr = ""
    private var playbackTitle = ""
    private var playbackIsStream = false
    private var playbackIsDemo = false
    private var fallbackDurationMs = 0L
    private var intendedResumeMs = 0L
    private var playbackReachedReady = false
    private var lastPersistedPosMs = -1L
    private var lastPersistWallMs = 0L

    private fun persistPlaybackProgress(force: Boolean = false) {
        val player = exoPlayer ?: return
        if (playbackUriStr.isBlank()) return
        if (!playbackReachedReady && intendedResumeMs > 2_000L) return
        val now = SystemClock.elapsedRealtime()
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.takeIf { it > 0L } ?: fallbackDurationMs
        if (!force) {
            if (now - lastPersistWallMs < 5000L) return
            if (lastPersistedPosMs >= 0L && abs(pos - lastPersistedPosMs) < 1500L) return
        }
        lastPersistWallMs = now
        lastPersistedPosMs = pos
        prefs.upsertWatchProgress(
            mediaId = playbackMediaId,
            uriString = playbackUriStr,
            title = playbackTitle,
            positionMs = pos,
            durationMs = dur,
            isStream = playbackIsStream,
            isDemo = playbackIsDemo,
        )
    }

    private val telemetryTicker = object : Runnable {
        override fun run() {
            val player = exoPlayer
            if (player != null) {
                val pos = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration.coerceAtLeast(0L)
                renderer.isPlaying = player.isPlaying || (player.playWhenReady && player.playbackState != Player.STATE_ENDED)
                renderer.isMuted = player.volume <= 0.01f
                renderer.volumeFraction = readVolumeFraction()
                renderer.positionMs = pos
                renderer.durationMs = dur
                renderer.activeSubtitleText = SrtVttParser.activeCueAt(
                    cues = subtitleCues,
                    positionMs = pos,
                    offsetMs = renderer.config.subtitleOffsetMs,
                )
                persistPlaybackProgress(force = false)
            }
            mainHandler.postDelayed(this, 100L)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(
                OVERRIDE_TRANSITION_OPEN,
                R.anim.vr_enter_fade,
                R.anim.vr_stay,
            )
            overrideActivityTransition(
                OVERRIDE_TRANSITION_CLOSE,
                R.anim.vr_enter_fade,
                R.anim.vr_exit_fade,
            )
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finish()
                }
            },
        )

        prefs = PrefsStore(this)
        audioManager = getSystemService(AudioManager::class.java)
        val initialCfg = prefs.loadConfig()
        applyThermalBrightnessCap(initialCfg.thermalCapEnabled)
        DisplayRefreshHelper.enablePeakRefreshRate(this)

        headTracker = HeadTracker(this).apply {
            displayRotation = currentDisplayRotation()
        }
        dockRenderer = VrDockRenderer()

        val videoUriStr = intent.getStringExtra(EXTRA_VIDEO_URI)
        if (videoUriStr.isNullOrBlank()) {
            finish()
            return
        }
        playbackUriStr = videoUriStr
        playbackMediaId = intent.getStringExtra(EXTRA_MEDIA_ID) ?: "hist_${videoUriStr.hashCode()}"
        playbackTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "Video"
        playbackIsStream = intent.getBooleanExtra(EXTRA_IS_STREAM, false)
        playbackIsDemo = intent.getBooleanExtra(EXTRA_IS_DEMO, false)
        fallbackDurationMs = intent.getLongExtra(EXTRA_DURATION_MS, 0L)
        val resumeMs = if (intent.hasExtra(EXTRA_RESUME_MS)) {
            intent.getLongExtra(EXTRA_RESUME_MS, 0L).coerceAtLeast(0L)
        } else {
            prefs.getResumePositionMs(videoUriStr)
        }
        intendedResumeMs = resumeMs

        val videoUri = Uri.parse(videoUriStr)
        val subUriStr = intent.getStringExtra(EXTRA_SUBTITLE_URI)

        val player = ExoPlayer.Builder(this).build().apply {
            repeatMode = if (playbackIsDemo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            playbackReachedReady = true
                            persistPlaybackProgress(force = true)
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            renderer.videoAspectRatio =
                                (videoSize.width.toFloat() / videoSize.height.toFloat()).coerceIn(1.1f, 2.4f)
                        }
                    }

                    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                        Toast.makeText(
                            this@VrPlayerActivity,
                            "Playback error: ${error.localizedMessage ?: "Unsupported stream or codec"}",
                            Toast.LENGTH_LONG,
                        ).show()
                    }
                },
            )
            setMediaItem(MediaItem.fromUri(videoUri))
            if (resumeMs > 2_000L) {
                seekTo(resumeMs)
                Toast.makeText(
                    this@VrPlayerActivity,
                    "Resuming at ${MediaRepository.formatDuration(resumeMs)}",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            prepare()
            playWhenReady = true
        }
        exoPlayer = player

        renderer = StereoGlRenderer(
            headTracker = headTracker,
            dockRenderer = dockRenderer,
            onSurfaceTextureCreated = { surfaceTex ->
                mainHandler.post {
                    decoderSurface?.release()
                    val surf = Surface(surfaceTex)
                    decoderSurface = surf
                    exoPlayer?.setVideoSurface(surf)
                }
            },
            onDockActionTriggered = { action, scrubFrac ->
                mainHandler.post { handleDockAction(action, scrubFrac) }
            },
        ).also {
            it.config = initialCfg
            it.volumeFraction = readVolumeFraction()
        }

        if (!subUriStr.isNullOrBlank()) {
            lifecycleScope.launch {
                val (_, rawText) = MediaRepository.readTextFromUri(this@VrPlayerActivity, Uri.parse(subUriStr))
                val parsed = rawText?.let { SrtVttParser.parse(it) }.orEmpty()
                subtitleCues = parsed
                renderer.subtitlesLoaded = parsed.isNotEmpty()
                renderer.subtitlesEnabled = parsed.isNotEmpty()
            }
        }

        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            setOnTouchListener { _, ev ->
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = ev.x
                        lastTouchY = ev.y
                        touchMovedDistance = 0f
                        touchDownElapsedMs = SystemClock.elapsedRealtime()
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val dx = ev.x - lastTouchX
                        val dy = ev.y - lastTouchY
                        lastTouchX = ev.x
                        lastTouchY = ev.y
                        touchMovedDistance += abs(dx) + abs(dy)
                        if (touchMovedDistance > 16f) {
                            headTracker.addTouchDrag(
                                deltaXRad = -dx * 0.0028f,
                                deltaYRad = -dy * 0.0028f,
                            )
                        }
                    }

                    MotionEvent.ACTION_UP -> {
                        if (touchMovedDistance <= 18f) {
                            val heldMs = SystemClock.elapsedRealtime() - touchDownElapsedMs
                            if (dockRenderer.dockLocked && heldMs >= 650L) {
                                dockRenderer.dockLocked = false
                                dockRenderer.dockHidden = false
                            } else if (!renderer.triggerCurrentGazeOrToggleDock() && !dockRenderer.dockLocked) {
                                dockRenderer.dockHidden = !dockRenderer.dockHidden
                            }
                        }
                    }
                }
                true
            }
        }

        setContentView(glView)
    }

    private fun handleDockAction(action: DockAction, scrubFrac: Float) {
        val player = exoPlayer
        val cfg = renderer.config
        when (action) {
            DockAction.NONE -> Unit
            DockAction.TOGGLE_PLAY -> if (player != null) {
                if (player.isPlaying) {
                    player.pause()
                } else {
                    if (player.playbackState == Player.STATE_ENDED) {
                        player.seekTo(0L)
                    }
                    player.play()
                }
            }
            DockAction.SEEK_BACK -> if (player != null) {
                player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
            }
            DockAction.SEEK_FWD -> if (player != null) {
                val target = player.currentPosition + 10_000L
                val max = player.duration.takeIf { it > 0L } ?: target
                player.seekTo(target.coerceAtMost(max))
            }
            DockAction.SCRUB -> if (player != null && player.duration > 0L) {
                player.seekTo((player.duration * scrubFrac).toLong())
            }
            DockAction.TOGGLE_MUTE -> if (player != null) {
                player.volume = if (player.volume <= 0.01f) 1f else 0f
            }
            DockAction.VOL_DOWN -> adjustMediaVolume(-1)
            DockAction.VOL_UP -> {
                if (player != null && player.volume <= 0.01f) {
                    player.volume = 1f
                }
                adjustMediaVolume(1)
            }
            DockAction.TOGGLE_LOCK -> {
                val nextLocked = !dockRenderer.dockLocked
                dockRenderer.dockLocked = nextLocked
                dockRenderer.showSettings = false
                dockRenderer.dockHidden = nextLocked
            }
            DockAction.TOGGLE_CC -> if (renderer.subtitlesLoaded) {
                renderer.subtitlesEnabled = !renderer.subtitlesEnabled
            }
            DockAction.CYCLE_ROOM -> {
                val updated = cfg.copy(environment = cfg.environment.next())
                renderer.config = updated
                prefs.saveConfig(updated)
            }
            DockAction.RECENTER -> headTracker.recenter(calibrateBedPose = false)
            DockAction.TOGGLE_SETTINGS -> dockRenderer.showSettings = !dockRenderer.showSettings
            DockAction.CYCLE_BED_TILT -> {
                val cur = headTracker.getEffectiveBedTiltDeg()
                val nextTilt = BED_TILT_PRESETS.firstOrNull { it > cur + 2f } ?: BED_TILT_PRESETS.first()
                headTracker.setBedTiltDeg(nextTilt)
                val updated = cfg.copy(bedTiltDeg = nextTilt)
                renderer.config = updated
                prefs.saveConfig(updated)
            }
            DockAction.IPD_MINUS -> {
                val updated = cfg.copy(ipdMm = (cfg.ipdMm - 1f).coerceIn(54f, 72f))
                renderer.config = updated
                prefs.saveConfig(updated)
            }
            DockAction.IPD_PLUS -> {
                val updated = cfg.copy(ipdMm = (cfg.ipdMm + 1f).coerceIn(54f, 72f))
                renderer.config = updated
                prefs.saveConfig(updated)
            }
            DockAction.FOV_MINUS -> {
                val updated = cfg.copy(fovDeg = (cfg.fovDeg - 2f).coerceIn(65f, 95f))
                renderer.config = updated
                prefs.saveConfig(updated)
            }
            DockAction.FOV_PLUS -> {
                val updated = cfg.copy(fovDeg = (cfg.fovDeg + 2f).coerceIn(65f, 95f))
                renderer.config = updated
                prefs.saveConfig(updated)
            }
            DockAction.CYCLE_FORMAT -> {
                val updated = cfg.copy(videoFormat = cfg.videoFormat.next())
                renderer.config = updated
                prefs.saveConfig(updated)
            }
            DockAction.TOGGLE_SWAP -> {
                val updated = cfg.copy(eyeSwap = !cfg.eyeSwap)
                renderer.config = updated
                prefs.saveConfig(updated)
            }
            DockAction.TOGGLE_OPTICS -> {
                val updated = cfg.copy(barrelEnabled = !cfg.barrelEnabled)
                renderer.config = updated
                prefs.saveConfig(updated)
            }
            DockAction.TOGGLE_THERMAL -> {
                val updated = cfg.copy(thermalCapEnabled = !cfg.thermalCapEnabled)
                renderer.config = updated
                prefs.saveConfig(updated)
                applyThermalBrightnessCap(updated.thermalCapEnabled)
            }
            DockAction.EXIT_VR -> finish()
        }
    }

    override fun finish() {
        persistPlaybackProgress(force = true)
        super.finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(R.anim.vr_enter_fade, R.anim.vr_exit_fade)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_SPACE -> {
                if (!renderer.triggerCurrentGazeOrToggleDock()) {
                    handleDockAction(DockAction.TOGGLE_PLAY, 0f)
                }
                true
            }

            KeyEvent.KEYCODE_BUTTON_B -> {
                finish()
                true
            }

            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                handleDockAction(DockAction.SEEK_BACK, 0f)
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                handleDockAction(DockAction.SEEK_FWD, 0f)
                true
            }

            KeyEvent.KEYCODE_BUTTON_X -> {
                headTracker.recenter(calibrateBedPose = true)
                true
            }

            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        headTracker.displayRotation = currentDisplayRotation()
        glView.onResume()
        headTracker.start()
        mainHandler.post(telemetryTicker)
        volumeControlStream = AudioManager.STREAM_MUSIC
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        headTracker.displayRotation = currentDisplayRotation()
    }

    private fun currentDisplayRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_90
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }

    override fun onPause() {
        super.onPause()
        persistPlaybackProgress(force = true)
        mainHandler.removeCallbacks(telemetryTicker)
        headTracker.stop()
        glView.onPause()
        exoPlayer?.pause()
    }

    override fun onDestroy() {
        persistPlaybackProgress(force = true)
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        exoPlayer?.setVideoSurface(null)
        exoPlayer?.release()
        exoPlayer = null
        decoderSurface?.release()
        decoderSurface = null
        renderer.releaseSurfaceTexture()
    }

    private fun applyThermalBrightnessCap(capEnabled: Boolean) {
        val attrs = window.attributes
        attrs.screenBrightness = if (capEnabled) 0.60f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = attrs
    }

    private fun readVolumeFraction(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, max)
        return cur.toFloat() / max.toFloat()
    }

    private fun adjustMediaVolume(direction: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).coerceIn(0, max)
        val step = kotlin.math.max(1, kotlin.math.ceil(max / 12.0).toInt())
        val target = (cur + direction * step).coerceIn(0, max)
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
        renderer.volumeFraction = readVolumeFraction()
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    companion object {
        private val BED_TILT_PRESETS = floatArrayOf(30f, 46f, 60f, 75f, 90f)
        const val EXTRA_VIDEO_URI = "extra_video_uri"
        const val EXTRA_VIDEO_TITLE = "extra_video_title"
        const val EXTRA_SUBTITLE_URI = "extra_subtitle_uri"
        const val EXTRA_SUBTITLE_NAME = "extra_subtitle_name"
        const val EXTRA_MEDIA_ID = "extra_media_id"
        const val EXTRA_DURATION_MS = "extra_duration_ms"
        const val EXTRA_IS_STREAM = "extra_is_stream"
        const val EXTRA_IS_DEMO = "extra_is_demo"
        const val EXTRA_RESUME_MS = "extra_resume_ms"
    }
}
