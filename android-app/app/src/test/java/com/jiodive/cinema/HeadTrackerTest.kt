package com.jiodive.cinema

import android.view.Surface
import com.jiodive.cinema.vr.HeadTracker
import com.jiodive.cinema.vr.HeadTracker.HeadPoseCore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class HeadTrackerTest {
    @Test
    fun uprightLandscapeFacingNorthYieldsIdentityView() {
        val raw = floatArrayOf(
            0f, -1f, 0f, 0f,
            0f, 0f, -1f, 0f,
            1f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f,
        )
        val out = FloatArray(16)
        HeadTracker.computeGlViewFromWorldMatrix(raw, Surface.ROTATION_90, out)

        assertEquals(0f, -out[2], 1e-5f)
        assertEquals(0f, -out[6], 1e-5f)
        assertEquals(-1f, -out[10], 1e-5f)
        assertEquals(0f, out[1], 1e-5f)
        assertEquals(1f, out[5], 1e-5f)
        assertEquals(0f, out[9], 1e-5f)
    }

    @Test
    fun landscapeTurnedRight30DegreesFacesEastAndNorth() {
        val rad = Math.toRadians(30.0)
        val s = sin(rad).toFloat()
        val c = cos(rad).toFloat()
        // X_s = +Up; -Y_s = (c East, -s North); -Z_s = (s East, c North)
        val raw = floatArrayOf(
            0f, -c, -s, 0f,
            0f, s, -c, 0f,
            1f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f,
        )
        val out = FloatArray(16)
        HeadTracker.computeGlViewFromWorldMatrix(raw, Surface.ROTATION_90, out)

        // Forward = (-m[2], -m[6], -m[10]) = (+s East, 0 Up, -c South)
        assertEquals(s, -out[2], 1e-5f)
        assertEquals(0f, -out[6], 1e-5f)
        assertEquals(-c, -out[10], 1e-5f)
    }

    @Test
    fun bedModeReclinedHeadTurnHasZeroHorizonRoll() {
        val core = HeadPoseCore()
        core.setBedMode(enabled = true, defaultTiltDeg = 46f)

        // User starts upright facing North, then reclines 46 deg and turns 35 deg right around spine
        core.onSensorViewMatrix(buildGlSensorView(yawDeg = 0f, pitchDeg = 0f))
        assertEquals(46f, core.getEffectiveBedTiltDeg(), 0.5f)

        val reclinedCenter = buildSpineFirstGlSensorView(headingDeg = 0f, reclineDeg = 46f, headTurnRightDeg = 0f)
        core.onSensorViewMatrix(reclinedCenter)
        val view = FloatArray(16)
        val fwd = FloatArray(3)
        core.getViewRotationAndForward(view, fwd, dtSec = 0.016f)

        assertEquals(0f, fwd[0], 1e-4f)
        assertEquals(0f, fwd[1], 1e-4f)
        assertEquals(-1f, fwd[2], 1e-4f)
        assertEquals(1f, view[5], 1e-4f) // camera up aligned with virtual screen up

        // Turn head 35 deg right along the reclined pillow (around spine, NOT world gravity)
        val turnedRight = buildSpineFirstGlSensorView(headingDeg = 0f, reclineDeg = 46f, headTurnRightDeg = 35f)
        core.onSensorViewMatrix(turnedRight)
        core.getViewRotationAndForward(view, fwd, dtSec = 0.016f)

        // Pure horizontal yaw in virtual bed space, ZERO horizon roll
        val expectedSin = sin(Math.toRadians(35.0)).toFloat()
        val expectedCos = cos(Math.toRadians(35.0)).toFloat()
        assertEquals(expectedSin, fwd[0], 1e-4f)
        assertEquals(0f, fwd[1], 1e-4f)
        assertEquals(-expectedCos, fwd[2], 1e-4f)
        assertEquals(0f, view[4], 1e-4f) // xCam.y == 0 -> zero roll
        assertEquals(1f, view[5], 1e-4f) // yCam.y == 1 -> upright horizon
    }

    @Test
    fun bedModeFlatOnBackAt90AndPastZenithAvoidsGimbalFlip() {
        val core = HeadPoseCore()
        core.setBedMode(enabled = true, defaultTiltDeg = 46f)

        // User is already lying nearly flat (89 deg) when entering Bed Mode
        val flat89 = buildSpineFirstGlSensorView(headingDeg = 25f, reclineDeg = 89f, headTurnRightDeg = 0f)
        core.onSensorViewMatrix(flat89)
        assertEquals(89f, core.getEffectiveBedTiltDeg(), 1f)

        val view = FloatArray(16)
        val fwd = FloatArray(3)
        core.getViewRotationAndForward(view, fwd, dtSec = 0.016f)
        assertEquals(0f, fwd[0], 1e-4f)
        assertEquals(0f, fwd[1], 1e-4f)
        assertEquals(-1f, fwd[2], 1e-4f)

        // Tilting chin slightly back past zenith (94 deg) & recentering must NOT flip 180 degrees
        val pastZenith94 = buildSpineFirstGlSensorView(headingDeg = 25f, reclineDeg = 94f, headTurnRightDeg = 0f)
        core.onSensorViewMatrix(pastZenith94)
        core.recenter(calibrateBedPose = true)
        repeat(60) {
            core.getViewRotationAndForward(view, fwd, dtSec = 0.05f)
        }
        assertEquals(0f, fwd[0], 1e-4f)
        assertEquals(0f, fwd[1], 1e-4f)
        assertEquals(-1f, fwd[2], 1e-4f)
        assertTrue("Up vector must not flip upside down", view[5] > 0.99f)

        // Turning head 30 deg left on flat pillow still yields zero roll
        val turnedLeft = buildSpineFirstGlSensorView(headingDeg = 25f, reclineDeg = 94f, headTurnRightDeg = -30f)
        core.onSensorViewMatrix(turnedLeft)
        core.getViewRotationAndForward(view, fwd, dtSec = 0.016f)
        assertEquals(-sin(Math.toRadians(30.0)).toFloat(), fwd[0], 1e-3f)
        assertEquals(0f, fwd[1], 1e-3f)
        assertEquals(0f, view[4], 1e-3f)
    }

    @Test
    fun bedModeExitSmoothlyAlignsUprightHeading() {
        val core = HeadPoseCore()
        core.setBedMode(enabled = true, defaultTiltDeg = 46f)
        core.onSensorViewMatrix(buildGlSensorView(yawDeg = 35f, pitchDeg = 46f))

        // Exit Bed Mode while sitting back upright facing 35 deg East
        core.onSensorViewMatrix(buildGlSensorView(yawDeg = 35f, pitchDeg = 0f))
        core.setBedMode(enabled = false)

        val view = FloatArray(16)
        val fwd = FloatArray(3)
        repeat(60) {
            core.getViewRotationAndForward(view, fwd, dtSec = 0.05f)
        }
        assertEquals(0f, fwd[0], 1e-3f)
        assertEquals(0f, fwd[1], 1e-3f)
        assertEquals(-1f, fwd[2], 1e-3f)
        assertEquals(1f, view[5], 1e-3f)
    }

    @Test
    fun bedModeDockRecenterPreservesReclineAngleAndTouchDragHasNoRoll() {
        val core = HeadPoseCore()
        core.setBedMode(enabled = true, defaultTiltDeg = 46f)

        // Calibrate at 75 deg recline facing North
        val reclined75 = buildSpineFirstGlSensorView(headingDeg = 0f, reclineDeg = 75f, headTurnRightDeg = 0f)
        core.onSensorViewMatrix(reclined75)
        assertEquals(75f, core.getEffectiveBedTiltDeg(), 1f)

        // User turns body 40 deg East in bed and looks down -21 deg at the dock "Center" button
        val lookingAtDock = buildSpineFirstGlSensorView(headingDeg = 40f, reclineDeg = 54f, headTurnRightDeg = 0f)
        core.onSensorViewMatrix(lookingAtDock)
        core.recenter(calibrateBedPose = false)

        // Recline angle stays ~75 deg (does NOT drop down to 54 deg)
        assertEquals(75f, core.getEffectiveBedTiltDeg(), 1f)

        // Advance smoothing to settled state while looking back up at 75 deg facing 40 deg East
        val settled75East = buildSpineFirstGlSensorView(headingDeg = 40f, reclineDeg = 75f, headTurnRightDeg = 0f)
        core.onSensorViewMatrix(settled75East)
        val view = FloatArray(16)
        val fwd = FloatArray(3)
        repeat(60) {
            core.getViewRotationAndForward(view, fwd, dtSec = 0.05f)
        }
        assertEquals(0f, fwd[0], 1e-3f)
        assertEquals(0f, fwd[1], 1e-3f)
        assertEquals(-1f, fwd[2], 1e-3f)

        // Touch drag horizontally in Bed Mode at 75 deg must pan horizontally with zero roll
        core.addTouchDrag(deltaXRad = Math.toRadians(20.0).toFloat(), deltaYRad = 0f)
        core.getViewRotationAndForward(view, fwd, dtSec = 0.016f)
        assertEquals(0f, fwd[1], 1e-3f)
        assertEquals(0f, view[4], 1e-3f)
    }

    /** Upright world-Y yaw first, then pitch up by [pitchDeg] (world-to-camera matrix). */
    private fun buildGlSensorView(yawDeg: Float, pitchDeg: Float): FloatArray {
        val ry = rotY(Math.toRadians(yawDeg.toDouble()).toFloat())
        val rx = rotX(Math.toRadians(-pitchDeg.toDouble()).toFloat())
        return mul(rx, ry)
    }

    /** Recline to [reclineDeg] along [headingDeg], then rotate around head spine by [headTurnRightDeg]. */
    private fun buildSpineFirstGlSensorView(
        headingDeg: Float,
        reclineDeg: Float,
        headTurnRightDeg: Float,
    ): FloatArray {
        val base = buildGlSensorView(yawDeg = headingDeg, pitchDeg = reclineDeg)
        val spineTurn = rotY(Math.toRadians(headTurnRightDeg.toDouble()).toFloat())
        return mul(spineTurn, base)
    }

    private fun rotX(rad: Float): FloatArray {
        val c = cos(rad.toDouble()).toFloat()
        val s = sin(rad.toDouble()).toFloat()
        return floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, c, s, 0f,
            0f, -s, c, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    private fun rotY(rad: Float): FloatArray {
        val c = cos(rad.toDouble()).toFloat()
        val s = sin(rad.toDouble()).toFloat()
        return floatArrayOf(
            c, 0f, -s, 0f,
            0f, 1f, 0f, 0f,
            s, 0f, c, 0f,
            0f, 0f, 0f, 1f,
        )
    }

    private fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(16)
        for (c in 0..3) {
            for (r in 0..3) {
                out[c * 4 + r] = a[r] * b[c * 4] + a[4 + r] * b[c * 4 + 1] + a[8 + r] * b[c * 4 + 2] + a[12 + r] * b[c * 4 + 3]
            }
        }
        return out
    }
}
