package com.seekerverify.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seekerverify.app.model.AirdropTier
import com.seekerverify.app.ui.theme.SeekerBlue
import com.seekerverify.app.ui.theme.SeekerGold
import com.seekerverify.app.ui.theme.SeekerGreen
import com.seekerverify.app.ui.theme.SolanaGreen
import com.seekerverify.app.ui.theme.SolanaPurple
import com.seekerverify.app.ui.theme.TierLuminary
import com.seekerverify.app.ui.theme.TierProspector
import com.seekerverify.app.ui.theme.TierScout
import com.seekerverify.app.ui.theme.TierSovereign
import com.seekerverify.app.ui.theme.TierVanguard
import com.seekerverify.app.ui.viewmodel.PredictorViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PredictorScreen(
    walletAddress: String,
    rpcUrl: String,
    viewModel: PredictorViewModel = viewModel()
) {
    val result by viewModel.result.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Season 2 Predictor",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Estimate your potential Season 2 airdrop tier",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (result == null && !isLoading) {
            // Not yet run — show prompt
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = SeekerBlue
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Analyze Your On-Chain Activity",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "We'll scan your transaction history, token holdings, staking activity, and more to predict your Season 2 airdrop tier.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { viewModel.runPrediction(walletAddress, rpcUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = SeekerBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Run Prediction", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (isLoading) {
            Spacer(modifier = Modifier.height(32.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = SeekerBlue, modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Analyzing on-chain activity...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "This may take a moment",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        result?.let { prediction ->
            // Predicted Tier Card
            val tierColor = getTierColor(prediction.predictedTier)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = tierColor),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PREDICTED TIER",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = prediction.predictedTier.displayName,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${NumberFormat.getNumberInstance(Locale.US).format(prediction.predictedTier.skrDisplay.toLong())} SKR",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Confidence badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${prediction.confidence} Confidence",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Score & Percentile Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Activity Score",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = String.format("%.0f", prediction.compositeScore),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = SeekerBlue
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Top ${String.format("%.1f", 100 - prediction.percentile)}%",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SolanaGreen
                            )
                            Text(
                                text = "of all Seekers",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val animatedProgress by animateFloatAsState(
                        targetValue = (prediction.compositeScore / 100.0).toFloat(),
                        animationSpec = tween(durationMillis = 1200),
                        label = "score"
                    )

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = SeekerBlue,
                        trackColor = SeekerBlue.copy(alpha = 0.15f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Score Breakdown Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Score Breakdown",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    prediction.breakdown.entries.sortedByDescending { it.value }.forEach { (metric, score) ->
                        MetricRow(name = metric, score = score)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Re-run button
            Button(
                onClick = { viewModel.runPrediction(walletAddress, rpcUrl) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = SeekerBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Re-run Prediction")
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Disclaimer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "This prediction is based on observable on-chain activity and Season 1 tier distribution patterns. Actual Season 2 criteria may differ. Not financial advice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MetricRow(name: String, score: Double) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = String.format("%.0f", score),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = when {
                    score >= 70 -> SolanaGreen
                    score >= 40 -> SeekerGold
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))

        val animatedProgress by animateFloatAsState(
            targetValue = (score / 100.0).toFloat(),
            animationSpec = tween(durationMillis = 800),
            label = name
        )

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = when {
                score >= 70 -> SolanaGreen
                score >= 40 -> SeekerGold
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            },
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)
        )
    }
}

private fun getTierColor(tier: AirdropTier): Color {
    return when (tier) {
        AirdropTier.SOVEREIGN -> TierSovereign
        AirdropTier.LUMINARY -> TierLuminary
        AirdropTier.VANGUARD -> TierVanguard
        AirdropTier.PROSPECTOR -> TierProspector
        AirdropTier.SCOUT -> TierScout
        AirdropTier.DEVELOPER -> SolanaPurple
    }
}
