package com.jiodive.cinema.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiodive.cinema.data.LibraryCatalog
import com.jiodive.cinema.data.MediaRepository
import com.jiodive.cinema.model.CinemaConfig
import com.jiodive.cinema.model.DurationFilterPreset
import com.jiodive.cinema.model.DurationFilterState
import com.jiodive.cinema.model.MediaItem
import com.jiodive.cinema.model.RecentStreamEntry
import com.jiodive.cinema.model.SubtitleTextSize
import com.jiodive.cinema.model.TheaterEnvironment
import com.jiodive.cinema.ui.theme.LocalCinemaExtras
import java.util.Locale
import kotlin.math.roundToInt

internal val SheetTopShape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpticsBottomSheet(
    config: CinemaConfig,
    canWriteSystemSettings: Boolean,
    onUpdateConfig: (CinemaConfig) -> Unit,
    onRequestWriteSettings: () -> Unit,
    onReplayOnboarding: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extras = LocalCinemaExtras.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                        text = "Display & Headset",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Optical alignment, comfort & theme preferences",
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.inkMuted,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            SwitchRow(
                title = "Dark Appearance",
                subtitle = "Low-glare theater interface for portrait browsing",
                checked = config.darkTheme,
                onCheckedChange = { onUpdateConfig(config.copy(darkTheme = it)) },
            )

            SwitchRow(
                title = "Thermal Brightness Protection",
                subtitle = if (canWriteSystemSettings) {
                    "Caps display brightness at 60% inside JioDive"
                } else {
                    "Limits VR brightness in-window to prevent overheating"
                },
                checked = config.thermalCapEnabled,
                onCheckedChange = { onUpdateConfig(config.copy(thermalCapEnabled = it)) },
            )

            AnimatedVisibility(
                visible = !canWriteSystemSettings && config.thermalCapEnabled,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(150)),
            ) {
                OutlinedButton(
                    onClick = onRequestWriteSettings,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Enable System Brightness Lock (Optional)")
                }
            }

            HorizontalDivider(color = extras.cardBorder)

            LabeledSlider(
                label = "Eye Separation (IPD)",
                valueText = "${config.ipdMm.roundToInt()} mm",
                value = config.ipdMm,
                range = 54f..72f,
                onValueChange = { onUpdateConfig(config.copy(ipdMm = it)) },
            )

            LabeledSlider(
                label = "Field of View",
                valueText = "${config.fovDeg.roundToInt()}°",
                value = config.fovDeg,
                range = 65f..95f,
                onValueChange = { onUpdateConfig(config.copy(fovDeg = it)) },
            )

            AnimatedVisibility(
                visible = config.environment == TheaterEnvironment.BED,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(180)) + fadeOut(tween(150)),
            ) {
                LabeledSlider(
                    label = "Bed Ceiling Angle",
                    valueText = "${config.bedTiltDeg.roundToInt()}°",
                    value = config.bedTiltDeg,
                    range = 25f..90f,
                    onValueChange = { onUpdateConfig(config.copy(bedTiltDeg = it)) },
                )
            }

            LabeledSlider(
                label = "Edge Clarity",
                valueText = "${(config.sharpenStrength * 100).roundToInt()}%",
                value = config.sharpenStrength,
                range = 0f..1f,
                onValueChange = { onUpdateConfig(config.copy(sharpenStrength = it)) },
            )

            LabeledSlider(
                label = "Gaze Select Delay",
                valueText = String.format(Locale.US, "%.2fs", config.gazeDwellMs / 1000f),
                value = config.gazeDwellMs.toFloat(),
                range = 750f..2000f,
                onValueChange = { onUpdateConfig(config.copy(gazeDwellMs = it.roundToInt())) },
            )

            SwitchRow(
                title = "JioDive Lens Distortion",
                subtitle = "Counter-warps optics for biconvex headset lenses",
                checked = config.barrelEnabled,
                onCheckedChange = { onUpdateConfig(config.copy(barrelEnabled = it)) },
            )

            SwitchRow(
                title = "Swap Left / Right Eyes",
                subtitle = "Reverse stereo pairing for inverted 3D sources",
                checked = config.eyeSwap,
                onCheckedChange = { onUpdateConfig(config.copy(eyeSwap = it)) },
            )

            OutlinedButton(
                onClick = {
                    onDismiss()
                    onReplayOnboarding()
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Replay Quick Setup Guide")
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Done")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamUrlBottomSheet(
    initialUrl: String,
    recentStreams: List<RecentStreamEntry>,
    onLoadUrl: (String) -> Unit,
    onRemoveRecent: (RecentStreamEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val extras = LocalCinemaExtras.current
    val haptic = LocalHapticFeedback.current
    @Suppress("DEPRECATION")
    val clipboard = LocalClipboardManager.current
    var text by remember(initialUrl) { mutableStateOf(initialUrl) }
    val fieldInteraction = remember { MutableInteractionSource() }
    val fieldFocused by fieldInteraction.collectIsFocusedAsState()
    val urlRingBorder by animateColorAsState(
        targetValue = when {
            fieldFocused -> MaterialTheme.colorScheme.primary
            text.isNotEmpty() -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else -> extras.cardBorder
        },
        animationSpec = tween(220),
        label = "streamFieldRing",
    )
    val urlRingWidth by animateDpAsState(
        targetValue = if (fieldFocused) 1.75.dp else 1.dp,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 460f),
        label = "streamFieldRingWidth",
    )
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val trimmed = text.trim()
    val isValidUrl = trimmed.startsWith("http://", ignoreCase = true) ||
        trimmed.startsWith("https://", ignoreCase = true)

    val starterStreams = remember {
        listOf(
            RecentStreamEntry(
                url = "https://vjs.zencdn.net/v/oceans.mp4",
                title = "Oceans HD Sample",
            ),
            RecentStreamEntry(
                url = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4",
                title = "For Bigger Escapes",
            ),
        )
    }
    val displayChips = recentStreams.ifEmpty { starterStreams }
    val isShowingHistory = recentStreams.isNotEmpty()

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
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Add Video Stream",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (isValidUrl) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    text = LibraryCatalog.formatBadge(trimmed, isStream = true),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = "Paste a direct MP4, MKV, WEBM, or HLS (.m3u8) stream link.",
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.inkMuted,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = urlRingWidth,
                        color = urlRingBorder,
                        shape = RoundedCornerShape(14.dp),
                    ),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    interactionSource = fieldInteraction,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.56f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = {
                            if (isValidUrl) onLoadUrl(trimmed)
                        },
                    ),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            tint = if (fieldFocused || isValidUrl) MaterialTheme.colorScheme.primary else extras.inkMuted,
                        )
                    },
                    trailingIcon = {
                        if (text.isNotEmpty()) {
                            IconButton(onClick = { text = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear URL", modifier = Modifier.size(18.dp))
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    val pasted = clipboard.getText()?.text?.trim().orEmpty()
                                    if (pasted.isNotEmpty()) {
                                        text = pasted
                                    }
                                },
                            ) {
                                Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste URL", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    placeholder = { Text("https://example.com/movie.m3u8") },
                )
            }

            AnimatedVisibility(
                visible = trimmed.isNotEmpty() && !isValidUrl,
                enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(140)),
            ) {
                Text(
                    text = "URL must start with https:// or http://",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.inkMuted,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isShowingHistory) "RECENT STREAMS" else "QUICK SAMPLE STREAMS",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.7.sp,
                        ),
                        color = extras.inkMuted,
                    )
                    if (isShowingHistory) {
                        Text(
                            text = "${recentStreams.size} saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = extras.inkMuted,
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(displayChips, key = { it.url }) { entry ->
                        val activeChip = trimmed == entry.url
                        val badge = LibraryCatalog.formatBadge(entry.url, isStream = true)
                        val host = LibraryCatalog.extractHostLabel(entry.url)
                        val bg by animateColorAsState(
                            targetValue = if (activeChip) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            animationSpec = tween(180),
                            label = "recentStreamChipBg",
                        )
                        val border by animateColorAsState(
                            targetValue = if (activeChip) MaterialTheme.colorScheme.primary else extras.cardBorder,
                            animationSpec = tween(180),
                            label = "recentStreamChipBorder",
                        )

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                text = entry.url
                                onLoadUrl(entry.url)
                            },
                            shape = RoundedCornerShape(14.dp),
                            color = bg,
                            border = BorderStroke(1.dp, border),
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(start = 10.dp, end = if (isShowingHistory) 6.dp else 10.dp, top = 8.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                ) {
                                    Text(
                                        text = badge,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                                Column(modifier = Modifier.widthIn(max = 176.dp)) {
                                    Text(
                                        text = entry.title,
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = host,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = extras.inkMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (isShowingHistory) {
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(RoundedCornerShape(11.dp))
                                            .clickable { onRemoveRecent(entry) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Remove recent stream",
                                            tint = extras.inkMuted,
                                            modifier = Modifier.size(13.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (isValidUrl) {
                        onLoadUrl(trimmed)
                    }
                },
                enabled = isValidUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text("Load Stream")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceGuideBottomSheet(
    title: String,
    skipNextTime: Boolean,
    onSkipNextTimeChanged: (Boolean) -> Unit,
    onConfirmEnterVr: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extras = LocalCinemaExtras.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val progressAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 3000, easing = LinearEasing),
        )
        onConfirmEnterVr()
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp),
                contentAlignment = Alignment.Center,
            ) {
                JioDiveInsertionMotionGraphic()
            }

            Text(
                text = "Insert Phone into JioDive",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Opening \"$title\" in cinema VR mode. Look at any control or tap the screen to pause.",
                style = MaterialTheme.typography.bodyMedium,
                color = extras.inkMuted,
            )

            LinearProgressIndicator(
                progress = { progressAnim.value },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(12.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Checkbox(
                    checked = skipNextTime,
                    onCheckedChange = onSkipNextTimeChanged,
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = "Don't show this guide again",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.inkMuted,
                )
            }

            Button(
                onClick = onConfirmEnterVr,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text("Enter VR Now")
            }

            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    val extras = LocalCinemaExtras.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.titleSmall)
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = extras.turquoiseAccent,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val extras = LocalCinemaExtras.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = extras.inkMuted)
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun JioDiveInsertionMotionGraphic() {
    val accent = MaterialTheme.colorScheme.primary
    val muted = LocalCinemaExtras.current.inkMuted
    val inf = rememberInfiniteTransition(label = "jioDiveInsert")
    val slideState = inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phoneSlideT",
    )

    val visorPath = remember { Path() }

    Box(modifier = Modifier.size(width = 160.dp, height = 84.dp)) {
        // Cached static visor cradle silhouette
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    visorPath.reset()
                    visorPath.moveTo(w * 0.18f, h * 0.42f)
                    visorPath.quadraticTo(w * 0.50f, h * 0.36f, w * 0.82f, h * 0.42f)
                    visorPath.cubicTo(w * 0.90f, h * 0.44f, w * 0.91f, h * 0.56f, w * 0.89f, h * 0.76f)
                    visorPath.cubicTo(w * 0.87f, h * 0.90f, w * 0.76f, h * 0.94f, w * 0.65f, h * 0.94f)
                    visorPath.cubicTo(w * 0.56f, h * 0.94f, w * 0.54f, h * 0.81f, w * 0.50f, h * 0.81f)
                    visorPath.cubicTo(w * 0.46f, h * 0.81f, w * 0.44f, h * 0.94f, w * 0.35f, h * 0.94f)
                    visorPath.cubicTo(w * 0.24f, h * 0.94f, w * 0.13f, h * 0.90f, w * 0.11f, h * 0.76f)
                    visorPath.cubicTo(w * 0.09f, h * 0.56f, w * 0.10f, h * 0.44f, w * 0.18f, h * 0.42f)
                    visorPath.close()
                    val stroke = Stroke(width = 2.6.dp.toPx(), join = StrokeJoin.Round)
                    val fillColor = muted.copy(alpha = 0.14f)
                    val strokeColor = muted.copy(alpha = 0.55f)
                    onDrawBehind {
                        drawPath(visorPath, color = fillColor)
                        drawPath(visorPath, color = strokeColor, style = stroke)
                    }
                },
        )

        Canvas(modifier = Modifier.size(width = 160.dp, height = 84.dp)) {
            val w = size.width
            val h = size.height
            val slideT = slideState.value

            // Phone sliding downward into the visor cradle and latching
            val moveFrac = (slideT / 0.72f).coerceIn(0f, 1f)
            val phoneY = h * (0.04f + 0.43f * moveFrac)
            val phoneAlpha = if (slideT > 0.85f) (1f - slideT) / 0.15f else 1f
            val phoneW = w * 0.48f
            val phoneH = h * 0.28f
            val phoneX = (w - phoneW) * 0.5f

            drawRoundRect(
                color = accent.copy(alpha = 0.22f * phoneAlpha),
                topLeft = Offset(phoneX, phoneY),
                size = Size(phoneW, phoneH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
            )
            drawRoundRect(
                color = accent.copy(alpha = phoneAlpha),
                topLeft = Offset(phoneX, phoneY),
                size = Size(phoneW, phoneH),
                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = 2.4.dp.toPx()),
            )
            drawLine(
                color = accent.copy(alpha = 0.7f * phoneAlpha),
                start = Offset(w * 0.5f, phoneY + 4.dp.toPx()),
                end = Offset(w * 0.5f, phoneY + phoneH - 4.dp.toPx()),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDurationBottomSheet(
    initialState: DurationFilterState,
    onApply: (DurationFilterState) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extras = LocalCinemaExtras.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var minSlider by remember { mutableFloatStateOf(initialState.customMinMinutes.coerceIn(0, 240).toFloat()) }
    var limitMax by remember { mutableStateOf(initialState.customMaxMinutes != null) }
    var maxSlider by remember {
        mutableFloatStateOf(
            (initialState.customMaxMinutes ?: (initialState.customMinMinutes + 60))
                .coerceIn(15, 360)
                .toFloat(),
        )
    }

    val minMinutes = ((minSlider / 5f).roundToInt() * 5).coerceIn(0, 240)
    val rawMaxMinutes = ((maxSlider / 5f).roundToInt() * 5).coerceIn(15, 360)
    val maxMinutes = rawMaxMinutes.coerceAtLeast((minMinutes + 10).coerceAtMost(360))

    fun fmtMin(m: Int): String {
        val h = m / 60
        val rem = m % 60
        return when {
            h > 0 && rem == 0 -> "${h}h"
            h > 0 -> "${h}h ${rem}m"
            else -> "${m}m"
        }
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
                        text = "Custom Video Length",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (limitMax) {
                            "Show videos between ${fmtMin(minMinutes)} and ${fmtMin(maxMinutes)}"
                        } else {
                            "Show videos ${fmtMin(minMinutes)} or longer"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.inkMuted,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    Triple("45m+", 45, null),
                    Triple("90m+", 90, null),
                    Triple("1h-2h", 60, 120),
                    Triple("2h30+", 150, null),
                ).forEach { (label, quickMin, quickMax) ->
                    val active = minMinutes == quickMin && ((quickMax == null && !limitMax) || (limitMax && maxMinutes == quickMax))
                    Surface(
                        onClick = {
                            minSlider = quickMin.toFloat()
                            limitMax = quickMax != null
                            if (quickMax != null) maxSlider = quickMax.toFloat()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = extras.cardBorder)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "Minimum Length", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = fmtMin(minMinutes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = minSlider,
                    onValueChange = {
                        minSlider = it
                        if (limitMax && maxSlider < it + 10f) {
                            maxSlider = (it + 15f).coerceAtMost(360f)
                        }
                    },
                    valueRange = 0f..240f,
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            SwitchRow(
                title = "Set Maximum Cap",
                subtitle = "Limit results to a specific upper length window",
                checked = limitMax,
                onCheckedChange = {
                    limitMax = it
                    if (it && maxSlider <= minSlider) {
                        maxSlider = (minMinutes + 30).coerceIn(15, 360).toFloat()
                    }
                },
            )

            AnimatedVisibility(visible = limitMax) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(text = "Maximum Length", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = fmtMin(maxMinutes),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = maxSlider,
                        onValueChange = { maxSlider = it },
                        valueRange = 15f..360f,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Reset")
                }
                Button(
                    onClick = {
                        onApply(
                            DurationFilterState(
                                preset = DurationFilterPreset.CUSTOM,
                                customMinMinutes = minMinutes,
                                customMaxMinutes = if (limitMax) maxMinutes else null,
                            ),
                        )
                    },
                    modifier = Modifier
                        .weight(1.4f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text("Apply Filter")
                }
            }
        }
    }
}
