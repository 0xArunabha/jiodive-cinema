package com.jiodive.cinema.ui.screens

import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiodive.cinema.data.LibraryCatalog
import com.jiodive.cinema.data.MediaRepository
import com.jiodive.cinema.model.MediaItem
import com.jiodive.cinema.model.SubtitleTextSize
import com.jiodive.cinema.ui.theme.LocalCinemaExtras
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSyncBottomSheet(
    subtitleLabel: String,
    subtitleCueCount: Int,
    offsetMs: Long,
    textSize: SubtitleTextSize,
    onOffsetChange: (Long) -> Unit,
    onTextSizeChange: (SubtitleTextSize) -> Unit,
    onReplaceFile: () -> Unit,
    onClearSubtitles: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extras = LocalCinemaExtras.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Local drag mirror avoids 120Hz SharedPreferences flushes while keeping live preview responsive
    var draggingOffsetMs by remember(offsetMs) { mutableStateOf<Long?>(null) }
    val activeOffsetMs = draggingOffsetMs ?: offsetMs
    val animatedPreviewSp by animateFloatAsState(
        targetValue = textSize.previewSp,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 420f),
        label = "subPreviewSp",
    )
    val previewBorderCol by animateColorAsState(
        targetValue = if (activeOffsetMs != 0L) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        } else {
            extras.cardBorder
        },
        animationSpec = tween(220),
        label = "subPreviewBorder",
    )

    fun applyOffset(targetMs: Long) {
        draggingOffsetMs = null
        onOffsetChange(targetMs.coerceIn(-3_000L, 3_000L))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetTopShape,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Subtitles & Sync",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Fine-tune timing offset and VR caption scale",
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.inkMuted,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // Loaded track metadata + quick Replace button
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, extras.cardBorder),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Subtitles,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = subtitleLabel,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = extras.emeraldReady,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = "$subtitleCueCount cues loaded",
                                style = MaterialTheme.typography.bodySmall,
                                color = extras.inkMuted,
                            )
                        }
                    }
                    TextButton(onClick = onReplaceFile) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Replace")
                    }
                }
            }

            // Live mini VR caption preview reflecting chosen text size + offset (non-overlapping under font scaling)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0A0D0F),
                border = BorderStroke(1.2.dp, previewBorderCol),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "VR THEATRE PREVIEW",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                letterSpacing = 0.6.sp,
                            ),
                            color = Color.White.copy(alpha = 0.45f),
                        )
                        if (activeOffsetMs != 0L) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                            ) {
                                Text(
                                    text = LibraryCatalog.formatSubtitleOffset(activeOffsetMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xD00C1012),
                        border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            text = "Curved IMAX subtitle preview",
                            color = Color.White,
                            fontSize = animatedPreviewSp.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            // Sync timing slider + surgical nudge buttons
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "Sync Offset", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = LibraryCatalog.formatSubtitleOffset(activeOffsetMs),
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = extras.turquoiseAccent,
                    )
                }
                Slider(
                    value = activeOffsetMs.toFloat(),
                    onValueChange = { raw ->
                        val snapped = ((raw / 100f).roundToInt() * 100L).coerceIn(-3_000L, 3_000L)
                        if (snapped != activeOffsetMs) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            draggingOffsetMs = snapped
                        }
                    },
                    onValueChangeFinished = {
                        draggingOffsetMs?.let { applyOffset(it) }
                    },
                    valueRange = -3_000f..3_000f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(
                        "-0.5s" to -500L,
                        "-0.1s" to -100L,
                        "Reset" to null,
                        "+0.1s" to 100L,
                        "+0.5s" to 500L,
                    ).forEach { (btnLabel, deltaMs) ->
                        val isReset = deltaMs == null
                        val canClick = when {
                            isReset -> activeOffsetMs != 0L
                            deltaMs!! < 0L -> activeOffsetMs > -3_000L
                            else -> activeOffsetMs < 3_000L
                        }
                        Surface(
                            onClick = {
                                if (canClick) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    applyOffset(if (isReset) 0L else activeOffsetMs + deltaMs!!)
                                }
                            },
                            enabled = canClick,
                            shape = RoundedCornerShape(10.dp),
                            color = if (isReset && activeOffsetMs != 0L) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = btnLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = when {
                                        isReset && activeOffsetMs != 0L -> MaterialTheme.colorScheme.onPrimaryContainer
                                        !canClick -> extras.inkMuted.copy(alpha = 0.45f)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                        }
                    }
                }
            }

            // VR Text Size segmented toggle
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "VR Text Size", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SubtitleTextSize.entries.forEach { option ->
                        val active = option == textSize
                        val bg by animateColorAsState(
                            targetValue = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                            animationSpec = tween(180),
                            label = "subSizeBg",
                        )
                        val fg by animateColorAsState(
                            targetValue = if (active) MaterialTheme.colorScheme.onPrimary else extras.inkMuted,
                            animationSpec = tween(180),
                            label = "subSizeFg",
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(11.dp))
                                .background(bg)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onTextSizeChange(option)
                                }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = fg,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        onClearSubtitles()
                        onDismiss()
                    },
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.55f)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Remove", color = Color(0xFFEF4444))
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Done")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaActionBottomSheet(
    item: MediaItem,
    selected: Boolean,
    resumePositionMs: Long,
    hasWatchHistory: Boolean,
    canRemoveFromLibrary: Boolean,
    thumbnailFailed: Boolean,
    onWatchVr: (resumeMs: Long) -> Unit,
    onSelectSpotlight: () -> Unit,
    onRetryThumbnail: () -> Unit,
    onRemoveFromHistory: () -> Unit,
    onRemoveFromLibrary: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extras = LocalCinemaExtras.current
    val context = LocalContext.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val badge = LibraryCatalog.formatBadge(
        pathOrUrl = item.uri.toString(),
        isStream = item.isStream,
        width = item.width,
        height = item.height,
    )
    val originText = if (item.isStream) {
        LibraryCatalog.extractHostLabel(item.uri.toString())
    } else {
        item.folderName.ifBlank { "Device Video" }
    }
    val metaLine = buildString {
        append(MediaRepository.formatDuration(item.durationMs))
        append(" · ")
        append(badge)
        append(" · ")
        append(originText)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetTopShape,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.inkMuted,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            HorizontalDivider(color = extras.cardBorder)

            if (resumePositionMs > 2_000L) {
                Button(
                    onClick = { onWatchVr(resumePositionMs) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Resume at ${MediaRepository.formatDuration(resumePositionMs)}")
                }
                ActionSheetRow(
                    icon = Icons.Rounded.Replay,
                    label = "Start Over from 00:00",
                    onClick = { onWatchVr(0L) },
                )
            } else {
                Button(
                    onClick = { onWatchVr(0L) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("Watch in VR Theater")
                }
            }

            if (!selected) {
                ActionSheetRow(
                    icon = Icons.Rounded.Theaters,
                    label = "Highlight in Spotlight",
                    onClick = {
                        onSelectSpotlight()
                        onDismiss()
                    },
                )
            }

            if (item.isStream) {
                ActionSheetRow(
                    icon = Icons.Rounded.ContentCopy,
                    label = "Copy Stream URL",
                    onClick = {
                        clipboard.setText(AnnotatedString(item.uri.toString()))
                        Toast.makeText(context, "Stream URL copied", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    },
                )
            }

            if (thumbnailFailed) {
                ActionSheetRow(
                    icon = Icons.Rounded.Refresh,
                    label = "Reload Artwork Preview",
                    onClick = {
                        onRetryThumbnail()
                        onDismiss()
                    },
                )
            }

            if (hasWatchHistory) {
                ActionSheetRow(
                    icon = Icons.Rounded.History,
                    label = "Remove from Continue Watching",
                    onClick = {
                        onRemoveFromHistory()
                        onDismiss()
                    },
                )
            }

            if (canRemoveFromLibrary) {
                ActionSheetRow(
                    icon = Icons.Rounded.DeleteOutline,
                    label = "Remove from Library",
                    tint = Color(0xFFEF4444),
                    onClick = {
                        onRemoveFromLibrary()
                        onDismiss()
                    },
                )
            }
        }
    }
}

@Composable
private fun ActionSheetRow(
    icon: ImageVector,
    label: String,
    tint: Color? = null,
    onClick: () -> Unit,
) {
    val contentColor = tint ?: MaterialTheme.colorScheme.onSurface
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val rowScale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMedium),
        label = "actionRowScale",
    )
    val enterAlpha = remember(label) { Animatable(0f) }
    val enterShiftY = remember(label) { Animatable(12f) }
    LaunchedEffect(label) {
        launch { enterAlpha.animateTo(1f, tween(210, easing = FastOutSlowInEasing)) }
        launch { enterShiftY.animateTo(0f, tween(220, easing = FastOutSlowInEasing)) }
    }

    val pressBorder by animateColorAsState(
        targetValue = if (pressed) {
            (tint ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.55f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(140),
        label = "actionRowBorder",
    )

    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = RoundedCornerShape(14.dp),
        color = if (tint != null) tint.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, pressBorder),
        modifier = Modifier.graphicsLayer {
            scaleX = rowScale
            scaleY = rowScale
            alpha = enterAlpha.value
            translationY = enterShiftY.value
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(19.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor,
            )
        }
    }
}
