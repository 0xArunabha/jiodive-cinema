package com.jiodive.cinema.vr

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.Matrix
import android.os.Build
import android.view.Surface
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Low-latency 3DoF IMU head tracker calibrated for landscape phone-in-headset VR.
 * Handles both Surface.ROTATION_90 and Surface.ROTATION_270 (whichever way JioDive latches),
 * supports yaw recentering and touch-drag look, and safely bypasses static table-flat
 * emulator virtual sensors so VR starts squarely facing the theater screen.
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

    private val lock = Any()
    private val rawRotRowMajor = FloatArray(16)
    private val glCamFromWorld = FloatArray(16)

    @Volatile
    var displayRotation: Int = Surface.ROTATION_90

    private var hasSensorData = false
    private var yawOffsetRad = 0f
    private var latestSensorYawRad = 0f
    private var touchYawRad = 0f
    private var touchPitchRad = 0f
    private var bedModePitchOffsetRad = 0f

    init {
        Matrix.setIdentityM(glCamFromWorld, 0)
    }

    fun start() {
        rotationSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    fun setBedMode(enabled: Boolean) {
        synchronized(lock) {
            bedModePitchOffsetRad = if (enabled) Math.toRadians(-46.0).toFloat() else 0f
        }
    }

    fun recenter() {
        synchronized(lock) {
            yawOffsetRad = latestSensorYawRad
            touchYawRad = 0f
            touchPitchRad = 0f
        }
    }

    fun addTouchDrag(deltaXRad: Float, deltaYRad: Float) {
        synchronized(lock) {
            touchYawRad += deltaXRad
            touchPitchRad = (touchPitchRad + deltaYRad).coerceIn(-1.25f, 1.25f)
        }
    }

    /**
     * Populates a column-major 4x4 camera rotation matrix (world-to-camera / view orientation)
     * plus extracts the forward look unit vector `(fx, fy, fz)` in GL world space.
     */
    fun getViewRotationAndForward(outViewMat: FloatArray, outForward: FloatArray) {
        val yawAdj: Float
        val pitchAdj: Float
        val camMatCopy = FloatArray(16)
        synchronized(lock) {
            yawAdj = yawOffsetRad + touchYawRad
            pitchAdj = touchPitchRad + bedModePitchOffsetRad
            if (hasSensorData) {
                System.arraycopy(glCamFromWorld, 0, camMatCopy, 0, 16)
            } else {
                Matrix.setIdentityM(camMatCopy, 0)
            }
        }

        val tmp = FloatArray(16)
        val yawMat = FloatArray(16)
        val pitchMat = FloatArray(16)
        Matrix.setRotateM(yawMat, 0, Math.toDegrees((-yawAdj).toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.setRotateM(pitchMat, 0, Math.toDegrees((-pitchAdj).toDouble()).toFloat(), 1f, 0f, 0f)

        Matrix.multiplyMM(tmp, 0, camMatCopy, 0, yawMat, 0)
        Matrix.multiplyMM(outViewMat, 0, pitchMat, 0, tmp, 0)

        val fx = -outViewMat[2]
        val fy = -outViewMat[6]
        val fz = -outViewMat[10]
        val len = sqrt((fx * fx + fy * fy + fz * fz).toDouble()).toFloat().coerceAtLeast(1e-5f)
        outForward[0] = fx / len
        outForward[1] = fy / len
        outForward[2] = fz / len
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(rawRotRowMajor, event.values)
        val computed = FloatArray(16)
        computeGlViewFromWorldMatrix(rawRotRowMajor, displayRotation, computed)

        synchronized(lock) {
            System.arraycopy(computed, 0, glCamFromWorld, 0, 16)
            val fx = -glCamFromWorld[2]
            val fz = -glCamFromWorld[10]
            latestSensorYawRad = atan2(fx.toDouble(), (-fz).toDouble()).toFloat()
            if (!hasSensorData) {
                yawOffsetRad = latestSensorYawRad
                hasSensorData = true
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

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
            // Col0 (+X_sensor) = (raw[0], raw[8], -raw[4])
            // Col1 (+Y_sensor) = (raw[1], raw[9], -raw[5])
            // Col2 (+Z_sensor) = (raw[2], raw[10], -raw[6])
            val xCamX = -s * rawRotRowMajor[1]
            val xCamY = -s * rawRotRowMajor[9]
            val xCamZ = s * rawRotRowMajor[5]

            val yCamX = s * rawRotRowMajor[0]
            val yCamY = s * rawRotRowMajor[8]
            val yCamZ = -s * rawRotRowMajor[4]

            val zCamX = rawRotRowMajor[2]
            val zCamY = rawRotRowMajor[10]
            val zCamZ = -rawRotRowMajor[6]

            // Row 0 = xCam, Row 1 = yCam, Row 2 = zCam in column-major OpenGL layout (m[col * 4 + row])
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
