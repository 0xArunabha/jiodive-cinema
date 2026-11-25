package com.jiodive.cinema

import android.view.Surface
import com.jiodive.cinema.vr.HeadTracker
import org.junit.Assert.assertEquals
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
}
