package com.seekerverify.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

// The 5 displayable tiers in order
private val DISPLAY_TIERS = listOf(
    AirdropTier.SCOUT,
    AirdropTier.PROSPECTOR,
    AirdropTier.VANGUARD,
    AirdropTier.LUMINARY,
    AirdropTier.SOVEREIGN
)

// Season 1 data per tier
private data class Season1TierData(
    val skrPerWallet: Long,        // SKR airdrop amount (display units)
    val percentOfWallets: Double,  // % of all wallets in this tier
    val approxWallets: Long,       // estimated wallet count
    val percentileFloor: Double,   // cumulative percentile floor
    val percentileCeiling: Double  // cumulative percentile ceiling
)

private val SEASON1_DATA = mapOf(
    AirdropTier.SCOUT to Season1TierData(5_000, 19.5, 27_300L, 0.0, 19.5),
    AirdropTier.PROSPECTOR to Season1TierData(10_000, 64.2, 89_900L, 19.5, 83.7),
    AirdropTier.VANGUARD to Season1TierData(40_000, 11.9, 16_700L, 83.7, 95.6),
    AirdropTier.LUMINARY to Season1TierData(125_000, 4.0, 5_600L, 95.6, 99.6),
    AirdropTier.SOVEREIGN to Season1TierData(750_000, 0.4, 560L, 99.6, 100.0)
)

// Season 2 predicted score thresholds
private val TIER_SCORE_THRESHOLDS = mapOf(
    AirdropTier.SCOUT to 0,
    AirdropTier.PROSPECTOR to 13,
    AirdropTier.VANGUARD to 53,
    AirdropTier.LUMINARY to 71,
    AirdropTier.SOVEREIGN to 79
)

private enum class SeasonTab { SEASON_1, SEASON_2 }

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

    var selectedTab by remember { mutableStateOf(SeasonTab.SEASON_2) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp)
    ) {
        // Header
        Text(
            text = "Airdrop Tiers",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- Season 1 / Season 2 Toggle ---
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            SeasonTabButton(
                text = "Season 1",
                isSelected = selectedTab == SeasonTab.SEASON_1,
                onClick = { selectedTab = SeasonTab.SEASON_1 },
                modifier = Modifier.weight(1f)
            )
            SeasonTabButton(
                text = "Season 2",
                isSelected = selectedTab == SeasonTab.SEASON_2,
                onClick = { selectedTab = SeasonTab.SEASON_2 },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Subtitle based on selected tab
        Text(
            text = if (selectedTab == SeasonTab.SEASON_1)
                "Historical airdrop data from Season 1"
            else
                "Swipe to explore predicted Season 2 tiers",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- Swipeable Tier Cards ---
        val prediction = result
        val predictedTier = prediction?.predictedTier
        val initialPage = if (selectedTab == SeasonTab.SEASON_2 && predictedTier != null) {
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
            if (selectedTab == SeasonTab.SEASON_1) {
                Season1TierCard(tier = tier)
            } else {
                Season2TierCard(
                    tier = tier,
                    isUserPrediction = tier == predictedTier,
                    userScore = if (tier == predictedTier) prediction?.compositeScore else null,
                    confidence = if (tier == predictedTier) prediction?.confidence else null
                )
            }
        }

        // Page indicator dots
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            DISPLAY_TIERS.forEachIndexed { index, tier ->
                val isSelected = pagerState.currentPage == index
                val isUserTier = selectedTab == SeasonTab.SEASON_2 && tier == predictedTier
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

        // --- Content below cards depends on tab ---
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {

            if (selectedTab == SeasonTab.SEASON_1) {
                // Season 1 summary stats
                Season1SummaryCard()
            } else {
                // Season 2 prediction content
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
                    text = if (selectedTab == SeasonTab.SEASON_1)
                        "Season 1 data sourced from on-chain claim records. ~140,000 total eligible wallets."
                    else
                        "Predictions based on on-chain activity and Season 1 distribution. Actual Season 2 criteria may differ. Not financial advice.",
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

// --- Season Toggle Button ---

@Composable
private fun SeasonTabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) SeekerBlue
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color.White
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// --- Season 1 Tier Card ---

@Composable
private fun Season1TierCard(tier: AirdropTier) {
    val tierColor = getTierColor(tier)
    val data = SEASON1_DATA[tier] ?: return
    val nf = NumberFormat.getNumberInstance(Locale.US)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        colors = CardDefaults.cardColors(containerColor = tierColor),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top: SEASON 1 badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.2f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "SEASON 1",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            // Center: Tier name and SKR airdrop
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
                    text = "${nf.format(data.skrPerWallet)} SKR",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Text(
                    text = "per wallet",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            // Bottom: Distribution stats
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "${data.percentOfWallets}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " of wallets",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "  ~${nf.format(data.approxWallets)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

// --- Season 2 Tier Card ---

@Composable
private fun Season2TierCard(
    tier: AirdropTier,
    isUserPrediction: Boolean,
    userScore: Double?,
    confidence: String?
) {
    val tierColor = getTierColor(tier)
    val nf = NumberFormat.getNumberInstance(Locale.US)
    val skrAmount = nf.format(tier.skrDisplay.toLong())
    val scoreThreshold = TIER_SCORE_THRESHOLDS[tier] ?: 0
    val s1data = SEASON1_DATA[tier]

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
                        text = "SEASON 2",
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
                s1data?.let {
                    Text(
                        text = "S1: ${it.percentOfWallets}% of wallets",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            // Bottom: User score or threshold
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

// --- Season 1 Summary Card ---

@Composable
private fun Season1SummaryCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Season 1 Distribution",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "~140,000 eligible wallets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            val nf = NumberFormat.getNumberInstance(Locale.US)

            DISPLAY_TIERS.reversed().forEach { tier ->
                val data = SEASON1_DATA[tier] ?: return@forEach
                val tierColor = getTierColor(tier)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tier color dot
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(tierColor)
                    )
                    Spacer(modifier = Modifier.width(10.dp))

                    // Tier name
                    Text(
                        text = tier.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.width(90.dp)
                    )

                    // SKR amount
                    Text(
                        text = "${nf.format(data.skrPerWallet)} SKR",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    // Distribution %
                    Text(
                        text = "${data.percentOfWallets}%",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = tierColor,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(50.dp)
                    )
                }

                // Mini progress bar
                LinearProgressIndicator(
                    progress = { (data.percentOfWallets / 100.0).toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .padding(start = 22.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = tierColor,
                    trackColor = tierColor.copy(alpha = 0.1f)
                )
            }
        }
    }
}

// --- Shared Components ---

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
