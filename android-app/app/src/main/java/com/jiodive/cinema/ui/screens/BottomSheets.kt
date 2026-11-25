package com.jiodive.cinema.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jiodive.cinema.model.CinemaConfig
import com.jiodive.cinema.ui.theme.LocalCinemaExtras
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val SheetTopShape = RoundedCornerShape(topStart = 17.dp, topEnd = 17.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpticsBottomSheet(
    config: CinemaConfig,
    canWriteSystemSettings: Boolean,
    onUpdateConfig: (CinemaConfig) -> Unit,
    onRequestWriteSettings: () -> Unit,
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Headset & Optics",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Calibrated for JioDive Cardboard lenses",
                        style = MaterialTheme.typography.bodySmall,
                        color = extras.inkMuted,
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close")
                }
            }

            // Thermal brightness cap card (from cardboard/default.json: max_brightness = 60)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (canWriteSystemSettings) extras.emeraldContainer else extras.goldContainer,
                border = BorderStroke(1.dp, extras.cardBorder),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = if (canWriteSystemSettings) Icons.Rounded.BrightnessHigh else Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = if (canWriteSystemSettings) extras.emeraldReady else extras.goldHighlight,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "60% Thermal Brightness Cap",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (canWriteSystemSettings) {
                                "System brightness cap permitted (prevents overheating in JioDive)."
                            } else {
                                "Window brightness caps at 60% in VR. Grant system modify permission for full lock."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = extras.inkMuted,
                        )
                    }
                    Switch(
                        checked = config.thermalCapEnabled,
                        onCheckedChange = { onUpdateConfig(config.copy(thermalCapEnabled = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }

            if (!canWriteSystemSettings && config.thermalCapEnabled) {
                OutlinedButton(
                    onClick = onRequestWriteSettings,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Allow System Brightness Control (Optional)")
                }
            }

            LabeledSlider(
                label = "Lens Separation (IPD)",
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

            LabeledSlider(
                label = "Lens Edge Sharpening",
                valueText = "${(config.sharpenStrength * 100).roundToInt()}%",
                value = config.sharpenStrength,
                range = 0f..1f,
                onValueChange = { onUpdateConfig(config.copy(sharpenStrength = it)) },
            )

            LabeledSlider(
                label = "Gaze Dwell Click Delay",
                valueText = String.format("%.2fs", config.gazeDwellMs / 1000f),
                value = config.gazeDwellMs.toFloat(),
                range = 750f..2000f,
                onValueChange = { onUpdateConfig(config.copy(gazeDwellMs = it.roundToInt())) },
            )

            SwitchRow(
                title = "Cardboard Barrel Distortion",
                subtitle = "Warps edges to match biconvex VR lenses",
                checked = config.barrelEnabled,
                onCheckedChange = { onUpdateConfig(config.copy(barrelEnabled = it)) },
            )

            SwitchRow(
                title = "Swap Left / Right Eyes",
                subtitle = "Flip stereo eye halves for reversed 3D rips",
                checked = config.eyeSwap,
                onCheckedChange = { onUpdateConfig(config.copy(eyeSwap = it)) },
            )

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(20.dp),
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
    onLoadUrl: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val extras = LocalCinemaExtras.current
    var text by remember(initialUrl) { mutableStateOf(initialUrl) }
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
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Load Video Stream",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Paste a direct MP4, MKV, or HLS (.m3u8) stream link.",
                style = MaterialTheme.typography.bodySmall,
                color = extras.inkMuted,
            )

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                placeholder = { Text("https://example.com/movie.mp4") },
            )

            Button(
                onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isNotEmpty()) {
                        onLoadUrl(trimmed)
                    }
                },
                enabled = text.trim().startsWith("http://") || text.trim().startsWith("https://"),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(20.dp),
            ) {
                Text("Add Stream & Select")
            }
        }
    }
}

/**
 * JioImmerse DeviceGuideActivity-style portrait countdown before rotating into
 * landscape stereoscopic playback so user can slide phone into JioDive smoothly.
 */
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
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val steps = 30
        for (i in 1..steps) {
            delay(100L)
            progress = i.toFloat() / steps.toFloat()
        }
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = extras.activePillBg,
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(16.dp),
                )
            }

            Text(
                text = "Place Phone Inside JioDive",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Launching \"$title\" in stereoscopic VR. Tap your screen or gaze at any control inside the cinema to pause or switch rooms.",
                style = MaterialTheme.typography.bodyMedium,
                color = extras.inkMuted,
            )

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(18.dp)),
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
                    text = "Don't show insertion guide again",
                    style = MaterialTheme.typography.bodySmall,
                    color = extras.inkMuted,
                )
            }

            Button(
                onClick = onConfirmEnterVr,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(20.dp),
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
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
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
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, extras.cardBorder),
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
