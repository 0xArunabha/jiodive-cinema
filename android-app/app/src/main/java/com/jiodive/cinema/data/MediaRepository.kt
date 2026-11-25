package com.jiodive.cinema.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import com.jiodive.cinema.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaRepository {
    val builtInDemos: List<MediaItem> = listOf(
        MediaItem(
            id = "demo_oceans",
            uri = Uri.parse("https://vjs.zencdn.net/v/oceans.mp4"),
            title = "Oceans HD Sample",
            durationMs = 46_000L,
            width = 960,
            height = 400,
            sizeBytes = 0L,
            isStream = true,
            isDemo = true,
        ),
        MediaItem(
            id = "demo_escapes",
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"),
            title = "For Bigger Escapes (HD)",
            durationMs = 15_000L,
            width = 1280,
            height = 720,
            sizeBytes = 0L,
            isStream = true,
            isDemo = true,
        ),
    )

    suspend fun queryDeviceVideos(context: Context, limit: Int = 60): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.SIZE,
            )
            val results = ArrayList<MediaItem>()
            runCatching {
                resolver.query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Video.Media.DATE_ADDED} DESC",
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                    val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                    val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)

                    while (cursor.moveToNext() && results.size < limit) {
                        val id = cursor.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id,
                        )
                        val name = cursor.getString(nameCol) ?: "Video $id"
                        val thumb = loadThumbnailSafe(context, resolver, contentUri)
                        results.add(
                            MediaItem(
                                id = "ms_$id",
                                uri = contentUri,
                                title = name.substringBeforeLast('.'),
                                durationMs = cursor.getLong(durCol).coerceAtLeast(0L),
                                width = cursor.getInt(widthCol).coerceAtLeast(0),
                                height = cursor.getInt(heightCol).coerceAtLeast(0),
                                sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L),
                                isStream = false,
                                isDemo = false,
                                thumbnail = thumb,
                            ),
                        )
                    }
                }
            }
            results
        }

    suspend fun inspectPickedVideo(context: Context, uri: Uri): MediaItem =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            var displayName = "Picked Video"
            var sizeBytes = 0L

            runCatching {
                resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                    ?.use { c ->
                        if (c.moveToFirst()) {
                            val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                            if (nameIdx >= 0) displayName = c.getString(nameIdx) ?: displayName
                            if (sizeIdx >= 0) sizeBytes = c.getLong(sizeIdx)
                        }
                    }
            }

            var durationMs = 0L
            var width = 0
            var height = 0
            var thumb: Bitmap? = null

            val retriever = MediaMetadataRetriever()
            runCatching {
                retriever.setDataSource(context, uri)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                thumb = retriever.getFrameAtTime(1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            runCatching { retriever.release() }

            if (thumb == null) {
                thumb = loadThumbnailSafe(context, resolver, uri)
            }

            MediaItem(
                id = "picked_${uri.hashCode()}",
                uri = uri,
                title = displayName.substringBeforeLast('.'),
                durationMs = durationMs,
                width = width,
                height = height,
                sizeBytes = sizeBytes,
                isStream = false,
                isDemo = false,
                thumbnail = thumb,
            )
        }

    suspend fun readTextFromUri(context: Context, uri: Uri): Pair<String, String?> =
        withContext(Dispatchers.IO) {
            var name = "Subtitles"
            runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) name = c.getString(idx) ?: name
                    }
                }
            }
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            name to text
        }

    private fun loadThumbnailSafe(
        context: Context,
        resolver: ContentResolver,
        uri: Uri,
    ): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val loaded = runCatching { resolver.loadThumbnail(uri, Size(480, 270), null) }.getOrNull()
            if (loaded != null) return loaded
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "--:--"
        val totalSec = ms / 1000L
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return if (hours > 0L) {
            String.format("%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }
}
