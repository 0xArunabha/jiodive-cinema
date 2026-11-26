package com.jiodive.cinema.vr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.jiodive.cinema.model.CinemaConfig
import com.jiodive.cinema.model.ScreenPreset
import com.jiodive.cinema.model.TheaterEnvironment
import com.jiodive.cinema.model.VideoFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * OpenGL ES 3.0 stereoscopic cinema renderer supporting:
 * - Curved IMAX / Standard / Balcony cinema mesh backed by ExoPlayer SurfaceTexture
 * - Contrast-Adaptive Sharpening (CAS) fragment shader + 2D/Half-SBS/Full-SBS/Half-OU/Full-OU UV slicing
 * - Procedural Cinema Hall (floor aisle, stepped seats, glowing screen light), OLED Void, & Bed modes
 * - Floating VR Control Dock + Crisp Subtitle Quad + Gaze Dwell Reticle
 * - Per-eye FBO Brown-Conrady barrel distortion post-pass for JioDive optical lenses
 */
class StereoGlRenderer(
    private val headTracker: HeadTracker,
    private val dockRenderer: VrDockRenderer,
    private val onSurfaceTextureCreated: (SurfaceTexture) -> Unit,
    private val onDockActionTriggered: (DockAction, Float) -> Unit,
) : GLSurfaceView.Renderer {

    private var configInitialized: Boolean = false

    @Volatile
    var config: CinemaConfig = CinemaConfig()
        set(value) {
            val envChanged = !configInitialized || field.environment != value.environment
            val tiltChanged = !configInitialized || kotlin.math.abs(field.bedTiltDeg - value.bedTiltDeg) > 0.1f
            if (field.screenPreset != value.screenPreset) {
                screenMeshDirty = true
                roomMeshDirty = true
            }
            if (field.thermalCapEnabled != value.thermalCapEnabled) {
                fboDirty = true
            }
            if (configInitialized && field.environment != value.environment) {
                sceneFade = 0.25f
            }
            field = value
            configInitialized = true
            if (envChanged) {
                headTracker.setBedMode(value.environment == TheaterEnvironment.BED, value.bedTiltDeg)
            } else if (tiltChanged && value.environment == TheaterEnvironment.BED) {
                headTracker.setBedTiltDeg(value.bedTiltDeg)
            }
        }

    @Volatile
    var isPlaying: Boolean = false
    @Volatile
    var isMuted: Boolean = false
    @Volatile
    var volumeFraction: Float = 0.7f
    @Volatile
    var subtitlesLoaded: Boolean = false
    @Volatile
    var subtitlesEnabled: Boolean = true
    @Volatile
    var positionMs: Long = 0L
    @Volatile
    var durationMs: Long = 0L
    @Volatile
    var activeSubtitleText: String? = null
        set(value) {
            if (field != value) {
                field = value
                subtitleBitmapDirty = true
            }
        }

    @Volatile
    var videoAspectRatio: Float = 16f / 9f
        set(value) {
            if (kotlin.math.abs(field - value) > 0.02f) {
                field = value
                screenMeshDirty = true
                roomMeshDirty = true
            }
        }

    private var surfaceTexture: SurfaceTexture? = null
    private var videoTexId: Int = 0
    @Volatile
    private var frameAvailable: Boolean = false
    private var hasVideoFrame: Boolean = false

    private var dockTexId: Int = 0
    private var subtitleTexId: Int = 0
    private val subtitleBitmap: Bitmap = Bitmap.createBitmap(1280, 200, Bitmap.Config.ARGB_8888)
    private val subtitleCanvas = Canvas(subtitleBitmap)
    @Volatile
    private var subtitleBitmapDirty = true
    private var lastSubtitleTextSizePx = 0f

    @Volatile
    private var screenMeshDirty = true
    @Volatile
    private var roomMeshDirty = true
    @Volatile
    private var fboDirty = true
    private var viewportW = 1920
    private var viewportH = 1080

    // Offscreen FBO for optional per-eye barrel distortion pass
    private var fboId = 0
    private var fboColorTexId = 0
    private var fboDepthRboId = 0
    private var fboW = 0
    private var fboH = 0

    // 4x4 low-cost offscreen FBO capturing 16-zone movie bounce colors once per frame
    private var bounceFboId = 0
    private var bounceTexId = 0

    // Programs
    private var videoProg = 0
    private var colorProg = 0
    private var bounceProg = 0
    private var beamProg = 0
    private var texQuadProg = 0
    private var reticleProg = 0
    private var barrelProg = 0

    // Cached uniform locations (eliminates per-frame driver string lookups)
    private var uVideoMvpLoc = -1
    private var uVideoTexMatrixLoc = -1
    private var uVideoUvRectLoc = -1
    private var uVideoSharpenLoc = -1
    private var uVideoFadeLoc = -1
    private var uVideoTexelLoc = -1
    private var uVideoTexLoc = -1

    private var uColorMvpLoc = -1
    private var uColorScreenPosLoc = -1
    private var uColorBounceTexLoc = -1
    private var uColorFadeLoc = -1

    private var uBounceTexMatrixLoc = -1
    private var uBounceUvRectLoc = -1
    private var uBounceTexLoc = -1

    private var uBeamMvpLoc = -1
    private var uBeamTexLoc = -1
    private var uBeamFadeLoc = -1

    private var uQuadMvpLoc = -1
    private var uQuadAlphaLoc = -1
    private var uQuadTexLoc = -1

    private var uReticleDwellLoc = -1
    private var uReticleAspectLoc = -1

    private var uBarrelK1Loc = -1
    private var uBarrelK2Loc = -1
    private var uBarrelTexLoc = -1

    // Buffers
    private var screenVbo = 0
    private var screenIbo = 0
    private var screenIndexCount = 0

    private var roomVbo = 0
    private var roomVertexCount = 0

    private var beamVbo = 0
    private var beamVertexCount = 0

    private var quadVbo = 0
    private var fullScreenQuadVbo = 0

    // Matrices
    private val viewRotMat = FloatArray(16)
    private val viewMat = FloatArray(16)
    private val projMat = FloatArray(16)
    private val modelMat = FloatArray(16)
    private val mvpMat = FloatArray(16)
    private val tmpMat = FloatArray(16)
    private val texTransformMat = FloatArray(16)
    private val lookForward = FloatArray(3)

    // Gaze dwell tracking & dock bitmap cache signature
    private var currentHoveredAction: DockAction? = null
    private var currentScrubNorm: Float = 0f
    private var hoverStartTimeNs: Long = 0L
    private var dwellConsumed: Boolean = false
    private var lastDockRedrawMs: Long = 0L
    private var dockRedrawForceDirty: Boolean = true
    private var lastDockSignature: String = ""
    private var lastDockLockedState: Boolean = false

    private val dockCenterY = -1.20f
    private val bedDockCenterY = -0.92f
    private var smoothDockCenterY = dockCenterY
    private val dockCenterZ = -2.35f
    private val dockHalfW = 1.06f
    private val dockHalfH = 0.33f
    private val dockTiltRad = Math.toRadians(18.0).toFloat()

    // Smooth per-frame animation timers
    private var lastFrameNs: Long = 0L
    @Volatile
    private var sceneFade: Float = 0f
    private var dockVisibility: Float = 1f
    private var smoothScreenDist: Float = 6.8f
    private var smoothScreenCenterY: Float = 0.55f
    private var screenPresetInitialized: Boolean = false

    fun releaseSurfaceTexture() {
        runCatching { surfaceTexture?.setOnFrameAvailableListener(null) }
        runCatching { surfaceTexture?.release() }
        surfaceTexture = null
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Clear any stale object IDs if the EGL context was lost & recreated after backgrounding
        screenVbo = 0
        screenIbo = 0
        roomVbo = 0
        beamVbo = 0
        quadVbo = 0
        fullScreenQuadVbo = 0
        fboId = 0
        fboColorTexId = 0
        fboDepthRboId = 0
        fboW = 0
        fboH = 0
        bounceFboId = 0
        bounceTexId = 0
        hasVideoFrame = false
        frameAvailable = false
        subtitleBitmapDirty = true
        lastSubtitleTextSizePx = 0f
        releaseSurfaceTexture()

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)

        videoTexId = createExternalOesTexture()
        surfaceTexture = SurfaceTexture(videoTexId).apply {
            setOnFrameAvailableListener { frameAvailable = true }
        }
        Matrix.setIdentityM(texTransformMat, 0)
        onSurfaceTextureCreated(surfaceTexture!!)

        dockTexId = createRgbaTexture()
        subtitleTexId = createRgbaTexture()

        videoProg = compileProgram(VERTEX_SHADER_3D_TEX, FRAGMENT_SHADER_OES_SHARPEN)
        colorProg = compileProgram(VERTEX_SHADER_COLOR, FRAGMENT_SHADER_COLOR)
        bounceProg = compileProgram(VERTEX_SHADER_2D_POS, FRAGMENT_SHADER_BOUNCE)
        beamProg = compileProgram(VERTEX_SHADER_BEAM, FRAGMENT_SHADER_BEAM)
        texQuadProg = compileProgram(VERTEX_SHADER_3D_TEX, FRAGMENT_SHADER_2D_TEX)
        reticleProg = compileProgram(VERTEX_SHADER_2D_POS, FRAGMENT_SHADER_RETICLE)
        barrelProg = compileProgram(VERTEX_SHADER_2D_TEX, FRAGMENT_SHADER_BARREL)

        cacheUniformLocations()
        buildUnitQuadVbo()
        setupBounceFbo()
        buildCinemaRoomGeometry()
        rebuildScreenGeometry(this.config.screenPreset)
        dockRedrawForceDirty = true
    }

    private fun cacheUniformLocations() {
        uVideoMvpLoc = GLES30.glGetUniformLocation(videoProg, "uMvp")
        uVideoTexMatrixLoc = GLES30.glGetUniformLocation(videoProg, "uTexMatrix")
        uVideoUvRectLoc = GLES30.glGetUniformLocation(videoProg, "uUvRect")
        uVideoSharpenLoc = GLES30.glGetUniformLocation(videoProg, "uSharpen")
        uVideoFadeLoc = GLES30.glGetUniformLocation(videoProg, "uFade")
        uVideoTexelLoc = GLES30.glGetUniformLocation(videoProg, "uTexel")
        uVideoTexLoc = GLES30.glGetUniformLocation(videoProg, "uTex")

        uColorMvpLoc = GLES30.glGetUniformLocation(colorProg, "uMvp")
        uColorScreenPosLoc = GLES30.glGetUniformLocation(colorProg, "uScreenPos")
        uColorBounceTexLoc = GLES30.glGetUniformLocation(colorProg, "uBounceTex")
        uColorFadeLoc = GLES30.glGetUniformLocation(colorProg, "uFade")

        uBounceTexMatrixLoc = GLES30.glGetUniformLocation(bounceProg, "uTexMatrix")
        uBounceUvRectLoc = GLES30.glGetUniformLocation(bounceProg, "uUvRect")
        uBounceTexLoc = GLES30.glGetUniformLocation(bounceProg, "uTex")

        uBeamMvpLoc = GLES30.glGetUniformLocation(beamProg, "uMvp")
        uBeamTexLoc = GLES30.glGetUniformLocation(beamProg, "uBounceTex")
        uBeamFadeLoc = GLES30.glGetUniformLocation(beamProg, "uFade")

        uQuadMvpLoc = GLES30.glGetUniformLocation(texQuadProg, "uMvp")
        uQuadAlphaLoc = GLES30.glGetUniformLocation(texQuadProg, "uAlpha")
        uQuadTexLoc = GLES30.glGetUniformLocation(texQuadProg, "uTex")

        uReticleDwellLoc = GLES30.glGetUniformLocation(reticleProg, "uDwell")
        uReticleAspectLoc = GLES30.glGetUniformLocation(reticleProg, "uAspect")

        uBarrelK1Loc = GLES30.glGetUniformLocation(barrelProg, "uK1")
        uBarrelK2Loc = GLES30.glGetUniformLocation(barrelProg, "uK2")
        uBarrelTexLoc = GLES30.glGetUniformLocation(barrelProg, "uTex")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportW = width.coerceAtLeast(2)
        viewportH = height.coerceAtLeast(2)
        fboDirty = true
    }

    override fun onDrawFrame(gl: GL10?) {
        if (frameAvailable) {
            frameAvailable = false
            runCatching {
                surfaceTexture?.updateTexImage()
                surfaceTexture?.getTransformMatrix(texTransformMat)
                hasVideoFrame = true
            }
        }
        if (screenMeshDirty) {
            screenMeshDirty = false
            rebuildScreenGeometry(config.screenPreset)
        }
        if (roomMeshDirty) {
            roomMeshDirty = false
            buildCinemaRoomGeometry()
        }
        if (fboDirty) {
            fboDirty = false
            setupEyeFbo(viewportW / 2, viewportH)
        }

        val nowNs = System.nanoTime()
        val dtSec = if (lastFrameNs == 0L) 0.016f else ((nowNs - lastFrameNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        lastFrameNs = nowNs

        sceneFade = (sceneFade + dtSec * 2.2f).coerceAtMost(1f)

        val targetDist = config.screenPreset.distanceMeters
        val targetCenterY = if (config.environment == TheaterEnvironment.BED) {
            0f
        } else {
            CinemaHallBuilder.screenCenterYFor(config.screenPreset, videoAspectRatio)
        }
        val targetDockY = if (config.environment == TheaterEnvironment.BED) bedDockCenterY else dockCenterY
        if (!screenPresetInitialized) {
            smoothScreenDist = targetDist
            smoothScreenCenterY = targetCenterY
            smoothDockCenterY = targetDockY
            screenPresetInitialized = true
        } else {
            val presetLerp = 1f - kotlin.math.exp((-10f * dtSec).toDouble()).toFloat()
            smoothScreenDist += (targetDist - smoothScreenDist) * presetLerp
            smoothScreenCenterY += (targetCenterY - smoothScreenCenterY) * presetLerp
            smoothDockCenterY += (targetDockY - smoothDockCenterY) * presetLerp
        }

        headTracker.getViewRotationAndForward(viewRotMat, lookForward, dtSec)
        val gazeHit = updateGazeAndDockBitmap(nowNs)

        val isLockedHover = dockRenderer.dockLocked && currentHoveredAction == DockAction.TOGGLE_LOCK
        val targetDockVis = when {
            dockRenderer.dockLocked -> if (isLockedHover) 1f else 0f
            dockRenderer.dockHidden -> 0f
            else -> 1f
        }
        val dockLerp = 1f - kotlin.math.exp((-14f * dtSec).toDouble()).toFloat()
        dockVisibility += (targetDockVis - dockVisibility) * dockLerp

        if (config.environment == TheaterEnvironment.CINEMA && bounceFboId != 0 && hasVideoFrame) {
            updateBounceTexture()
        }

        val eyeW = viewportW / 2
        val aspect = eyeW.toFloat() / viewportH.toFloat().coerceAtLeast(1f)
        val halfFovRad = Math.toRadians((config.fovDeg * 0.5).toDouble()).toFloat()
        val near = 0.1f
        val far = 80f
        val top = near * tan(halfFovRad.toDouble()).toFloat()
        val right = top * aspect
        Matrix.frustumM(projMat, 0, -right, right, -top, top, near, far)

        // Stereo IPD separation in meters (half per eye)
        val halfIpdM = (config.ipdMm * 0.001f) * 0.5f

        GLES30.glViewport(0, 0, viewportW, viewportH)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        for (eyeIdx in 0..1) {
            val isRightEye = eyeIdx == 1
            val eyeShiftX = if (isRightEye) halfIpdM else -halfIpdM
            val stereoContentRight = if (config.eyeSwap) !isRightEye else isRightEye

            if (config.barrelEnabled && fboId != 0) {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
                GLES30.glViewport(0, 0, fboW, fboH)
            } else {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                GLES30.glViewport(if (isRightEye) eyeW else 0, 0, eyeW, viewportH)
            }

            renderSceneForEye(
                eyeShiftX = eyeShiftX,
                useRightStereoEye = stereoContentRight,
                gazeDwellFraction = gazeHit,
            )

            if (config.barrelEnabled && fboId != 0) {
                GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
                GLES30.glViewport(if (isRightEye) eyeW else 0, 0, eyeW, viewportH)
                drawBarrelQuad()
            }
        }
    }

    /**
     * Triggers whichever VR Dock button the user is currently gazing at (called on screen tap or gamepad A).
     */
    fun triggerCurrentGazeOrToggleDock(): Boolean {
        val action = currentHoveredAction
        if (action != null && action != DockAction.NONE) {
            onDockActionTriggered(action, currentScrubNorm)
            return true
        }
        return false
    }

    private fun updateGazeAndDockBitmap(nowNs: Long): Float {
        if (dockRenderer.dockLocked != lastDockLockedState) {
            lastDockLockedState = dockRenderer.dockLocked
            currentHoveredAction = null
            hoverStartTimeNs = nowNs
            dwellConsumed = false
            dockRedrawForceDirty = true
        }

        // Intersect ray (origin 0, dir = lookForward) with tilted Dock quad centered at (0, dockCenterY, dockCenterZ)
        val nY = sin(dockTiltRad.toDouble()).toFloat()
        val nZ = cos(dockTiltRad.toDouble()).toFloat()
        val denom = lookForward[1] * nY + lookForward[2] * nZ
        var hovered: DockAction? = null
        var scrubFrac = 0f

        if (denom < -0.05f && (!dockRenderer.dockHidden || dockRenderer.dockLocked)) {
            val t = (smoothDockCenterY * nY + dockCenterZ * nZ) / denom
            if (t in 0.4f..8.0f) {
                val hitX = lookForward[0] * t
                val hitY = lookForward[1] * t - smoothDockCenterY
                val hitZ = lookForward[2] * t - dockCenterZ

                // Project onto local quad up axis (cos tilt, -sin tilt)
                val localY = hitY * nZ - hitZ * nY
                val u = (hitX / (dockHalfW * 2f)) + 0.5f
                val v = 0.5f - (localY / (dockHalfH * 2f))
                val res = dockRenderer.hitTest(u, v)
                if (res != null) {
                    hovered = res.first
                    scrubFrac = res.second
                }
            }
        }

        if (hovered != currentHoveredAction) {
            currentHoveredAction = hovered
            hoverStartTimeNs = nowNs
            dwellConsumed = false
        }
        currentScrubNorm = scrubFrac

        var dwellProgress = 0f
        if (hovered != null && hovered != DockAction.NONE && !dwellConsumed) {
            val elapsedMs = (nowNs - hoverStartTimeNs) / 1_000_000f
            val limit = if (dockRenderer.dockLocked) {
                2_000f
            } else {
                config.gazeDwellMs.toFloat().coerceAtLeast(500f)
            }
            dwellProgress = (elapsedMs / limit).coerceIn(0f, 1f)
            if (dwellProgress >= 1f) {
                dwellConsumed = true
                onDockActionTriggered(hovered, currentScrubNorm)
            }
        }

        val nowMs = nowNs / 1_000_000L
        val volStep = (volumeFraction * 20f).toInt()
        val effectiveBedTilt = headTracker.getEffectiveBedTiltDeg()
        val sig = "${dockRenderer.dockLocked}|${dockRenderer.showSettings}|$isPlaying|$isMuted|$volStep|$subtitlesLoaded|$subtitlesEnabled|${positionMs / 1000L}|${durationMs / 1000L}|${config.environment.ordinal}|${effectiveBedTilt.toInt()}|${config.videoFormat.ordinal}|${config.ipdMm.toInt()}|${config.fovDeg.toInt()}|${config.eyeSwap}|${config.barrelEnabled}|${config.thermalCapEnabled}|${hovered?.ordinal ?: -1}|${(dwellProgress * 28f).toInt()}|${(scrubFrac * 100f).toInt()}"
        val redrawIntervalMs = if (hovered != null || dwellProgress > 0.01f) 33L else 80L
        if (dockRedrawForceDirty || (sig != lastDockSignature && nowMs - lastDockRedrawMs >= redrawIntervalMs)) {
            dockRedrawForceDirty = false
            lastDockSignature = sig
            lastDockRedrawMs = nowMs
            dockRenderer.redraw(
                isPlaying = isPlaying,
                isMuted = isMuted,
                volumeFraction = volumeFraction,
                subtitlesLoaded = subtitlesLoaded,
                subtitlesEnabled = subtitlesEnabled,
                positionMs = positionMs,
                durationMs = durationMs,
                config = config,
                hoveredAction = hovered,
                hoverScrubNorm = scrubFrac,
                dwellProgress = dwellProgress,
                effectiveBedTiltDeg = effectiveBedTilt,
            )
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dockTexId)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, dockRenderer.bitmap, 0)
        }

        if (subtitleBitmapDirty || config.subtitleTextSize.vrPx != lastSubtitleTextSizePx) {
            subtitleBitmapDirty = false
            redrawSubtitleBitmap(activeSubtitleText)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, subtitleTexId)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, subtitleBitmap, 0)
        }

        return dwellProgress
    }

    private fun renderSceneForEye(
        eyeShiftX: Float,
        useRightStereoEye: Boolean,
        gazeDwellFraction: Float,
    ) {
        GLES30.glClearColor(
            if (config.environment == TheaterEnvironment.CINEMA) 0.024f else 0f,
            if (config.environment == TheaterEnvironment.CINEMA) 0.032f else 0f,
            if (config.environment == TheaterEnvironment.CINEMA) 0.038f else 0f,
            1f,
        )
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)

        // view = Translate(-eyeShiftX, 0, 0) * viewRotMat
        System.arraycopy(viewRotMat, 0, viewMat, 0, 16)
        viewMat[12] -= eyeShiftX

        val (uOff, uScale, vOff, vScale) = stereoUvRect(config.videoFormat, useRightStereoEye)
        val easeFade = sceneFade * sceneFade * (3f - 2f * sceneFade)

        // 1. Cinema Auditorium Room (Lit + 4x4 Hardware-Filtered Video Bounce)
        if (config.environment == TheaterEnvironment.CINEMA && roomVertexCount > 0) {
            Matrix.setIdentityM(modelMat, 0)
            Matrix.multiplyMM(tmpMat, 0, viewMat, 0, modelMat, 0)
            Matrix.multiplyMM(mvpMat, 0, projMat, 0, tmpMat, 0)

            GLES30.glUseProgram(colorProg)
            GLES30.glUniformMatrix4fv(uColorMvpLoc, 1, false, mvpMat, 0)
            GLES30.glUniform3f(
                uColorScreenPosLoc,
                0f,
                smoothScreenCenterY,
                -smoothScreenDist,
            )
            GLES30.glUniform1f(uColorFadeLoc, easeFade)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bounceTexId)
            GLES30.glUniform1i(uColorBounceTexLoc, 0)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, roomVbo)
            val stride = CinemaHallBuilder.FLOATS_PER_VERTEX * 4
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, stride, 3 * 4)
            GLES30.glEnableVertexAttribArray(2)
            GLES30.glVertexAttribPointer(2, 3, GLES30.GL_FLOAT, false, stride, 6 * 4)
            GLES30.glEnableVertexAttribArray(3)
            GLES30.glVertexAttribPointer(3, 1, GLES30.GL_FLOAT, false, stride, 9 * 4)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, roomVertexCount)
            GLES30.glDisableVertexAttribArray(2)
            GLES30.glDisableVertexAttribArray(3)
        }

        // 2. Curved Video Screen
        Matrix.setIdentityM(modelMat, 0)
        Matrix.translateM(modelMat, 0, 0f, smoothScreenCenterY, -smoothScreenDist)
        Matrix.multiplyMM(tmpMat, 0, viewMat, 0, modelMat, 0)
        Matrix.multiplyMM(mvpMat, 0, projMat, 0, tmpMat, 0)

        GLES30.glUseProgram(videoProg)
        GLES30.glUniformMatrix4fv(uVideoMvpLoc, 1, false, mvpMat, 0)
        GLES30.glUniformMatrix4fv(uVideoTexMatrixLoc, 1, false, texTransformMat, 0)
        GLES30.glUniform4f(uVideoUvRectLoc, uOff, uScale, vOff, vScale)
        GLES30.glUniform1f(uVideoSharpenLoc, config.sharpenStrength)
        GLES30.glUniform1f(uVideoFadeLoc, easeFade)
        GLES30.glUniform2f(uVideoTexelLoc, 1f / 1920f, 1f / 1080f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTexId)
        GLES30.glUniform1i(uVideoTexLoc, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, screenVbo)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, screenIbo)
        val texStride = 5 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, texStride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, texStride, 3 * 4)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, screenIndexCount, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)

        // 2b. Subtle volumetric overhead projector cone in Cinema Hall
        if (config.environment == TheaterEnvironment.CINEMA && beamVertexCount > 0 && hasVideoFrame) {
            Matrix.setIdentityM(modelMat, 0)
            Matrix.multiplyMM(tmpMat, 0, viewMat, 0, modelMat, 0)
            Matrix.multiplyMM(mvpMat, 0, projMat, 0, tmpMat, 0)

            GLES30.glDepthMask(false)
            GLES30.glUseProgram(beamProg)
            GLES30.glUniformMatrix4fv(uBeamMvpLoc, 1, false, mvpMat, 0)
            GLES30.glUniform1f(uBeamFadeLoc, easeFade)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bounceTexId)
            GLES30.glUniform1i(uBeamTexLoc, 0)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, beamVbo)
            val beamStride = 4 * 4
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, beamStride, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 1, GLES30.GL_FLOAT, false, beamStride, 3 * 4)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, beamVertexCount)
            GLES30.glDepthMask(true)
        }

        // 3. HUD Layers: Subtitles + Animated Floating Dock
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)

        if (subtitlesEnabled && !activeSubtitleText.isNullOrBlank()) {
            val subDist = smoothScreenDist * 0.72f
            val subY = if (config.environment == TheaterEnvironment.BED) {
                -1.00f
            } else {
                (smoothScreenCenterY - 0.55f) * 0.25f - 1.05f
            }
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, 0f, subY, -subDist)
            Matrix.scaleM(modelMat, 0, subDist * 0.36f, subDist * 0.06f, 1f)
            drawTexturedQuad(subtitleTexId, alpha = easeFade)
        }

        if (dockVisibility > 0.01f) {
            val e = dockVisibility * dockVisibility * (3f - 2f * dockVisibility)
            val slideY = (1f - e) * -0.14f
            val scaleDock = 0.94f + 0.06f * e
            val dynamicTiltDeg = 32f - 14f * e
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, 0f, smoothDockCenterY + slideY, dockCenterZ)
            Matrix.rotateM(modelMat, 0, -dynamicTiltDeg, 1f, 0f, 0f)
            Matrix.scaleM(modelMat, 0, dockHalfW * scaleDock, dockHalfH * scaleDock, 1f)
            drawTexturedQuad(dockTexId, alpha = e * easeFade)
        }

        // 4. Gaze Reticle (drawn centered in eye view space when dock visible or hovering)
        if (dockVisibility > 0.05f || gazeDwellFraction > 0.01f) {
            GLES30.glUseProgram(reticleProg)
            GLES30.glDisableVertexAttribArray(1)
            GLES30.glUniform1f(uReticleDwellLoc, gazeDwellFraction)
            val eyeAspect = (viewportW * 0.5f) / viewportH.toFloat().coerceAtLeast(1f)
            GLES30.glUniform1f(uReticleAspectLoc, eyeAspect)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, fullScreenQuadVbo)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }
    }

    private fun drawTexturedQuad(texId: Int, alpha: Float = 1f) {
        Matrix.multiplyMM(tmpMat, 0, viewMat, 0, modelMat, 0)
        Matrix.multiplyMM(mvpMat, 0, projMat, 0, tmpMat, 0)
        GLES30.glUseProgram(texQuadProg)
        GLES30.glUniformMatrix4fv(uQuadMvpLoc, 1, false, mvpMat, 0)
        GLES30.glUniform1f(uQuadAlphaLoc, alpha)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glUniform1i(uQuadTexLoc, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
        val stride = 5 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, stride, 3 * 4)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun drawBarrelQuad() {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glUseProgram(barrelProg)
        GLES30.glUniform1f(uBarrelK1Loc, config.k1)
        GLES30.glUniform1f(uBarrelK2Loc, config.k2)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboColorTexId)
        GLES30.glUniform1i(uBarrelTexLoc, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, fullScreenQuadVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun stereoUvRect(format: VideoFormat, rightEye: Boolean): Quad4 {
        return when (format) {
            VideoFormat.FLAT_2D -> Quad4(0f, 1f, 0f, 1f)
            VideoFormat.HALF_SBS, VideoFormat.FULL_SBS ->
                if (rightEye) Quad4(0.5f, 0.5f, 0f, 1f) else Quad4(0f, 0.5f, 0f, 1f)
            VideoFormat.HALF_OU, VideoFormat.FULL_OU ->
                if (rightEye) Quad4(0f, 1f, 0.5f, 0.5f) else Quad4(0f, 1f, 0f, 0.5f)
        }
    }

    private fun redrawSubtitleBitmap(text: String?) {
        subtitleBitmap.eraseColor(Color.TRANSPARENT)
        if (text.isNullOrBlank()) return

        val basePxSize = config.subtitleTextSize.vrPx
        lastSubtitleTextSizePx = basePxSize

        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(198, 12, 16, 18)
        }
        val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = basePxSize
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val lines = text.lines().take(2)
        val maxContentWidth = subtitleBitmap.width - 88f
        val widestRaw = lines.maxOf { txtPaint.measureText(it) }.coerceAtLeast(1f)
        val pxSize = if (widestRaw > maxContentWidth) {
            (basePxSize * (maxContentWidth / widestRaw)).coerceAtLeast(24f)
        } else {
            basePxSize
        }
        txtPaint.textSize = pxSize

        val lineHeight = pxSize * 1.28f
        val top = ((subtitleBitmap.height - lines.size * lineHeight) * 0.5f - 10f).coerceAtLeast(8f)
        val bottom = (top + lines.size * lineHeight + 24f).coerceAtMost(subtitleBitmap.height - 8f)
        val maxW = (lines.maxOf { txtPaint.measureText(it) }.coerceAtLeast(180f) + 64f)
            .coerceAtMost(subtitleBitmap.width - 24f)
        val left = (subtitleBitmap.width - maxW) * 0.5f
        subtitleCanvas.drawRoundRect(left, top, left + maxW, bottom, 26f, 26f, pillPaint)

        lines.forEachIndexed { idx, line ->
            val y = top + pxSize * 1.12f + idx * lineHeight
            subtitleCanvas.drawText(line, subtitleBitmap.width * 0.5f, y, txtPaint)
        }
    }

    private fun rebuildScreenGeometry(preset: ScreenPreset) {
        val cols = 32
        val rows = 12
        val arcRad = Math.toRadians(preset.curveDegrees.toDouble()).toFloat()
        val radius = preset.widthMeters / arcRad.coerceAtLeast(0.1f)
        val height = preset.widthMeters / videoAspectRatio.coerceIn(1.2f, 2.4f)

        val vertices = FloatArray((cols + 1) * (rows + 1) * 5)
        var vIdx = 0
        for (r in 0..rows) {
            val v = r.toFloat() / rows.toFloat()
            val y = (0.5f - v) * height
            for (c in 0..cols) {
                val u = c.toFloat() / cols.toFloat()
                val angle = (u - 0.5f) * arcRad
                val x = radius * sin(angle.toDouble()).toFloat()
                val z = radius * (1f - cos(angle.toDouble()).toFloat())
                vertices[vIdx++] = x
                vertices[vIdx++] = y
                vertices[vIdx++] = z
                vertices[vIdx++] = u
                vertices[vIdx++] = v
            }
        }

        val indices = ShortArray(cols * rows * 6)
        var iIdx = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val a = (r * (cols + 1) + c).toShort()
                val b = (a + 1).toShort()
                val d = ((r + 1) * (cols + 1) + c).toShort()
                val e = (d + 1).toShort()
                indices[iIdx++] = a
                indices[iIdx++] = d
                indices[iIdx++] = b
                indices[iIdx++] = b
                indices[iIdx++] = d
                indices[iIdx++] = e
            }
        }
        screenIndexCount = indices.size

        if (screenVbo == 0) {
            val bufs = IntArray(2)
            GLES30.glGenBuffers(2, bufs, 0)
            screenVbo = bufs[0]
            screenIbo = bufs[1]
        }

        val vBuf = toFloatBuffer(vertices)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, screenVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vBuf, GLES30.GL_STATIC_DRAW)

        val iBuf = toShortBuffer(indices)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, screenIbo)
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 2, iBuf, GLES30.GL_STATIC_DRAW)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    private fun buildUnitQuadVbo() {
        val quadData = floatArrayOf(
            -1f, 1f, 0f, 0f, 0f,
            -1f, -1f, 0f, 0f, 1f,
            1f, 1f, 0f, 1f, 0f,
            1f, -1f, 0f, 1f, 1f,
        )
        val fullData = floatArrayOf(
            -1f, 1f,
            -1f, -1f,
            1f, 1f,
            1f, -1f,
        )
        val ids = IntArray(2)
        GLES30.glGenBuffers(2, ids, 0)
        quadVbo = ids[0]
        fullScreenQuadVbo = ids[1]

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quadVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quadData.size * 4, toFloatBuffer(quadData), GLES30.GL_STATIC_DRAW)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, fullScreenQuadVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fullData.size * 4, toFloatBuffer(fullData), GLES30.GL_STATIC_DRAW)
    }

    private fun buildCinemaRoomGeometry() {
        val verts = CinemaHallBuilder.buildHallVertices(config.screenPreset, videoAspectRatio)
        roomVertexCount = verts.size / CinemaHallBuilder.FLOATS_PER_VERTEX
        if (roomVbo == 0) {
            val ids = IntArray(1)
            GLES30.glGenBuffers(1, ids, 0)
            roomVbo = ids[0]
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, roomVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, toFloatBuffer(verts), GLES30.GL_STATIC_DRAW)
        buildProjectorBeam()
    }

    private fun buildProjectorBeam() {
        val aspect = videoAspectRatio.coerceIn(1.2f, 2.4f)
        val screenHeight = config.screenPreset.widthMeters / aspect
        val screenCenterY = CinemaHallBuilder.screenCenterYFor(config.screenPreset, aspect)
        val ceilingY = kotlin.math.max(4.35f, screenCenterY + screenHeight * 0.5f + 0.92f)
        val projY = ceilingY - 1.04f
        val projZ = 4.46f
        val endZ = -2.2f
        val endY = screenCenterY + screenHeight * 0.25f

        val pHalfW = 0.52f
        val pHalfH = 0.16f
        val eHalfW = 2.45f
        val eHalfH = 0.92f

        // 4 tapered light cone sides fading cleanly to 0 before reaching viewer sightline
        val b = ArrayList<Float>(96)
        fun quad(
            x0: Float, y0: Float, z0: Float, a0: Float,
            x1: Float, y1: Float, z1: Float, a1: Float,
            x2: Float, y2: Float, z2: Float, a2: Float,
            x3: Float, y3: Float, z3: Float, a3: Float,
        ) {
            b.add(x0); b.add(y0); b.add(z0); b.add(a0)
            b.add(x1); b.add(y1); b.add(z1); b.add(a1)
            b.add(x2); b.add(y2); b.add(z2); b.add(a2)
            b.add(x0); b.add(y0); b.add(z0); b.add(a0)
            b.add(x2); b.add(y2); b.add(z2); b.add(a2)
            b.add(x3); b.add(y3); b.add(z3); b.add(a3)
        }
        // Top sheet
        quad(-pHalfW, projY + pHalfH, projZ, 0.09f, pHalfW, projY + pHalfH, projZ, 0.09f, eHalfW, endY + eHalfH, endZ, 0f, -eHalfW, endY + eHalfH, endZ, 0f)
        // Bottom sheet
        quad(-pHalfW, projY - pHalfH, projZ, 0.09f, -eHalfW, endY - eHalfH, endZ, 0f, eHalfW, endY - eHalfH, endZ, 0f, pHalfW, projY - pHalfH, projZ, 0.09f)
        // Left sheet
        quad(-pHalfW, projY - pHalfH, projZ, 0.08f, -pHalfW, projY + pHalfH, projZ, 0.08f, -eHalfW, endY + eHalfH, endZ, 0f, -eHalfW, endY - eHalfH, endZ, 0f)
        // Right sheet
        quad(pHalfW, projY + pHalfH, projZ, 0.08f, pHalfW, projY - pHalfH, projZ, 0.08f, eHalfW, endY - eHalfH, endZ, 0f, eHalfW, endY + eHalfH, endZ, 0f)

        val arr = FloatArray(b.size) { b[it] }
        beamVertexCount = arr.size / 4
        if (beamVbo == 0) {
            val ids = IntArray(1)
            GLES30.glGenBuffers(1, ids, 0)
            beamVbo = ids[0]
        }
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, beamVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, arr.size * 4, toFloatBuffer(arr), GLES30.GL_STATIC_DRAW)
    }

    private fun setupBounceFbo() {
        val fbos = IntArray(1)
        val texs = IntArray(1)
        GLES30.glGenFramebuffers(1, fbos, 0)
        GLES30.glGenTextures(1, texs, 0)
        bounceFboId = fbos[0]
        bounceTexId = texs[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, bounceTexId)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 4, 4, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bounceFboId)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, bounceTexId, 0)
        if (GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            bounceFboId = 0
        } else {
            GLES30.glViewport(0, 0, 4, 4)
            GLES30.glClearColor(0f, 0f, 0f, 1f)
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun updateBounceTexture() {
        val (uOff, uScale, vOff, vScale) = stereoUvRect(config.videoFormat, config.eyeSwap)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, bounceFboId)
        GLES30.glViewport(0, 0, 4, 4)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glUseProgram(bounceProg)
        GLES30.glUniformMatrix4fv(uBounceTexMatrixLoc, 1, false, texTransformMat, 0)
        GLES30.glUniform4f(uBounceUvRectLoc, uOff, uScale, vOff, vScale)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTexId)
        GLES30.glUniform1i(uBounceTexLoc, 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, fullScreenQuadVbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun setupEyeFbo(w: Int, h: Int) {
        val fboScale = if (config.thermalCapEnabled) 0.85f else 1.0f
        val targetW = (w * fboScale).toInt().coerceIn(320, 1280)
        val targetH = (h * fboScale).toInt().coerceIn(320, 1280)
        if (targetW == fboW && targetH == fboH && fboId != 0) return

        if (fboId != 0) {
            GLES30.glDeleteFramebuffers(1, intArrayOf(fboId), 0)
            GLES30.glDeleteTextures(1, intArrayOf(fboColorTexId), 0)
            GLES30.glDeleteRenderbuffers(1, intArrayOf(fboDepthRboId), 0)
            fboId = 0
        }

        fboW = targetW
        fboH = targetH

        val fbos = IntArray(1)
        val texs = IntArray(1)
        val rbos = IntArray(1)
        GLES30.glGenFramebuffers(1, fbos, 0)
        GLES30.glGenTextures(1, texs, 0)
        GLES30.glGenRenderbuffers(1, rbos, 0)

        fboId = fbos[0]
        fboColorTexId = texs[0]
        fboDepthRboId = rbos[0]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboColorTexId)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, fboW, fboH, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, fboDepthRboId)
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT16, fboW, fboH)

        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fboId)
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, fboColorTexId, 0)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, fboDepthRboId)
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.e("StereoGl", "FBO incomplete: status=$status fboW=$fboW fboH=$fboH")
            fboId = 0
        } else {
            Log.d("StereoGl", "FBO ready: fboId=$fboId tex=$fboColorTexId (${fboW}x${fboH}) vp=${viewportW}x${viewportH}")
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    private fun createExternalOesTexture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, ids[0])
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    private fun createRgbaTexture(): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return ids[0]
    }

    private fun compileProgram(vsSrc: String, fsSrc: String): Int {
        val cleanVs = vsSrc.trimIndent().trim()
        val cleanFs = fsSrc.trimIndent().trim()
        val vs = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER)
        GLES30.glShaderSource(vs, cleanVs)
        GLES30.glCompileShader(vs)
        val vsStatus = IntArray(1)
        GLES30.glGetShaderiv(vs, GLES30.GL_COMPILE_STATUS, vsStatus, 0)
        if (vsStatus[0] == 0) {
            Log.e("StereoGl", "VS compile fail: ${GLES30.glGetShaderInfoLog(vs)}\n$cleanVs")
        }

        val fs = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER)
        GLES30.glShaderSource(fs, cleanFs)
        GLES30.glCompileShader(fs)
        val fsStatus = IntArray(1)
        GLES30.glGetShaderiv(fs, GLES30.GL_COMPILE_STATUS, fsStatus, 0)
        if (fsStatus[0] == 0) {
            Log.e("StereoGl", "FS compile fail: ${GLES30.glGetShaderInfoLog(fs)}\n$cleanFs")
        }

        val prog = GLES30.glCreateProgram()
        GLES30.glAttachShader(prog, vs)
        GLES30.glAttachShader(prog, fs)
        GLES30.glLinkProgram(prog)
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(prog, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e("StereoGl", "Program link fail: ${GLES30.glGetProgramInfoLog(prog)}")
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return prog
    }

    private fun toFloatBuffer(arr: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(arr)
            position(0)
        }

    private fun toShortBuffer(arr: ShortArray): ShortBuffer =
        ByteBuffer.allocateDirect(arr.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().apply {
            put(arr)
            position(0)
        }

    private data class Quad4(val a: Float, val b: Float, val c: Float, val d: Float)

    private companion object {
        const val VERTEX_SHADER_3D_TEX = """
            #version 300 es
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec2 aUv;
            uniform mat4 uMvp;
            out vec2 vUv;
            void main() {
                gl_Position = uMvp * vec4(aPos, 1.0);
                vUv = aUv;
            }
        """

        const val FRAGMENT_SHADER_OES_SHARPEN = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            uniform samplerExternalOES uTex;
            uniform mat4 uTexMatrix;
            uniform vec4 uUvRect;
            uniform float uSharpen;
            uniform float uFade;
            uniform vec2 uTexel;
            in vec2 vUv;
            out vec4 fragColor;
            void main() {
                vec2 sliced = vec2(
                    uUvRect.x + vUv.x * uUvRect.y,
                    uUvRect.z + vUv.y * uUvRect.w
                );
                // Convert top-down v (0..1) into bottom-up SurfaceTexture input (0..1) and transform
                vec2 uv = (uTexMatrix * vec4(sliced.x, 1.0 - sliced.y, 0.0, 1.0)).xy;
                vec3 c = texture(uTex, uv).rgb;
                if (uSharpen > 0.01) {
                    vec3 n = texture(uTex, uv + vec2(0.0, uTexel.y)).rgb;
                    vec3 s = texture(uTex, uv - vec2(0.0, uTexel.y)).rgb;
                    vec3 e = texture(uTex, uv + vec2(uTexel.x, 0.0)).rgb;
                    vec3 w = texture(uTex, uv - vec2(uTexel.x, 0.0)).rgb;
                    vec3 detail = c * 4.0 - (n + s + e + w);
                    c = clamp(c + detail * (uSharpen * 0.42), 0.0, 1.0);
                }
                fragColor = vec4(c * uFade, 1.0);
            }
        """

        const val FRAGMENT_SHADER_2D_TEX = """
            #version 300 es
            precision mediump float;
            uniform sampler2D uTex;
            uniform float uAlpha;
            in vec2 vUv;
            out vec4 fragColor;
            void main() {
                vec4 c = texture(uTex, vUv);
                fragColor = vec4(c.rgb, c.a * uAlpha);
            }
        """

        const val VERTEX_SHADER_COLOR = """
            #version 300 es
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNormal;
            layout(location = 2) in vec3 aAlbedo;
            layout(location = 3) in float aEmissive;
            uniform mat4 uMvp;
            out vec3 vWorldPos;
            out vec3 vNormal;
            out vec3 vAlbedo;
            out float vEmissive;
            void main() {
                gl_Position = uMvp * vec4(aPos, 1.0);
                vWorldPos = aPos;
                vNormal = aNormal;
                vAlbedo = aAlbedo;
                vEmissive = aEmissive;
            }
        """

        const val FRAGMENT_SHADER_BOUNCE = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3 : require
            precision mediump float;
            uniform samplerExternalOES uTex;
            uniform mat4 uTexMatrix;
            uniform vec4 uUvRect;
            in vec2 vPos;
            out vec4 fragColor;
            void main() {
                vec2 vUv = vPos * 0.5 + 0.5;
                vec2 sliced = vec2(uUvRect.x + vUv.x * uUvRect.y, uUvRect.z + (1.0 - vUv.y) * uUvRect.w);
                vec2 uv = (uTexMatrix * vec4(sliced.x, 1.0 - sliced.y, 0.0, 1.0)).xy;
                fragColor = vec4(texture(uTex, uv).rgb, 1.0);
            }
        """

        const val FRAGMENT_SHADER_COLOR = """
            #version 300 es
            precision mediump float;
            uniform sampler2D uBounceTex;
            uniform vec3 uScreenPos;
            uniform float uFade;
            in vec3 vWorldPos;
            in vec3 vNormal;
            in vec3 vAlbedo;
            in float vEmissive;
            out vec4 fragColor;

            void main() {
                vec3 N = normalize(vNormal);
                vec3 toScreen = uScreenPos - vWorldPos;
                float dist = length(toScreen);
                vec3 L = toScreen / max(dist, 0.001);
                vec3 V = normalize(-vWorldPos);

                // Single hardware bilinear lookup into 4x4 downsampled movie-bounce texture
                float sideBias = smoothstep(-5.5, 5.5, vWorldPos.x);
                float vertBias = smoothstep(-2.4, 3.2, vWorldPos.y);
                vec3 bounceRaw = texture(uBounceTex, vec2(sideBias, vertBias)).rgb;
                vec3 screenBounce = max(bounceRaw, vec3(0.08, 0.21, 0.23));

                // Procedural zero-texture micro-materials driven by surface normal & world coordinates
                vec3 albedo = vAlbedo;
                float isWallOrDrape = smoothstep(0.45, 0.85, abs(N.x));
                float isFloor = smoothstep(0.65, 0.92, N.y);
                float isCeiling = smoothstep(0.65, 0.92, -N.y);
                float slatPattern = 0.87 + 0.13 * sin(vWorldPos.z * 32.0 + vWorldPos.x * 16.0);
                float weavePattern = 0.91 + 0.09 * sin(vWorldPos.x * 40.0) * sin(vWorldPos.z * 40.0);
                float cofferPattern = 0.92 + 0.08 * sin(vWorldPos.x * 14.0) * sin(vWorldPos.z * 14.0);
                float matFactor = mix(1.0, slatPattern, isWallOrDrape * (1.0 - step(0.15, vEmissive)));
                matFactor *= mix(1.0, weavePattern, isFloor * (1.0 - step(0.15, vEmissive)));
                matFactor *= mix(1.0, cofferPattern, isCeiling);
                albedo *= matFactor;

                // Readable hemisphere ambient hall fill + wrapped movie-screen illumination
                vec3 hemi = mix(vec3(0.036, 0.046, 0.056), vec3(0.078, 0.110, 0.124), N.y * 0.5 + 0.5);
                float wrappedNdotL = clamp(dot(N, L) * 0.82 + 0.18, 0.0, 1.0);
                float att = 1.0 / (1.0 + 0.015 * dist * dist);

                // Satin velvet & metal rim specular along curtains, armrests, and headrest tops
                vec3 H = normalize(L + V);
                float spec = pow(max(dot(N, H), 0.0001), 26.0) * 0.24;

                // Soft architectural sconce wash spilling warm amber & teal across wall bays
                float wallProx = smoothstep(3.4, 6.2, abs(vWorldPos.x));
                float dySconce = (vWorldPos.y - 0.35) * 0.58;
                float vertCone = exp(-(dySconce * dySconce));
                float bayWave = 0.45 + 0.55 * max(sin((vWorldPos.z + 0.85) * 1.48), 0.0);
                vec3 sconceColor = mix(vec3(0.08, 0.28, 0.26), vec3(0.32, 0.21, 0.06), 0.5 + 0.5 * sin(vWorldPos.z * 0.74));
                vec3 sconceWash = sconceColor * wallProx * vertCone * bayWave * 0.65;

                // Gentle up-wash near proscenium ceiling & footlight apron
                float dzStage = (vWorldPos.z + 5.8) * 0.38;
                float stageProx = exp(-(dzStage * dzStage)) * smoothstep(-2.8, -1.6, vWorldPos.y);
                vec3 lit = albedo * (hemi + screenBounce * wrappedNdotL * att * 2.65 + sconceWash + vec3(0.04, 0.14, 0.14) * stageProx)
                    + screenBounce * spec * att;

                // Refined self-illumination for concealed sconce slits, bias halo, and step markers
                vec3 emissiveTerm = vAlbedo * vEmissive * 0.92;
                vec3 color = lit + emissiveTerm;

                // Gentle atmospheric auditorium depth haze + smooth filmic highlight rolloff
                float fog = exp(-0.0014 * dist * dist);
                color = mix(vec3(0.020, 0.030, 0.036), color, fog);
                color = color / (vec3(1.0) + color * 0.18);
                fragColor = vec4(color * uFade, 1.0);
            }
        """

        const val VERTEX_SHADER_BEAM = """
            #version 300 es
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in float aIntensity;
            uniform mat4 uMvp;
            out float vIntensity;
            void main() {
                gl_Position = uMvp * vec4(aPos, 1.0);
                vIntensity = aIntensity;
            }
        """

        const val FRAGMENT_SHADER_BEAM = """
            #version 300 es
            precision mediump float;
            uniform sampler2D uBounceTex;
            uniform float uFade;
            in float vIntensity;
            out vec4 fragColor;
            void main() {
                vec3 tint = mix(vec3(0.42, 0.72, 0.78), texture(uBounceTex, vec2(0.5, 0.55)).rgb, 0.65);
                fragColor = vec4(tint, vIntensity * uFade);
            }
        """

        const val VERTEX_SHADER_2D_POS = """
            #version 300 es
            layout(location = 0) in vec2 aPos;
            out vec2 vPos;
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
                vPos = aPos;
            }
        """

        const val FRAGMENT_SHADER_RETICLE = """
            #version 300 es
            precision mediump float;
            in vec2 vPos;
            uniform float uDwell;
            uniform float uAspect;
            out vec4 fragColor;
            void main() {
                vec2 p = vec2(vPos.x * uAspect, vPos.y);
                float d = length(p);
                // Inner center dot + turquoise dwell progress ring
                float dotMask = smoothstep(0.011, 0.007, d);
                float ringMask = smoothstep(0.032, 0.028, d) * smoothstep(0.020, 0.024, d);
                if (uDwell > 0.01) {
                    float angle = atan(p.y, p.x);
                    float normAngle = fract((1.5707963 - angle) / 6.2831853);
                    float arc = ringMask * step(normAngle, uDwell);
                    fragColor = vec4(0.18, 0.83, 0.75, clamp(dotMask + arc * 0.95, 0.0, 1.0));
                } else {
                    fragColor = vec4(1.0, 1.0, 1.0, dotMask * 0.85);
                }
            }
        """

        const val VERTEX_SHADER_2D_TEX = """
            #version 300 es
            layout(location = 0) in vec2 aPos;
            out vec2 vUv;
            void main() {
                gl_Position = vec4(aPos, 0.0, 1.0);
                vUv = aPos * 0.5 + 0.5;
            }
        """

        const val FRAGMENT_SHADER_BARREL = """
            #version 300 es
            precision mediump float;
            uniform sampler2D uTex;
            uniform float uK1;
            uniform float uK2;
            in vec2 vUv;
            out vec4 fragColor;
            void main() {
                vec2 centered = (vUv - 0.5) * 2.0;
                float r2 = dot(centered, centered);
                float scale = 1.0 + uK1 * r2 + uK2 * r2 * r2;
                vec2 distorted = centered * scale;
                vec2 sampleUv = distorted * 0.5 + 0.5;
                if (sampleUv.x < 0.0 || sampleUv.x > 1.0 || sampleUv.y < 0.0 || sampleUv.y > 1.0) {
                    fragColor = vec4(0.0, 0.0, 0.0, 1.0);
                } else {
                    fragColor = texture(uTex, sampleUv);
                }
            }
        """
    }
}
