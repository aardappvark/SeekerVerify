package com.seekerverify.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.seekerverify.app.ui.theme.SeekerBlue
import com.seekerverify.app.ui.theme.SolanaGreen
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Radar/spider chart visualizing prediction metric scores across 9 axes.
 * Optionally overlays a second polygon for projected scores.
 */
@Composable
fun RadarChart(
    breakdown: Map<String, Double>,
    projectedBreakdown: Map<String, Double>? = null,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    // Ordered axes matching PredictorEngine breakdown keys
    val axes = remember {
        listOf(
            "Transactions" to "TX",
            "Unique dApps" to "dApps",
            "Token Diversity" to "Tokens",
            "SKR Staking" to "Staking",
            ".skr Domain" to "Domain",
            "NFTs" to "NFTs",
            "Wallet Age" to "Age",
            "dApp Frequency" to "Freq",
            "Consistency" to "Active",
            "Season 1 Tier" to "S1 Tier"
        )
    }

    // Normalize scores to 0-1
    val scores = remember(breakdown) {
        axes.map { (key, _) -> ((breakdown[key] ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) }
    }
    val projectedScores = remember(projectedBreakdown) {
        projectedBreakdown?.let { pb ->
            axes.map { (key, _) -> ((pb[key] ?: 0.0) / 100.0).toFloat().coerceIn(0f, 1f) }
        }
    }

    // Animate the polygon scale from 0 to 1
    val animatedScale by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1000),
        label = "radarScale"
    )

    val gridColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val labelColor = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f)
    val axisColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
        ) {
            val centerX = size.width / 2
            val centerY = size.height / 2
            val radius = min(centerX, centerY) * 0.68f
            val n = axes.size
            val angleStep = (2 * PI / n).toFloat()
            val startAngle = (-PI / 2).toFloat() // Start from top

            // Draw concentric grid rings (25%, 50%, 75%, 100%)
            for (ring in 1..4) {
                val ringRadius = radius * ring / 4f
                val ringPath = Path()
                for (i in 0 until n) {
                    val angle = startAngle + i * angleStep
                    val x = centerX + ringRadius * cos(angle)
                    val y = centerY + ringRadius * sin(angle)
                    if (i == 0) ringPath.moveTo(x, y) else ringPath.lineTo(x, y)
                }
                ringPath.close()
                drawPath(ringPath, gridColor, style = Stroke(width = 1f))
            }

            // Draw axis lines from center to each vertex
            for (i in 0 until n) {
                val angle = startAngle + i * angleStep
                val x = centerX + radius * cos(angle)
                val y = centerY + radius * sin(angle)
                drawLine(axisColor, Offset(centerX, centerY), Offset(x, y), strokeWidth = 1f)
            }

            // Draw projected polygon first (behind current)
            if (projectedScores != null) {
                drawDataPolygon(
                    scores = projectedScores,
                    scale = animatedScale,
                    centerX = centerX,
                    centerY = centerY,
                    radius = radius,
                    startAngle = startAngle,
                    angleStep = angleStep,
                    fillColor = SolanaGreen.copy(alpha = 0.08f),
                    strokeColor = SolanaGreen.copy(alpha = 0.4f),
                    strokeWidth = 2f
                )
            }

            // Draw current scores polygon
            drawDataPolygon(
                scores = scores,
                scale = animatedScale,
                centerX = centerX,
                centerY = centerY,
                radius = radius,
                startAngle = startAngle,
                angleStep = angleStep,
                fillColor = SeekerBlue.copy(alpha = 0.15f),
                strokeColor = SeekerBlue,
                strokeWidth = 2.5f
            )

            // Draw score dots on vertices
            for (i in scores.indices) {
                val angle = startAngle + i * angleStep
                val scoreRadius = radius * scores[i] * animatedScale
                val x = centerX + scoreRadius * cos(angle)
                val y = centerY + scoreRadius * sin(angle)
                drawCircle(SeekerBlue, radius = 4f, center = Offset(x, y))
            }

            // Draw axis labels
            for (i in axes.indices) {
                val angle = startAngle + i * angleStep
                val labelRadius = radius + 28f
                val x = centerX + labelRadius * cos(angle)
                val y = centerY + labelRadius * sin(angle)
                drawAxisLabel(axes[i].second, x, y, labelColor)
            }
        }
    }
}

private fun DrawScope.drawDataPolygon(
    scores: List<Float>,
    scale: Float,
    centerX: Float,
    centerY: Float,
    radius: Float,
    startAngle: Float,
    angleStep: Float,
    fillColor: Color,
    strokeColor: Color,
    strokeWidth: Float
) {
    val path = Path()
    for (i in scores.indices) {
        val angle = startAngle + i * angleStep
        val scoreRadius = radius * scores[i] * scale
        val x = centerX + scoreRadius * cos(angle)
        val y = centerY + scoreRadius * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, fillColor)
    drawPath(path, strokeColor, style = Stroke(width = strokeWidth))
}

private fun DrawScope.drawAxisLabel(
    text: String,
    x: Float,
    y: Float,
    color: Color
) {
    drawContext.canvas.nativeCanvas.apply {
        val paint = android.graphics.Paint().apply {
            this.color = android.graphics.Color.argb(
                (color.alpha * 255).toInt(),
                (color.red * 255).toInt(),
                (color.green * 255).toInt(),
                (color.blue * 255).toInt()
            )
            textSize = 28f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        drawText(text, x, y + 10f, paint)
    }
}
