package com.jiodive.cinema.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import android.util.Size
import com.jiodive.cinema.model.MediaItem
import com.jiodive.cinema.model.SavedMediaEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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
            addedAtMs = 2L,
        ),
        MediaItem(
            id = "demo_escapes",
            uri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"),
            title = "For Bigger Escapes",
            durationMs = 15_000L,
            width = 1280,
            height = 720,
            sizeBytes = 0L,
            isStream = true,
            isDemo = true,
            addedAtMs = 1L,
        ),
    )

    // Byte-budgeted in-memory LRU cache (~20 MB -> ~100+ HD video thumbnails without OOM risk).
    private val thumbCache = object : LruCache<String, Bitmap>(20 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    private val thumbDispatcher = Dispatchers.IO.limitedParallelism(4)
    private val thumbScope = CoroutineScope(SupervisorJob() + thumbDispatcher)
    private val inFlightThumbs = ConcurrentHashMap<String, Deferred<Bitmap?>>()
    private val failedThumbs = ConcurrentHashMap<String, Boolean>()
    private val _thumbnailUpdates = MutableStateFlow(0L)
    val thumbnailUpdates: StateFlow<Long> = _thumbnailUpdates.asStateFlow()

    fun getCachedThumbnail(cacheKey: String): Bitmap? {
        val bmp = thumbCache.get(cacheKey)
        return if (bmp != null && !bmp.isRecycled) bmp else null
    }

    fun hasThumbnailFailed(cacheKey: String): Boolean = failedThumbs.containsKey(cacheKey)

    suspend fun loadThumbnailForItem(context: Context, item: MediaItem): Bitmap? =
        loadThumbnailByUri(
            context = context,
            cacheKey = item.id,
            uri = item.uri,
            isStream = item.isStream,
            fallback = item.thumbnail,
        )

    suspend fun loadThumbnailStateByUri(
        context: Context,
        cacheKey: String,
        uri: Uri,
        isStream: Boolean,
        fallback: Bitmap? = null,
        forceRetry: Boolean = false,
    ): ThumbnailResult {
        val bmp = loadThumbnailByUri(
            context = context,
            cacheKey = cacheKey,
            uri = uri,
            isStream = isStream,
            fallback = fallback,
            forceRetry = forceRetry,
        )
        return if (bmp != null && !bmp.isRecycled) {
            ThumbnailResult.Loaded(bmp)
        } else {
            ThumbnailResult.Unavailable
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun loadThumbnailByUri(
        context: Context,
        cacheKey: String,
        uri: Uri,
        isStream: Boolean,
        fallback: Bitmap? = null,
        forceRetry: Boolean = false,
    ): Bitmap? {
        getCachedThumbnail(cacheKey)?.let { return it }
        if (fallback != null && !fallback.isRecycled) {
            thumbCache.put(cacheKey, fallback)
            failedThumbs.remove(cacheKey)
            return fallback
        }
        if (forceRetry) {
            failedThumbs.remove(cacheKey)
        } else if (failedThumbs.containsKey(cacheKey)) {
            return null
        }

        val appCtx = context.applicationContext
        val deferred = inFlightThumbs.computeIfAbsent(cacheKey) {
            thumbScope.async {
                val timeoutMs = when {
                    isStream && LibraryCatalog.formatBadge(uri.toString(), isStream = true) in listOf("HLS", "DASH") -> 4_500L
                    isStream -> 7_000L
                    else -> 4_000L
                }
                val decodeJob = thumbScope.async {
                    if (isStream) {
                        fetchStreamThumbnail(appCtx, uri)
                    } else {
                        loadThumbnailSafe(appCtx, appCtx.contentResolver, uri)
                    }
                }
                val decoded = withTimeoutOrNull(timeoutMs) { decodeJob.await() }
                if (decoded != null && !decoded.isRecycled) {
                    failedThumbs.remove(cacheKey)
                    thumbCache.put(cacheKey, decoded)
                    _thumbnailUpdates.update { it + 1L }
                } else {
                    failedThumbs[cacheKey] = true
                    if (isStream && decoded == null) {
                        // Broadcast live cache promotion if slow background decode completes cleanly afterward
                        decodeJob.invokeOnCompletion { err ->
                            if (err == null) {
                                val late = runCatching { decodeJob.getCompleted() }.getOrNull()
                                if (late != null && !late.isRecycled) {
                                    failedThumbs.remove(cacheKey)
                                    thumbCache.put(cacheKey, late)
                                    _thumbnailUpdates.update { it + 1L }
                                }
                            }
                        }
                    }
                }
                inFlightThumbs.remove(cacheKey)
                decoded
            }
        }
        return deferred.await()
    }

    private fun streamThumbFile(context: Context, uri: Uri): File {
        val cacheDir = File(context.cacheDir, "stream_thumbs").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray(Charsets.UTF_8))
        val hex = digest.take(12).joinToString("") { "%02x".format(Locale.ROOT, it) }
        return File(cacheDir, "v4_$hex.jpg")
    }

    /**
     * Fetches and disk-caches a real video frame for network MP4 demos / streams so the
     * launcher displays actual movie stills rather than placeholder blocks.
     */
    suspend fun fetchStreamThumbnail(context: Context, uri: Uri): Bitmap? =
        withContext(thumbDispatcher) {
            val cacheFile = streamThumbFile(context, uri)
            if (cacheFile.exists() && cacheFile.length() > 1024L) {
                runCatching { BitmapFactory.decodeFile(cacheFile.absolutePath) }.getOrNull()?.let { cached ->
                    if (!isFrameMostlyBlack(cached)) return@withContext cached
                    cached.recycle()
                }
            }

            val headers = hashMapOf(
                "User-Agent" to "Mozilla/5.0 (Linux; Android 14) JioDiveCinema/1.0",
            )
            val retriever = MediaMetadataRetriever()
            var frame: Bitmap? = null
            try {
                runCatching {
                    retriever.setDataSource(uri.toString(), headers)
                    // OPTION_CLOSEST_SYNC jumps straight to keyframe timestamps over HTTP without decoding every intermediate frame
                    val probeTimesUs = longArrayOf(5_000_000L, 2_200_000L, 10_000_000L)
                    for (tUs in probeTimesUs) {
                        val candidate = retriever.getScaledFrameAtTime(
                            tUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            720,
                            405,
                        )
                        if (candidate != null) {
                            if (frame != null && frame !== candidate && !frame.isRecycled) {
                                frame.recycle()
                            }
                            frame = candidate
                            if (!isFrameMostlyBlack(candidate)) break
                        }
                    }
                }
            } finally {
                runCatching { retriever.release() }
            }

            val bmp = frame ?: return@withContext null
            val scaled = if (bmp.width > 720) {
                val targetH = (bmp.height * (720f / bmp.width)).toInt().coerceAtLeast(180)
                Bitmap.createScaledBitmap(bmp, 720, targetH, true).also {
                    if (it !== bmp) bmp.recycle()
                }
            } else {
                bmp
            }

            if (!isFrameMostlyBlack(scaled)) {
                runCatching {
                    FileOutputStream(cacheFile).use { fos ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 86, fos)
                    }
                }
            }
            scaled
        }

    /**
     * Queries MediaStore video metadata only (no synchronous thumbnail decoding in the loop),
     * so even libraries with hundreds of videos load instantaneously.
     */
    suspend fun queryDeviceVideos(context: Context, limit: Int = 500): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val projection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.DATE_ADDED,
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
                    val bucketCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                    val addedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)

                    while (cursor.moveToNext() && results.size < limit) {
                        val id = cursor.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                            id,
                        )
                        val name = cursor.getString(nameCol) ?: "Video $id"
                        val bucket = if (bucketCol >= 0) cursor.getString(bucketCol).orEmpty().trim() else ""
                        val addedMs = if (addedCol >= 0) cursor.getLong(addedCol).coerceAtLeast(0L) * 1000L else 0L
                        val mediaId = "ms_$id"
                        results.add(
                            MediaItem(
                                id = mediaId,
                                uri = contentUri,
                                title = name.substringBeforeLast('.'),
                                durationMs = cursor.getLong(durCol).coerceAtLeast(0L),
                                width = cursor.getInt(widthCol).coerceAtLeast(0),
                                height = cursor.getInt(heightCol).coerceAtLeast(0),
                                sizeBytes = cursor.getLong(sizeCol).coerceAtLeast(0L),
                                isStream = false,
                                isDemo = false,
                                folderName = bucket.ifBlank { "Device" },
                                addedAtMs = addedMs,
                                thumbnail = getCachedThumbnail(mediaId),
                            ),
                        )
                    }
                }
            }
            results
        }

    suspend fun restoreSavedCustomMedia(
        context: Context,
        saved: List<SavedMediaEntry>,
    ): List<MediaItem> =
        withContext(Dispatchers.IO) {
            val resolver = context.contentResolver
            val restored = ArrayList<MediaItem>(saved.size)
            for (entry in saved) {
                val uri = Uri.parse(entry.uriString)
                if (!entry.isStream && uri.scheme == ContentResolver.SCHEME_CONTENT) {
                    val stillAccessible = runCatching {
                        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                            c.moveToFirst()
                        } ?: false
                    }.getOrDefault(false)
                    if (!stillAccessible) continue
                }
                restored.add(
                    MediaItem(
                        id = entry.id,
                        uri = uri,
                        title = entry.title,
                        durationMs = entry.durationMs,
                        width = entry.width,
                        height = entry.height,
                        sizeBytes = entry.sizeBytes,
                        isStream = entry.isStream,
                        isDemo = false,
                        folderName = entry.folderName,
                        addedAtMs = entry.addedAtMs,
                        thumbnail = getCachedThumbnail(entry.id),
                    ),
                )
            }
            restored
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

            val retriever = MediaMetadataRetriever()
            runCatching {
                retriever.setDataSource(context, uri)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            }
            runCatching { retriever.release() }

            val mediaId = "picked_${uri.hashCode()}"
            val thumb = loadThumbnailSafe(context, resolver, uri)?.also {
                thumbCache.put(mediaId, it)
            }

            MediaItem(
                id = mediaId,
                uri = uri,
                title = displayName.substringBeforeLast('.'),
                durationMs = durationMs,
                width = width,
                height = height,
                sizeBytes = sizeBytes,
                isStream = false,
                isDemo = false,
                folderName = "Picked Files",
                addedAtMs = System.currentTimeMillis(),
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
            val raw = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: return null
            if (raw.width > 640) {
                val targetH = (raw.height * (640f / raw.width)).toInt().coerceAtLeast(160)
                Bitmap.createScaledBitmap(raw, 640, targetH, true).also {
                    if (it !== raw) raw.recycle()
                }
            } else {
                raw
            }
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun isFrameMostlyBlack(bmp: Bitmap): Boolean {
        if (bmp.width < 8 || bmp.height < 8) return false
        var brightSamples = 0
        for (gx in 1..3) {
            for (gy in 1..3) {
                val px = (bmp.width * gx) / 4
                val py = (bmp.height * gy) / 4
                val c = bmp.getPixel(px, py)
                val luma = ((c ushr 16) and 0xFF) + ((c ushr 8) and 0xFF) + (c and 0xFF)
                if (luma > 36) brightSamples++
            }
        }
        return brightSamples == 0
    }

    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "--:--"
        val totalSec = ms / 1000L
        val hours = totalSec / 3600L
        val mins = (totalSec % 3600L) / 60L
        val secs = totalSec % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", mins, secs)
        }
    }
}
