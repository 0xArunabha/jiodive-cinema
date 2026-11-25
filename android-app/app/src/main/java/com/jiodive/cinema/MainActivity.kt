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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jiodive.cinema.data.MediaRepository
import com.jiodive.cinema.data.PrefsStore
import com.jiodive.cinema.data.SrtVttParser
import com.jiodive.cinema.model.MediaItem
import com.jiodive.cinema.ui.screens.CompanionHubScreen
import com.jiodive.cinema.ui.screens.DeviceGuideBottomSheet
import com.jiodive.cinema.ui.screens.OpticsBottomSheet
import com.jiodive.cinema.ui.screens.StreamUrlBottomSheet
import com.jiodive.cinema.ui.theme.JioDiveCinemaTheme
import com.jiodive.cinema.vr.VrPlayerActivity
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = PrefsStore(this)

        setContent {
            val scope = rememberCoroutineScope()
            var config by remember { mutableStateOf(prefs.loadConfig()) }

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

            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val obs = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        hasStoragePerm = ContextCompat.checkSelfPermission(
                            this@MainActivity,
                            videoPermission,
                        ) == PackageManager.PERMISSION_GRANTED
                        canWriteSettings = Settings.System.canWrite(this@MainActivity)
                        config = prefs.loadConfig()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(obs)
                onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
            }

            val mediaItems = remember {
                mutableStateListOf<MediaItem>().apply { addAll(MediaRepository.builtInDemos) }
            }
            var selectedVideo by remember { mutableStateOf<MediaItem?>(MediaRepository.builtInDemos.first()) }
            var isLoadingLibrary by remember { mutableStateOf(false) }

            var subtitleUri by remember { mutableStateOf<Uri?>(null) }
            var subtitleLabel by remember { mutableStateOf<String?>(null) }
            var subtitleCueCount by remember { mutableStateOf(0) }

            var showOpticsSheet by remember { mutableStateOf(false) }
            var showStreamSheet by remember { mutableStateOf(false) }
            var showGuideSheet by remember { mutableStateOf(false) }

            fun refreshDeviceVideos() {
                if (!hasStoragePerm) return
                isLoadingLibrary = true
                scope.launch {
                    val deviceVideos = MediaRepository.queryDeviceVideos(this@MainActivity)
                    val customEntries = mediaItems.filter { it.id.startsWith("picked_") || it.id.startsWith("url_") }
                    mediaItems.clear()
                    mediaItems.addAll(customEntries)
                    mediaItems.addAll(deviceVideos)
                    mediaItems.addAll(MediaRepository.builtInDemos)
                    if (selectedVideo == null && mediaItems.isNotEmpty()) {
                        selectedVideo = mediaItems.first()
                    }
                    isLoadingLibrary = false
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
                        }
                    }
                }
            }

            fun launchVrNow(video: MediaItem) {
                showGuideSheet = false
                val intent = Intent(this@MainActivity, VrPlayerActivity::class.java).apply {
                    putExtra(VrPlayerActivity.EXTRA_VIDEO_URI, video.uri.toString())
                    putExtra(VrPlayerActivity.EXTRA_VIDEO_TITLE, video.title)
                    putExtra(VrPlayerActivity.EXTRA_SUBTITLE_URI, subtitleUri?.toString())
                    putExtra(VrPlayerActivity.EXTRA_SUBTITLE_NAME, subtitleLabel)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
            }

            fun onRequestEnterVr() {
                val target = selectedVideo ?: run {
                    Toast.makeText(this@MainActivity, "Select a video first", Toast.LENGTH_SHORT).show()
                    return
                }
                if (config.skipInsertionGuide) {
                    launchVrNow(target)
                } else {
                    showGuideSheet = true
                }
            }

            JioDiveCinemaTheme(darkTheme = config.darkTheme) {
                CompanionHubScreen(
                    config = config,
                    videos = mediaItems,
                    selectedVideo = selectedVideo,
                    subtitleLabel = subtitleLabel,
                    subtitleCueCount = subtitleCueCount,
                    hasStoragePermission = hasStoragePerm,
                    isLoadingLibrary = isLoadingLibrary,
                    onToggleTheme = { updateConfig(config.copy(darkTheme = !config.darkTheme)) },
                    onUpdateConfig = ::updateConfig,
                    onSelectVideo = { selectedVideo = it },
                    onRequestStoragePermission = { storagePermLauncher.launch(videoPermission) },
                    onPickVideoFile = {
                        pickVideoLauncher.launch(arrayOf("video/*", ".mkv", ".mp4", ".webm"))
                    },
                    onPickSubtitleFile = {
                        pickSubtitleLauncher.launch(arrayOf("text/*", "application/x-subrip", "*/*"))
                    },
                    onClearSubtitles = {
                        subtitleUri = null
                        subtitleLabel = null
                        subtitleCueCount = 0
                    },
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
                        onDismiss = { showOpticsSheet = false },
                    )
                }

                if (showStreamSheet) {
                    StreamUrlBottomSheet(
                        initialUrl = prefs.getLastStreamUrl(),
                        onLoadUrl = { url ->
                            prefs.saveLastStreamUrl(url)
                            val uri = Uri.parse(url)
                            val item = MediaItem(
                                id = "url_${url.hashCode()}",
                                uri = uri,
                                title = uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "Stream Video",
                                durationMs = 0L,
                                width = 0,
                                height = 0,
                                sizeBytes = 0L,
                                isStream = true,
                                isDemo = false,
                            )
                            mediaItems.removeAll { it.id == item.id }
                            mediaItems.add(0, item)
                            selectedVideo = item
                            showStreamSheet = false
                        },
                        onDismiss = { showStreamSheet = false },
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
                            selectedVideo?.let { launchVrNow(it) }
                        },
                        onDismiss = { showGuideSheet = false },
                    )
                }
            }
        }
    }
}
