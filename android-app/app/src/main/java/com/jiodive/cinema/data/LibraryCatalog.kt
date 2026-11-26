package com.jiodive.cinema.data

import android.graphics.Bitmap
import com.jiodive.cinema.model.DurationFilterState
import com.jiodive.cinema.model.LibrarySortOrder
import com.jiodive.cinema.model.MediaCategory
import com.jiodive.cinema.model.MediaItem
import java.util.Locale

sealed interface ThumbnailResult {
    data object Loading : ThumbnailResult
    data class Loaded(val bitmap: Bitmap) : ThumbnailResult
    data object Unavailable : ThumbnailResult
}

data class LibraryEntryMeta(
    val id: String,
    val uriString: String,
    val title: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val isStream: Boolean,
    val isDemo: Boolean,
    val folderName: String,
    val addedAtMs: Long,
)

object LibraryCatalog {
    fun formatBadge(
        pathOrUrl: String,
        isStream: Boolean,
        width: Int = 0,
        height: Int = 0,
    ): String {
        val fullLower = pathOrUrl.lowercase(Locale.ROOT)
        val clean = fullLower.substringBefore('?').substringBefore('#')
        if (isStream) {
            return when {
                clean.endsWith(".m3u8") || fullLower.contains(".m3u8") || fullLower.contains("format=hls") -> "HLS"
                clean.endsWith(".mpd") || fullLower.contains(".mpd") || fullLower.contains("format=dash") -> "DASH"
                clean.endsWith(".mkv") || fullLower.contains(".mkv") -> "MKV"
                clean.endsWith(".webm") || fullLower.contains(".webm") -> "WEBM"
                clean.endsWith(".mp4") || clean.endsWith(".m4v") || fullLower.contains(".mp4") -> "MP4"
                clean.endsWith(".mov") || fullLower.contains(".mov") -> "MOV"
                else -> "STREAM"
            }
        }
        return when {
            height >= 2100 || width >= 3800 -> "4K"
            height >= 1000 || width >= 1900 -> "1080p"
            height >= 700 || width >= 1200 -> "720p"
            clean.endsWith(".mkv") -> "MKV"
            clean.endsWith(".webm") -> "WEBM"
            clean.endsWith(".mov") -> "MOV"
            clean.endsWith(".ts") || clean.endsWith(".m2ts") -> "TS"
            clean.endsWith(".avi") -> "AVI"
            clean.endsWith(".mp4") || clean.endsWith(".m4v") -> "MP4"
            else -> "LOCAL"
        }
    }

    fun extractHostLabel(urlString: String): String {
        val afterScheme = if (urlString.contains("://")) {
            urlString.substringAfter("://")
        } else {
            urlString
        }
        val authority = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
        val hostOnly = authority.substringAfter('@').substringBefore(':').lowercase(Locale.ROOT)
        return hostOnly.removePrefix("www.").ifBlank { "stream" }
    }

    fun suggestStreamTitle(urlString: String): String {
        val cleanPath = urlString.substringBefore('?').substringBefore('#').trimEnd('/')
        val segment = cleanPath.substringAfterLast('/', "")
        val base = segment.substringBeforeLast('.', segment).replace('_', ' ').replace('-', ' ').trim()
        return if (base.length >= 2 && !base.equals("playlist", ignoreCase = true) && !base.equals("index", ignoreCase = true)) {
            base
        } else {
            extractHostLabel(urlString)
        }
    }

    fun extractFolders(items: List<MediaItem>): List<Pair<String, Int>> =
        extractFoldersGeneric(items) { it.folderName }

    fun extractFoldersFromMeta(items: List<LibraryEntryMeta>): List<Pair<String, Int>> =
        extractFoldersGeneric(items) { it.folderName }

    private inline fun <T> extractFoldersGeneric(
        items: List<T>,
        crossinline folderName: (T) -> String,
    ): List<Pair<String, Int>> {
        val counts = LinkedHashMap<String, Int>()
        for (item in items) {
            val folder = folderName(item).trim()
            if (folder.isNotEmpty()) {
                counts[folder] = (counts[folder] ?: 0) + 1
            }
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key.lowercase(Locale.ROOT) })
            .map { it.key to it.value }
    }

    fun filterAndSort(
        items: List<MediaItem>,
        category: MediaCategory,
        searchQuery: String,
        durationFilter: DurationFilterState,
        selectedFolder: String?,
        sortOrder: LibrarySortOrder,
    ): List<MediaItem> =
        filterAndSortGeneric(
            items = items,
            category = category,
            searchQuery = searchQuery,
            durationFilter = durationFilter,
            selectedFolder = selectedFolder,
            sortOrder = sortOrder,
            isStream = { it.isStream },
            folderName = { it.folderName },
            title = { it.title },
            uriString = { it.uri.toString() },
            durationMs = { it.durationMs },
            addedAtMs = { it.addedAtMs },
            isDemo = { it.isDemo },
        )

    fun filterAndSortMeta(
        items: List<LibraryEntryMeta>,
        category: MediaCategory,
        searchQuery: String,
        durationFilter: DurationFilterState,
        selectedFolder: String?,
        sortOrder: LibrarySortOrder,
    ): List<LibraryEntryMeta> =
        filterAndSortGeneric(
            items = items,
            category = category,
            searchQuery = searchQuery,
            durationFilter = durationFilter,
            selectedFolder = selectedFolder,
            sortOrder = sortOrder,
            isStream = { it.isStream },
            folderName = { it.folderName },
            title = { it.title },
            uriString = { it.uriString },
            durationMs = { it.durationMs },
            addedAtMs = { it.addedAtMs },
            isDemo = { it.isDemo },
        )

    private inline fun <T> filterAndSortGeneric(
        items: List<T>,
        category: MediaCategory,
        searchQuery: String,
        durationFilter: DurationFilterState,
        selectedFolder: String?,
        sortOrder: LibrarySortOrder,
        crossinline isStream: (T) -> Boolean,
        crossinline folderName: (T) -> String,
        crossinline title: (T) -> String,
        crossinline uriString: (T) -> String,
        crossinline durationMs: (T) -> Long,
        crossinline addedAtMs: (T) -> Long,
        crossinline isDemo: (T) -> Boolean,
    ): List<T> {
        val trimmedQuery = searchQuery.trim()
        val filtered = ArrayList<T>(items.size)
        for (item in items) {
            val stream = isStream(item)
            val catOk = when (category) {
                MediaCategory.ALL -> true
                MediaCategory.DEVICE -> !stream
                MediaCategory.STREAMS -> stream
            }
            if (!catOk) continue
            val folder = folderName(item)
            val folderOk = selectedFolder.isNullOrBlank() ||
                folder.equals(selectedFolder, ignoreCase = true)
            if (!folderOk) continue
            if (!durationFilter.matches(durationMs(item))) continue
            val queryOk = trimmedQuery.isEmpty() ||
                title(item).contains(trimmedQuery, ignoreCase = true) ||
                folder.contains(trimmedQuery, ignoreCase = true) ||
                (stream && uriString(item).contains(trimmedQuery, ignoreCase = true))
            if (queryOk) filtered.add(item)
        }

        return when (sortOrder) {
            LibrarySortOrder.RECENT -> filtered.sortedWith(
                compareByDescending<T> { addedAtMs(it) }
                    .thenBy { isDemo(it) }
                    .thenBy { title(it).lowercase(Locale.ROOT) },
            )
            LibrarySortOrder.LONGEST -> filtered.sortedWith(
                compareByDescending<T> { durationMs(it) }
                    .thenByDescending { addedAtMs(it) }
                    .thenBy { title(it).lowercase(Locale.ROOT) },
            )
            LibrarySortOrder.TITLE_AZ -> filtered.sortedBy { title(it).lowercase(Locale.ROOT) }
        }
    }

    fun formatSubtitleOffset(offsetMs: Long): String {
        val roundedSec = offsetMs / 1000.0
        return when {
            offsetMs == 0L -> "0.0s (Synced)"
            offsetMs > 0L -> String.format(Locale.US, "+%.1fs (Later)", roundedSec)
            else -> String.format(Locale.US, "%.1fs (Earlier)", roundedSec)
        }
    }
}
