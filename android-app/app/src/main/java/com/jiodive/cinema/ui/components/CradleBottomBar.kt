package com.jiodive.cinema.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jiodive.cinema.ui.theme.LocalCinemaExtras

enum class HubTab {
    LIBRARY,
    THEATER,
}

/**
 * Cradled 72dp bottom bar + 64dp circular center VR launch FAB matching JioImmerse's
 * HomeBottomFabLayout proportions & 150ms pill transitions.
 */
@Composable
fun CradleBottomBar(
    selectedTab: HubTab,
    onSelectTab: (HubTab) -> Unit,
    onEnterVr: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = LocalCinemaExtras.current
    val density = LocalDensity.current
    val barBg = extras.bottomBarBg
    val borderStroke = extras.cardBorder

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .height(92.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // 72dp bar with carved circular cradle notch matching JioImmerse dimens (64dp FAB + 8dp margin)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val fabPx = with(density) { 64.dp.toPx() }
                val marginPx = with(density) { 8.dp.toPx() }
                val cradleRadius = fabPx * 0.5f + marginPx
                val centerX = w * 0.5f

                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(centerX - cradleRadius - 20f, 0f)
                    cubicTo(
                        centerX - cradleRadius - 6f,
                        0f,
                        centerX - cradleRadius,
                        8f,
                        centerX - cradleRadius + 4f,
                        22f,
                    )
                    cubicTo(
                        centerX - cradleRadius * 0.55f,
                        cradleRadius * 1.15f,
                        centerX + cradleRadius * 0.55f,
                        cradleRadius * 1.15f,
                        centerX + cradleRadius - 4f,
                        22f,
                    )
                    cubicTo(
                        centerX + cradleRadius,
                        8f,
                        centerX + cradleRadius + 6f,
                        0f,
                        centerX + cradleRadius + 20f,
                        0f,
                    )
                    lineTo(w, 0f)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
                drawPath(path = path, color = barBg)

                val topEdge = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(centerX - cradleRadius - 20f, 0f)
                    cubicTo(
                        centerX - cradleRadius - 6f,
                        0f,
                        centerX - cradleRadius,
                        8f,
                        centerX - cradleRadius + 4f,
                        22f,
                    )
                    cubicTo(
                        centerX - cradleRadius * 0.55f,
                        cradleRadius * 1.15f,
                        centerX + cradleRadius * 0.55f,
                        cradleRadius * 1.15f,
                        centerX + cradleRadius - 4f,
                        22f,
                    )
                    cubicTo(
                        centerX + cradleRadius,
                        8f,
                        centerX + cradleRadius + 6f,
                        0f,
                        centerX + cradleRadius + 20f,
                        0f,
                    )
                    lineTo(w, 0f)
                }
                drawPath(
                    path = topEdge,
                    color = borderStroke,
                    style = Stroke(width = with(density) { 1.dp.toPx() }),
                )
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
                    selected = selectedTab == HubTab.LIBRARY,
                    onClick = { onSelectTab(HubTab.LIBRARY) },
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(84.dp))

                BottomTabPill(
                    icon = Icons.Rounded.Tune,
                    label = "Theater",
                    selected = selectedTab == HubTab.THEATER,
                    onClick = { onSelectTab(HubTab.THEATER) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Centered 64dp floating Enter VR FAB
        FloatingActionButton(
            onClick = onEnterVr,
            shape = CircleShape,
            containerColor = extras.fabSurface,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp,
                pressedElevation = 2.dp,
            ),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 2.dp)
                .size(64.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "Enter VR Theater",
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
private fun BottomTabPill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extras = LocalCinemaExtras.current
    val pillColor by animateColorAsState(
        targetValue = if (selected) extras.activePillBg else extras.bottomBarBg.copy(alpha = 0f),
        animationSpec = tween(durationMillis = 150),
        label = "tabPillColor",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else extras.inkMuted,
        animationSpec = tween(durationMillis = 150),
        label = "tabContentColor",
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(25.dp))
                .background(pillColor)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                ),
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
