package com.jiodive.cinema

import com.jiodive.cinema.data.SrtVttParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SrtVttParserTest {
    @Test
    fun parsesSrtAndFindsActiveCue() {
        val sample = """
            1
            00:00:01,500 --> 00:00:04,000
            <i>Welcome to JioDive</i>

            2
            00:00:05,000 --> 00:00:08,250
            Curved IMAX theater mode
        """.trimIndent()

        val cues = SrtVttParser.parse(sample)
        assertEquals(2, cues.size)
        assertEquals("Welcome to JioDive", SrtVttParser.activeCueAt(cues, 2000L))
        assertNull(SrtVttParser.activeCueAt(cues, 4500L))
        assertEquals("Curved IMAX theater mode", SrtVttParser.activeCueAt(cues, 6100L))
    }

    @Test
    fun parsesWebVttWithHeaderAndSettings() {
        val sample = """
            WEBVTT

            00:02.000 --> 00:05.500 align:center
            Dive right in
        """.trimIndent()

        val cues = SrtVttParser.parse(sample)
        assertEquals(1, cues.size)
        assertEquals("Dive right in", SrtVttParser.activeCueAt(cues, 3000L))
    }
}
