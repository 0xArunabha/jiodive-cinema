package com.jiodive.cinema.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiodive.cinema.data.LibraryCatalog
import com.jiodive.cinema.data.MediaRepository
import com.jiodive.cinema.data.ThumbnailResult
import com.jiodive.cinema.model.MediaItem
import kotlin.math.abs

private data class PosterPalette(
    val topColor: Color,
    val bottomColor: Color,
    val glowAccent: Color,
)

private val PosterPalettes = listOf(
    PosterPalette(
        topColor = Color(0xFF0E2930),
        bottomColor = Color(0xFF090F12),
        glowAccent = Color(0xFF38C6BD),
    ),
    PosterPalette(
        topColor = Color(0xFF17223B),
        bottomColor = Color(0xFF0A0D16),
        glowAccent = Color(0xFF5FA8FF),
    ),
    PosterPalette(
        topColor = Color(0xFF102A22),
        bottomColor = Color(0xFF09120F),
        glowAccent = Color(0xFF56B88C),
    ),
)

@Composable
fun rememberLazyThumbnailState(
    cacheKey: String,
    uri: Uri,
    isStream: Boolean,
    initialThumbnail: Bitmap?,
    refreshNonce: Int = 0,
    onLoaded: (String, Bitmap) -> Unit = { _, _ -> },
): ThumbnailResult {
    val context = LocalContext.current
    var handledNonce by remember(cacheKey) { mutableIntStateOf(refreshNonce) }
    var state by remember(cacheKey, initialThumbnail) {
        val seed = initialThumbnail?.takeIf { !it.isRecycled } ?: MediaRepository.getCachedThumbnail(cacheKey)
        mutableStateOf<ThumbnailResult>(
            when {
                seed != null -> ThumbnailResult.Loaded(seed)
                MediaRepository.hasThumbnailFailed(cacheKey) -> ThumbnailResult.Unavailable
                else -> ThumbnailResult.Loading
            },
        )
    }

    LaunchedEffect(cacheKey, uri, isStream, initialThumbnail, refreshNonce) {
        val forceRetry = refreshNonce > handledNonce
        if (!forceRetry) {
            val cached = initialThumbnail?.takeIf { !it.isRecycled } ?: MediaRepository.getCachedThumbnail(cacheKey)
            if (cached != null) {
                state = ThumbnailResult.Loaded(cached)
                return@LaunchedEffect
            }
        } else {
            handledNonce = refreshNonce
            state = ThumbnailResult.Loading
        }
        val result = MediaRepository.loadThumbnailStateByUri(
            context = context,
            cacheKey = cacheKey,
            uri = uri,
            isStream = isStream,
            fallback = if (!forceRetry) initialThumbnail else null,
            forceRetry = forceRetry,
        )
        state = result
        if (result is ThumbnailResult.Loaded) {
            onLoaded(cacheKey, result.bitmap)
        }
    }

    // Automatically upgrade from Fallback/Loading if a slow background stream decode completes later
    val isWaitingForThumb = state !is ThumbnailResult.Loaded
    LaunchedEffect(cacheKey, isWaitingForThumb) {
        if (!isWaitingForThumb) return@LaunchedEffect
        MediaRepository.thumbnailUpdates.collect {
            val cached = MediaRepository.getCachedThumbnail(cacheKey)
            if (cached != null) {
                state = ThumbnailResult.Loaded(cached)
                onLoaded(cacheKey, cached)
            }
        }
    }

    return state
}

@Composable
fun ThumbnailArtworkFrame(
    thumbnailState: ThumbnailResult,
    title: String,
    uriString: String,
    isStream: Boolean,
    modifier: Modifier = Modifier,
    width: Int = 0,
    height: Int = 0,
    folderName: String = "",
    shimmerIconSize: Dp = 24.dp,
    compactPoster: Boolean = true,
    kenBurnsScale: (() -> Float)? = null,
) {
    Crossfade(
        targetState = thumbnailState,
        animationSpec = tween(durationMillis = 320),
        modifier = modifier,
        label = "thumbArtworkFade",
    ) { resolved ->
        when (resolved) {
            is ThumbnailResult.Loaded -> {
                val revealScale = remember(resolved.bitmap) { Animatable(1.045f) }
                LaunchedEffect(resolved.bitmap) {
                    revealScale.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                    )
                }
                Image(
                    bitmap = resolved.bitmap.asImageBitmap(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val baseScale = if (kenBurnsScale != null) kenBurnsScale() else 1f
                            val combined = baseScale * revealScale.value
                            scaleX = combined
                            scaleY = combined
                        },
                )
            }

            ThumbnailResult.Loading -> {
                CinemaPlaceholderShimmer(iconSize = shimmerIconSize)
            }

            ThumbnailResult.Unavailable -> {
                StylizedMediaPosterFallback(
                    title = title,
                    uriString = uriString,
                    isStream = isStream,
                    width = width,
                    height = height,
                    folderName = folderName,
                    compact = compactPoster,
                )
            }
        }
    }
}

@Composable
fun CinemaPlaceholderShimmer(iconSize: Dp) {
    val inf = rememberInfiniteTransition(label = "cinemaShimmer")
    val shiftState = inf.animateFloat(
        initialValue = -0.45f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerShift",
    )
    val shimmerColors = remember {
        listOf(Color.Transparent, Color(0xFF283338), Color.Transparent)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds()
            .background(Color(0xFF171D20)),
        contentAlignment = Alignment.Center,
    ) {
        // Cached shimmer gradient translated smoothly on GPU graphicsLayer (zero per-frame Brush allocations)
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = (shiftState.value - 0.5f) * size.width
                    scaleX = 0.52f
                }
                .drawWithCache {
                    val brush = Brush.horizontalGradient(
                        colors = shimmerColors,
                        startX = 0f,
                        endX = size.width,
                    )
                    onDrawBehind {
                        drawRect(brush = brush)
                    }
                },
        )
        Icon(
            imageVector = Icons.Rounded.Theaters,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.16f),
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun StylizedMediaPosterFallback(
    title: String,
    uriString: String,
    isStream: Boolean,
    width: Int = 0,
    height: Int = 0,
    folderName: String = "",
    compact: Boolean = true,
) {
    val palette = remember(title) {
        val idx = abs(title.hashCode()) % PosterPalettes.size
        PosterPalettes[idx]
    }
    val formatBadge = remember(uriString, isStream, width, height) {
        LibraryCatalog.formatBadge(
            pathOrUrl = uriString,
            isStream = isStream,
            width = width,
            height = height,
        )
    }
    val originTag = remember(uriString, isStream, folderName) {
        if (isStream) {
            LibraryCatalog.extractHostLabel(uriString)
        } else {
            folderName.ifBlank { "Local Media" }
        }
    }
    val ambientPulse = if (!compact) {
        rememberInfiniteTransition(label = "posterFallbackGlow").animateFloat(
            initialValue = 0.72f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "fallbackBeam",
        )
    } else {
        null
    }
    val glowColors = remember(palette) {
        listOf(
            palette.glowAccent.copy(alpha = 0.28f),
            Color.Transparent,
        )
    }

    val arcRect = remember { Path() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(palette.topColor, palette.bottomColor),
                ),
            ),
    ) {
        // Cached ambient beam glow scaled via GPU graphicsLayer (zero shader rebuilds; compact grid cards stay static at zero VSync cost)
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = ambientPulse?.value ?: 0.88f
                    scaleX = p
                    scaleY = p
                    alpha = (0.70f + 0.30f * ((p - 0.72f) / 0.32f)).coerceIn(0.65f, 1f)
                }
                .drawWithCache {
                    val c = Offset(size.width * 0.5f, size.height * 0.42f)
                    val r = size.width * 0.48f
                    val radial = Brush.radialGradient(colors = glowColors, center = c, radius = r)
                    onDrawBehind {
                        drawCircle(brush = radial, radius = r, center = c)
                    }
                },
        )

        // Cached architectural curved theater wireframe
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val sw = w * 0.58f
                    val sh = h * 0.40f
                    val left = (w - sw) * 0.5f
                    val right = left + sw
                    val top = h * 0.24f
                    val bot = top + sh
                    val bow = 6.dp.toPx()

                    arcRect.reset()
                    arcRect.moveTo(left, top)
                    arcRect.quadraticTo(w * 0.5f, top + bow, right, top)
                    arcRect.lineTo(right, bot)
                    arcRect.quadraticTo(w * 0.5f, bot - bow, left, bot)
                    arcRect.close()

                    val stroke = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
                    val wireColor = palette.glowAccent.copy(alpha = 0.20f)
                    onDrawBehind {
                        drawPath(path = arcRect, color = wireColor, style = stroke)
                    }
                },
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(0.75.dp, palette.glowAccent.copy(alpha = 0.34f)),
                modifier = Modifier.size(if (compact) 30.dp else 44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isStream) Icons.Rounded.Link else Icons.Rounded.Theaters,
                        contentDescription = null,
                        tint = palette.glowAccent.copy(alpha = 0.92f),
                        modifier = Modifier.size(if (compact) 16.dp else 22.dp),
                    )
                }
            }
            if (!compact) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = palette.glowAccent.copy(alpha = 0.18f),
                    ) {
                        Text(
                            text = formatBadge,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = palette.glowAccent,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        text = originTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
fun MediaFormatBadgePill(
    item: MediaItem,
    modifier: Modifier = Modifier,
) {
    val badge = remember(item.uri, item.isStream, item.width, item.height) {
        LibraryCatalog.formatBadge(
            pathOrUrl = item.uri.toString(),
            isStream = item.isStream,
            width = item.width,
            height = item.height,
        )
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = Color.Black.copy(alpha = 0.68f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.14f)),
        modifier = modifier,
    ) {
        Text(
            text = badge,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
            ),
            color = Color.White.copy(alpha = 0.92f),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
