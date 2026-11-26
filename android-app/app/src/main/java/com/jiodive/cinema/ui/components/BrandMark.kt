package com.jiodive.cinema.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jiodive.cinema.ui.theme.LocalCinemaExtras

@Composable
fun HeadsetBrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    val extras = LocalCinemaExtras.current
    val accent = extras.turquoiseAccent

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Single-tone strap arc
        val headband = Path().apply {
            moveTo(w * 0.16f, h * 0.26f)
            quadraticTo(w * 0.50f, h * 0.13f, w * 0.84f, h * 0.26f)
        }
        drawPath(
            path = headband,
            color = accent.copy(alpha = 0.78f),
            style = Stroke(width = w * 0.06f, cap = StrokeCap.Round),
        )

        // Visor silhouette
        val visor = Path().apply {
            moveTo(w * 0.20f, h * 0.34f)
            quadraticTo(w * 0.50f, h * 0.30f, w * 0.80f, h * 0.34f)
            cubicTo(w * 0.90f, h * 0.36f, w * 0.92f, h * 0.44f, w * 0.91f, h * 0.60f)
            cubicTo(w * 0.90f, h * 0.76f, w * 0.82f, h * 0.82f, w * 0.70f, h * 0.82f)
            cubicTo(w * 0.60f, h * 0.82f, w * 0.56f, h * 0.69f, w * 0.50f, h * 0.69f)
            cubicTo(w * 0.44f, h * 0.69f, w * 0.40f, h * 0.82f, w * 0.30f, h * 0.82f)
            cubicTo(w * 0.18f, h * 0.82f, w * 0.10f, h * 0.76f, w * 0.09f, h * 0.60f)
            cubicTo(w * 0.08f, h * 0.44f, w * 0.10f, h * 0.36f, w * 0.20f, h * 0.34f)
            close()
        }
        drawPath(
            path = visor,
            color = accent.copy(alpha = 0.12f),
        )
        drawPath(
            path = visor,
            color = accent,
            style = Stroke(width = w * 0.065f, join = StrokeJoin.Round),
        )

        // Matched symmetrical optical lenses
        drawRoundRect(
            color = accent,
            topLeft = Offset(w * 0.19f, h * 0.44f),
            size = Size(w * 0.24f, h * 0.20f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
        )
        drawRoundRect(
            color = accent,
            topLeft = Offset(w * 0.57f, h * 0.44f),
            size = Size(w * 0.24f, h * 0.20f),
            cornerRadius = CornerRadius(w * 0.06f, w * 0.06f),
        )
    }
}
