package com.seekerverify.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seekerverify.app.model.PredictionHistoryEntry
import com.seekerverify.app.ui.theme.SeekerBlue
import com.seekerverify.app.ui.theme.SeekerGold
import com.seekerverify.app.ui.theme.SolanaGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sparkline chart showing prediction score trajectory over time.
 * Includes tier threshold guide lines and trend summary.
 */
@Composable
fun ScoreTrajectoryChart(
    history: List<PredictionHistoryEntry>,
    modifier: Modifier = Modifier
) {
    if (history.size < 2) return

    val isDark = isSystemInDarkTheme()
    val sorted = remember(history) { history.sortedBy { it.timestamp } }

    // Trend calculation
    val firstScore = sorted.first().compositeScore
    val lastScore = sorted.last().compositeScore
    val delta = lastScore - firstScore
    val trendColor = when {
        delta > 1.0 -> SolanaGreen
        delta < -1.0 -> Color(0xFFFF6B6B)
        else -> SeekerBlue
    }
    val trendText = when {
        delta > 1.0 -> "+${"%.1f".format(delta)} pts"
        delta < -1.0 -> "${"%.1f".format(delta)} pts"
        else -> "Stable"
    }

    val gridColor = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.04f)
    val labelColor = if (isDark) Color.White.copy(alpha = 0.4f) else Color.Black.copy(alpha = 0.3f)

    // Tier thresholds for guide lines
    val thresholds = remember {
        listOf(
            13.0 to "Prosp.",
            53.0 to "Vang.",
            71.0 to "Lum.",
            79.0 to "Sov."
        )
    }

    // Animate draw progress
    val drawProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1200),
        label = "trajectoryDraw"
    )

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Score Trajectory",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = trendText,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = trendColor
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${sorted.size} data points",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val padding = 40f
            val chartWidth = size.width - padding * 2
            val chartHeight = size.height - padding * 1.5f
            val startX = padding
            val startY = padding * 0.5f

            val minScore = 0.0
            val maxScore = 100.0
            val scoreRange = maxScore - minScore

            // Draw tier threshold guide lines (dashed)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            for ((threshold, label) in thresholds) {
                val y = startY + chartHeight * (1 - (threshold - minScore) / scoreRange).toFloat()
                drawLine(
                    color = gridColor,
                    start = Offset(startX, y),
                    end = Offset(startX + chartWidth, y),
                    strokeWidth = 1f,
                    pathEffect = dashEffect
                )
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    startX - 4f,
                    y - 4f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(
                            (labelColor.alpha * 255).toInt(),
                            (labelColor.red * 255).toInt(),
                            (labelColor.green * 255).toInt(),
                            (labelColor.blue * 255).toInt()
                        )
                        textSize = 22f
                        textAlign = android.graphics.Paint.Align.RIGHT
                        isAntiAlias = true
                    }
                )
            }

            // Draw score line
            val pointCount = (sorted.size * drawProgress).toInt().coerceAtLeast(2)
            val visiblePoints = sorted.take(pointCount)

            val linePath = Path()
            val gradientPath = Path()

            for (i in visiblePoints.indices) {
                val x = if (sorted.size == 1) startX + chartWidth / 2
                else startX + chartWidth * i / (sorted.size - 1).toFloat()
                val y = startY + chartHeight * (1 - (visiblePoints[i].compositeScore - minScore) / scoreRange).toFloat()

                if (i == 0) {
                    linePath.moveTo(x, y)
                    gradientPath.moveTo(x, startY + chartHeight)
                    gradientPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    gradientPath.lineTo(x, y)
                }
            }

            // Close gradient path
            val lastX = if (sorted.size == 1) startX + chartWidth / 2
            else startX + chartWidth * (pointCount - 1) / (sorted.size - 1).toFloat()
            gradientPath.lineTo(lastX, startY + chartHeight)
            gradientPath.close()

            // Draw gradient fill
            drawPath(gradientPath, trendColor.copy(alpha = 0.08f))

            // Draw line
            drawPath(linePath, trendColor, style = Stroke(width = 2.5f))

            // Draw dots at each data point
            for (i in visiblePoints.indices) {
                val x = if (sorted.size == 1) startX + chartWidth / 2
                else startX + chartWidth * i / (sorted.size - 1).toFloat()
                val y = startY + chartHeight * (1 - (visiblePoints[i].compositeScore - minScore) / scoreRange).toFloat()
                drawCircle(trendColor, radius = 3.5f, center = Offset(x, y))
            }

            // Draw date labels (first and last)
            val dateFormat = SimpleDateFormat("MMM d", Locale.US)
            val firstDate = dateFormat.format(Date(sorted.first().timestamp))
            val lastDate = dateFormat.format(Date(sorted.last().timestamp))

            drawContext.canvas.nativeCanvas.apply {
                val datePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(
                        (labelColor.alpha * 255).toInt(),
                        (labelColor.red * 255).toInt(),
                        (labelColor.green * 255).toInt(),
                        (labelColor.blue * 255).toInt()
                    )
                    textSize = 24f
                    isAntiAlias = true
                }
                datePaint.textAlign = android.graphics.Paint.Align.LEFT
                drawText(firstDate, startX, startY + chartHeight + 30f, datePaint)
                datePaint.textAlign = android.graphics.Paint.Align.RIGHT
                drawText(lastDate, startX + chartWidth, startY + chartHeight + 30f, datePaint)
            }
        }
    }
}
