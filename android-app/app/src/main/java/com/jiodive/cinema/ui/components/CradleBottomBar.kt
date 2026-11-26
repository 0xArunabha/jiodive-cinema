package com.jiodive.cinema.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.jiodive.cinema.ui.theme.LocalCinemaExtras
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class HubTab {
    LIBRARY,
    THEATER,
}

/**
 * Cradled bottom bar with an asymmetric GPU-drawn liquid droplet ("worm" lead/tail inertia):
 * - True concentric circular cradle cutout (`arcTo` + tangent shoulder fillets) wrapping the round Play FAB.
 * - Multi-layer luminous cyan liquid pill with specular crest & rim reflection gliding underneath.
 * - Smooth reversing breathing beacon + iris swell on the Play FAB at full native display refresh (up to 120Hz)
 *   with zero Compose recompositions and zero per-frame list allocations.
 */
@Composable
fun CradleBottomBar(
    selectedTab: HubTab,
    tabProgress: () -> Float,
    onSelectTab: (HubTab) -> Unit,
    onEnterVr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = LocalCinemaExtras.current
    val density = LocalDensity.current
    val barBg = extras.bottomBarBg
    val isDarkDock = barBg.luminance() < 0.5f
    val borderStroke = extras.cardBorder
    val pillColor = extras.activePillBg
    val accentGlow = extras.turquoiseAccent

    // Trailing tail lags smoothly behind the leading head (`tabProgress()`), producing natural
    // elastic stretch mid-flight and a gentle liquid settle bounce on arrival — read strictly in draw phase.
    val tailProgress = remember { Animatable(tabProgress().coerceIn(0f, 1f)) }
    LaunchedEffect(Unit) {
        snapshotFlow { tabProgress().coerceIn(0f, 1f) }
            .collectLatest { target ->
                tailProgress.animateTo(
                    targetValue = target,
                    animationSpec = spring(dampingRatio = 0.72f, stiffness = 340f),
                )
            }
    }

    val fabInteraction = remember { MutableInteractionSource() }
    val fabPressed by fabInteraction.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.90f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMedium),
        label = "vrFabScale",
    )

    // Seamless reversible breathing heartbeat (no abrupt linear reset) + slow rotating aura sheen
    val beaconTransition = rememberInfiniteTransition(label = "fabBeacon")
    val breathState = beaconTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1950, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "fabBreath",
    )
    val coronaSpin = beaconTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "fabCoronaSpin",
    )

    // Pre-allocated Paths, spline arrays, & color lists (zero per-frame allocations at 120Hz)
    val blobPath = remember { Path() }
    val rimGlowPath = remember { Path() }
    val spineCount = 19
    val capMidSteps = 7
    val maxContourPts = spineCount * 2 + capMidSteps * 2
    val ptX = remember { FloatArray(maxContourPts) }
    val ptY = remember { FloatArray(maxContourPts) }
    val dropletColors = remember { ArrayList<Color>(3).apply { add(Color.Transparent); add(Color.Transparent); add(Color.Transparent) } }
    val underGlowColors = remember { ArrayList<Color>(2).apply { add(Color.Transparent); add(Color.Transparent) } }
    val rimBeamColors = remember { ArrayList<Color>(3).apply { add(Color.Transparent); add(Color.Transparent); add(Color.Transparent) } }
    val cradleVignetteColors = remember(barBg) {
        listOf(barBg.copy(alpha = 0.42f), Color.Transparent)
    }

    // Geometry cache so idle frames (head/tail stationary) do zero trigonometry or Brush allocations at 120Hz
    val geomCache = remember {
        object {
            var lastHead = -1f
            var lastTail = -1f
            var lastW = -1f
            var lastH = -1f
            var cachedLeftX = 0f
            var cachedRightX = 0f
            var cachedActiveEnergy = 0f
            var cachedCenterTransit = 0f
            var cachedMidNorm = 0f
            var cachedLeftCx = 0f
            var cachedRightCx = 0f
            var cachedChannelY = 0f
            var cachedGlowCenter = Offset.Zero
        }
    }

    // Concentric round cradle geometry constants:
    // Outer Box = 100dp, Bottom Bar = 72dp (bar top at 28dp), FAB = 64dp (radius 32dp) offsetY = 2dp -> center at 34dp
    // Inside bar coords: fabCyBar = 34dp - 28dp = 6dp; cradleR = 32dp + 5dp = 37dp -> crisp 5dp circular hug around FAB.
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            ) {
                // Static cached concentric circular cradle silhouette + soft vignette + top hairline
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithCache {
                            val w = size.width
                            val h = size.height
                            val centerX = w * 0.5f

                            val fabCyBar = with(density) { 6.dp.toPx() }
                            val cradleR = with(density) { 37.dp.toPx() }
                            val rf = with(density) { 12.dp.toPx() }
                            val dy = rf - fabCyBar
                            val totalD = cradleR + rf
                            val dx = sqrt((totalD * totalD - dy * dy).coerceAtLeast(1f))
                            val alphaDeg = Math.toDegrees(asin((dy / totalD).toDouble().coerceIn(-1.0, 1.0))).toFloat()

                            val leftFilletRect = Rect(centerX - dx - rf, 0f, centerX - dx + rf, rf * 2f)
                            val cradleRect = Rect(centerX - cradleR, fabCyBar - cradleR, centerX + cradleR, fabCyBar + cradleR)
                            val rightFilletRect = Rect(centerX + dx - rf, 0f, centerX + dx + rf, rf * 2f)

                            fun Path.traceCradleTop() {
                                moveTo(0f, 0f)
                                lineTo(centerX - dx, 0f)
                                arcTo(leftFilletRect, -90f, 90f - alphaDeg, false)
                                arcTo(cradleRect, 180f - alphaDeg, -(180f - 2f * alphaDeg), false)
                                arcTo(rightFilletRect, 180f + alphaDeg, 90f - alphaDeg, false)
                                lineTo(w, 0f)
                            }

                            val fillPath = Path().apply {
                                traceCradleTop()
                                lineTo(w, h)
                                lineTo(0f, h)
                                close()
                            }
                            val topEdge = Path().apply {
                                traceCradleTop()
                            }
                            val vignetteCenter = Offset(centerX, fabCyBar)
                            val vignetteBrush = Brush.radialGradient(
                                colors = cradleVignetteColors,
                                center = vignetteCenter,
                                radius = cradleR * 1.08f,
                            )
                            val strokeWidthPx = with(density) { 1.dp.toPx() }

                            onDrawBehind {
                                drawCircle(
                                    brush = vignetteBrush,
                                    radius = cradleR * 1.08f,
                                    center = vignetteCenter,
                                )
                                drawPath(path = fillPath, color = barBg)
                                drawPath(
                                    path = topEdge,
                                    color = borderStroke,
                                    style = Stroke(width = strokeWidthPx),
                                )
                            }
                        },
                )

                // Asymmetric liquid droplet hugging the round Play FAB cradle + bioluminescent rim highlight sweep
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val head = tabProgress().coerceIn(0f, 1f)
                    val tail = tailProgress.value.coerceIn(-0.06f, 1.06f)

                    val w = size.width
                    val h = size.height
                    val centerX = w * 0.5f

                    val geometryDirty = abs(head - geomCache.lastHead) > 0.0005f ||
                        abs(tail - geomCache.lastTail) > 0.0005f ||
                        w != geomCache.lastW ||
                        h != geomCache.lastH

                    if (geometryDirty) {
                        val sidePadPx = 16.dp.toPx()
                        val centerGapPx = 92.dp.toPx()
                        val tabSlotW = ((w - sidePadPx * 2f - centerGapPx) * 0.5f).coerceAtLeast(1f)
                        val leftCx = sidePadPx + tabSlotW * 0.5f
                        val rightCx = w - sidePadPx - tabSlotW * 0.5f

                        val headCx = lerp(leftCx, rightCx, head)
                        val tailCx = lerp(leftCx, rightCx, tail)
                        val restHalfW = (116.dp.toPx().coerceAtMost(tabSlotW * 0.92f)) * 0.5f
                        val spanDist = abs(headCx - tailCx)

                        val leftX = min(headCx, tailCx) - restHalfW * (1f - 0.14f * (spanDist / w))
                        val rightX = max(headCx, tailCx) + restHalfW * (1f - 0.14f * (spanDist / w))
                        val blobW = (rightX - leftX).coerceAtLeast(36.dp.toPx())
                        val midX = (leftX + rightX) * 0.5f

                        val midNorm = ((head + tail) * 0.5f).coerceIn(0f, 1f)
                        val centerTransit = sin(midNorm * PI).toFloat().coerceIn(0f, 1f)
                        val stretchFactor = (spanDist / (tabSlotW * 0.85f)).coerceIn(0f, 1f)
                        val activeEnergy = max(centerTransit, stretchFactor)

                        val restHalfH = 20.5.dp.toPx()
                        val neckHalfH = 7.2.dp.toPx()
                        val restCenterY = h * 0.5f
                        val channelCenterY = h - 13.dp.toPx()
                        val cradleZonePx = 54.dp.toPx()
                        val minRadiusPx = 5.dp.toPx()
                        val slopeDx = 2.dp.toPx()
                        val minYClamp = 4.dp.toPx()
                        val maxYClamp = h - 2.5.dp.toPx()

                        fun underCradleAt(x: Float): Float {
                            val normDx = (abs(x - centerX) / cradleZonePx).coerceIn(0f, 1f)
                            return 0.5f * (1f + cos(normDx * PI.toFloat()))
                        }

                        fun cyAt(x: Float): Float = lerp(restCenterY, channelCenterY, underCradleAt(x))

                        fun halfHAt(x: Float): Float {
                            val uc = underCradleAt(x)
                            val u = ((x - leftX) / blobW).coerceIn(0f, 1f)
                            val leadBias = if (head >= tail) u else (1f - u)
                            val directionalPlump =
                                lerp(0.86f, 1.04f, leadBias) * (1f - 0.16f * stretchFactor) + 0.16f * stretchFactor
                            return (lerp(restHalfH, neckHalfH, uc) * directionalPlump).coerceAtLeast(minRadiusPx)
                        }

                        val rL0 = halfHAt((leftX + 14.dp.toPx()).coerceAtMost(midX))
                        val leftBodyX = (leftX + rL0).coerceAtMost(midX - 1f)
                        val rLeft = halfHAt(leftBodyX).coerceAtMost(blobW * 0.44f)

                        val rR0 = halfHAt((rightX - 14.dp.toPx()).coerceAtLeast(midX))
                        val rightBodyX = (rightX - rR0).coerceAtLeast(midX + 1f)
                        val rRight = halfHAt(rightBodyX).coerceAtMost(blobW * 0.44f)

                        var k = 0
                        val twoSlopeDx = 2f * slopeDx
                        for (i in 0 until spineCount) {
                            val frac = i.toFloat() / (spineCount - 1).toFloat()
                            val x = lerp(leftBodyX, rightBodyX, frac)
                            val cy = cyAt(x)
                            val hh = halfHAt(x)
                            val slope = (cyAt(x + slopeDx) - cyAt(x - slopeDx)) / twoSlopeDx
                            val tx = 1f / sqrt(1f + slope * slope)
                            val ty = slope * tx
                            ptX[k] = x + ty * hh
                            ptY[k] = (cy - tx * hh).coerceIn(minYClamp, maxYClamp)
                            k++
                        }

                        val cyRight = cyAt(rightBodyX)
                        val slopeR = (cyAt(rightBodyX + slopeDx) - cyAt(rightBodyX - slopeDx)) / twoSlopeDx
                        val txR = 1f / sqrt(1f + slopeR * slopeR)
                        val tyR = slopeR * txR
                        for (step in 1..capMidSteps) {
                            val ang = (-PI * 0.5f + PI * step / (capMidSteps + 1)).toFloat()
                            val cosA = cos(ang)
                            val sinA = sin(ang)
                            ptX[k] = rightBodyX + rRight * (txR * cosA - tyR * sinA)
                            ptY[k] = (cyRight + rRight * (tyR * cosA + txR * sinA)).coerceIn(minYClamp, maxYClamp)
                            k++
                        }

                        for (i in 0 until spineCount) {
                            val frac = i.toFloat() / (spineCount - 1).toFloat()
                            val x = lerp(rightBodyX, leftBodyX, frac)
                            val cy = cyAt(x)
                            val hh = halfHAt(x)
                            val slope = (cyAt(x + slopeDx) - cyAt(x - slopeDx)) / twoSlopeDx
                            val tx = 1f / sqrt(1f + slope * slope)
                            val ty = slope * tx
                            ptX[k] = x - ty * hh
                            ptY[k] = (cy + tx * hh).coerceIn(minYClamp, maxYClamp)
                            k++
                        }

                        val cyLeft = cyAt(leftBodyX)
                        val slopeL = (cyAt(leftBodyX + slopeDx) - cyAt(leftBodyX - slopeDx)) / twoSlopeDx
                        val txL = 1f / sqrt(1f + slopeL * slopeL)
                        val tyL = slopeL * txL
                        for (step in 1..capMidSteps) {
                            val ang = (PI * 0.5f + PI * step / (capMidSteps + 1)).toFloat()
                            val cosA = cos(ang)
                            val sinA = sin(ang)
                            ptX[k] = leftBodyX + rLeft * (txL * cosA - tyL * sinA)
                            ptY[k] = (cyLeft + rLeft * (tyL * cosA + txL * sinA)).coerceIn(minYClamp, maxYClamp)
                            k++
                        }

                        blobPath.reset()
                        val lastIdx = k - 1
                        blobPath.moveTo((ptX[lastIdx] + ptX[0]) * 0.5f, (ptY[lastIdx] + ptY[0]) * 0.5f)
                        for (idx in 0 until k) {
                            val nextIdx = if (idx + 1 < k) idx + 1 else 0
                            blobPath.quadraticTo(
                                ptX[idx],
                                ptY[idx],
                                (ptX[idx] + ptX[nextIdx]) * 0.5f,
                                (ptY[idx] + ptY[nextIdx]) * 0.5f,
                            )
                        }
                        blobPath.close()

                        geomCache.lastHead = head
                        geomCache.lastTail = tail
                        geomCache.lastW = w
                        geomCache.lastH = h
                        geomCache.cachedLeftX = leftX
                        geomCache.cachedRightX = rightX
                        geomCache.cachedActiveEnergy = activeEnergy
                        geomCache.cachedCenterTransit = centerTransit
                        geomCache.cachedMidNorm = midNorm
                        geomCache.cachedLeftCx = leftCx
                        geomCache.cachedRightCx = rightCx
                        geomCache.cachedChannelY = channelCenterY
                        geomCache.cachedGlowCenter = Offset(lerp(tailCx, headCx, 0.58f), channelCenterY)
                    }

                    val activeEnergy = geomCache.cachedActiveEnergy
                    val centerTransit = geomCache.cachedCenterTransit
                    val leftX = geomCache.cachedLeftX
                    val rightX = geomCache.cachedRightX

                    // 1. Soft ambient cyan radial bloom peeking beneath the circular Play FAB cradle
                    if (activeEnergy > 0.02f) {
                        val glowStrength = 0.14f + 0.36f * activeEnergy
                        underGlowColors[0] = accentGlow.copy(alpha = glowStrength)
                        underGlowColors[1] = Color.Transparent
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = underGlowColors,
                                center = geomCache.cachedGlowCenter,
                                radius = 64.dp.toPx(),
                            ),
                            radius = 64.dp.toPx(),
                            center = geomCache.cachedGlowCenter,
                        )
                    }

                    // 2. Soft outer neon bloom halo hugging the droplet silhouette
                    drawPath(
                        path = blobPath,
                        color = accentGlow.copy(alpha = 0.10f + 0.28f * activeEnergy),
                        style = Stroke(width = (3.5f + 2.5f * activeEnergy).dp.toPx()),
                    )

                    // 3. Adaptive high-contrast liquid jewel fill (dark obsidian-emerald in Dark, frosted aqua in Light)
                    val surgeTarget = if (isDarkDock) Color(0xFF134B49) else accentGlow.copy(alpha = 0.26f)
                    val deepSurge = lerp(pillColor, surgeTarget, 0.15f + activeEnergy * 0.85f)
                    val crestRim = lerp(deepSurge, accentGlow.copy(alpha = if (isDarkDock) 0.44f else 0.36f), activeEnergy * 0.62f)
                    if (head >= tail) {
                        dropletColors[0] = pillColor
                        dropletColors[1] = deepSurge
                        dropletColors[2] = crestRim
                    } else {
                        dropletColors[0] = crestRim
                        dropletColors[1] = deepSurge
                        dropletColors[2] = pillColor
                    }
                    drawPath(
                        path = blobPath,
                        brush = Brush.horizontalGradient(
                            colors = dropletColors,
                            startX = leftX,
                            endX = rightX,
                        ),
                    )

                    // 4. Razor-sharp bioluminescent specular inner rim along droplet
                    drawPath(
                        path = blobPath,
                        color = lerp(accentGlow, Color.White, 0.28f).copy(alpha = 0.30f + 0.62f * activeEnergy),
                        style = Stroke(width = (1.15f + 0.45f * activeEnergy).dp.toPx()),
                    )

                    // 5. Concentric circular cradle rim sweep matching our exact round `arcTo` cutout (`37.dp`)
                    if (centerTransit > 0.03f) {
                        val fabCyBar = 6.dp.toPx()
                        val cradleR = 37.dp.toPx()
                        val rf = 12.dp.toPx()
                        val dy = rf - fabCyBar
                        val totalD = cradleR + rf
                        val dx = sqrt((totalD * totalD - dy * dy).coerceAtLeast(1f))
                        val alphaDeg = Math.toDegrees(asin((dy / totalD).toDouble().coerceIn(-1.0, 1.0))).toFloat()

                        rimGlowPath.reset()
                        rimGlowPath.moveTo(centerX - dx - 10.dp.toPx(), 0.8.dp.toPx())
                        rimGlowPath.lineTo(centerX - dx, 0.8.dp.toPx())
                        rimGlowPath.arcTo(
                            Rect(centerX - dx - rf, 0.8.dp.toPx(), centerX - dx + rf, rf * 2f),
                            -90f,
                            90f - alphaDeg,
                            false,
                        )
                        rimGlowPath.arcTo(
                            Rect(centerX - cradleR, fabCyBar - cradleR, centerX + cradleR, fabCyBar + cradleR),
                            180f - alphaDeg,
                            -(180f - 2f * alphaDeg),
                            false,
                        )
                        rimGlowPath.arcTo(
                            Rect(centerX + dx - rf, 0.8.dp.toPx(), centerX + dx + rf, rf * 2f),
                            180f + alphaDeg,
                            90f - alphaDeg,
                            false,
                        )
                        rimGlowPath.lineTo(centerX + dx + 10.dp.toPx(), 0.8.dp.toPx())

                        val beamCenterX = lerp(geomCache.cachedLeftCx, geomCache.cachedRightCx, geomCache.cachedMidNorm)
                        rimBeamColors[0] = Color.Transparent
                        rimBeamColors[1] = lerp(accentGlow, Color.White, 0.38f).copy(alpha = 0.90f * centerTransit)
                        rimBeamColors[2] = Color.Transparent
                        drawPath(
                            path = rimGlowPath,
                            brush = Brush.horizontalGradient(
                                colors = rimBeamColors,
                                startX = beamCenterX - 58.dp.toPx(),
                                endX = beamCenterX + 58.dp.toPx(),
                            ),
                            style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BottomTabPill(
                        icon = Icons.Rounded.Movie,
                        label = "Library",
                        tabIndex = 0f,
                        tabProgress = tabProgress,
                        tailProgress = { tailProgress.value.coerceIn(0f, 1f) },
                        onClick = { onSelectTab(HubTab.LIBRARY) },
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(modifier = Modifier.width(92.dp))

                    BottomTabPill(
                        icon = Icons.Rounded.Tune,
                        label = "Theater",
                        tabIndex = 1f,
                        tabProgress = tabProgress,
                        tailProgress = { tailProgress.value.coerceIn(0f, 1f) },
                        onClick = { onSelectTab(HubTab.THEATER) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Soft reversed breathing ambient aura behind the round Play FAB + surge flare on tab transit
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-8).dp)
                    .size(84.dp)
                    .graphicsLayer {
                        val b = breathState.value
                        val transitWave = sin(tabProgress().coerceIn(0f, 1f) * PI).toFloat().coerceIn(0f, 1f)
                        val s = 0.94f + 0.14f * b + 0.18f * transitWave
                        scaleX = s
                        scaleY = s
                        alpha = (0.24f + 0.22f * b + 0.42f * transitWave).coerceIn(0f, 0.88f)
                        translationY = -2.dp.toPx() * transitWave
                    }
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                accentGlow.copy(alpha = 0.72f),
                                accentGlow.copy(alpha = 0.22f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )

            // Rotating luminous outer orbital rim drawn in cached canvas layer (no circular clip mask overhead at 120Hz)
            val orbitalRingColors = remember(accentGlow) {
                listOf(
                    accentGlow.copy(alpha = 0.10f),
                    Color.White.copy(alpha = 0.78f),
                    accentGlow.copy(alpha = 0.88f),
                    Color.Transparent,
                    accentGlow.copy(alpha = 0.10f),
                )
            }
            Spacer(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-1).dp)
                    .size(70.dp)
                    .graphicsLayer {
                        val b = breathState.value
                        val transitWave = sin(tabProgress().coerceIn(0f, 1f) * PI).toFloat().coerceIn(0f, 1f)
                        val s = fabScale * (1f + 0.02f * b + 0.06f * transitWave)
                        scaleX = s
                        scaleY = s
                        rotationZ = coronaSpin.value
                        alpha = (0.34f + 0.18f * b + 0.48f * transitWave).coerceIn(0f, 1f)
                        translationY = -2.dp.toPx() * transitWave
                    }
                    .drawWithCache {
                        val strokeW = 1.6.dp.toPx()
                        val ringRadius = size.minDimension * 0.5f - strokeW * 0.5f
                        val ringCenter = Offset(size.width * 0.5f, size.height * 0.5f)
                        val sweep = Brush.sweepGradient(colors = orbitalRingColors, center = ringCenter)
                        val ringStroke = Stroke(width = strokeW, cap = StrokeCap.Round)
                        onDrawBehind {
                            drawCircle(
                                brush = sweep,
                                radius = ringRadius,
                                center = ringCenter,
                                style = ringStroke,
                            )
                        }
                    },
            )

            // Play FAB with concentric positioning inside circular cradle + iris swell/squeeze reaction
            FloatingActionButton(
                onClick = onEnterVr,
                interactionSource = fabInteraction,
                shape = CircleShape,
                containerColor = extras.fabSurface,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 10.dp,
                    pressedElevation = 2.dp,
                ),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = 2.dp)
                    .size(64.dp)
                    .graphicsLayer {
                        val b = breathState.value
                        val transitWave = sin(tabProgress().coerceIn(0f, 1f) * PI).toFloat().coerceIn(0f, 1f)
                        val s = fabScale * (1f + 0.015f * b + 0.055f * transitWave)
                        scaleX = s * (1f + 0.025f * transitWave)
                        scaleY = s * (1f - 0.018f * transitWave)
                        translationY = -2.dp.toPx() * transitWave
                    },
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = "Enter VR Theater",
                    modifier = Modifier
                        .size(34.dp)
                        .graphicsLayer {
                            val b = breathState.value
                            val transitWave = sin(tabProgress().coerceIn(0f, 1f) * PI).toFloat().coerceIn(0f, 1f)
                            val iconScale = 1f + 0.045f * b + 0.08f * transitWave
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
        }

        // Fill the system navigation bar inset continuously so scrolled list items never peek below the dock
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(barBg)
                .windowInsetsBottomHeight(WindowInsets.navigationBars),
        )
    }
}

@Composable
private fun BottomTabPill(
    icon: ImageVector,
    label: String,
    tabIndex: Float,
    tabProgress: () -> Float,
    tailProgress: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = LocalCinemaExtras.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "tabScale",
    )

    val isDarkDock = extras.bottomBarBg.luminance() < 0.5f
    val activeColor = if (isDarkDock) {
        lerp(MaterialTheme.colorScheme.primary, Color.White, 0.34f)
    } else {
        Color(0xFF064D56)
    }
    val inactiveColor = extras.inkMuted

    // Overlaid muted/active content faded smoothly inside GPU graphicsLayer (zero VSync recompositions)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val p = tabProgress().coerceIn(0f, 1f)
                    val t = tailProgress().coerceIn(0f, 1f)
                    val closeness = (1f - abs(p - tabIndex)).coerceIn(0f, 1f)
                    val arriveEnergy = abs(p - t).coerceIn(0f, 1f) * closeness
                    val s = pressScale * (1f + 0.045f * closeness + 0.05f * arriveEnergy)
                    scaleX = s
                    scaleY = s
                    translationY = -1.2.dp.toPx() * arriveEnergy
                }
                .clip(RoundedCornerShape(25.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            TabContentRow(
                icon = icon,
                label = label,
                color = inactiveColor,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer {
                    val closeness = (1f - abs(tabProgress().coerceIn(0f, 1f) - tabIndex)).coerceIn(0f, 1f)
                    alpha = 1f - closeness
                },
            )
            TabContentRow(
                icon = icon,
                label = label,
                color = activeColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.graphicsLayer {
                    val closeness = (1f - abs(tabProgress().coerceIn(0f, 1f) - tabIndex)).coerceIn(0f, 1f)
                    alpha = closeness
                },
            )
        }
    }
}

@Composable
private fun TabContentRow(
    icon: ImageVector,
    label: String,
    color: Color,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = fontWeight),
            color = color,
            maxLines = 1,
        )
    }
}
