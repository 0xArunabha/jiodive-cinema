package com.jiodive.cinema.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirlineSeatReclineExtra
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jiodive.cinema.data.MediaRepository
import com.jiodive.cinema.model.CinemaConfig
import com.jiodive.cinema.model.MediaCategory
import com.jiodive.cinema.model.MediaItem
import com.jiodive.cinema.model.ScreenPreset
import com.jiodive.cinema.model.TheaterEnvironment
import com.jiodive.cinema.model.VideoFormat
import com.jiodive.cinema.ui.components.CradleBottomBar
import com.jiodive.cinema.ui.components.HeadsetBrandMark
import com.jiodive.cinema.ui.components.HubTab
import com.jiodive.cinema.ui.theme.LocalCinemaExtras
import kotlin.math.roundToInt

@Composable
fun CompanionHubScreen(
    config: CinemaConfig,
    videos: List<MediaItem>,
    selectedVideo: MediaItem?,
    subtitleLabel: String?,
    subtitleCueCount: Int,
    hasStoragePermission: Boolean,
    isLoadingLibrary: Boolean,
    onToggleTheme: () -> Unit,
    onUpdateConfig: (CinemaConfig) -> Unit,
    onSelectVideo: (MediaItem) -> Unit,
    onRequestStoragePermission: () -> Unit,
    onPickVideoFile: () -> Unit,
    onPickSubtitleFile: () -> Unit,
    onClearSubtitles: () -> Unit,
    onOpenStreamSheet: () -> Unit,
    onOpenOpticsSheet: () -> Unit,
    onEnterVr: () -> Unit,
) {
    var selectedTab by remember { mutableStateOf(HubTab.LIBRARY) }
    var searchQuery by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MediaCategory.ALL) }

    val filteredVideos = remember(videos, searchQuery, category) {
        videos.filter { item ->
            val catOk = when (category) {
                MediaCategory.ALL -> true
                MediaCategory.DEVICE -> !item.isStream
                MediaCategory.STREAMS -> item.isStream
            }
            val queryOk = searchQuery.isBlank() ||
                item.title.contains(searchQuery.trim(), ignoreCase = true)
            catOk && queryOk
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CompanionTopBar(
                config = config,
                onToggleTheme = onToggleTheme,
                onOpenOpticsSheet = onOpenOpticsSheet,
            )
        },
        bottomBar = {
            CradleBottomBar(
                selectedTab = selectedTab,
                onSelectTab = { selectedTab = it },
                onEnterVr = onEnterVr,
            )
        },
    ) { innerPadding ->
        when (selectedTab) {
            HubTab.LIBRARY -> {
                LibraryFeed(
                    config = config,
                    selectedVideo = selectedVideo,
                    filteredVideos = filteredVideos,
                    searchQuery = searchQuery,
                    category = category,
                    subtitleLabel = subtitleLabel,
                    subtitleCueCount = subtitleCueCount,
                    hasStoragePermission = hasStoragePermission,
                    isLoadingLibrary = isLoadingLibrary,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 24.dp,
                    ),
                    onSearchChange = { searchQuery = it },
                    onCategoryChange = { category = it },
                    onSelectVideo = onSelectVideo,
                    onRequestStoragePermission = onRequestStoragePermission,
                    onPickVideoFile = onPickVideoFile,
                    onPickSubtitleFile = onPickSubtitleFile,
                    onClearSubtitles = onClearSubtitles,
                    onOpenStreamSheet = onOpenStreamSheet,
                    onEnterVr = onEnterVr,
                )
            }

            HubTab.THEATER -> {
                TheaterConfigFeed(
                    config = config,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = innerPadding.calculateTopPadding() + 8.dp,
                        bottom = innerPadding.calculateBottomPadding() + 24.dp,
                    ),
                    onUpdateConfig = onUpdateConfig,
                    onOpenOpticsSheet = onOpenOpticsSheet,
                    onEnterVr = onEnterVr,
                )
            }
        }
    }
}

@Composable
private fun CompanionTopBar(
    config: CinemaConfig,
    onToggleTheme: () -> Unit,
    onOpenOpticsSheet: () -> Unit,
) {
    val extras = LocalCinemaExtras.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    HeadsetBrandMark(size = 38.dp)
                    Column {
                        Text(
                            text = "JioDive Cinema",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(extras.emeraldReady),
                            )
                            Text(
                                text = "JioDive · ${config.ipdMm.roundToInt()}mm · Thermal 60%",
                                style = MaterialTheme.typography.bodySmall,
                                color = extras.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Explicit Light / Dark theme switcher pill
                    Surface(
                        onClick = onToggleTheme,
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, extras.cardBorder),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = if (config.darkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = "Switch Light or Dark Mode",
                                tint = if (config.darkTheme) extras.goldHighlight else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = if (config.darkTheme) "Light" else "Dark",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    Surface(
                        onClick = onOpenOpticsSheet,
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, extras.cardBorder),
                        modifier = Modifier.size(38.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Headset Optics Settings",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(extras.cardBorder),
            )
        }
    }
}

@Composable
private fun LibraryFeed(
    config: CinemaConfig,
    selectedVideo: MediaItem?,
    filteredVideos: List<MediaItem>,
    searchQuery: String,
    category: MediaCategory,
    subtitleLabel: String?,
    subtitleCueCount: Int,
    hasStoragePermission: Boolean,
    isLoadingLibrary: Boolean,
    contentPadding: PaddingValues,
    onSearchChange: (String) -> Unit,
    onCategoryChange: (MediaCategory) -> Unit,
    onSelectVideo: (MediaItem) -> Unit,
    onRequestStoragePermission: () -> Unit,
    onPickVideoFile: () -> Unit,
    onPickSubtitleFile: () -> Unit,
    onClearSubtitles: () -> Unit,
    onOpenStreamSheet: () -> Unit,
    onEnterVr: () -> Unit,
) {
    val extras = LocalCinemaExtras.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // 1. JioImmerse Search Pill (`40-46dp` height, `20dp` radius)
        item(key = "search_bar") {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                placeholder = { Text("Search movies or streams...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = extras.inkMuted) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent,
                ),
            )
        }

        // 2. Spotlight Selected Movie Card (Real Thumbnail + Badges — ZERO wireframe slop!)
        if (selectedVideo != null) {
            item(key = "spotlight") {
                SpotlightVideoCard(
                    item = selectedVideo,
                    config = config,
                    subtitleLabel = subtitleLabel,
                    subtitleCueCount = subtitleCueCount,
                    onPickSubtitleFile = onPickSubtitleFile,
                    onClearSubtitles = onClearSubtitles,
                    onPlayVr = onEnterVr,
                )
            }
        }

        // 3. Quick Media Actions Row
        item(key = "source_actions") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SourceActionCard(
                    icon = Icons.Rounded.FolderOpen,
                    label = "Pick File",
                    accent = MaterialTheme.colorScheme.primary,
                    onClick = onPickVideoFile,
                    modifier = Modifier.weight(1f),
                )
                SourceActionCard(
                    icon = Icons.Rounded.Link,
                    label = "Stream URL",
                    accent = extras.emeraldReady,
                    onClick = onOpenStreamSheet,
                    modifier = Modifier.weight(1f),
                )
                SourceActionCard(
                    icon = Icons.Rounded.Subtitles,
                    label = if (subtitleLabel != null) "CC Ready" else "Add CC",
                    accent = extras.goldHighlight,
                    onClick = onPickSubtitleFile,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 4. Device Storage Permission Banner (inline, clean card — only when not yet granted)
        if (!hasStoragePermission) {
            item(key = "perm_banner") {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, extras.cardBorder),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = extras.activePillBg,
                            modifier = Modifier.size(42.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.VideoLibrary,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Phone Videos Here", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Allow video library access to display your local downloads & camera movies with thumbnails.",
                                style = MaterialTheme.typography.bodySmall,
                                color = extras.inkMuted,
                            )
                        }
                        TextButton(onClick = onRequestStoragePermission) {
                            Text("Allow", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 5. Category Chips
        item(key = "category_chips") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val entries = listOf(
                    MediaCategory.ALL to "All Videos",
                    MediaCategory.DEVICE to "On Device",
                    MediaCategory.STREAMS to "Streams & Demos",
                )
                items(entries) { (cat, label) ->
                    val selected = category == cat
                    FilterChip(
                        selected = selected,
                        onClick = { onCategoryChange(cat) },
                        label = { Text(label) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = Color.Transparent,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }

        // 6. Media Grid (Pairs cleanly in 2-column rows)
        if (isLoadingLibrary) {
            item(key = "loading_note") {
                Text(
                    text = "Scanning device videos...",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.inkMuted,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }

        val rows = filteredVideos.chunked(2)
        items(rows, key = { row -> row.joinToString("_") { it.id } }) { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                pair.forEach { video ->
                    VideoGridCard(
                        item = video,
                        selected = selectedVideo?.id == video.id,
                        onClick = { onSelectVideo(video) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SpotlightVideoCard(
    item: MediaItem,
    config: CinemaConfig,
    subtitleLabel: String?,
    subtitleCueCount: Int,
    onPickSubtitleFile: () -> Unit,
    onClearSubtitles: () -> Unit,
    onPlayVr: () -> Unit,
) {
    val extras = LocalCinemaExtras.current

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 7.8f)
                    .clickable(onClick = onPlayVr),
            ) {
                if (item.thumbnail != null) {
                    Image(
                        bitmap = item.thumbnail.asImageBitmap(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        extras.emeraldReady,
                                    ),
                                ),
                            ),
                    )
                }

                // Bottom contrast scrim for legibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.08f),
                                    Color.Black.copy(alpha = 0.72f),
                                ),
                            ),
                        ),
                )

                // Top status badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.65f),
                    ) {
                        Text(
                            text = "${config.environment.title} · ${config.screenPreset.title} · ${config.videoFormat.label}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }

                    if (item.height > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = extras.goldHighlight,
                        ) {
                            Text(
                                text = "${item.height}p",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF1E1600),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                // Bottom title + Play CTA inside spotlight
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${MediaRepository.formatDuration(item.durationMs)} · ${if (item.isStream) "Network Stream" else "Local Video"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = extras.turquoiseAccent,
                        modifier = Modifier.size(42.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "Watch in VR",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }

            // Subtitle strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Subtitles,
                        contentDescription = null,
                        tint = if (subtitleLabel != null) extras.emeraldReady else extras.inkMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (subtitleLabel != null) {
                            "$subtitleLabel ($subtitleCueCount lines)"
                        } else {
                            "No subtitles loaded (.srt / .vtt)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (subtitleLabel != null) {
                    TextButton(onClick = onClearSubtitles) {
                        Text("Remove")
                    }
                } else {
                    TextButton(onClick = onPickSubtitleFile) {
                        Text("Add CC")
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceActionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = LocalCinemaExtras.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, extras.cardBorder),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun VideoGridCard(
    item: MediaItem,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = LocalCinemaExtras.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary else extras.cardBorder,
        ),
        modifier = modifier,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9.5f),
            ) {
                if (item.thumbnail != null) {
                    Image(
                        bitmap = item.thumbnail.asImageBitmap(),
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        extras.emeraldContainer,
                                    ),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Theaters,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Selected",
                        tint = extras.emeraldReady,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(20.dp),
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                ) {
                    Text(
                        text = MediaRepository.formatDuration(item.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (item.isDemo) "Built-in Demo" else if (item.isStream) "Stream" else "Device Video",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.inkMuted,
                )
            }
        }
    }
}

@Composable
private fun TheaterConfigFeed(
    config: CinemaConfig,
    contentPadding: PaddingValues,
    onUpdateConfig: (CinemaConfig) -> Unit,
    onOpenOpticsSheet: () -> Unit,
    onEnterVr: () -> Unit,
) {
    val extras = LocalCinemaExtras.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "env_header") {
            Text(text = "Theater Environment", style = MaterialTheme.typography.titleMedium)
        }

        item(key = "env_cards") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TheaterEnvironment.entries.forEach { env ->
                    val selected = config.environment == env
                    val icon = when (env) {
                        TheaterEnvironment.CINEMA -> Icons.Rounded.Theaters
                        TheaterEnvironment.VOID -> Icons.Rounded.NightlightRound
                        TheaterEnvironment.BED -> Icons.Rounded.Bed
                    }
                    Surface(
                        onClick = { onUpdateConfig(config.copy(environment = env)) },
                        shape = RoundedCornerShape(22.dp),
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary else extras.cardBorder,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary else extras.inkMuted,
                            )
                            Text(text = env.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = env.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = extras.inkMuted,
                            )
                        }
                    }
                }
            }
        }

        item(key = "seat_header") {
            Text(text = "Screen Scale & Seat", style = MaterialTheme.typography.titleMedium)
        }

        item(key = "seat_cards") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScreenPreset.entries.forEach { preset ->
                    val selected = config.screenPreset == preset
                    Surface(
                        onClick = { onUpdateConfig(config.copy(screenPreset = preset)) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (selected) extras.emeraldContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) extras.emeraldReady else extras.cardBorder,
                        ),
                        modifier = Modifier.weight(1f),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AirlineSeatReclineExtra,
                                contentDescription = null,
                                tint = if (selected) extras.emeraldReady else extras.inkMuted,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(text = preset.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${preset.widthMeters.toInt()}m wide",
                                style = MaterialTheme.typography.bodySmall,
                                color = extras.inkMuted,
                            )
                        }
                    }
                }
            }
        }

        item(key = "format_header") {
            Text(text = "3D Video Format", style = MaterialTheme.typography.titleMedium)
        }

        item(key = "format_chips") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VideoFormat.entries) { fmt ->
                    val selected = config.videoFormat == fmt
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdateConfig(config.copy(videoFormat = fmt)) },
                        label = { Text(fmt.label) },
                        shape = RoundedCornerShape(20.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = selected,
                            borderColor = Color.Transparent,
                            selectedBorderColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }

        item(key = "optics_card") {
            Surface(
                onClick = onOpenOpticsSheet,
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, extras.cardBorder),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = CircleShape,
                        color = extras.goldContainer,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.CenterFocusStrong,
                                contentDescription = null,
                                tint = extras.goldHighlight,
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Optics, Gaze & Thermal Cap", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "IPD ${config.ipdMm.roundToInt()}mm · FOV ${config.fovDeg.roundToInt()}° · Dwell ${config.gazeDwellMs}ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = extras.inkMuted,
                        )
                    }
                    Text(
                        text = "Tune",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        item(key = "launch_cta") {
            Button(
                onClick = onEnterVr,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text("Enter VR Theater")
            }
        }
    }
}
