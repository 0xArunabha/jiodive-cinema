package com.jiodive.cinema

import com.jiodive.cinema.model.DurationFilterPreset
import com.jiodive.cinema.model.DurationFilterState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DurationFilterTest {
    @Test
    fun presetThresholdsMatch30m1hAnd2hMoviesAccurately() {
        val thirtyMinPlus = DurationFilterState(preset = DurationFilterPreset.MIN_30M)
        assertFalse(thirtyMinPlus.matches(0L))
        assertFalse(thirtyMinPlus.matches(29L * 60_000L))
        assertTrue(thirtyMinPlus.matches(30L * 60_000L))
        assertTrue(thirtyMinPlus.matches(95L * 60_000L))

        val oneHourPlus = DurationFilterState(preset = DurationFilterPreset.MIN_1H)
        assertFalse(oneHourPlus.matches(0L))
        assertFalse(oneHourPlus.matches(59L * 60_000L))
        assertTrue(oneHourPlus.matches(60L * 60_000L))
        assertTrue(oneHourPlus.matches(135L * 60_000L))

        val twoHourPlus = DurationFilterState(preset = DurationFilterPreset.MIN_2H)
        assertFalse(twoHourPlus.matches(119L * 60_000L))
        assertTrue(twoHourPlus.matches(120L * 60_000L))
        assertTrue(twoHourPlus.matches(180L * 60_000L))
    }

    @Test
    fun customRangeHonorsBothBoundedAndOpenEndedRanges() {
        val bounded = DurationFilterState(
            preset = DurationFilterPreset.CUSTOM,
            customMinMinutes = 45,
            customMaxMinutes = 90,
        )
        assertFalse(bounded.matches(44L * 60_000L))
        assertTrue(bounded.matches(45L * 60_000L))
        assertTrue(bounded.matches(90L * 60_000L))
        assertFalse(bounded.matches(91L * 60_000L))

        val openEnded = DurationFilterState(
            preset = DurationFilterPreset.CUSTOM,
            customMinMinutes = 90,
            customMaxMinutes = null,
        )
        assertFalse(openEnded.matches(89L * 60_000L))
        assertTrue(openEnded.matches(90L * 60_000L))
        assertTrue(openEnded.matches(240L * 60_000L))
    }
}
