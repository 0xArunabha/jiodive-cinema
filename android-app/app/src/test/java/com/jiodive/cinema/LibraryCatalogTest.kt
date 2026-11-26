package com.jiodive.cinema

import com.jiodive.cinema.data.LibraryCatalog
import com.jiodive.cinema.data.LibraryEntryMeta
import com.jiodive.cinema.model.DurationFilterState
import com.jiodive.cinema.model.LibrarySortOrder
import com.jiodive.cinema.model.MediaCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCatalogTest {
    @Test
    fun formatBadgeRecognizesStreamProtocolsAndDeviceResolutions() {
        assertEquals("HLS", LibraryCatalog.formatBadge("https://cdn.example.com/live/master.m3u8?token=abc", isStream = true))
        assertEquals("HLS", LibraryCatalog.formatBadge("https://cdn.example.com/play?id=9&format=hls", isStream = true))
        assertEquals("DASH", LibraryCatalog.formatBadge("https://cdn.example.com/vod/manifest.mpd", isStream = true))
        assertEquals("MKV", LibraryCatalog.formatBadge("https://cdn.example.com/movie.mkv", isStream = true))
        assertEquals("MP4", LibraryCatalog.formatBadge("https://cdn.example.com/oceans.mp4", isStream = true))
        assertEquals("MOV", LibraryCatalog.formatBadge("https://cdn.example.com/trailer.mov", isStream = true))

        assertEquals("4K", LibraryCatalog.formatBadge("file:///movie.mp4", isStream = false, width = 3840, height = 2160))
        assertEquals("1080p", LibraryCatalog.formatBadge("file:///movie.mp4", isStream = false, width = 1920, height = 1080))
        assertEquals("720p", LibraryCatalog.formatBadge("file:///movie.mp4", isStream = false, width = 1280, height = 720))
        assertEquals("TS", LibraryCatalog.formatBadge("file:///clip.ts", isStream = false))
        assertEquals("AVI", LibraryCatalog.formatBadge("file:///clip.avi", isStream = false))
    }

    @Test
    fun formatSubtitleOffsetFormatsZeroPositiveAndNegativeValues() {
        assertEquals("0.0s (Synced)", LibraryCatalog.formatSubtitleOffset(0L))
        assertEquals("+1.5s (Later)", LibraryCatalog.formatSubtitleOffset(1_500L))
        assertEquals("-0.8s (Earlier)", LibraryCatalog.formatSubtitleOffset(-800L))
    }

    @Test
    fun hostAndTitleExtractionProducesReadableLabels() {
        assertEquals("vjs.zencdn.net", LibraryCatalog.extractHostLabel("https://www.vjs.zencdn.net/v/oceans.mp4?hd=1"))
        assertEquals("big buck bunny", LibraryCatalog.suggestStreamTitle("https://cdn.example.com/movies/big_buck-bunny.mp4?t=2"))
    }

    @Test
    fun filterAndSortSupportsFoldersAndAllSortOrders() {
        val a = LibraryEntryMeta(
            id = "a",
            uriString = "content://media/a",
            title = "Zebra Doc",
            durationMs = 600_000L,
            width = 1920,
            height = 1080,
            isStream = false,
            isDemo = false,
            folderName = "Movies",
            addedAtMs = 300L,
        )
        val b = LibraryEntryMeta(
            id = "b",
            uriString = "content://media/b",
            title = "Alpha Short",
            durationMs = 120_000L,
            width = 1280,
            height = 720,
            isStream = false,
            isDemo = false,
            folderName = "Download",
            addedAtMs = 500L,
        )
        val c = LibraryEntryMeta(
            id = "c",
            uriString = "https://cdn.example.com/epic.m3u8",
            title = "Epic Feature",
            durationMs = 5_400_000L,
            width = 1920,
            height = 1080,
            isStream = true,
            isDemo = false,
            folderName = "",
            addedAtMs = 100L,
        )
        val all = listOf(a, b, c)

        assertEquals(listOf("Download" to 1, "Movies" to 1), LibraryCatalog.extractFoldersFromMeta(all))

        assertEquals(
            listOf("b", "a", "c"),
            LibraryCatalog.filterAndSortMeta(all, MediaCategory.ALL, "", DurationFilterState(), null, LibrarySortOrder.RECENT).map { it.id },
        )
        assertEquals(
            listOf("c", "a", "b"),
            LibraryCatalog.filterAndSortMeta(all, MediaCategory.ALL, "", DurationFilterState(), null, LibrarySortOrder.LONGEST).map { it.id },
        )
        assertEquals(
            listOf("b", "c", "a"),
            LibraryCatalog.filterAndSortMeta(all, MediaCategory.ALL, "", DurationFilterState(), null, LibrarySortOrder.TITLE_AZ).map { it.id },
        )
        assertEquals(
            listOf("a"),
            LibraryCatalog.filterAndSortMeta(all, MediaCategory.ALL, "", DurationFilterState(), "Movies", LibrarySortOrder.RECENT).map { it.id },
        )
    }
}
