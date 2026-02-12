package com.seekerverify.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seekerverify.app.model.AirdropTier
import com.seekerverify.app.ui.theme.SeekerBlue
import com.seekerverify.app.ui.theme.SeekerGold
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

// The 5 displayable tiers in order (excluding DEVELOPER)
private val DISPLAY_TIERS = listOf(
    AirdropTier.SCOUT,
    AirdropTier.PROSPECTOR,
    AirdropTier.VANGUARD,
    AirdropTier.LUMINARY,
    AirdropTier.SOVEREIGN
)

// Approximate activity score thresholds to reach each tier
// Derived from PredictorEngine's percentile-to-tier mapping
private val TIER_SCORE_THRESHOLDS = mapOf(
    AirdropTier.SCOUT to 0,
    AirdropTier.PROSPECTOR to 13,
    AirdropTier.VANGUARD to 53,
    AirdropTier.LUMINARY to 71,
    AirdropTier.SOVEREIGN to 79
)

// Season 1 distribution ranges
private val TIER_PERCENTILE_RANGES = mapOf(
    AirdropTier.SCOUT to "Bottom 19.5% of wallets",
    AirdropTier.PROSPECTOR to "64.2% of wallets",
    AirdropTier.VANGUARD to "11.9% of wallets",
    AirdropTier.LUMINARY to "4.0% of wallets",
    AirdropTier.SOVEREIGN to "Top 0.4% of wallets"
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PredictorScreen(
    walletAddress: String,
    rpcUrl: String,
    viewModel: PredictorViewModel = viewModel()
) {
    val result by viewModel.result.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {
        Text(
            text = "Season 2 Predictor",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            text = "Swipe to explore all airdrop tiers",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Swipeable Tier Cards ---
        val prediction = result
        val predictedTier = prediction?.predictedTier
        val initialPage = if (predictedTier != null) {
            DISPLAY_TIERS.indexOf(predictedTier).coerceAtLeast(0)
        } else {
            1 // Default to Prospector (most common)
        }

        val pagerState = rememberPagerState(
            initialPage = initialPage,
            pageCount = { DISPLAY_TIERS.size }
        )

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 40.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val tier = DISPLAY_TIERS[page]
            val isUserTier = tier == predictedTier
            TierCard(
                tier = tier,
                isUserPrediction = isUserTier,
                userScore = if (isUserTier) prediction?.compositeScore else null,
                confidence = if (isUserTier) prediction?.confidence else null
            )
        }

        // Page indicator dots
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            DISPLAY_TIERS.forEachIndexed { index, tier ->
                val isSelected = pagerState.currentPage == index
                val isUserTier = tier == predictedTier
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (isSelected) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isSelected && isUserTier -> SolanaGreen
                                isSelected -> SeekerBlue
                                isUserTier -> SolanaGreen.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            }
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Run Prediction / Results Section ---
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {

            if (prediction == null && !isLoading) {
                Button(
                    onClick = { viewModel.runPrediction(walletAddress, rpcUrl) },
                    modifier = Modifier.fillMaxWidth(),
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
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Analyze your on-chain activity to see which tier matches your profile",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = SeekerBlue, modifier = Modifier.size(40.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Analyzing on-chain activity...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            prediction?.let { pred ->
                // Activity Score Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "Your Activity Score",
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
                                text = String.format("%.0f", pred.compositeScore),
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SeekerBlue
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "Top ${String.format("%.1f", 100 - pred.percentile)}%",
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
                            targetValue = (pred.compositeScore / 100.0).toFloat(),
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

                        pred.breakdown.entries.sortedByDescending { it.value }.forEach { (metric, score) ->
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
                    text = "Predictions based on on-chain activity and Season 1 distribution. Actual Season 2 criteria may differ. Not financial advice.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
}

@Composable
private fun TierCard(
    tier: AirdropTier,
    isUserPrediction: Boolean,
    userScore: Double?,
    confidence: String?
) {
    val tierColor = getTierColor(tier)
    val skrAmount = NumberFormat.getNumberInstance(Locale.US).format(tier.skrDisplay.toLong())
    val scoreThreshold = TIER_SCORE_THRESHOLDS[tier] ?: 0
    val percentileRange = TIER_PERCENTILE_RANGES[tier] ?: ""

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUserPrediction) tierColor else tierColor.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top badge
            if (isUserPrediction) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.25f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "YOUR PREDICTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "SEASON 1 TIER",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Center: Tier name and SKR
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = tier.displayName,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$skrAmount SKR",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }

            // Bottom: Stats
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = percentileRange,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(6.dp))

                if (isUserPrediction && userScore != null) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Score: ${String.format("%.0f", userScore)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        confidence?.let {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$it Confidence",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Score needed: ~$scoreThreshold+",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
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
