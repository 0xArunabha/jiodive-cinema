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

    @Volatile
    var config: CinemaConfig = CinemaConfig()
        set(value) {
            if (field.screenPreset != value.screenPreset) {
                screenMeshDirty = true
            }
            field = value
            headTracker.setBedMode(value.environment == TheaterEnvironment.BED)
        }

    @Volatile
    var isPlaying: Boolean = false
    @Volatile
    var isMuted: Boolean = false
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

    private var surfaceTexture: SurfaceTexture? = null
    private var videoTexId: Int = 0
    @Volatile
    private var frameAvailable: Boolean = false

    private var dockTexId: Int = 0
    private var subtitleTexId: Int = 0
    private val subtitleBitmap: Bitmap = Bitmap.createBitmap(1024, 160, Bitmap.Config.ARGB_8888)
    private val subtitleCanvas = Canvas(subtitleBitmap)
    @Volatile
    private var subtitleBitmapDirty = true

    private var screenMeshDirty = true
    private var viewportW = 1920
    private var viewportH = 1080

    // Offscreen FBO for optional per-eye barrel distortion pass
    private var fboId = 0
    private var fboColorTexId = 0
    private var fboDepthRboId = 0
    private var fboW = 0
    private var fboH = 0

    // Programs
    private var videoProg = 0
    private var colorProg = 0
    private var texQuadProg = 0
    private var reticleProg = 0
    private var barrelProg = 0

    // Buffers
    private var screenVbo = 0
    private var screenIbo = 0
    private var screenIndexCount = 0

    private var roomVbo = 0
    private var roomVertexCount = 0

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

    // Gaze dwell tracking
    private var currentHoveredAction: DockAction? = null
    private var currentScrubNorm: Float = 0f
    private var hoverStartTimeNs: Long = 0L
    private var dwellConsumed: Boolean = false
    private var lastDockRedrawMs: Long = 0L

    private val dockCenterY = -1.18f
    private val dockCenterZ = -2.35f
    private val dockHalfW = 1.02f
    private val dockHalfH = 0.30f
    private val dockTiltRad = Math.toRadians(18.0).toFloat()

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
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
        texQuadProg = compileProgram(VERTEX_SHADER_3D_TEX, FRAGMENT_SHADER_2D_TEX)
        reticleProg = compileProgram(VERTEX_SHADER_2D_POS, FRAGMENT_SHADER_RETICLE)
        barrelProg = compileProgram(VERTEX_SHADER_2D_TEX, FRAGMENT_SHADER_BARREL)

        buildUnitQuadVbo()
        buildCinemaRoomGeometry()
        rebuildScreenGeometry(this.config.screenPreset)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportW = width.coerceAtLeast(2)
        viewportH = height.coerceAtLeast(2)
        setupEyeFbo(viewportW / 2, viewportH)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (frameAvailable) {
            frameAvailable = false
            runCatching {
                surfaceTexture?.updateTexImage()
                surfaceTexture?.getTransformMatrix(texTransformMat)
            }
        }
        if (screenMeshDirty) {
            screenMeshDirty = false
            rebuildScreenGeometry(config.screenPreset)
        }

        headTracker.getViewRotationAndForward(viewRotMat, lookForward)
        val gazeHit = updateGazeAndDockBitmap()

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

    private fun updateGazeAndDockBitmap(): Float {
        // Intersect ray (origin 0, dir = lookForward) with tilted Dock quad centered at (0, dockCenterY, dockCenterZ)
        val nY = sin(dockTiltRad.toDouble()).toFloat()
        val nZ = cos(dockTiltRad.toDouble()).toFloat()
        val denom = lookForward[1] * nY + lookForward[2] * nZ
        var hovered: DockAction? = null
        var scrubFrac = 0f

        if (denom < -0.05f && !dockRenderer.dockHidden) {
            val t = (dockCenterY * nY + dockCenterZ * nZ) / denom
            if (t in 0.4f..8.0f) {
                val hitX = lookForward[0] * t
                val hitY = lookForward[1] * t - dockCenterY
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

        val nowNs = System.nanoTime()
        if (hovered != currentHoveredAction) {
            currentHoveredAction = hovered
            hoverStartTimeNs = nowNs
            dwellConsumed = false
        }
        currentScrubNorm = scrubFrac

        var dwellProgress = 0f
        if (hovered != null && hovered != DockAction.NONE && !dwellConsumed) {
            val elapsedMs = (nowNs - hoverStartTimeNs) / 1_000_000f
            val limit = config.gazeDwellMs.toFloat().coerceAtLeast(500f)
            dwellProgress = (elapsedMs / limit).coerceIn(0f, 1f)
            if (dwellProgress >= 1f) {
                dwellConsumed = true
                onDockActionTriggered(hovered, currentScrubNorm)
            }
        }

        val nowMs = nowNs / 1_000_000L
        if (nowMs - lastDockRedrawMs >= 66L) {
            lastDockRedrawMs = nowMs
            dockRenderer.redraw(
                isPlaying = isPlaying,
                isMuted = isMuted,
                subtitlesLoaded = subtitlesLoaded,
                subtitlesEnabled = subtitlesEnabled,
                positionMs = positionMs,
                durationMs = durationMs,
                config = config,
                hoveredAction = hovered,
                hoverScrubNorm = scrubFrac,
            )
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, dockTexId)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, dockRenderer.bitmap, 0)
        }

        if (subtitleBitmapDirty) {
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

        // 1. Cinema Auditorium Room
        if (config.environment == TheaterEnvironment.CINEMA && roomVertexCount > 0) {
            Matrix.setIdentityM(modelMat, 0)
            Matrix.multiplyMM(tmpMat, 0, viewMat, 0, modelMat, 0)
            Matrix.multiplyMM(mvpMat, 0, projMat, 0, tmpMat, 0)

            GLES30.glUseProgram(colorProg)
            val uMvp = GLES30.glGetUniformLocation(colorProg, "uMvp")
            GLES30.glUniformMatrix4fv(uMvp, 1, false, mvpMat, 0)

            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, roomVbo)
            val stride = 7 * 4
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, stride, 0)
            GLES30.glEnableVertexAttribArray(1)
            GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, stride, 3 * 4)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, roomVertexCount)
        }

        // 2. Curved Video Screen
        Matrix.setIdentityM(modelMat, 0)
        Matrix.translateM(modelMat, 0, 0f, 0.2f, -config.screenPreset.distanceMeters)
        Matrix.multiplyMM(tmpMat, 0, viewMat, 0, modelMat, 0)
        Matrix.multiplyMM(mvpMat, 0, projMat, 0, tmpMat, 0)

        val (uOff, uScale, vOff, vScale) = stereoUvRect(config.videoFormat, useRightStereoEye)

        GLES30.glUseProgram(videoProg)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(videoProg, "uMvp"), 1, false, mvpMat, 0)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(videoProg, "uTexMatrix"), 1, false, texTransformMat, 0)
        GLES30.glUniform4f(GLES30.glGetUniformLocation(videoProg, "uUvRect"), uOff, uScale, vOff, vScale)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(videoProg, "uSharpen"), config.sharpenStrength)
        GLES30.glUniform2f(GLES30.glGetUniformLocation(videoProg, "uTexel"), 1f / 1920f, 1f / 1080f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, videoTexId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(videoProg, "uTex"), 0)

        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, screenVbo)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, screenIbo)
        val texStride = 5 * 4
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, texStride, 0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, texStride, 3 * 4)
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, screenIndexCount, GLES30.GL_UNSIGNED_SHORT, 0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, 0)

        // 3. HUD Layers: Subtitles + Dock (always visible without clipping into seats)
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)

        if (subtitlesEnabled && !activeSubtitleText.isNullOrBlank()) {
            val subDist = config.screenPreset.distanceMeters * 0.72f
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, 0f, -1.05f, -subDist)
            Matrix.scaleM(modelMat, 0, subDist * 0.36f, subDist * 0.06f, 1f)
            drawTexturedQuad(subtitleTexId)
        }

        if (!dockRenderer.dockHidden) {
            Matrix.setIdentityM(modelMat, 0)
            Matrix.translateM(modelMat, 0, 0f, dockCenterY, dockCenterZ)
            Matrix.rotateM(modelMat, 0, -Math.toDegrees(dockTiltRad.toDouble()).toFloat(), 1f, 0f, 0f)
            Matrix.scaleM(modelMat, 0, dockHalfW, dockHalfH, 1f)
            drawTexturedQuad(dockTexId)
        }

        // 4. Gaze Reticle (drawn centered in eye view space when dock visible or hovering)
        if (!dockRenderer.dockHidden || gazeDwellFraction > 0.01f) {
            GLES30.glUseProgram(reticleProg)
            GLES30.glDisableVertexAttribArray(1)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(reticleProg, "uDwell"), gazeDwellFraction)
            val eyeAspect = (viewportW * 0.5f) / viewportH.toFloat().coerceAtLeast(1f)
            GLES30.glUniform1f(GLES30.glGetUniformLocation(reticleProg, "uAspect"), eyeAspect)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, fullScreenQuadVbo)
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 2 * 4, 0)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        }
    }

    private fun drawTexturedQuad(texId: Int) {
        Matrix.multiplyMM(tmpMat, 0, viewMat, 0, modelMat, 0)
        Matrix.multiplyMM(mvpMat, 0, projMat, 0, tmpMat, 0)
        GLES30.glUseProgram(texQuadProg)
        GLES30.glUniformMatrix4fv(GLES30.glGetUniformLocation(texQuadProg, "uMvp"), 1, false, mvpMat, 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(texQuadProg, "uTex"), 0)

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
        GLES30.glUniform1f(GLES30.glGetUniformLocation(barrelProg, "uK1"), config.k1)
        GLES30.glUniform1f(GLES30.glGetUniformLocation(barrelProg, "uK2"), config.k2)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fboColorTexId)
        GLES30.glUniform1i(GLES30.glGetUniformLocation(barrelProg, "uTex"), 0)

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

        val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 12, 16, 18)
        }
        val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 40f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val lines = text.lines().take(2)
        val lineHeight = 50f
        val top = (subtitleBitmap.height - lines.size * lineHeight) * 0.5f - 10f
        val bottom = top + lines.size * lineHeight + 24f
        val maxW = lines.maxOf { txtPaint.measureText(it) }.coerceAtLeast(180f) + 64f
        val left = (subtitleBitmap.width - maxW) * 0.5f
        subtitleCanvas.drawRoundRect(left, top, left + maxW, bottom, 24f, 24f, pillPaint)

        lines.forEachIndexed { idx, line ->
            val y = top + 46f + idx * lineHeight
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
        val verts = ArrayList<Float>()
        fun addBox(x1: Float, y1: Float, z1: Float, x2: Float, y2: Float, z2: Float, r: Float, g: Float, b: Float, a: Float) {
            fun tri(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float) {
                verts.addAll(listOf(ax, ay, az, r, g, b, a, bx, by, bz, r, g, b, a, cx, cy, cz, r, g, b, a))
            }
            // Top face
            tri(x1, y2, z2, x2, y2, z2, x2, y2, z1)
            tri(x1, y2, z2, x2, y2, z1, x1, y2, z1)
            // Front face
            tri(x1, y1, z2, x2, y1, z2, x2, y2, z2)
            tri(x1, y1, z2, x2, y2, z2, x1, y2, z2)
        }

        // Auditorium dark carpeted floor
        addBox(-9f, -2.5f, -11f, 9f, -2.45f, 4f, 0.04f, 0.06f, 0.07f, 1f)
        // Turquoise aisle accent guide strips
        addBox(-1.65f, -2.44f, -7.5f, -1.55f, -2.42f, 2f, 0.08f, 0.56f, 0.54f, 0.85f)
        addBox(1.55f, -2.44f, -7.5f, 1.65f, -2.42f, 2f, 0.08f, 0.56f, 0.54f, 0.85f)
        // Stepped cinema seat tiers in front
        addBox(-6f, -2.45f, -5.8f, 6f, -1.95f, -5.0f, 0.08f, 0.11f, 0.12f, 1f)
        addBox(-6f, -2.45f, -4.1f, 6f, -1.80f, -3.3f, 0.09f, 0.12f, 0.13f, 1f)
        // Warm cinema gold stage apron under curved screen
        addBox(-5.2f, -2.35f, -6.9f, 5.2f, -2.30f, -6.5f, 0.72f, 0.55f, 0.12f, 0.65f)
        // Acoustic side wall columns
        for (i in 0..3) {
            val z = -8.5f + i * 3.0f
            addBox(-8.6f, -2.4f, z, -8.3f, 3.6f, z + 0.35f, 0.05f, 0.14f, 0.16f, 1f)
            addBox(8.3f, -2.4f, z, 8.6f, 3.6f, z + 0.35f, 0.05f, 0.14f, 0.16f, 1f)
        }

        roomVertexCount = verts.size / 7
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        roomVbo = ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, roomVbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, verts.size * 4, toFloatBuffer(verts.toFloatArray()), GLES30.GL_STATIC_DRAW)
    }

    private fun setupEyeFbo(w: Int, h: Int) {
        val targetW = w.coerceIn(320, 1600)
        val targetH = h.coerceIn(320, 1600)
        if (targetW == fboW && targetH == fboH && fboId != 0) return
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
                fragColor = vec4(c, 1.0);
            }
        """

        const val FRAGMENT_SHADER_2D_TEX = """
            #version 300 es
            precision mediump float;
            uniform sampler2D uTex;
            in vec2 vUv;
            out vec4 fragColor;
            void main() {
                fragColor = texture(uTex, vUv);
            }
        """

        const val VERTEX_SHADER_COLOR = """
            #version 300 es
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec4 aColor;
            uniform mat4 uMvp;
            out vec4 vColor;
            void main() {
                gl_Position = uMvp * vec4(aPos, 1.0);
                vColor = aColor;
            }
        """

        const val FRAGMENT_SHADER_COLOR = """
            #version 300 es
            precision mediump float;
            in vec4 vColor;
            out vec4 fragColor;
            void main() {
                fragColor = vColor;
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
