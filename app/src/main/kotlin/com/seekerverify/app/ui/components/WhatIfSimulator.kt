package com.seekerverify.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalView
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.engine.PredictorEngine
import com.seekerverify.app.model.AirdropTier
import com.seekerverify.app.ui.theme.SeekerBlue
import com.seekerverify.app.ui.theme.SeekerGold
import com.seekerverify.app.ui.theme.SolanaGreen
import com.seekerverify.app.ui.util.hapticTap
import kotlin.math.roundToInt

/**
 * Interactive What-If Simulator.
 * Users adjust sliders to simulate future on-chain actions and see
 * real-time impact on their predicted airdrop tier.
 */
@Composable
fun WhatIfSimulator(
    currentMetrics: PredictorEngine.ActivityMetrics,
    currentResult: PredictorEngine.PredictorResult,
    onSimulatorUsed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val prefs = remember { AppPreferences(view.context) }
    var isExpanded by remember { mutableStateOf(false) }
    var hasTriggeredUsed by remember { mutableStateOf(false) }

    // Slider states — deltas on top of current metrics
    var extraStakeDays by remember { mutableFloatStateOf(0f) }
    var extraTransactions by remember { mutableFloatStateOf(0f) }
    var extraDappInteractions by remember { mutableFloatStateOf(0f) }
    var extraPrograms by remember { mutableFloatStateOf(0f) }
    var extraNfts by remember { mutableFloatStateOf(0f) }
    var extraActiveDays by remember { mutableFloatStateOf(0f) }

    // Compute simulated result on every recomposition (PredictorEngine.predict is pure + fast)
    val simulatedMetrics = currentMetrics.copy(
        totalTransactions = currentMetrics.totalTransactions + extraTransactions.roundToInt(),
        uniquePrograms = currentMetrics.uniquePrograms + extraPrograms.roundToInt(),
        dappInteractions = currentMetrics.dappInteractions + extraDappInteractions.roundToInt(),
        nftCount = currentMetrics.nftCount + extraNfts.roundToInt(),
        skrStaked = currentMetrics.skrStaked || extraStakeDays > 0,
        stakingDurationDays = maxOf(
            currentMetrics.stakingDurationDays,
            extraStakeDays.roundToInt()
        ),
        uniqueActiveDays = currentMetrics.uniqueActiveDays + extraActiveDays.roundToInt()
    )
    val simulatedResult = remember(simulatedMetrics) {
        PredictorEngine.predict(simulatedMetrics)
    }

    val scoreDelta = simulatedResult.compositeScore - currentResult.compositeScore
    val tierChanged = simulatedResult.predictedTier != currentResult.predictedTier
    val hasAnyAdjustment = extraStakeDays > 0 || extraTransactions > 0 ||
        extraDappInteractions > 0 || extraPrograms > 0 || extraNfts > 0 || extraActiveDays > 0

    if (hasAnyAdjustment && !hasTriggeredUsed) {
        hasTriggeredUsed = true
        onSimulatorUsed()
    }

    GlassCard(modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header with expand toggle
            TextButton(
                onClick = { isExpanded = !isExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = SeekerBlue
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "What-If Simulator",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp
                        else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!isExpanded) {
                Text(
                    text = "Simulate future actions to see tier impact",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Result preview — always visible when expanded
                    if (hasAnyAdjustment) {
                        SimulatedResultCard(
                            currentTier = currentResult.predictedTier,
                            simulatedTier = simulatedResult.predictedTier,
                            currentScore = currentResult.compositeScore,
                            simulatedScore = simulatedResult.compositeScore,
                            scoreDelta = scoreDelta,
                            tierChanged = tierChanged
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Slider: Staking Duration (biggest lever — 21% weight)
                    SimulatorSlider(
                        label = "Staking Duration",
                        value = extraStakeDays,
                        onValueChange = { extraStakeDays = it; view.hapticTap(prefs) },
                        valueRange = 0f..365f,
                        steps = 72,
                        displayValue = "${extraStakeDays.roundToInt()} days",
                        weight = "21%"
                    )

                    // Slider: Additional Transactions (16% weight)
                    SimulatorSlider(
                        label = "Extra Transactions",
                        value = extraTransactions,
                        onValueChange = { extraTransactions = it; view.hapticTap(prefs) },
                        valueRange = 0f..500f,
                        steps = 49,
                        displayValue = "+${extraTransactions.roundToInt()}",
                        weight = "16%"
                    )

                    // Slider: dApp Interactions (11% weight)
                    SimulatorSlider(
                        label = "Extra dApp Interactions",
                        value = extraDappInteractions,
                        onValueChange = { extraDappInteractions = it; view.hapticTap(prefs) },
                        valueRange = 0f..200f,
                        steps = 19,
                        displayValue = "+${extraDappInteractions.roundToInt()}",
                        weight = "11%"
                    )

                    // Slider: Unique dApps (12% weight)
                    SimulatorSlider(
                        label = "New Programs",
                        value = extraPrograms,
                        onValueChange = { extraPrograms = it; view.hapticTap(prefs) },
                        valueRange = 0f..15f,
                        steps = 14,
                        displayValue = "+${extraPrograms.roundToInt()}",
                        weight = "12%"
                    )

                    // Slider: NFTs (5% weight)
                    SimulatorSlider(
                        label = "Extra NFTs",
                        value = extraNfts,
                        onValueChange = { extraNfts = it; view.hapticTap(prefs) },
                        valueRange = 0f..20f,
                        steps = 19,
                        displayValue = "+${extraNfts.roundToInt()}",
                        weight = "5%"
                    )

                    // Slider: Consistency (5% weight)
                    SimulatorSlider(
                        label = "Active Days",
                        value = extraActiveDays,
                        onValueChange = { extraActiveDays = it; view.hapticTap(prefs) },
                        valueRange = 0f..60f,
                        steps = 11,
                        displayValue = "+${extraActiveDays.roundToInt()} days",
                        weight = "5%"
                    )

                    if (!hasAnyAdjustment) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Adjust the sliders above to simulate future actions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SimulatedResultCard(
    currentTier: AirdropTier,
    simulatedTier: AirdropTier,
    currentScore: Double,
    simulatedScore: Double,
    scoreDelta: Double,
    tierChanged: Boolean
) {
    val targetColor = if (tierChanged) SeekerGold else SolanaGreen
    val animatedProgress by animateFloatAsState(
        targetValue = (simulatedScore / 100.0).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(400),
        label = "simScore"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(targetColor.copy(alpha = 0.1f))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (tierChanged) {
                Text(
                    text = currentTier.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = SeekerGold
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = simulatedTier.displayName,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = if (tierChanged) SeekerGold else SolanaGreen
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = targetColor,
            trackColor = targetColor.copy(alpha = 0.2f)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Score: ${"%.1f".format(simulatedScore)} (+${"%.1f".format(scoreDelta)} pts)",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = targetColor
        )
    }
}

@Composable
private fun SimulatorSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    displayValue: String,
    weight: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (value > 0) SeekerBlue else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = weight,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 10.sp
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = SeekerBlue,
                activeTrackColor = SeekerBlue,
                inactiveTrackColor = SeekerBlue.copy(alpha = 0.2f)
            )
        )
    }
}

private fun Int.formatK(): String = when {
    this >= 1000 -> "${this / 1000}K"
    else -> "$this"
}
