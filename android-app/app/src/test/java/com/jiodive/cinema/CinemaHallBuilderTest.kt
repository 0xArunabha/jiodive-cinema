package com.jiodive.cinema

import com.jiodive.cinema.model.ScreenPreset
import com.jiodive.cinema.vr.CinemaHallBuilder
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaHallBuilderTest {
    @Test
    fun hallVerticesValidForAllScreenPresets() {
        for (preset in ScreenPreset.entries) {
            val verts = CinemaHallBuilder.buildHallVertices(preset, 16f / 9f)
            assertTrue("Expected rich hall geometry for $preset", verts.size >= 1000 * CinemaHallBuilder.FLOATS_PER_VERTEX)
            assertTrue("Vertex array must be exact multiple of stride", verts.size % CinemaHallBuilder.FLOATS_PER_VERTEX == 0)
            for (v in verts) {
                assertTrue("Vertex attribute must be finite", v.isFinite())
            }
        }
    }
}
