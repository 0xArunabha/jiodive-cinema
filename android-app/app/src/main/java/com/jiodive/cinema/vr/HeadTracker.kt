package com.jiodive.cinema.vr

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Low-latency 3DoF IMU head tracker calibrated for landscape phone-in-headset VR.
 * Handles both Surface.ROTATION_90 and Surface.ROTATION_270 (whichever way JioDive latches),
 * supports yaw recentering, reclined Bed Mode 3D reference framing without horizon roll or
 * zenith gimbal lock, touch-drag look, and safely bypasses static table-flat emulator virtual
 * sensors so VR starts squarely facing the theater screen.
 */
class HeadTracker(context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val isEmulator = isAndroidEmulator()
    private val rotationSensor: Sensor? = if (isEmulator) {
        null
    } else {
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    private val rawRotRowMajor = FloatArray(16)
    private val sensorComputedMat = FloatArray(16)
    private val core = HeadPoseCore()

    @Volatile
    var displayRotation: Int = Surface.ROTATION_90

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun setBedMode(enabled: Boolean, defaultTiltDeg: Float? = null) {
        core.setBedMode(enabled, defaultTiltDeg)
    }

    fun setBedTiltDeg(tiltDeg: Float) {
        core.setBedTiltDeg(tiltDeg)
    }

    fun getEffectiveBedTiltDeg(): Float = core.getEffectiveBedTiltDeg()

    fun recenter(calibrateBedPose: Boolean = true) {
        core.recenter(calibrateBedPose)
    }

    fun addTouchDrag(deltaXRad: Float, deltaYRad: Float) {
        core.addTouchDrag(deltaXRad, deltaYRad)
    }

    /**
     * Populates a column-major 4x4 camera rotation matrix (world-to-camera / view orientation)
     * plus extracts the forward look unit vector `(fx, fy, fz)` in GL world space.
     */
    fun getViewRotationAndForward(
        outViewMat: FloatArray,
        outForward: FloatArray,
        dtSec: Float = 0.016f,
    ) {
        core.getViewRotationAndForward(outViewMat, outForward, dtSec)
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rawRotRowMajor, event.values)
        computeGlViewFromWorldMatrix(rawRotRowMajor, displayRotation, sensorComputedMat)
        core.onSensorViewMatrix(sensorComputedMat)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    /**
     * Pure-Kotlin 3DoF orientation & Bed Mode reference-frame solver (isolated from Android Sensor/GL
     * JNI stubs so all orientation and Bed Mode physics can be unit-tested directly on JVM).
     */
    class HeadPoseCore {
        private val lock = Any()

        private val glCamFromWorld = FloatArray(16)
        private val targetBedRefMat = FloatArray(16)
        private val smoothBedRefMat = FloatArray(16)

        private var hasSensorData = false
        private var bedModeEnabled = false
        private var bedRefInitialized = false
        private var defaultBedTiltDeg = 46f
        private var activeBedTiltDeg = 46f

        private var targetBedBlend = 0f
        private var currentBedBlend = 0f

        private var yawOffsetRad = 0f
        private var latestSensorYawRad = 0f
        private var touchYawRad = 0f
        private var touchPitchRad = 0f

        // Reusable scratch buffers (only accessed on render thread after lock release)
        private val scratchCam = FloatArray(16)
        private val scratchBedRef = FloatArray(16)
        private val scratchYawMat = FloatArray(16)
        private val scratchPitchMat = FloatArray(16)
        private val scratchTmp = FloatArray(16)
        private val scratchBedRefInv = FloatArray(16)
        private val scratchUpright = FloatArray(16)
        private val scratchBed = FloatArray(16)

        init {
            setIdentity(glCamFromWorld)
            setIdentity(targetBedRefMat)
            setIdentity(smoothBedRefMat)
        }

        fun setBedMode(enabled: Boolean, defaultTiltDeg: Float? = null) {
            synchronized(lock) {
                if (defaultTiltDeg != null) {
                    defaultBedTiltDeg = defaultTiltDeg.coerceIn(20f, 90f)
                }
                val changed = enabled != bedModeEnabled
                bedModeEnabled = enabled
                targetBedBlend = if (enabled) 1f else 0f

                if (!hasSensorData) {
                    currentBedBlend = targetBedBlend
                    if (enabled) {
                        activeBedTiltDeg = defaultBedTiltDeg
                    }
                    return
                }

                if (!changed) return

                touchYawRad = 0f
                touchPitchRad = 0f

                if (enabled) {
                    val calibratedTilt = computeBedReferenceFromSensorPose(
                        sensorViewMat = glCamFromWorld,
                        fallbackYawRad = latestSensorYawRad,
                        defaultTiltDeg = defaultBedTiltDeg,
                        forceAngleDeg = null,
                        outBedRefMat = targetBedRefMat,
                    )
                    activeBedTiltDeg = calibratedTilt
                    if (!bedRefInitialized) {
                        System.arraycopy(targetBedRefMat, 0, smoothBedRefMat, 0, 16)
                        bedRefInitialized = true
                    }
                } else {
                    // Leaving Bed Mode: align upright world-Y yaw with current horizontal facing
                    yawOffsetRad = latestSensorYawRad
                }
            }
        }

        fun setBedTiltDeg(tiltDeg: Float) {
            synchronized(lock) {
                val clamped = tiltDeg.coerceIn(20f, 90f)
                defaultBedTiltDeg = clamped
                activeBedTiltDeg = clamped
                touchPitchRad = 0f
                if (!hasSensorData) return

                val refYaw = if (bedRefInitialized) {
                    extractHorizontalYawRad(targetBedRefMat, latestSensorYawRad)
                } else {
                    latestSensorYawRad
                }
                buildTiltedBedMatrix(refYaw, clamped, targetBedRefMat)
                if (!bedRefInitialized) {
                    System.arraycopy(targetBedRefMat, 0, smoothBedRefMat, 0, 16)
                    bedRefInitialized = true
                }
            }
        }

        fun getEffectiveBedTiltDeg(): Float = synchronized(lock) { activeBedTiltDeg }

        fun recenter(calibrateBedPose: Boolean = true) {
            synchronized(lock) {
                touchYawRad = 0f
                touchPitchRad = 0f
                if (!hasSensorData) return

                yawOffsetRad = latestSensorYawRad
                if (bedModeEnabled) {
                    if (calibrateBedPose) {
                        activeBedTiltDeg = computeBedReferenceFromSensorPose(
                            sensorViewMat = glCamFromWorld,
                            fallbackYawRad = latestSensorYawRad,
                            defaultTiltDeg = defaultBedTiltDeg,
                            forceAngleDeg = null,
                            outBedRefMat = targetBedRefMat,
                        )
                    } else {
                        // Dock "Center" click: preserve current bed recline angle (and side-lying
                        // roll if already side-calibrated) while recentering horizontal bed facing.
                        val sideRolled = bedRefInitialized && abs(targetBedRefMat[4]) > 0.5f
                        if (sideRolled) {
                            computeBedReferenceFromSensorPose(
                                sensorViewMat = glCamFromWorld,
                                fallbackYawRad = latestSensorYawRad,
                                defaultTiltDeg = activeBedTiltDeg,
                                forceAngleDeg = null,
                                outBedRefMat = targetBedRefMat,
                            )
                        } else {
                            val yaw = extractHorizontalYawRad(glCamFromWorld, latestSensorYawRad)
                            buildTiltedBedMatrix(yaw, activeBedTiltDeg, targetBedRefMat)
                        }
                    }
                    bedRefInitialized = true
                }
            }
        }

        fun addTouchDrag(deltaXRad: Float, deltaYRad: Float) {
            synchronized(lock) {
                touchYawRad += deltaXRad
                touchPitchRad = (touchPitchRad + deltaYRad).coerceIn(-1.40f, 1.40f)
            }
        }

        fun onSensorViewMatrix(computedViewMat: FloatArray) {
            synchronized(lock) {
                System.arraycopy(computedViewMat, 0, glCamFromWorld, 0, 16)
                latestSensorYawRad = extractHorizontalYawRad(glCamFromWorld, latestSensorYawRad)
                if (!hasSensorData) {
                    yawOffsetRad = latestSensorYawRad
                    hasSensorData = true
                    if (bedModeEnabled) {
                        activeBedTiltDeg = computeBedReferenceFromSensorPose(
                            sensorViewMat = glCamFromWorld,
                            fallbackYawRad = latestSensorYawRad,
                            defaultTiltDeg = defaultBedTiltDeg,
                            forceAngleDeg = null,
                            outBedRefMat = targetBedRefMat,
                        )
                        System.arraycopy(targetBedRefMat, 0, smoothBedRefMat, 0, 16)
                        bedRefInitialized = true
                        currentBedBlend = 1f
                        targetBedBlend = 1f
                    }
                }
            }
        }

        fun getViewRotationAndForward(
            outViewMat: FloatArray,
            outForward: FloatArray,
            dtSec: Float = 0.016f,
        ) {
            val yawOff: Float
            val tYaw: Float
            val tPitch: Float
            val sensorPresent: Boolean
            val blend: Float
            val refAlpha = 1f - exp((-12f * dtSec.coerceIn(0.001f, 0.1f)).toDouble()).toFloat()

            synchronized(lock) {
                yawOff = yawOffsetRad
                tYaw = touchYawRad
                tPitch = touchPitchRad
                sensorPresent = hasSensorData

                if (sensorPresent) {
                    System.arraycopy(glCamFromWorld, 0, scratchCam, 0, 16)
                    if (bedRefInitialized) {
                        slerpRotationMatrices(smoothBedRefMat, targetBedRefMat, refAlpha, smoothBedRefMat)
                        System.arraycopy(smoothBedRefMat, 0, scratchBedRef, 0, 16)
                    } else {
                        setIdentity(scratchBedRef)
                    }
                    currentBedBlend += (targetBedBlend - currentBedBlend) * refAlpha
                    if (abs(targetBedBlend - currentBedBlend) < 0.002f) {
                        currentBedBlend = targetBedBlend
                    }
                } else {
                    setIdentity(scratchCam)
                    setIdentity(scratchBedRef)
                    currentBedBlend = targetBedBlend
                }
                blend = currentBedBlend
            }

            if (!sensorPresent) {
                // Emulator / sensorless fallback: stay squarely facing screen with clean touch drag
                setRotateX(scratchPitchMat, -tPitch)
                setRotateY(scratchYawMat, -tYaw)
                multiplyMM(outViewMat, scratchPitchMat, scratchYawMat)
                extractForward(outViewMat, outForward)
                return
            }

            if (blend < 0.998f) {
                // Upright mode: world-Y yaw recenter + screen-space pitch touch drag
                setRotateY(scratchYawMat, -(yawOff + tYaw))
                setRotateX(scratchPitchMat, -tPitch)
                multiplyMM(scratchTmp, scratchCam, scratchYawMat)
                multiplyMM(scratchUpright, scratchPitchMat, scratchTmp)
            }

            if (blend > 0.002f) {
                // Bed mode: transform world into reclined bed reference frame before applying touch
                // drag so turning left/right around the reclined spine produces ZERO horizon roll.
                transpose3x3(scratchBedRef, scratchBedRefInv)
                setRotateX(scratchPitchMat, -tPitch)
                setRotateY(scratchYawMat, -tYaw)
                multiplyMM(scratchTmp, scratchCam, scratchBedRefInv)
                multiplyMM(scratchBed, scratchTmp, scratchPitchMat)
                System.arraycopy(scratchBed, 0, scratchTmp, 0, 16)
                multiplyMM(scratchBed, scratchTmp, scratchYawMat)
            }

            when {
                blend <= 0.002f -> System.arraycopy(scratchUpright, 0, outViewMat, 0, 16)
                blend >= 0.998f -> System.arraycopy(scratchBed, 0, outViewMat, 0, 16)
                else -> slerpRotationMatrices(scratchUpright, scratchBed, blend, outViewMat)
            }

            extractForward(outViewMat, outForward)
        }
    }

    companion object {
        /**
         * Pure-math conversion from Android Sensor rotation matrix (`rawRotRowMajor`: world-from-sensor
         * row-major 4x4) to an OpenGL ES column-major view orientation matrix (`world-to-camera`).
         */
        fun computeGlViewFromWorldMatrix(
            rawRotRowMajor: FloatArray,
            displayRotation: Int,
            outGlViewMat: FloatArray,
        ) {
            val rev = displayRotation == Surface.ROTATION_270
            val s = if (rev) -1f else 1f

            // Columns of rawRotRowMajor (worldFromSensor row-major) are Sensor X, Y, Z in Android World (East, North, Up).
            // Convert each to OpenGL World (East=+X, Up=+Y, South=-Z):
            val xCamX = -s * rawRotRowMajor[1]
            val xCamY = -s * rawRotRowMajor[9]
            val xCamZ = s * rawRotRowMajor[5]

            val yCamX = s * rawRotRowMajor[0]
            val yCamY = s * rawRotRowMajor[8]
            val yCamZ = -s * rawRotRowMajor[4]

            val zCamX = rawRotRowMajor[2]
            val zCamY = rawRotRowMajor[10]
            val zCamZ = -rawRotRowMajor[6]

            outGlViewMat[0] = xCamX
            outGlViewMat[4] = xCamY
            outGlViewMat[8] = xCamZ
            outGlViewMat[12] = 0f

            outGlViewMat[1] = yCamX
            outGlViewMat[5] = yCamY
            outGlViewMat[9] = yCamZ
            outGlViewMat[13] = 0f

            outGlViewMat[2] = zCamX
            outGlViewMat[6] = zCamY
            outGlViewMat[10] = zCamZ
            outGlViewMat[14] = 0f

            outGlViewMat[3] = 0f
            outGlViewMat[7] = 0f
            outGlViewMat[11] = 0f
            outGlViewMat[15] = 1f
        }

        /**
         * Extracts horizontal compass heading in GL world space (`0` = North `-Z`, `+pi/2` = East `+X`).
         * Falls back to camera Right vector near zenith/nadir (`|pitch| > ~75 deg`) to eliminate
         * 180-degree azimuth flips when lying flat on a bed.
         */
        internal fun extractHorizontalYawRad(viewMat: FloatArray, fallbackYawRad: Float): Float {
            val fx = -viewMat[2]
            val fz = -viewMat[10]
            val fHoriz = hypot(fx.toDouble(), fz.toDouble()).toFloat()
            if (fHoriz >= 0.25f) {
                return atan2(fx.toDouble(), (-fz).toDouble()).toFloat()
            }
            val rx = viewMat[0]
            val rz = viewMat[8]
            val rHoriz = hypot(rx.toDouble(), rz.toDouble()).toFloat()
            if (rHoriz >= 0.25f) {
                return atan2(rz.toDouble(), rx.toDouble()).toFloat()
            }
            return fallbackYawRad
        }

        /**
         * Builds the `world-to-bedVirtual` reference frame matrix.
         * - If the user is already reclined (`fy >= sin(18 deg)`), calibrates directly to their
         *   current reclined forward gaze, smoothly blending from gravity-leveled horizon (`<= 46 deg`)
         *   to head-aligned orientation near zenith (`>= 67 deg`) and preserving side-lying poses.
         * - If upright (`fy < sin(18 deg)`), initializes at `defaultTiltDeg` upward along current yaw.
         * Returns the active bed tilt angle in degrees.
         */
        internal fun computeBedReferenceFromSensorPose(
            sensorViewMat: FloatArray,
            fallbackYawRad: Float,
            defaultTiltDeg: Float,
            forceAngleDeg: Float?,
            outBedRefMat: FloatArray,
        ): Float {
            if (forceAngleDeg != null) {
                val yaw = extractHorizontalYawRad(sensorViewMat, fallbackYawRad)
                val clamped = forceAngleDeg.coerceIn(20f, 90f)
                buildTiltedBedMatrix(yaw, clamped, outBedRefMat)
                return clamped
            }

            var fx = -sensorViewMat[2]
            var fy = -sensorViewMat[6]
            var fz = -sensorViewMat[10]
            val fLen = sqrt(fx * fx + fy * fy + fz * fz).coerceAtLeast(1e-5f)
            fx /= fLen
            fy /= fLen
            fz /= fLen

            // If user is currently sitting upright (< 18 deg upward pitch), pre-aim at defaultTiltDeg
            if (fy < 0.309017f) {
                val yaw = extractHorizontalYawRad(sensorViewMat, fallbackYawRad)
                val clamped = defaultTiltDeg.coerceIn(20f, 90f)
                buildTiltedBedMatrix(yaw, clamped, outBedRefMat)
                return clamped
            }

            // Sensor camera Right axis in GL world
            var rxS = sensorViewMat[0]
            var ryS = sensorViewMat[4]
            var rzS = sensorViewMat[8]
            val rSLen = sqrt(rxS * rxS + ryS * ryS + rzS * rzS).coerceAtLeast(1e-5f)
            rxS /= rSLen
            ryS /= rSLen
            rzS /= rSLen

            // Gravity-leveled right axis: forward x WorldUp = (-fz, 0, fx)
            val gx = -fz
            val gz = fx
            val gLen = hypot(gx.toDouble(), gz.toDouble()).toFloat()

            var gravWeight = 0f
            var gxNorm = rxS
            var gzNorm = rzS
            if (gLen > 0.08f) {
                gxNorm = gx / gLen
                gzNorm = gz / gLen
                val rollAlign = gxNorm * rxS + gzNorm * rzS
                if (rollAlign >= 0.72f) {
                    val t = ((abs(fy) - 0.72f) / (0.92f - 0.72f)).coerceIn(0f, 1f)
                    gravWeight = 1f - t * t * (3f - 2f * t)
                }
            }

            val mixRx = gravWeight * gxNorm + (1f - gravWeight) * rxS
            val mixRy = (1f - gravWeight) * ryS
            val mixRz = gravWeight * gzNorm + (1f - gravWeight) * rzS

            // Orthogonalize right vector against forward vector
            val proj = mixRx * fx + mixRy * fy + mixRz * fz
            var r0x = mixRx - proj * fx
            var r0y = mixRy - proj * fy
            var r0z = mixRz - proj * fz
            var r0Len = sqrt(r0x * r0x + r0y * r0y + r0z * r0z)
            if (r0Len < 1e-4f) {
                r0x = rxS
                r0y = ryS
                r0z = rzS
                r0Len = 1f
            }
            r0x /= r0Len
            r0y /= r0Len
            r0z /= r0Len

            // Up vector u0 = r0 x f (right-handed: X x (-Z) = +Y)
            var u0x = r0y * fz - r0z * fy
            var u0y = r0z * fx - r0x * fz
            var u0z = r0x * fy - r0y * fx
            val u0Len = sqrt(u0x * u0x + u0y * u0y + u0z * u0z).coerceAtLeast(1e-5f)
            u0x /= u0Len
            u0y /= u0Len
            u0z /= u0Len

            // Pack column-major world-to-bedRef matrix (rows are r0, u0, -f)
            outBedRefMat[0] = r0x; outBedRefMat[4] = r0y; outBedRefMat[8] = r0z; outBedRefMat[12] = 0f
            outBedRefMat[1] = u0x; outBedRefMat[5] = u0y; outBedRefMat[9] = u0z; outBedRefMat[13] = 0f
            outBedRefMat[2] = -fx; outBedRefMat[6] = -fy; outBedRefMat[10] = -fz; outBedRefMat[14] = 0f
            outBedRefMat[3] = 0f;  outBedRefMat[7] = 0f;  outBedRefMat[11] = 0f;  outBedRefMat[15] = 1f

            return Math.toDegrees(asin(fy.coerceIn(-1f, 1f)).toDouble()).toFloat().coerceIn(20f, 90f)
        }

        internal fun buildTiltedBedMatrix(yawRad: Float, tiltDeg: Float, outMat: FloatArray) {
            val rotX = FloatArray(16)
            val rotY = FloatArray(16)
            setRotateX(rotX, -Math.toRadians(tiltDeg.toDouble()).toFloat())
            setRotateY(rotY, yawRad)
            multiplyMM(outMat, rotX, rotY)
        }

        internal fun extractForward(viewMat: FloatArray, outForward: FloatArray) {
            val fx = -viewMat[2]
            val fy = -viewMat[6]
            val fz = -viewMat[10]
            val len = sqrt((fx * fx + fy * fy + fz * fz).toDouble()).toFloat().coerceAtLeast(1e-5f)
            outForward[0] = fx / len
            outForward[1] = fy / len
            outForward[2] = fz / len
        }

        private fun setIdentity(m: FloatArray) {
            for (i in 0..15) m[i] = 0f
            m[0] = 1f; m[5] = 1f; m[10] = 1f; m[15] = 1f
        }

        private fun setRotateX(m: FloatArray, rad: Float) {
            val c = cos(rad.toDouble()).toFloat()
            val s = sin(rad.toDouble()).toFloat()
            setIdentity(m)
            m[5] = c; m[6] = s
            m[9] = -s; m[10] = c
        }

        private fun setRotateY(m: FloatArray, rad: Float) {
            val c = cos(rad.toDouble()).toFloat()
            val s = sin(rad.toDouble()).toFloat()
            setIdentity(m)
            m[0] = c; m[2] = -s
            m[8] = s; m[10] = c
        }

        private fun multiplyMM(out: FloatArray, a: FloatArray, b: FloatArray) {
            for (c in 0..3) {
                val b0 = b[c * 4]
                val b1 = b[c * 4 + 1]
                val b2 = b[c * 4 + 2]
                val b3 = b[c * 4 + 3]
                out[c * 4] = a[0] * b0 + a[4] * b1 + a[8] * b2 + a[12] * b3
                out[c * 4 + 1] = a[1] * b0 + a[5] * b1 + a[9] * b2 + a[13] * b3
                out[c * 4 + 2] = a[2] * b0 + a[6] * b1 + a[10] * b2 + a[14] * b3
                out[c * 4 + 3] = a[3] * b0 + a[7] * b1 + a[11] * b2 + a[15] * b3
            }
        }

        private fun transpose3x3(src: FloatArray, out: FloatArray) {
            setIdentity(out)
            out[0] = src[0]; out[1] = src[4]; out[2] = src[8]
            out[4] = src[1]; out[5] = src[5]; out[6] = src[9]
            out[8] = src[2]; out[9] = src[6]; out[10] = src[10]
        }

        private fun slerpRotationMatrices(a: FloatArray, b: FloatArray, t: Float, out: FloatArray) {
            val qa = matrixToQuat(a)
            val qb = matrixToQuat(b)
            var dot = qa[0] * qb[0] + qa[1] * qb[1] + qa[2] * qb[2] + qa[3] * qb[3]
            if (dot < 0f) {
                qb[0] = -qb[0]; qb[1] = -qb[1]; qb[2] = -qb[2]; qb[3] = -qb[3]
                dot = -dot
            }

            val s0: Float
            val s1: Float
            if (dot > 0.9995f) {
                s0 = 1f - t
                s1 = t
            } else {
                val theta0 = acos(dot.coerceIn(-1f, 1f).toDouble())
                val sinTheta0 = sin(theta0)
                val theta = theta0 * t
                val sinTheta = sin(theta)
                s0 = (cos(theta) - dot * sinTheta / sinTheta0).toFloat()
                s1 = (sinTheta / sinTheta0).toFloat()
            }

            var w = s0 * qa[0] + s1 * qb[0]
            var x = s0 * qa[1] + s1 * qb[1]
            var y = s0 * qa[2] + s1 * qb[2]
            var z = s0 * qa[3] + s1 * qb[3]
            val norm = sqrt(w * w + x * x + y * y + z * z).coerceAtLeast(1e-6f)
            w /= norm; x /= norm; y /= norm; z /= norm

            quatToMatrix(w, x, y, z, out)
        }

        private fun matrixToQuat(m: FloatArray): FloatArray {
            val m00 = m[0]; val m10 = m[1]; val m20 = m[2]
            val m01 = m[4]; val m11 = m[5]; val m21 = m[6]
            val m02 = m[8]; val m12 = m[9]; val m22 = m[10]
            val trace = m00 + m11 + m22
            val w: Float; val x: Float; val y: Float; val z: Float
            if (trace > 0f) {
                val s = 2f * sqrt((1f + trace).coerceAtLeast(1e-6f))
                w = 0.25f * s
                x = (m21 - m12) / s
                y = (m02 - m20) / s
                z = (m10 - m01) / s
            } else if (m00 > m11 && m00 > m22) {
                val s = 2f * sqrt((1f + m00 - m11 - m22).coerceAtLeast(1e-6f))
                w = (m21 - m12) / s
                x = 0.25f * s
                y = (m01 + m10) / s
                z = (m02 + m20) / s
            } else if (m11 > m22) {
                val s = 2f * sqrt((1f + m11 - m00 - m22).coerceAtLeast(1e-6f))
                w = (m02 - m20) / s
                x = (m01 + m10) / s
                y = 0.25f * s
                z = (m12 + m21) / s
            } else {
                val s = 2f * sqrt((1f + m22 - m00 - m11).coerceAtLeast(1e-6f))
                w = (m10 - m01) / s
                x = (m02 + m20) / s
                y = (m12 + m21) / s
                z = 0.25f * s
            }
            return floatArrayOf(w, x, y, z)
        }

        private fun quatToMatrix(w: Float, x: Float, y: Float, z: Float, out: FloatArray) {
            val xx = x * x; val yy = y * y; val zz = z * z
            val xy = x * y; val xz = x * z; val yz = y * z
            val wx = w * x; val wy = w * y; val wz = w * z
            setIdentity(out)
            out[0] = 1f - 2f * (yy + zz)
            out[1] = 2f * (xy + wz)
            out[2] = 2f * (xz - wy)
            out[4] = 2f * (xy - wz)
            out[5] = 1f - 2f * (xx + zz)
            out[6] = 2f * (yz + wx)
            out[8] = 2f * (xz + wy)
            out[9] = 2f * (yz - wx)
            out[10] = 1f - 2f * (xx + yy)
        }

        private fun isAndroidEmulator(): Boolean {
            val fp = Build.FINGERPRINT.lowercase()
            val hw = Build.HARDWARE.lowercase()
            val prod = Build.PRODUCT.lowercase()
            return fp.contains("generic") ||
                fp.contains("emulator") ||
                hw.contains("ranchu") ||
                hw.contains("goldfish") ||
                prod.contains("sdk") ||
                Build.MODEL.lowercase().contains("emulator")
        }
    }
}
