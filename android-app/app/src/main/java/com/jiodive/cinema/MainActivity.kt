package com.jiodive.cinema

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jiodive.cinema.data.LibraryCatalog
import com.jiodive.cinema.data.MediaRepository
import com.jiodive.cinema.data.PrefsStore
import com.jiodive.cinema.data.SrtVttParser
import com.jiodive.cinema.model.MediaItem
import com.jiodive.cinema.model.WatchHistoryEntry
import com.jiodive.cinema.ui.components.DisplayRefreshHelper
import com.jiodive.cinema.ui.screens.CompanionHubScreen
import com.jiodive.cinema.ui.screens.CustomDurationBottomSheet
import com.jiodive.cinema.ui.screens.DeviceGuideBottomSheet
import com.jiodive.cinema.ui.screens.FirstRunOnboardingSheet
import com.jiodive.cinema.ui.screens.MediaActionBottomSheet
import com.jiodive.cinema.ui.screens.OpticsBottomSheet
import com.jiodive.cinema.ui.screens.StreamUrlBottomSheet
import com.jiodive.cinema.ui.screens.SubtitleSyncBottomSheet
import com.jiodive.cinema.ui.theme.JioDiveCinemaTheme
import com.jiodive.cinema.vr.VrPlayerActivity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DisplayRefreshHelper.enablePeakRefreshRate(this)
        val prefs = PrefsStore(this)

        setContent {
            val scope = rememberCoroutineScope()
            var config by remember { mutableStateOf(prefs.loadConfig()) }
            var watchHistory by remember { mutableStateOf(prefs.getWatchHistory()) }
            var durationFilter by remember { mutableStateOf(prefs.loadDurationFilter()) }
            var sortOrder by remember { mutableStateOf(prefs.loadSortOrder()) }
            var recentStreams by remember { mutableStateOf(prefs.getRecentStreams()) }

            DisposableEffect(config.darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (config.darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (config.darkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
                onDispose {}
            }

            fun updateConfig(newCfg: com.jiodive.cinema.model.CinemaConfig) {
                config = newCfg
                prefs.saveConfig(newCfg)
            }

            val videoPermission = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_VIDEO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }
            }

            var hasStoragePerm by remember {
                mutableStateOf(
                    ContextCompat.checkSelfPermission(this@MainActivity, videoPermission) ==
                        PackageManager.PERMISSION_GRANTED,
                )
            }
            var canWriteSettings by remember {
                mutableStateOf(Settings.System.canWrite(this@MainActivity))
            }

            val mediaItems = remember {
                mutableStateListOf<MediaItem>().apply { addAll(MediaRepository.builtInDemos) }
            }
            var selectedVideo by remember { mutableStateOf<MediaItem?>(MediaRepository.builtInDemos.first()) }
            var isLoadingLibrary by remember { mutableStateOf(false) }

            var subtitleUri by remember { mutableStateOf<Uri?>(null) }
            var subtitleLabel by remember { mutableStateOf<String?>(null) }
            var subtitleCueCount by remember { mutableIntStateOf(0) }

            var thumbnailRetryTokens by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
            var showOpticsSheet by remember { mutableStateOf(false) }
            var showStreamSheet by remember { mutableStateOf(false) }
            var showSubtitleSheet by remember { mutableStateOf(false) }
            var actionTargetItem by remember { mutableStateOf<MediaItem?>(null) }
            var showGuideSheet by remember { mutableStateOf(false) }
            var showCustomDurationSheet by remember { mutableStateOf(false) }
            var showOnboardingSheet by remember { mutableStateOf(!prefs.hasCompletedOnboarding()) }
            var pendingVrResumeMs by remember { mutableStateOf<Long?>(null) }

            fun syncDiscoveredDurations(history: List<WatchHistoryEntry>) {
                if (history.isEmpty() || mediaItems.isEmpty()) return
                val durByUri = history.filter { it.durationMs > 0L }.associate { it.uriString to it.durationMs }
                if (durByUri.isEmpty()) return
                for (i in mediaItems.indices) {
                    val item = mediaItems[i]
                    if (item.durationMs <= 0L) {
                        val knownDur = durByUri[item.uri.toString()]
                        if (knownDur != null && knownDur > 0L) {
                            val updated = item.copy(durationMs = knownDur)
                            mediaItems[i] = updated
                            if (!updated.isDemo) {
                                prefs.saveCustomMedia(updated)
                            }
                            if (selectedVideo?.id == updated.id) {
                                selectedVideo = updated
                            }
                        }
                    }
                }
            }

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                var isFirstResume = true
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        if (isFirstResume) {
                            isFirstResume = false
                            return@LifecycleEventObserver
                        }
                        hasStoragePerm = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            videoPermission,
                        ) == PackageManager.PERMISSION_GRANTED
                        canWriteSettings = Settings.System.canWrite(this@MainActivity)
                        config = prefs.loadConfig()
                        val updatedHistory = prefs.getWatchHistory()
                        watchHistory = updatedHistory
                        syncDiscoveredDurations(updatedHistory)
                        durationFilter = prefs.loadDurationFilter()
                        sortOrder = prefs.loadSortOrder()
                        recentStreams = prefs.getRecentStreams()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }

            suspend fun rebuildMediaLibrary(includeDeviceScan: Boolean) {
                val savedCustom = MediaRepository.restoreSavedCustomMedia(
                    this@MainActivity,
                    prefs.getSavedMediaEntries(),
                )
                val deviceVideos = if (includeDeviceScan && hasStoragePerm) {
                    MediaRepository.queryDeviceVideos(this@MainActivity)
                } else {
                    mediaItems.filter { !it.isStream && !it.isDemo && !it.id.startsWith("picked_") }
                }

                val seenUris = HashSet<String>()
                val merged = ArrayList<MediaItem>(savedCustom.size + deviceVideos.size + MediaRepository.builtInDemos.size)
                (savedCustom + deviceVideos + MediaRepository.builtInDemos).forEach { entry ->
                    if (seenUris.add(entry.uri.toString())) {
                        merged.add(entry)
                    }
                }
                mediaItems.clear()
                mediaItems.addAll(merged)
                syncDiscoveredDurations(watchHistory)
                if (selectedVideo == null && mediaItems.isNotEmpty()) {
                    selectedVideo = mediaItems.first()
                }
            }

            fun refreshDeviceVideos() {
                if (!hasStoragePerm) return
                isLoadingLibrary = true
                scope.launch {
                    rebuildMediaLibrary(includeDeviceScan = true)
                    isLoadingLibrary = false
                }
            }

            LaunchedEffect(Unit) {
                rebuildMediaLibrary(includeDeviceScan = false)
                prefs.getLastSubtitle()?.let { (savedSubUriStr, _) ->
                    val parsedUri = Uri.parse(savedSubUriStr)
                    val (name, raw) = MediaRepository.readTextFromUri(this@MainActivity, parsedUri)
                    val cues = raw?.let { SrtVttParser.parse(it) }.orEmpty()
                    if (cues.isNotEmpty()) {
                        subtitleUri = parsedUri
                        subtitleLabel = name
                        subtitleCueCount = cues.size
                    } else {
                        prefs.clearLastSubtitle()
                    }
                }
            }

            LaunchedEffect(hasStoragePerm) {
                if (hasStoragePerm) {
                    refreshDeviceVideos()
                }
            }

            val storagePermLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                hasStoragePerm = granted
                if (granted) {
                    refreshDeviceVideos()
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "You can still open any file using Pick File",
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }

            val pickVideoLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    scope.launch {
                        val item = MediaRepository.inspectPickedVideo(this@MainActivity, uri)
                        prefs.saveCustomMedia(item)
                        mediaItems.removeAll { it.uri == uri }
                        mediaItems.add(0, item)
                        selectedVideo = item
                    }
                }
            }

            val pickSubtitleLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    scope.launch {
                        val (name, raw) = MediaRepository.readTextFromUri(this@MainActivity, uri)
                        val cues = raw?.let { SrtVttParser.parse(it) }.orEmpty()
                        if (cues.isEmpty()) {
                            Toast.makeText(
                                this@MainActivity,
                                "No valid .srt or .vtt cues found in file",
                                Toast.LENGTH_SHORT,
                            ).show()
                        } else {
                            subtitleUri = uri
                            subtitleLabel = name
                            subtitleCueCount = cues.size
                            prefs.saveLastSubtitle(uri.toString(), name)
                        }
                    }
                }
            }

            fun launchVrNow(video: MediaItem, resumeMs: Long? = null) {
                showGuideSheet = false
                pendingVrResumeMs = null
                val targetResumeMs = resumeMs ?: prefs.getResumePositionMs(video.uri.toString())
                val intent = Intent(this@MainActivity, VrPlayerActivity::class.java).apply {
                    putExtra(VrPlayerActivity.EXTRA_MEDIA_ID, video.id)
                    putExtra(VrPlayerActivity.EXTRA_VIDEO_URI, video.uri.toString())
                    putExtra(VrPlayerActivity.EXTRA_VIDEO_TITLE, video.title)
                    putExtra(VrPlayerActivity.EXTRA_DURATION_MS, video.durationMs)
                    putExtra(VrPlayerActivity.EXTRA_IS_STREAM, video.isStream)
                    putExtra(VrPlayerActivity.EXTRA_IS_DEMO, video.isDemo)
                    putExtra(VrPlayerActivity.EXTRA_RESUME_MS, targetResumeMs)
                    putExtra(VrPlayerActivity.EXTRA_SUBTITLE_URI, subtitleUri?.toString())
                    putExtra(VrPlayerActivity.EXTRA_SUBTITLE_NAME, subtitleLabel)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    @Suppress("DEPRECATION")
                    overridePendingTransition(R.anim.vr_enter_fade, R.anim.vr_stay)
                }
            }

            fun onRequestEnterVr() {
                val target = selectedVideo ?: run {
                    Toast.makeText(this@MainActivity, "Select a video first", Toast.LENGTH_SHORT).show()
                    return
                }
                if (config.skipInsertionGuide) {
                    launchVrNow(target)
                } else {
                    pendingVrResumeMs = null
                    showGuideSheet = true
                }
            }

            JioDiveCinemaTheme(darkTheme = config.darkTheme) {
                CompanionHubScreen(
                    config = config,
                    videos = mediaItems,
                    selectedVideo = selectedVideo,
                    watchHistory = watchHistory,
                    durationFilter = durationFilter,
                    sortOrder = sortOrder,
                    thumbnailRetryTokens = thumbnailRetryTokens,
                    subtitleLabel = subtitleLabel,
                    subtitleCueCount = subtitleCueCount,
                    hasStoragePermission = hasStoragePerm,
                    isLoadingLibrary = isLoadingLibrary,
                    onToggleTheme = { updateConfig(config.copy(darkTheme = !config.darkTheme)) },
                    onUpdateConfig = ::updateConfig,
                    onSelectVideo = { selectedVideo = it },
                    onDurationFilterChange = { nextFilter ->
                        durationFilter = nextFilter
                        prefs.saveDurationFilter(nextFilter)
                    },
                    onSortOrderChange = { nextOrder ->
                        sortOrder = nextOrder
                        prefs.saveSortOrder(nextOrder)
                    },
                    onOpenCustomDurationSheet = { showCustomDurationSheet = true },
                    onResumeHistoryEntry = { entry ->
                        val matched = mediaItems.firstOrNull { it.uri.toString() == entry.uriString }
                            ?: MediaItem(
                                id = entry.mediaId,
                                uri = Uri.parse(entry.uriString),
                                title = entry.title,
                                durationMs = entry.durationMs,
                                width = 0,
                                height = 0,
                                sizeBytes = 0L,
                                isStream = entry.isStream,
                                isDemo = entry.isDemo,
                                thumbnail = MediaRepository.getCachedThumbnail(entry.mediaId),
                            )
                        selectedVideo = matched
                        if (config.skipInsertionGuide) {
                            launchVrNow(matched, entry.positionMs)
                        } else {
                            pendingVrResumeMs = entry.positionMs
                            showGuideSheet = true
                        }
                    },
                    onRemoveHistoryEntry = { entry ->
                        prefs.removeWatchHistory(entry.uriString)
                        watchHistory = prefs.getWatchHistory()
                    },
                    onClearWatchHistory = {
                        prefs.clearWatchHistory()
                        watchHistory = emptyList()
                    },
                    onThumbnailLoaded = { mediaId, bmp ->
                        val idx = mediaItems.indexOfFirst { it.id == mediaId }
                        if (idx >= 0 && mediaItems[idx].thumbnail == null) {
                            mediaItems[idx] = mediaItems[idx].copy(thumbnail = bmp)
                        }
                        if (selectedVideo?.id == mediaId && selectedVideo?.thumbnail == null) {
                            selectedVideo = selectedVideo?.copy(thumbnail = bmp)
                        }
                    },
                    onRequestStoragePermission = { storagePermLauncher.launch(videoPermission) },
                    onPickVideoFile = {
                        pickVideoLauncher.launch(arrayOf("video/*", "video/x-matroska", "video/mp2ts"))
                    },
                    onPickSubtitleFile = {
                        pickSubtitleLauncher.launch(arrayOf("text/*", "application/x-subrip", "*/*"))
                    },
                    onOpenSubtitleSheet = { showSubtitleSheet = true },
                    onCardMoreActions = { item -> actionTargetItem = item },
                    onOpenStreamSheet = { showStreamSheet = true },
                    onOpenOpticsSheet = { showOpticsSheet = true },
                    onEnterVr = ::onRequestEnterVr,
                )

                if (showOpticsSheet) {
                    OpticsBottomSheet(
                        config = config,
                        canWriteSystemSettings = canWriteSettings,
                        onUpdateConfig = ::updateConfig,
                        onRequestWriteSettings = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                Uri.parse("package:$packageName"),
                            )
                            startActivity(intent)
                        },
                        onReplayOnboarding = { showOnboardingSheet = true },
                        onDismiss = { showOpticsSheet = false },
                    )
                }

                if (showOnboardingSheet) {
                    FirstRunOnboardingSheet(
                        onComplete = {
                            prefs.setOnboardingCompleted(true)
                            showOnboardingSheet = false
                        },
                    )
                }

                if (showStreamSheet) {
                    StreamUrlBottomSheet(
                        initialUrl = prefs.getLastStreamUrl(),
                        recentStreams = recentStreams,
                        onLoadUrl = { url ->
                            val cleanUrl = url.trim()
                            val existing = mediaItems.firstOrNull { it.uri.toString() == cleanUrl }
                            val title = existing?.title ?: LibraryCatalog.suggestStreamTitle(cleanUrl)
                            prefs.saveLastStreamUrl(cleanUrl)
                            prefs.recordRecentStream(cleanUrl, title)
                            recentStreams = prefs.getRecentStreams()
                            if (existing != null) {
                                val updated = if (existing.isDemo) {
                                    existing
                                } else {
                                    existing.copy(addedAtMs = System.currentTimeMillis()).also {
                                        prefs.saveCustomMedia(it)
                                        mediaItems.removeAll { m -> m.id == existing.id }
                                        mediaItems.add(0, it)
                                    }
                                }
                                selectedVideo = updated
                            } else {
                                val uri = Uri.parse(cleanUrl)
                                val item = MediaItem(
                                    id = "url_${cleanUrl.hashCode()}",
                                    uri = uri,
                                    title = title,
                                    durationMs = 0L,
                                    width = 0,
                                    height = 0,
                                    sizeBytes = 0L,
                                    isStream = true,
                                    isDemo = false,
                                    addedAtMs = System.currentTimeMillis(),
                                )
                                prefs.saveCustomMedia(item)
                                mediaItems.add(0, item)
                                selectedVideo = item
                            }
                            showStreamSheet = false
                        },
                        onRemoveRecent = { entry ->
                            prefs.removeRecentStream(entry.url)
                            recentStreams = prefs.getRecentStreams()
                        },
                        onDismiss = { showStreamSheet = false },
                    )
                }

                if (showSubtitleSheet && subtitleLabel != null) {
                    SubtitleSyncBottomSheet(
                        subtitleLabel = subtitleLabel!!,
                        subtitleCueCount = subtitleCueCount,
                        offsetMs = config.subtitleOffsetMs,
                        textSize = config.subtitleTextSize,
                        onOffsetChange = { nextOffset ->
                            updateConfig(config.copy(subtitleOffsetMs = nextOffset))
                        },
                        onTextSizeChange = { nextSize ->
                            updateConfig(config.copy(subtitleTextSize = nextSize))
                        },
                        onReplaceFile = {
                            showSubtitleSheet = false
                            pickSubtitleLauncher.launch(arrayOf("text/*", "application/x-subrip", "*/*"))
                        },
                        onClearSubtitles = {
                            subtitleUri = null
                            subtitleLabel = null
                            subtitleCueCount = 0
                            prefs.clearLastSubtitle()
                            updateConfig(config.copy(subtitleOffsetMs = 0L))
                        },
                        onDismiss = { showSubtitleSheet = false },
                    )
                }

                actionTargetItem?.let { item ->
                    val uriStr = item.uri.toString()
                    val histEntry = watchHistory.firstOrNull { it.uriString == uriStr }
                    val canRemoveFromLib = !item.isDemo && (item.isStream || item.id.startsWith("picked_"))
                    MediaActionBottomSheet(
                        item = item,
                        selected = selectedVideo?.id == item.id,
                        resumePositionMs = prefs.getResumePositionMs(uriStr),
                        hasWatchHistory = histEntry != null,
                        canRemoveFromLibrary = canRemoveFromLib,
                        thumbnailFailed = MediaRepository.hasThumbnailFailed(item.id),
                        onWatchVr = { resumeMs ->
                            actionTargetItem = null
                            selectedVideo = item
                            if (config.skipInsertionGuide) {
                                launchVrNow(item, resumeMs)
                            } else {
                                pendingVrResumeMs = resumeMs
                                showGuideSheet = true
                            }
                        },
                        onSelectSpotlight = { selectedVideo = item },
                        onRetryThumbnail = {
                            thumbnailRetryTokens = thumbnailRetryTokens + (item.id to (thumbnailRetryTokens[item.id] ?: 0) + 1)
                        },
                        onRemoveFromHistory = {
                            prefs.removeWatchHistory(uriStr)
                            watchHistory = prefs.getWatchHistory()
                        },
                        onRemoveFromLibrary = {
                            prefs.removeSavedMedia(uriStr)
                            prefs.removeWatchHistory(uriStr)
                            if (item.isStream) {
                                prefs.removeRecentStream(uriStr)
                                recentStreams = prefs.getRecentStreams()
                            }
                            watchHistory = prefs.getWatchHistory()
                            mediaItems.removeAll { it.id == item.id }
                            if (selectedVideo?.id == item.id) {
                                selectedVideo = mediaItems.firstOrNull()
                            }
                        },
                        onDismiss = { actionTargetItem = null },
                    )
                }

                if (showCustomDurationSheet) {
                    CustomDurationBottomSheet(
                        initialState = durationFilter,
                        onApply = { updated ->
                            durationFilter = updated
                            prefs.saveDurationFilter(updated)
                            showCustomDurationSheet = false
                        },
                        onReset = {
                            val reset = com.jiodive.cinema.model.DurationFilterState()
                            durationFilter = reset
                            prefs.saveDurationFilter(reset)
                            showCustomDurationSheet = false
                        },
                        onDismiss = { showCustomDurationSheet = false },
                    )
                }

                if (showGuideSheet && selectedVideo != null) {
                    DeviceGuideBottomSheet(
                        title = selectedVideo!!.title,
                        skipNextTime = config.skipInsertionGuide,
                        onSkipNextTimeChanged = { checked ->
                            updateConfig(config.copy(skipInsertionGuide = checked))
                        },
                        onConfirmEnterVr = {
                            selectedVideo?.let { launchVrNow(it, pendingVrResumeMs) }
                        },
                        onDismiss = {
                            pendingVrResumeMs = null
                            showGuideSheet = false
                        },
                    )
                }
            }
        }
    }
}
