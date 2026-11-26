package com.jiodive.cinema.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirlineSeatReclineExtra
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiodive.cinema.data.LibraryCatalog
import com.jiodive.cinema.data.MediaRepository
import com.jiodive.cinema.model.CinemaConfig
import com.jiodive.cinema.model.DurationFilterPreset
import com.jiodive.cinema.model.DurationFilterState
import com.jiodive.cinema.model.LibrarySortOrder
import com.jiodive.cinema.model.MediaCategory
import com.jiodive.cinema.model.MediaItem
import com.jiodive.cinema.model.ScreenPreset
import com.jiodive.cinema.model.TheaterEnvironment
import com.jiodive.cinema.model.VideoFormat
import com.jiodive.cinema.model.WatchHistoryEntry
import com.jiodive.cinema.ui.components.CradleBottomBar
import com.jiodive.cinema.ui.components.HeadsetBrandMark
import com.jiodive.cinema.ui.components.HubTab
import com.jiodive.cinema.ui.components.MediaFormatBadgePill
import com.jiodive.cinema.ui.components.ThumbnailArtworkFrame
import com.jiodive.cinema.ui.components.rememberLazyThumbnailState
import com.jiodive.cinema.ui.theme.LocalCinemaExtras
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

@Composable
fun CompanionHubScreen(
    config: CinemaConfig,
    videos: List<MediaItem>,
    selectedVideo: MediaItem?,
    watchHistory: List<WatchHistoryEntry>,
    durationFilter: DurationFilterState,
    sortOrder: LibrarySortOrder,
    thumbnailRetryTokens: Map<String, Int>,
    subtitleLabel: String?,
    subtitleCueCount: Int,
    hasStoragePermission: Boolean,
    isLoadingLibrary: Boolean,
    @Suppress("UNUSED_PARAMETER") onToggleTheme: () -> Unit,
    onUpdateConfig: (CinemaConfig) -> Unit,
    onSelectVideo: (MediaItem) -> Unit,
    onDurationFilterChange: (DurationFilterState) -> Unit,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
    onOpenCustomDurationSheet: () -> Unit,
    onResumeHistoryEntry: (WatchHistoryEntry) -> Unit,
    onRemoveHistoryEntry: (WatchHistoryEntry) -> Unit,
    onClearWatchHistory: () -> Unit,
    onThumbnailLoaded: (String, Bitmap) -> Unit,
    onRequestStoragePermission: () -> Unit,
    onPickVideoFile: () -> Unit,
    onPickSubtitleFile: () -> Unit,
    onOpenSubtitleSheet: () -> Unit,
    onCardMoreActions: (MediaItem) -> Unit,
    onOpenStreamSheet: () -> Unit,
    onOpenOpticsSheet: () -> Unit,
    onEnterVr: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })
    val selectedTab by remember {
        derivedStateOf { if (pagerState.currentPage == 0) HubTab.LIBRARY else HubTab.THEATER }
    }

    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(MediaCategory.ALL) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }

    val availableFolders by remember(videos, category) {
        derivedStateOf {
            val inCategory = when (category) {
                MediaCategory.ALL -> videos
                MediaCategory.DEVICE -> videos.filter { !it.isStream }
                MediaCategory.STREAMS -> videos.filter { it.isStream }
            }
            LibraryCatalog.extractFolders(inCategory)
        }
    }
    LaunchedEffect(availableFolders) {
        if (selectedFolder != null && availableFolders.none { it.first == selectedFolder }) {
            selectedFolder = null
        }
    }

    val libraryListState = rememberLazyListState()
    val theaterListState = rememberLazyListState()

    BackHandler(enabled = selectedTab == HubTab.THEATER || isSearchOpen || searchQuery.isNotEmpty()) {
        when {
            searchQuery.isNotEmpty() -> searchQuery = ""
            isSearchOpen -> isSearchOpen = false
            else -> scope.launch {
                pagerState.animateScrollToPage(
                    page = HubTab.LIBRARY.ordinal,
                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f),
                )
            }
        }
    }

    val isScrolled by remember {
        derivedStateOf {
            val st = if (pagerState.currentPage == 0) libraryListState else theaterListState
            st.firstVisibleItemIndex > 0 || st.firstVisibleItemScrollOffset > 1
        }
    }

    val filteredVideos by remember(videos, searchQuery, category, durationFilter, selectedFolder, sortOrder) {
        derivedStateOf {
            LibraryCatalog.filterAndSort(
                items = videos,
                category = category,
                searchQuery = searchQuery,
                durationFilter = durationFilter,
                selectedFolder = selectedFolder,
                sortOrder = sortOrder,
            )
        }
    }

    val historyByUri = remember(watchHistory) {
        watchHistory.associateBy { it.uriString }
    }

    val baseAccent = MaterialTheme.colorScheme.primary
    val targetGlow by produceState(
        initialValue = baseAccent,
        key1 = selectedVideo?.id,
        key2 = selectedVideo?.thumbnail,
    ) {
        val bmp = selectedVideo?.thumbnail
        value = withContext(Dispatchers.Default) {
            extractAmbientGlow(bmp, baseAccent)
        }
    }
    val ambientGlow by animateColorAsState(
        targetValue = targetGlow,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "ambientHeroGlow",
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CompanionTopBar(
                elevated = isScrolled,
                searchActive = isSearchOpen || searchQuery.isNotEmpty(),
                onToggleSearch = {
                    val opening = !(isSearchOpen || searchQuery.isNotEmpty())
                    if (opening) {
                        isSearchOpen = true
                        scope.launch {
                            if (pagerState.currentPage != HubTab.LIBRARY.ordinal) {
                                pagerState.animateScrollToPage(
                                    page = HubTab.LIBRARY.ordinal,
                                    animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f),
                                )
                            }
                            libraryListState.animateScrollToItem(0)
                        }
                    } else {
                        searchQuery = ""
                        isSearchOpen = false
                    }
                },
                onOpenOpticsSheet = onOpenOpticsSheet,
            )
        },
        bottomBar = {
            CradleBottomBar(
                selectedTab = selectedTab,
                tabProgress = {
                    (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)
                },
                onSelectTab = { target ->
                    scope.launch {
                        pagerState.animateScrollToPage(
                            page = target.ordinal,
                            animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f),
                        )
                    }
                },
                onEnterVr = onEnterVr,
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (config.darkTheme) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ambientGlow.copy(alpha = 0.24f),
                                    ambientGlow.copy(alpha = 0.08f),
                                    Color.Transparent,
                                ),
                            ),
                        ),
                )
            }

            val feedPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            )

            // Both tabs stay composed & GPU-layered via beyondViewportPageCount = 1 so tab switches
            // run with zero LazyColumn rebuild lag!
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val isPageActive = pagerState.currentPage == page || pagerState.isScrollInProgress
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val offset = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                                .coerceIn(0f, 1f)
                            if (offset > 0.001f) {
                                val s = 1f - 0.04f * offset
                                scaleX = s
                                scaleY = s
                                alpha = 1f - 0.35f * offset
                            }
                        },
                ) {
                    if (page == 0) {
                        LibraryFeed(
                            config = config,
                            isTabActive = isPageActive,
                            selectedVideo = selectedVideo,
                            filteredVideos = filteredVideos,
                            availableFolders = availableFolders,
                            selectedFolder = selectedFolder,
                            sortOrder = sortOrder,
                            thumbnailRetryTokens = thumbnailRetryTokens,
                            watchHistory = watchHistory,
                            historyByUri = historyByUri,
                            isSearchOpen = isSearchOpen,
                            searchQuery = searchQuery,
                            category = category,
                            durationFilter = durationFilter,
                            subtitleLabel = subtitleLabel,
                            subtitleCueCount = subtitleCueCount,
                            hasStoragePermission = hasStoragePermission,
                            isLoadingLibrary = isLoadingLibrary,
                            ambientGlow = ambientGlow,
                            listState = libraryListState,
                            contentPadding = feedPadding,
                            onSearchChange = { searchQuery = it },
                            onCloseSearch = {
                                searchQuery = ""
                                isSearchOpen = false
                            },
                            onCategoryChange = { category = it },
                            onFolderSelect = { selectedFolder = it },
                            onSortOrderChange = onSortOrderChange,
                            onDurationFilterChange = onDurationFilterChange,
                            onOpenCustomDurationSheet = onOpenCustomDurationSheet,
                            onResetFilters = {
                                searchQuery = ""
                                isSearchOpen = false
                                category = MediaCategory.ALL
                                selectedFolder = null
                                onDurationFilterChange(DurationFilterState())
                            },
                            onSelectVideo = onSelectVideo,
                            onResumeHistoryEntry = onResumeHistoryEntry,
                            onRemoveHistoryEntry = onRemoveHistoryEntry,
                            onClearWatchHistory = onClearWatchHistory,
                            onThumbnailLoaded = onThumbnailLoaded,
                            onRequestStoragePermission = onRequestStoragePermission,
                            onPickVideoFile = onPickVideoFile,
                            onPickSubtitleFile = onPickSubtitleFile,
                            onOpenSubtitleSheet = onOpenSubtitleSheet,
                            onCardMoreActions = onCardMoreActions,
                            onOpenStreamSheet = onOpenStreamSheet,
                            onEnterVr = onEnterVr,
                        )
                    } else {
                        TheaterConfigFeed(
                            config = config,
                            isTabActive = isPageActive,
                            listState = theaterListState,
                            contentPadding = feedPadding,
                            onUpdateConfig = onUpdateConfig,
                            onOpenOpticsSheet = onOpenOpticsSheet,
                            onEnterVr = onEnterVr,
                        )
                    }
                }
            }
        }
    }
}

private fun extractAmbientGlow(bmp: Bitmap?, fallback: Color): Color {
    if (bmp == null || bmp.isRecycled || bmp.width < 4 || bmp.height < 4) return fallback
    return runCatching {
        var rSum = 0L
        var gSum = 0L
        var bSum = 0L
        var count = 0
        for (gx in 1..4) {
            for (gy in 1..4) {
                val px = (bmp.width * gx) / 5
                val py = (bmp.height * gy) / 5
                val c = bmp.getPixel(px, py)
                rSum += android.graphics.Color.red(c)
                gSum += android.graphics.Color.green(c)
                bSum += android.graphics.Color.blue(c)
                count++
            }
        }
        Color(
            red = (rSum / count).toInt().coerceIn(24, 240),
            green = (gSum / count).toInt().coerceIn(24, 240),
            blue = (bSum / count).toInt().coerceIn(24, 240),
        )
    }.getOrDefault(fallback)
}

@Composable
private fun CompanionTopBar(
    elevated: Boolean,
    searchActive: Boolean,
    onToggleSearch: () -> Unit,
    onOpenOpticsSheet: () -> Unit,
) {
    val extras = LocalCinemaExtras.current
    val dividerAlpha by animateFloatAsState(
        targetValue = if (elevated) 1f else 0f,
        animationSpec = tween(140),
        label = "headerDividerAlpha",
    )
    val headerBgColor = MaterialTheme.colorScheme.background

    val searchInteraction = remember { MutableInteractionSource() }
    val searchPressed by searchInteraction.collectIsPressedAsState()
    val searchScale by animateFloatAsState(
        targetValue = if (searchPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "searchBtnScale",
    )
    val searchBg by animateColorAsState(
        targetValue = if (searchActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f)
        },
        animationSpec = tween(200),
        label = "searchBtnBg",
    )
    val searchBorder by animateColorAsState(
        targetValue = if (searchActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(200),
        label = "searchBtnBorder",
    )

    val settingsInteraction = remember { MutableInteractionSource() }
    val settingsPressed by settingsInteraction.collectIsPressedAsState()
    val settingsScale by animateFloatAsState(
        targetValue = if (settingsPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "settingsScale",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(headerBgColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                HeadsetBrandMark(size = 34.dp)
                Column {
                    Text(
                        text = "JioDive Cinema",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Headset Companion",
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.inkMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = onToggleSearch,
                    interactionSource = searchInteraction,
                    shape = CircleShape,
                    color = searchBg,
                    border = BorderStroke(1.dp, searchBorder),
                    modifier = Modifier
                        .size(38.dp)
                        .graphicsLayer {
                            scaleX = searchScale
                            scaleY = searchScale
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = searchActive,
                            transitionSpec = {
                                (scaleIn(tween(180), initialScale = 0.75f) + fadeIn(tween(180))) togetherWith
                                    (scaleOut(tween(120), targetScale = 0.75f) + fadeOut(tween(120)))
                            },
                            label = "topSearchIcon",
                        ) { active ->
                            Icon(
                                imageVector = if (active) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = if (active) "Close search" else "Search library",
                                tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }

                Surface(
                    onClick = onOpenOpticsSheet,
                    interactionSource = settingsInteraction,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                    modifier = Modifier
                        .size(38.dp)
                        .graphicsLayer {
                            scaleX = settingsScale
                            scaleY = settingsScale
                        },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = "Display & Headset Settings",
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
                .alpha(dividerAlpha)
                .background(extras.cardBorder),
        )
    }
}

@Composable
private fun LibraryFeed(
    config: CinemaConfig,
    isTabActive: Boolean,
    selectedVideo: MediaItem?,
    filteredVideos: List<MediaItem>,
    availableFolders: List<Pair<String, Int>>,
    selectedFolder: String?,
    sortOrder: LibrarySortOrder,
    thumbnailRetryTokens: Map<String, Int>,
    watchHistory: List<WatchHistoryEntry>,
    historyByUri: Map<String, WatchHistoryEntry>,
    isSearchOpen: Boolean,
    searchQuery: String,
    category: MediaCategory,
    durationFilter: DurationFilterState,
    subtitleLabel: String?,
    subtitleCueCount: Int,
    hasStoragePermission: Boolean,
    isLoadingLibrary: Boolean,
    ambientGlow: Color,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onSearchChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onCategoryChange: (MediaCategory) -> Unit,
    onFolderSelect: (String?) -> Unit,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
    onDurationFilterChange: (DurationFilterState) -> Unit,
    onOpenCustomDurationSheet: () -> Unit,
    onResetFilters: () -> Unit,
    onSelectVideo: (MediaItem) -> Unit,
    onResumeHistoryEntry: (WatchHistoryEntry) -> Unit,
    onRemoveHistoryEntry: (WatchHistoryEntry) -> Unit,
    onClearWatchHistory: () -> Unit,
    onThumbnailLoaded: (String, Bitmap) -> Unit,
    onRequestStoragePermission: () -> Unit,
    onPickVideoFile: () -> Unit,
    onPickSubtitleFile: () -> Unit,
    onOpenSubtitleSheet: () -> Unit,
    onCardMoreActions: (MediaItem) -> Unit,
    onOpenStreamSheet: () -> Unit,
    onEnterVr: () -> Unit,
) {
    val extras = LocalCinemaExtras.current
    val searchFocusRequester = remember { FocusRequester() }
    val searchInteraction = remember { MutableInteractionSource() }
    val searchFocused by searchInteraction.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            delay(90)
            runCatching { searchFocusRequester.requestFocus() }
            keyboardController?.show()
        } else {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    val searchGlowActive = searchFocused || searchQuery.isNotEmpty()
    val searchBorderColor by animateColorAsState(
        targetValue = when {
            searchFocused -> extras.turquoiseAccent
            searchQuery.isNotEmpty() -> extras.turquoiseAccent.copy(alpha = 0.65f)
            else -> extras.cardBorder
        },
        animationSpec = tween(220),
        label = "searchRingColor",
    )
    val searchBorderWidth by animateDpAsState(
        targetValue = if (searchFocused) 1.75.dp else 1.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f),
        label = "searchRingWidth",
    )

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "hero_header") {
            Column(modifier = Modifier.fillMaxWidth()) {
                AnimatedVisibility(
                    visible = isSearchOpen,
                    enter = expandVertically(
                        animationSpec = spring(dampingRatio = 0.80f, stiffness = 420f),
                    ) + fadeIn(tween(220)),
                    exit = shrinkVertically(
                        animationSpec = tween(200, easing = FastOutSlowInEasing),
                    ) + fadeOut(tween(150)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = if (selectedVideo != null && searchQuery.isBlank()) 16.dp else 0.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(
                                width = searchBorderWidth,
                                color = searchBorderColor,
                                shape = RoundedCornerShape(22.dp),
                            ),
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            interactionSource = searchInteraction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
                            singleLine = true,
                            shape = RoundedCornerShape(22.dp),
                            placeholder = { Text("Search movies or streams") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null,
                                    tint = if (searchGlowActive) extras.turquoiseAccent else extras.inkMuted,
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        if (searchQuery.isNotEmpty()) {
                                            onSearchChange("")
                                        } else {
                                            onCloseSearch()
                                        }
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = if (searchQuery.isNotEmpty()) "Clear search" else "Close search",
                                        tint = extras.inkMuted,
                                    )
                                }
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.94f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.88f),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        )
                    }
                }

                AnimatedVisibility(
                    visible = selectedVideo != null && searchQuery.isBlank(),
                    enter = expandVertically(tween(220)) + fadeIn(tween(220)),
                    exit = shrinkVertically(tween(180)) + fadeOut(tween(140)),
                ) {
                    selectedVideo?.let { heroItem ->
                        SpotlightVideoCard(
                            item = heroItem,
                            config = config,
                            ambientGlow = ambientGlow,
                            listState = listState,
                            isActive = isTabActive && listState.firstVisibleItemIndex == 0 && searchQuery.isBlank(),
                            resumePositionMs = historyByUri[heroItem.uri.toString()]?.positionMs ?: 0L,
                            refreshNonce = thumbnailRetryTokens[heroItem.id] ?: 0,
                            onThumbnailLoaded = onThumbnailLoaded,
                            onPlayVr = onEnterVr,
                        )
                    }
                }
            }
        }

        if (searchQuery.isBlank()) {
            item(key = "source_actions") {
                val subtitlePillText = when {
                    subtitleLabel == null -> "Subtitles"
                    config.subtitleOffsetMs != 0L -> String.format(
                        Locale.US,
                        "CC %+.1fs",
                        config.subtitleOffsetMs / 1000.0,
                    )
                    else -> "CC ($subtitleCueCount)"
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SourcePillAction(
                        icon = Icons.Rounded.FolderOpen,
                        label = "Open File",
                        selected = false,
                        onClick = onPickVideoFile,
                        modifier = Modifier.weight(1f),
                    )
                    SourcePillAction(
                        icon = Icons.Rounded.Link,
                        label = "Stream URL",
                        selected = false,
                        onClick = onOpenStreamSheet,
                        modifier = Modifier.weight(1f),
                    )
                    SourcePillAction(
                        icon = Icons.Rounded.Subtitles,
                        label = subtitlePillText,
                        selected = subtitleLabel != null,
                        onClick = if (subtitleLabel != null) onOpenSubtitleSheet else onPickSubtitleFile,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (watchHistory.isNotEmpty() && searchQuery.isBlank()) {
            item(key = "continue_watching") {
                ContinueWatchingSection(
                    entries = watchHistory,
                    thumbnailRetryTokens = thumbnailRetryTokens,
                    onResumeEntry = onResumeHistoryEntry,
                    onRemoveEntry = onRemoveHistoryEntry,
                    onClearAll = onClearWatchHistory,
                )
            }
        }

        item(key = "library_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "${filteredVideos.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = extras.inkMuted,
                    )
                }
                if (!hasStoragePermission) {
                    Text(
                        text = "Scan phone videos",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onRequestStoragePermission)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Gliding single-track segmented category selector
        item(key = "category_tabs") {
            SlidingCategoryTrack(
                selected = category,
                onSelect = onCategoryChange,
            )
        }

        // Fast duration filter strip (All, 30m+, 1h+, 2h+, Custom)
        item(key = "duration_filters") {
            DurationFilterRow(
                filter = durationFilter,
                onSelectPreset = { preset ->
                    if (preset == DurationFilterPreset.CUSTOM) {
                        onOpenCustomDurationSheet()
                    } else {
                        onDurationFilterChange(durationFilter.copy(preset = preset))
                    }
                },
            )
        }

        // Sort (Recent / Longest / A-Z) & discovered Folder quick chips
        item(key = "sort_folder_filters") {
            SortAndFolderFilterRow(
                sortOrder = sortOrder,
                folders = availableFolders,
                selectedFolder = selectedFolder,
                onSortOrderChange = onSortOrderChange,
                onFolderSelect = onFolderSelect,
            )
        }

        if (isLoadingLibrary) {
            item(key = "loading_note") {
                Text(
                    text = "Scanning local storage...",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.inkMuted,
                )
            }
        }

        if (filteredVideos.isEmpty() && !isLoadingLibrary) {
            item(key = "empty_filtered_library") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, extras.cardBorder),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "No videos match current filters",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Try switching category, folder, or widening the length filter.",
                            style = MaterialTheme.typography.bodySmall,
                            color = extras.inkMuted,
                        )
                        OutlinedButton(onClick = onResetFilters, shape = RoundedCornerShape(12.dp)) {
                            Text("Reset Filters")
                        }
                    }
                }
            }
        } else {
            val rows = filteredVideos.chunked(2)
            items(rows, key = { row -> row.joinToString("_") { it.id } }) { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateItem(
                            fadeInSpec = tween(220),
                            fadeOutSpec = tween(160),
                            placementSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    pair.forEach { video ->
                        VideoGridCard(
                            item = video,
                            selected = selectedVideo?.id == video.id,
                            resumePositionMs = historyByUri[video.uri.toString()]?.positionMs ?: 0L,
                            refreshNonce = thumbnailRetryTokens[video.id] ?: 0,
                            onThumbnailLoaded = onThumbnailLoaded,
                            onClick = { onSelectVideo(video) },
                            onMoreActions = { onCardMoreActions(video) },
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
}

@Composable
private fun ContinueWatchingSection(
    entries: List<WatchHistoryEntry>,
    thumbnailRetryTokens: Map<String, Int>,
    onResumeEntry: (WatchHistoryEntry) -> Unit,
    onRemoveEntry: (WatchHistoryEntry) -> Unit,
    onClearAll: () -> Unit,
) {
    val extras = LocalCinemaExtras.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    text = "Continue Watching",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = "Clear",
                style = MaterialTheme.typography.labelMedium,
                color = extras.inkMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClearAll)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(entries, key = { it.uriString }) { entry ->
                ContinueWatchingCard(
                    entry = entry,
                    refreshNonce = thumbnailRetryTokens[entry.mediaId] ?: 0,
                    onClick = { onResumeEntry(entry) },
                    onRemove = { onRemoveEntry(entry) },
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    entry: WatchHistoryEntry,
    refreshNonce: Int,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val extras = LocalCinemaExtras.current
    val resolvedThumb = rememberLazyThumbnailState(
        cacheKey = entry.mediaId,
        uri = Uri.parse(entry.uriString),
        isStream = entry.isStream,
        initialThumbnail = null,
        refreshNonce = refreshNonce,
    )

    Column(
        modifier = Modifier
            .width(164.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, extras.cardBorder),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ThumbnailArtworkFrame(
                    thumbnailState = resolvedThumb,
                    title = entry.title,
                    uriString = entry.uriString,
                    isStream = entry.isStream,
                    shimmerIconSize = 22.dp,
                    compactPoster = true,
                    modifier = Modifier.fillMaxSize(),
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                            ),
                        ),
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(34.dp)
                        .clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier.size(22.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Remove from history",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }

                val timeLabel = if (entry.positionMs > 1_000L && entry.durationMs > 0L) {
                    "${MediaRepository.formatDuration(entry.positionMs)} / ${MediaRepository.formatDuration(entry.durationMs)}"
                } else if (entry.positionMs > 1_000L) {
                    MediaRepository.formatDuration(entry.positionMs)
                } else {
                    MediaRepository.formatDuration(entry.durationMs)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.78f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    )
                }

                if (entry.progressFraction > 0.01f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.2f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(entry.progressFraction)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(horizontal = 2.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (entry.positionMs > 2_000L) {
                    "Resume ${MediaRepository.formatDuration(entry.positionMs)}"
                } else if (entry.isStream) {
                    "Stream"
                } else {
                    "Local Video"
                },
                style = MaterialTheme.typography.bodySmall,
                color = extras.inkMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DurationFilterRow(
    filter: DurationFilterState,
    onSelectPreset: (DurationFilterPreset) -> Unit,
) {
    val extras = LocalCinemaExtras.current
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(DurationFilterPreset.entries, key = { it.name }) { preset ->
            val selected = filter.preset == preset
            val label = if (preset == DurationFilterPreset.CUSTOM && selected) {
                filter.chipLabel()
            } else {
                preset.label
            }
            val bg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(180),
                label = "durBg",
            )
            val fg by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else extras.inkMuted,
                animationSpec = tween(180),
                label = "durFg",
            )
            val borderCol by animateColorAsState(
                targetValue = if (selected) MaterialTheme.colorScheme.primary else extras.cardBorder,
                animationSpec = tween(180),
                label = "durBorder",
            )

            Surface(
                onClick = { onSelectPreset(preset) },
                shape = RoundedCornerShape(16.dp),
                color = bg,
                border = BorderStroke(1.dp, borderCol),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (preset == DurationFilterPreset.CUSTOM) {
                        Icon(
                            imageVector = Icons.Rounded.Schedule,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else fg,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = fg,
                    )
                }
            }
        }
    }
}

@Composable
private fun SortAndFolderFilterRow(
    sortOrder: LibrarySortOrder,
    folders: List<Pair<String, Int>>,
    selectedFolder: String?,
    onSortOrderChange: (LibrarySortOrder) -> Unit,
    onFolderSelect: (String?) -> Unit,
) {
    val extras = LocalCinemaExtras.current
    val haptic = LocalHapticFeedback.current

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(LibrarySortOrder.entries, key = { "sort_${it.name}" }) { order ->
            val active = sortOrder == order
            FilterChipPill(
                label = order.label,
                selected = active,
                leadingIcon = if (active) Icons.Rounded.SwapVert else null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSortOrderChange(order)
                },
            )
        }

        if (folders.isNotEmpty()) {
            item(key = "sort_folder_divider") {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .width(1.dp)
                        .height(18.dp)
                        .background(extras.cardBorder),
                )
            }

            item(key = "folder_all") {
                FilterChipPill(
                    label = "All folders",
                    selected = selectedFolder == null,
                    leadingIcon = Icons.Rounded.Folder,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFolderSelect(null)
                    },
                )
            }

            items(folders, key = { "folder_${it.first}" }) { (folder, count) ->
                val active = selectedFolder == folder
                FilterChipPill(
                    label = "$folder ($count)",
                    selected = active,
                    leadingIcon = null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onFolderSelect(if (active) null else folder)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    leadingIcon: ImageVector?,
    onClick: () -> Unit,
) {
    val extras = LocalCinemaExtras.current
    val bg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        animationSpec = tween(180),
        label = "chipBg",
    )
    val borderCol by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f) else extras.cardBorder,
        animationSpec = tween(180),
        label = "chipBorder",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else extras.inkMuted,
        animationSpec = tween(180),
        label = "chipFg",
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, borderCol),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else fg,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fg,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SlidingCategoryTrack(
    selected: MediaCategory,
    onSelect: (MediaCategory) -> Unit,
) {
    val extras = LocalCinemaExtras.current
    val entries = remember {
        listOf(
            MediaCategory.ALL to "All",
            MediaCategory.DEVICE to "On Device",
            MediaCategory.STREAMS to "Streams",
        )
    }
    val selectedIdx = entries.indexOfFirst { it.first == selected }.coerceAtLeast(0)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
    ) {
        val segmentW = maxWidth / entries.size
        val segmentPx = with(LocalDensity.current) { segmentW.toPx() }
        val indicatorOffsetPx by animateFloatAsState(
            targetValue = segmentPx * selectedIdx,
            animationSpec = spring(dampingRatio = 0.80f, stiffness = 460f),
            label = "categorySlideOffset",
        )

        Box(
            modifier = Modifier
                .graphicsLayer { translationX = indicatorOffsetPx }
                .width(segmentW)
                .fillMaxHeight()
                .clip(RoundedCornerShape(17.dp))
                .background(MaterialTheme.colorScheme.primary),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            entries.forEach { (cat, label) ->
                val isSelected = cat == selected
                val fg by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else extras.inkMuted,
                    animationSpec = tween(180),
                    label = "catFg",
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(17.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelect(cat) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = fg,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotlightVideoCard(
    item: MediaItem,
    config: CinemaConfig,
    ambientGlow: Color,
    listState: LazyListState,
    isActive: Boolean,
    resumePositionMs: Long,
    refreshNonce: Int,
    onThumbnailLoaded: (String, Bitmap) -> Unit,
    onPlayVr: () -> Unit,
) {
    val thumbState = rememberLazyThumbnailState(
        cacheKey = item.id,
        uri = item.uri,
        isStream = item.isStream,
        initialThumbnail = item.thumbnail,
        refreshNonce = refreshNonce,
        onLoaded = onThumbnailLoaded,
    )
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "spotlightPressScale",
    )

    // Infinite states + selection beam sweep read strictly in draw/graphicsLayer (zero 120Hz recomposition; paused when scrolled away)
    val inf = if (isActive) rememberInfiniteTransition(label = "heroMotion") else null
    val kenBurnsState = inf?.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "kenBurns",
    )
    val playPulseState = inf?.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "spotlightPlayPulse",
    )
    val selectionSweep = remember { Animatable(1f) }
    LaunchedEffect(item.id) {
        selectionSweep.snapTo(-0.35f)
        selectionSweep.animateTo(
            targetValue = 1.35f,
            animationSpec = tween(durationMillis = 720, easing = FastOutSlowInEasing),
        )
    }
    val sweepColors = remember(ambientGlow) {
        listOf(
            Color.Transparent,
            ambientGlow.copy(alpha = 0.28f),
            Color.White.copy(alpha = 0.14f),
            Color.Transparent,
        )
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF12171A),
        border = BorderStroke(1.2.dp, ambientGlow.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .graphicsLayer {
                val p = if (listState.firstVisibleItemIndex > 1) {
                    1f
                } else {
                    (listState.firstVisibleItemScrollOffset / 480f).coerceIn(0f, 1f)
                }
                val s = scale * (1f - 0.04f * p)
                scaleX = s
                scaleY = s
                alpha = 1f - 0.25f * p
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onPlayVr),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val t = selectionSweep.value
                    if (t in -0.34f..1.34f) {
                        val bandW = size.width * 0.48f
                        val startX = t * size.width - bandW * 0.5f
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = sweepColors,
                                start = Offset(startX, 0f),
                                end = Offset(startX + bandW, size.height),
                            ),
                        )
                    }
                },
        ) {
            ThumbnailArtworkFrame(
                thumbnailState = thumbState,
                title = item.title,
                uriString = item.uri.toString(),
                isStream = item.isStream,
                width = item.width,
                height = item.height,
                folderName = item.folderName,
                shimmerIconSize = 42.dp,
                compactPoster = false,
                kenBurnsScale = { kenBurnsState?.value ?: 1f },
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.04f),
                                Color.Black.copy(alpha = 0.24f),
                                Color.Black.copy(alpha = 0.86f),
                            ),
                        ),
                    ),
            )

            val modeLabel = if (config.videoFormat == VideoFormat.FLAT_2D) {
                "${config.environment.title} · ${config.screenPreset.title}"
            } else {
                "${config.environment.title} · ${config.videoFormat.label}"
            }
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.56f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.14f)),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp),
            ) {
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                AnimatedContent(
                    targetState = item,
                    contentKey = { it.id },
                    transitionSpec = {
                        (slideInVertically(tween(220)) { it / 3 } + fadeIn(tween(220))) togetherWith
                            (slideOutVertically(tween(150)) { -it / 3 } + fadeOut(tween(150)))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 12.dp),
                    label = "heroMetaSwap",
                ) { meta ->
                    Column {
                        Text(
                            text = meta.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val kindLabel = if (meta.isStream) "Stream" else meta.folderName.ifBlank { "On Device" }
                        val resumeText = if (resumePositionMs > 2_000L) {
                            " · Resume ${MediaRepository.formatDuration(resumePositionMs)}"
                        } else {
                            ""
                        }
                        Text(
                            text = "${MediaRepository.formatDuration(meta.durationMs)} · $kindLabel$resumeText",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.78f),
                        )
                    }
                }

                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .graphicsLayer {
                                val f = playPulseState?.value ?: 0.35f
                                val s = 0.94f + 0.18f * f
                                scaleX = s
                                scaleY = s
                                alpha = 0.22f + 0.24f * f
                            }
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                val s = 1f + 0.03f * (playPulseState?.value ?: 0.35f)
                                scaleX = s
                                scaleY = s
                            },
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
        }
    }
}

@Composable
private fun SourcePillAction(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "pillActionScale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(200),
        label = "pillActionBg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(200),
        label = "pillActionFg",
    )

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) {
        Row(
            modifier = Modifier.padding(vertical = 11.dp, horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else contentColor.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.size(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VideoGridCard(
    item: MediaItem,
    selected: Boolean,
    resumePositionMs: Long,
    refreshNonce: Int,
    onThumbnailLoaded: (String, Bitmap) -> Unit,
    onClick: () -> Unit,
    onMoreActions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbState = rememberLazyThumbnailState(
        cacheKey = item.id,
        uri = item.uri,
        isStream = item.isStream,
        initialThumbnail = item.thumbnail,
        refreshNonce = refreshNonce,
        onLoaded = onThumbnailLoaded,
    )
    val extras = LocalCinemaExtras.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "gridCardScale",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else extras.cardBorder.copy(alpha = 0.45f),
        animationSpec = tween(200),
        label = "gridBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 0.75.dp,
        animationSpec = tween(200),
        label = "gridBorderW",
    )
    val titleColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
        animationSpec = tween(200),
        label = "gridTitle",
    )

    val subtitleText = remember(item.uri, item.isStream, item.folderName) {
        if (item.isStream) {
            LibraryCatalog.extractHostLabel(item.uri.toString())
        } else {
            item.folderName.ifBlank { "Device Video" }
        }
    }

    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMoreActions()
                },
            ),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(borderWidth, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ThumbnailArtworkFrame(
                    thumbnailState = thumbState,
                    title = item.title,
                    uriString = item.uri.toString(),
                    isStream = item.isStream,
                    width = item.width,
                    height = item.height,
                    folderName = item.folderName,
                    shimmerIconSize = 24.dp,
                    compactPoster = true,
                    modifier = Modifier.fillMaxSize(),
                )

                MediaFormatBadgePill(
                    item = item,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )

                if (resumePositionMs > 2_000L && item.durationMs > 0L) {
                    val progress = (resumePositionMs.toFloat() / item.durationMs.toFloat()).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.Black.copy(alpha = 0.35f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.72f),
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
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.inkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onMoreActions),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Video actions",
                    tint = extras.inkMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun TheaterConfigFeed(
    config: CinemaConfig,
    isTabActive: Boolean,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onUpdateConfig: (CinemaConfig) -> Unit,
    onOpenOpticsSheet: () -> Unit,
    onEnterVr: () -> Unit,
) {
    val extras = LocalCinemaExtras.current

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "stage_preview") {
            InteractiveTheaterStagePreview(config = config, isVisible = isTabActive)
        }

        item(key = "env_header") {
            SectionEyebrow("Viewing Environment")
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
                    val cardInteraction = remember { MutableInteractionSource() }
                    val pressed by cardInteraction.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = when {
                            pressed -> 0.94f
                            selected -> 1.02f
                            else -> 1f
                        },
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
                        label = "envCardScale",
                    )
                    val bg by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        animationSpec = tween(200),
                        label = "envBg",
                    )
                    val border by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        animationSpec = tween(200),
                        label = "envBorder",
                    )
                    val tint by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary else extras.inkMuted,
                        animationSpec = tween(200),
                        label = "envTint",
                    )

                    Surface(
                        onClick = { onUpdateConfig(config.copy(environment = env)) },
                        interactionSource = cardInteraction,
                        shape = RoundedCornerShape(16.dp),
                        color = bg,
                        border = if (selected) BorderStroke(1.5.dp, border) else null,
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = tint,
                            )
                            Text(text = env.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = env.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = extras.inkMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        item(key = "seat_header") {
            SectionEyebrow("Screen Distance")
        }

        item(key = "seat_cards") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ScreenPreset.entries.forEach { preset ->
                    val selected = config.screenPreset == preset
                    val cardInteraction = remember { MutableInteractionSource() }
                    val pressed by cardInteraction.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = when {
                            pressed -> 0.94f
                            selected -> 1.02f
                            else -> 1f
                        },
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
                        label = "presetScale",
                    )
                    val bg by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        animationSpec = tween(200),
                        label = "presetBg",
                    )
                    val border by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        animationSpec = tween(200),
                        label = "presetBorder",
                    )
                    val tint by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary else extras.inkMuted,
                        animationSpec = tween(200),
                        label = "presetTint",
                    )

                    Surface(
                        onClick = { onUpdateConfig(config.copy(screenPreset = preset)) },
                        interactionSource = cardInteraction,
                        shape = RoundedCornerShape(16.dp),
                        color = bg,
                        border = if (selected) BorderStroke(1.5.dp, border) else null,
                        modifier = Modifier
                            .weight(1f)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AirlineSeatReclineExtra,
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(text = preset.title, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "${preset.widthMeters.toInt()}m curved",
                                style = MaterialTheme.typography.bodySmall,
                                color = extras.inkMuted,
                            )
                        }
                    }
                }
            }
        }

        item(key = "format_header") {
            SectionEyebrow("Stereo Format")
        }

        item(key = "format_chips") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(VideoFormat.entries) { fmt ->
                    val selected = config.videoFormat == fmt
                    val pillInteraction = remember { MutableInteractionSource() }
                    val pressed by pillInteraction.collectIsPressedAsState()
                    val scale by animateFloatAsState(
                        targetValue = if (pressed) 0.93f else 1f,
                        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
                        label = "fmtScale",
                    )
                    val bg by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        animationSpec = tween(180),
                        label = "fmtBg",
                    )
                    val fg by animateColorAsState(
                        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else extras.inkMuted,
                        animationSpec = tween(180),
                        label = "fmtFg",
                    )
                    Surface(
                        onClick = { onUpdateConfig(config.copy(videoFormat = fmt)) },
                        interactionSource = pillInteraction,
                        shape = RoundedCornerShape(16.dp),
                        color = bg,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    ) {
                        Text(
                            text = fmt.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = fg,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }

        item(key = "optics_card") {
            Surface(
                onClick = onOpenOpticsSheet,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Display & Headset Optics", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Lens alignment, sharpening & appearance",
                            style = MaterialTheme.typography.bodySmall,
                            color = extras.inkMuted,
                        )
                    }
                    Text(
                        text = "Configure",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        item(key = "launch_cta") {
            val btnInteraction = remember { MutableInteractionSource() }
            val pressed by btnInteraction.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (pressed) 0.97f else 1f,
                animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
                label = "ctaScale",
            )
            Button(
                onClick = onEnterVr,
                interactionSource = btnInteraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Enter VR Theater")
            }
        }
    }
}

/**
 * Interactive animated 2.5D cinema preview reacting smoothly in the draw phase
 * (zero VSync recompositions).
 */
@Composable
private fun InteractiveTheaterStagePreview(config: CinemaConfig, isVisible: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val targetWidthFrac = when (config.screenPreset) {
        ScreenPreset.IMAX -> 0.74f
        ScreenPreset.STANDARD -> 0.56f
        ScreenPreset.BALCONY -> 0.44f
    }
    val targetCurveFrac = when (config.screenPreset) {
        ScreenPreset.IMAX -> 1.0f
        ScreenPreset.STANDARD -> 0.65f
        ScreenPreset.BALCONY -> 0.42f
    }
    val targetTiltFrac = if (config.environment == TheaterEnvironment.BED) 1f else 0f
    val targetWallsAlpha = if (config.environment == TheaterEnvironment.CINEMA) 1f else 0f

    val screenWState = animateFloatAsState(
        targetValue = targetWidthFrac,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "previewScreenW",
    )
    val curveState = animateFloatAsState(
        targetValue = targetCurveFrac,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow),
        label = "previewCurve",
    )
    val bedTiltState = animateFloatAsState(
        targetValue = targetTiltFrac,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "previewBedTilt",
    )
    val hallAlphaState = animateFloatAsState(
        targetValue = targetWallsAlpha,
        animationSpec = tween(320),
        label = "previewHallAlpha",
    )

    val inf = if (isVisible) rememberInfiniteTransition(label = "stageBeamPulse") else null
    val shimmerState = inf?.animateFloat(
        initialValue = 0.78f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "screenGlowPulse",
    )

    val screenPath = remember { Path() }
    val starOffsets = remember {
        listOf(
            Offset(0.14f, 0.22f), Offset(0.24f, 0.66f), Offset(0.82f, 0.28f),
            Offset(0.76f, 0.72f), Offset(0.32f, 0.18f), Offset(0.68f, 0.20f),
        )
    }
    val stageGlowColors = remember { ArrayList<Color>(2).apply { add(Color.Transparent); add(Color.Transparent) } }
    val screenGradColors = remember { ArrayList<Color>(2).apply { add(Color.Transparent); add(Color.Transparent) } }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF0A0D0F),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val screenWFrac = screenWState.value
                val curveFrac = curveState.value
                val bedTilt = bedTiltState.value
                val hallAlpha = hallAlphaState.value
                val shimmer = shimmerState?.value ?: 0.88f

                val screenCy = h * (0.44f - 0.14f * bedTilt)
                stageGlowColors[0] = accent.copy(alpha = 0.24f * shimmer)
                stageGlowColors[1] = Color.Transparent
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = stageGlowColors,
                        center = Offset(w * 0.5f, screenCy),
                        radius = w * 0.42f,
                    ),
                    radius = w * 0.42f,
                    center = Offset(w * 0.5f, screenCy),
                )

                if (hallAlpha > 0.02f) {
                    val ribColor = Color.White.copy(alpha = 0.12f * hallAlpha)
                    for (i in 1..3) {
                        val t = i / 4f
                        val lx = w * (0.06f + 0.12f * t)
                        val rx = w - lx
                        drawLine(ribColor, Offset(lx, h * (0.08f + 0.07f * t)), Offset(lx, h * (0.92f - 0.08f * t)), strokeWidth = 1.4.dp.toPx())
                        drawLine(ribColor, Offset(rx, h * (0.08f + 0.07f * t)), Offset(rx, h * (0.92f - 0.08f * t)), strokeWidth = 1.4.dp.toPx())
                    }
                    for (s in -3..3) {
                        val sx = w * 0.5f + s * w * 0.095f
                        drawRoundRect(
                            color = Color(0xFF1A2226).copy(alpha = 0.9f * hallAlpha),
                            topLeft = Offset(sx - 12.dp.toPx(), h * 0.78f),
                            size = Size(24.dp.toPx(), 16.dp.toPx()),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        )
                    }
                } else if (config.environment == TheaterEnvironment.VOID) {
                    for (i in starOffsets.indices) {
                        val norm = starOffsets[i]
                        drawCircle(
                            color = Color.White.copy(alpha = 0.25f * shimmer),
                            radius = 1.4.dp.toPx(),
                            center = Offset(norm.x * w, norm.y * h),
                        )
                    }
                }

                val sw = w * screenWFrac
                val sh = (sw * 0.44f).coerceAtMost(h * 0.54f)
                val left = (w - sw) * 0.5f
                val right = left + sw
                val topY = screenCy - sh * 0.5f
                val botY = screenCy + sh * 0.5f
                val bow = 10.dp.toPx() * curveFrac

                screenPath.reset()
                screenPath.moveTo(left, topY)
                screenPath.quadraticTo(w * 0.5f, topY + bow, right, topY)
                screenPath.lineTo(right, botY)
                screenPath.quadraticTo(w * 0.5f, botY - bow, left, botY)
                screenPath.close()

                screenGradColors[0] = accent.copy(alpha = 0.42f * shimmer)
                screenGradColors[1] = Color(0xFF0E2B33).copy(alpha = 0.92f)
                drawPath(
                    path = screenPath,
                    brush = Brush.verticalGradient(colors = screenGradColors),
                )
                drawPath(
                    path = screenPath,
                    color = accent.copy(alpha = 0.85f),
                    style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round),
                )

                if (config.videoFormat == VideoFormat.HALF_SBS || config.videoFormat == VideoFormat.FULL_SBS) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.45f),
                        start = Offset(w * 0.5f, topY + bow + 4.dp.toPx()),
                        end = Offset(w * 0.5f, botY - bow - 4.dp.toPx()),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                } else if (config.videoFormat == VideoFormat.HALF_OU || config.videoFormat == VideoFormat.FULL_OU) {
                    drawLine(
                        color = Color.White.copy(alpha = 0.45f),
                        start = Offset(left + 6.dp.toPx(), screenCy),
                        end = Offset(right - 6.dp.toPx(), screenCy),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color.Black.copy(alpha = 0.62f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
            ) {
                Text(
                    text = "${config.environment.title} · ${config.screenPreset.widthMeters.toInt()}m (${config.screenPreset.curveDegrees.toInt()}° curve)",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.86f),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionEyebrow(label: String) {
    val extras = LocalCinemaExtras.current
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.8.sp,
        ),
        color = extras.inkMuted,
    )
}
