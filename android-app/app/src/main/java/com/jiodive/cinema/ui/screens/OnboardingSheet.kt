package com.jiodive.cinema.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiodive.cinema.ui.theme.LocalCinemaExtras
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private data class OnboardingStepSpec(
    val title: String,
    val body: String,
)

private val OnboardingSteps = listOf(
    OnboardingStepSpec(
        title = "Pick Any Movie or Stream",
        body = "Open phone videos, attach synced .srt/.vtt subtitles, or paste direct MP4, MKV, and HLS (.m3u8) links.",
    ),
    OnboardingStepSpec(
        title = "Tune Your Theater & Lenses",
        body = "Switch between curved Cinema Hall, pure black OLED Void, or reclined Bed Mode and set your JioDive IPD.",
    ),
    OnboardingStepSpec(
        title = "Slide In & Gaze to Control",
        body = "Latch your phone into JioDive. Look at dock buttons for ~1s to click, adjust volume, or Lock Dock in Bed Mode.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstRunOnboardingSheet(
    onComplete: () -> Unit,
) {
    val extras = LocalCinemaExtras.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { OnboardingSteps.size })
    val isLastStep = pagerState.currentPage == OnboardingSteps.lastIndex

    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch {
            pagerState.animateScrollToPage(pagerState.currentPage - 1)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onComplete,
        sheetState = sheetState,
        shape = SheetTopShape,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = "WELCOME · STEP ${pagerState.currentPage + 1} OF ${OnboardingSteps.size}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.7.sp,
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }

                TextButton(onClick = onComplete) {
                    Text(
                        text = if (isLastStep) "Close" else "Skip",
                        color = extras.inkMuted,
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val spec = OnboardingSteps[page]
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = Color(0xFF0A0D0F),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(176.dp),
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val stageActive = page == pagerState.currentPage || pagerState.isScrollInProgress
                            when (page) {
                                0 -> OnboardingPickVideoStage(isVisible = stageActive)
                                1 -> OnboardingRoomOpticsStage(isVisible = stageActive)
                                else -> OnboardingHeadsetGazeStage(isVisible = stageActive)
                            }
                        }
                    }

                    Text(
                        text = spec.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = spec.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = extras.inkMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            }

            // Interactive capsule step indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OnboardingSteps.indices.forEach { idx ->
                    val active = idx == pagerState.currentPage
                    val dotW by animateDpAsState(
                        targetValue = if (active) 24.dp else 8.dp,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f),
                        label = "onboardDotW",
                    )
                    val dotCol by animateColorAsState(
                        targetValue = if (active) MaterialTheme.colorScheme.primary else extras.cardBorder,
                        animationSpec = tween(200),
                        label = "onboardDotCol",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(dotW)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(dotCol)
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(idx) }
                            },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (pagerState.currentPage > 0) {
                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Back")
                    }
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (isLastStep) {
                            onComplete()
                        } else {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(if (pagerState.currentPage > 0) 1.4f else 1f)
                        .height(48.dp),
                ) {
                    if (isLastStep) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.size(7.dp))
                        Text("Get Started")
                    } else {
                        Text("Next")
                        Spacer(modifier = Modifier.size(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPickVideoStage(isVisible: Boolean) {
    val accent = Color(0xFF38C6BD)
    val pulseState = if (isVisible) {
        rememberInfiniteTransition(label = "stage1").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse",
        )
    } else {
        null
    }

    val playTriPath = remember { Path() }
    val stage1GlowColors = remember { listOf(accent.copy(alpha = 0.22f), Color.Transparent) }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val p = pulseState?.value ?: 0.5f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = stage1GlowColors,
                    center = Offset(w * 0.5f, h * 0.48f),
                    radius = w * 0.40f,
                ),
                radius = w * 0.40f,
                center = Offset(w * 0.5f, h * 0.48f),
            )

            // Back library card silhouette
            val leftW = w * 0.34f
            val leftH = h * 0.50f
            drawRoundRect(
                color = Color(0xFF151E22),
                topLeft = Offset(w * 0.14f, h * 0.24f + p * 4.dp.toPx()),
                size = Size(leftW, leftH),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.32f),
                topLeft = Offset(w * 0.14f, h * 0.24f + p * 4.dp.toPx()),
                size = Size(leftW, leftH),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                style = Stroke(width = 1.5.dp.toPx()),
            )

            // Front spotlight card silhouette
            val heroW = w * 0.42f
            val heroH = h * 0.58f
            val hx = w * 0.42f
            val hy = h * 0.19f - p * 4.dp.toPx()
            drawRoundRect(
                color = Color(0xFF102428),
                topLeft = Offset(hx, hy),
                size = Size(heroW, heroH),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.85f),
                topLeft = Offset(hx, hy),
                size = Size(heroW, heroH),
                cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                style = Stroke(width = 2.dp.toPx()),
            )

            // Play glyph inside spotlight card
            val cx = hx + heroW * 0.5f
            val cy = hy + heroH * 0.5f
            drawCircle(color = accent, radius = 16.dp.toPx(), center = Offset(cx, cy))
            playTriPath.reset()
            playTriPath.moveTo(cx - 4.dp.toPx(), cy - 6.dp.toPx())
            playTriPath.lineTo(cx + 8.dp.toPx(), cy)
            playTriPath.lineTo(cx - 4.dp.toPx(), cy + 6.dp.toPx())
            playTriPath.close()
            drawPath(playTriPath, color = Color(0xFF042220))
        }

        // Format pills row along bottom edge of artwork
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("MP4 / MKV", "HLS .m3u8", "CC Sync").forEach { tag ->
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xCC162528),
                    border = BorderStroke(0.75.dp, accent.copy(alpha = 0.42f)),
                ) {
                    Text(
                        text = tag,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingRoomOpticsStage(isVisible: Boolean) {
    val accent = Color(0xFF38C6BD)
    val morph = if (isVisible) {
        rememberInfiniteTransition(label = "stage2").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "opticsMorph",
        )
    } else {
        null
    }

    val screenPath = remember { Path() }
    val stage2GlowColors = remember { listOf(accent.copy(alpha = 0.24f), Color.Transparent) }
    val stage2FillColors = remember { listOf(accent.copy(alpha = 0.38f), Color(0xFF0E252A)) }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val m = morph?.value ?: 0.5f

            drawCircle(
                brush = Brush.radialGradient(
                    colors = stage2GlowColors,
                    center = Offset(w * 0.5f, h * 0.36f),
                    radius = w * 0.44f,
                ),
                radius = w * 0.44f,
                center = Offset(w * 0.5f, h * 0.36f),
            )

            // Morphing curved cinema IMAX screen
            val sw = w * (0.50f + 0.14f * m)
            val sh = h * 0.36f
            val left = (w - sw) * 0.5f
            val right = left + sw
            val topY = h * 0.16f
            val botY = topY + sh
            val bow = (5f + 7f * m).dp.toPx()

            screenPath.reset()
            screenPath.moveTo(left, topY)
            screenPath.quadraticTo(w * 0.5f, topY + bow, right, topY)
            screenPath.lineTo(right, botY)
            screenPath.quadraticTo(w * 0.5f, botY - bow, left, botY)
            screenPath.close()
            drawPath(
                path = screenPath,
                brush = Brush.verticalGradient(colors = stage2FillColors),
            )
            drawPath(
                path = screenPath,
                color = accent,
                style = Stroke(width = 2.dp.toPx(), join = StrokeJoin.Round),
            )

            // Twin stereo lens IPD spacing reticle beneath screen
            val ipdShift = (3f - 6f * m).dp.toPx()
            val lensY = h * 0.72f
            val r = 13.dp.toPx()
            val leftLensX = w * 0.5f - 24.dp.toPx() - ipdShift
            val rightLensX = w * 0.5f + 24.dp.toPx() + ipdShift

            drawCircle(accent.copy(alpha = 0.18f), radius = r, center = Offset(leftLensX, lensY))
            drawCircle(accent.copy(alpha = 0.90f), radius = r, center = Offset(leftLensX, lensY), style = Stroke(2.dp.toPx()))
            drawCircle(accent.copy(alpha = 0.18f), radius = r, center = Offset(rightLensX, lensY))
            drawCircle(accent.copy(alpha = 0.90f), radius = r, center = Offset(rightLensX, lensY), style = Stroke(2.dp.toPx()))

            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(leftLensX + r + 3.dp.toPx(), lensY),
                end = Offset(rightLensX - r - 3.dp.toPx(), lensY),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }

        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xCC10181B),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp),
        ) {
            Text(
                text = "Hall · Void · Bed Mode",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.84f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun OnboardingHeadsetGazeStage(isVisible: Boolean) {
    val accent = Color(0xFF38C6BD)
    val progress = if (isVisible) {
        rememberInfiniteTransition(label = "stage3").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2100, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "gazeProgress",
        )
    } else {
        null
    }

    val visorPath = remember { Path() }
    val stage3GlowColors = remember { listOf(accent.copy(alpha = 0.22f), Color.Transparent) }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val t = progress?.value ?: 0.5f

        drawCircle(
            brush = Brush.radialGradient(
                colors = stage3GlowColors,
                center = Offset(w * 0.5f, h * 0.52f),
                radius = w * 0.42f,
            ),
            radius = w * 0.42f,
            center = Offset(w * 0.5f, h * 0.52f),
        )

        // Headset outline on left half
        val vx = w * 0.30f
        val vy = h * 0.52f
        visorPath.reset()
        visorPath.moveTo(vx - 52.dp.toPx(), vy - 18.dp.toPx())
        visorPath.quadraticTo(vx, vy - 26.dp.toPx(), vx + 52.dp.toPx(), vy - 18.dp.toPx())
        visorPath.cubicTo(vx + 64.dp.toPx(), vy - 14.dp.toPx(), vx + 66.dp.toPx(), vy + 4.dp.toPx(), vx + 62.dp.toPx(), vy + 24.dp.toPx())
        visorPath.cubicTo(vx + 56.dp.toPx(), vy + 40.dp.toPx(), vx + 26.dp.toPx(), vy + 40.dp.toPx(), vx + 14.dp.toPx(), vy + 28.dp.toPx())
        visorPath.cubicTo(vx + 6.dp.toPx(), vy + 20.dp.toPx(), vx - 6.dp.toPx(), vy + 20.dp.toPx(), vx - 14.dp.toPx(), vy + 28.dp.toPx())
        visorPath.cubicTo(vx - 26.dp.toPx(), vy + 40.dp.toPx(), vx - 56.dp.toPx(), vy + 40.dp.toPx(), vx - 62.dp.toPx(), vy + 24.dp.toPx())
        visorPath.cubicTo(vx - 66.dp.toPx(), vy + 4.dp.toPx(), vx - 64.dp.toPx(), vy - 14.dp.toPx(), vx - 52.dp.toPx(), vy - 18.dp.toPx())
        visorPath.close()
        drawPath(visorPath, color = Color(0xFF141E22))
        drawPath(visorPath, color = accent.copy(alpha = 0.78f), style = Stroke(width = 2.2.dp.toPx(), join = StrokeJoin.Round))

        // Sliding phone tray latching from above
        val dropFrac = (t / 0.55f).coerceIn(0f, 1f)
        val phoneY = vy - 48.dp.toPx() + 36.dp.toPx() * dropFrac
        val phoneAlpha = if (t > 0.85f) (1f - t) / 0.15f else 1f
        drawRoundRect(
            color = accent.copy(alpha = 0.28f * phoneAlpha),
            topLeft = Offset(vx - 34.dp.toPx(), phoneY),
            size = Size(68.dp.toPx(), 24.dp.toPx()),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
        )
        drawRoundRect(
            color = accent.copy(alpha = phoneAlpha),
            topLeft = Offset(vx - 34.dp.toPx(), phoneY),
            size = Size(68.dp.toPx(), 24.dp.toPx()),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            style = Stroke(width = 1.8.dp.toPx()),
        )

        // Gaze dwell ring on right half filling smoothly
        val gx = w * 0.72f
        val gy = h * 0.50f
        val gr = 24.dp.toPx()
        drawCircle(
            color = Color.White.copy(alpha = 0.14f),
            radius = gr,
            center = Offset(gx, gy),
            style = Stroke(width = 3.dp.toPx()),
        )
        val dwellSweep = ((t - 0.15f) / 0.75f).coerceIn(0f, 1f) * 360f
        drawArc(
            color = accent,
            startAngle = -90f,
            sweepAngle = dwellSweep,
            useCenter = false,
            topLeft = Offset(gx - gr, gy - gr),
            size = Size(gr * 2f, gr * 2f),
            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(color = Color.White, radius = 4.dp.toPx(), center = Offset(gx, gy))

        // Dotted gaze sightline connecting visor to reticle
        val steps = 4
        for (s in 0 until steps) {
            val f = (s + 0.5f) / steps
            val sx = vx + 68.dp.toPx() + f * (gx - gr - 12.dp.toPx() - (vx + 68.dp.toPx()))
            val sy = vy + 0.5f * sin(f * 3.14159).toFloat() * (-6.dp.toPx()) + 0.1f * cos(s.toDouble()).toFloat()
            drawCircle(
                color = accent.copy(alpha = 0.45f),
                radius = 2.dp.toPx(),
                center = Offset(sx, sy),
            )
        }
    }
}
