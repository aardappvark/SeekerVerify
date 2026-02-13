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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.LaunchedEffect
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
import com.seekerverify.app.engine.PredictorEngine
import com.seekerverify.app.engine.Season1Engine
import com.seekerverify.app.engine.SeasonProgress
import com.seekerverify.app.model.AirdropTier
import com.seekerverify.app.model.SeasonComparison
import com.seekerverify.app.model.Trend
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
    val skrPerWallet: Long,
    val percentOfWallets: Double,
    val approxWallets: Long,
    val percentileFloor: Double,
    val percentileCeiling: Double
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
    val s2Metrics by viewModel.metrics.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    // Season progress + projection
    val seasonProgress by viewModel.seasonProgress.collectAsState()
    val projectedResult by viewModel.projectedResult.collectAsState()
    val projectionAssumptions by viewModel.projectionAssumptions.collectAsState()

    // Season 1 state
    val s1Claim by viewModel.season1Claim.collectAsState()
    val s1Analysis by viewModel.season1Analysis.collectAsState()
    val s1Loading by viewModel.season1Loading.collectAsState()
    val seasonComparison by viewModel.comparison.collectAsState()

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
                "Your Season 1 airdrop position and activity"
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
        val userS1Tier = s1Claim?.tier

        val initialPage = when {
            selectedTab == SeasonTab.SEASON_2 && predictedTier != null ->
                DISPLAY_TIERS.indexOf(predictedTier).coerceAtLeast(0)
            selectedTab == SeasonTab.SEASON_1 && userS1Tier != null ->
                DISPLAY_TIERS.indexOf(userS1Tier).coerceAtLeast(0)
            else -> 1 // Default to Prospector
        }

        val pagerState = rememberPagerState(
            initialPage = initialPage,
            pageCount = { DISPLAY_TIERS.size }
        )

        // Auto-scroll to detected tier when it changes
        LaunchedEffect(selectedTab, userS1Tier, predictedTier) {
            val targetPage = when {
                selectedTab == SeasonTab.SEASON_1 && userS1Tier != null ->
                    DISPLAY_TIERS.indexOf(userS1Tier).coerceAtLeast(0)
                selectedTab == SeasonTab.SEASON_2 && predictedTier != null ->
                    DISPLAY_TIERS.indexOf(predictedTier).coerceAtLeast(0)
                else -> null
            }
            targetPage?.let {
                if (pagerState.currentPage != it) {
                    // Small delay to ensure pager layout is ready
                    kotlinx.coroutines.delay(150)
                    pagerState.animateScrollToPage(it)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 40.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val tier = DISPLAY_TIERS[page]
            if (selectedTab == SeasonTab.SEASON_1) {
                Season1TierCard(
                    tier = tier,
                    isUserTier = tier == userS1Tier
                )
            } else {
                Season2TierCard(
                    tier = tier,
                    isUserPrediction = tier == predictedTier,
                    userScore = if (tier == predictedTier) prediction?.compositeScore else null,
                    confidence = if (tier == predictedTier) prediction?.confidence else null,
                    currentScore = projectedResult?.current?.compositeScore
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
                val isUserTier = when (selectedTab) {
                    SeasonTab.SEASON_1 -> tier == userS1Tier
                    SeasonTab.SEASON_2 -> tier == predictedTier
                }
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
                // --- Season 1 Dynamic Content ---

                val hasAnalysis = s1Analysis != null

                if (!hasAnalysis && !s1Loading) {
                    // "Detect My Tier" button — initial state
                    Button(
                        onClick = { viewModel.runSeason1Analysis(walletAddress, rpcUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SeekerBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Detect My Season 1 Tier", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Scan on-chain history to find your Season 1 airdrop tier and activity",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (s1Loading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = SeekerBlue, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Scanning on-chain activity...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Results — show even when tier is null (activity data is still valuable)
                s1Analysis?.let { analysis ->
                    val detectedTier = s1Claim?.tier

                    if (detectedTier != null) {
                        // Full position card with tier info
                        Season1UserPositionCard(tier = detectedTier, analysis = analysis)
                    } else {
                        // Activity analysis without specific tier
                        Season1ActivityOnlyCard(analysis = analysis)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Season1HighlightsCard(highlights = analysis.activityHighlights)

                    // Activity Planner — S1 context (general on-chain activity tips)
                    Spacer(modifier = Modifier.height(16.dp))
                    Season1ActivityPlannerCard(analysis = analysis)

                    // Comparison card (only when both seasons have tier data)
                    seasonComparison?.let { comp ->
                        Spacer(modifier = Modifier.height(16.dp))
                        SeasonComparisonCard(comparison = comp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Re-run button
                    Button(
                        onClick = { viewModel.runSeason1Analysis(walletAddress, rpcUrl) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SeekerBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Re-analyze")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Distribution table (user's tier highlighted)
                Season1SummaryCard(userTier = s1Claim?.tier)

            } else {
                // --- Season 2 content ---

                // Season Progress Bar (always visible)
                seasonProgress?.let { progress ->
                    SeasonProgressCard(progress = progress)
                    Spacer(modifier = Modifier.height(12.dp))
                }

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
                    val projected = projectedResult

                    // Speculative estimate notice
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Speculative estimate only. Based on Season 1 patterns \u2014 actual Season 2 criteria may differ. Not financial advice.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // Dual Activity Score Card (current + projected)
                    DualScoreCard(
                        currentResult = projected?.current ?: pred,
                        projectedResult = projected?.projected ?: pred,
                        paceStatus = projected?.paceStatus,
                        hasProjection = projected != null
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Score Breakdown Card (with projected values for time-dependent metrics)
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

                            val projectedBreakdown = projected?.projected?.breakdown
                            val currentBreakdown = projected?.current?.breakdown ?: pred.breakdown
                            val TIME_DEPENDENT_METRICS = setOf(
                                "Transactions", "dApp Usage", "SKR Staking", "Wallet Age", "Programs Used"
                            )

                            currentBreakdown.entries.sortedByDescending { it.value }.forEach { (metric, score) ->
                                val projectedScore = if (metric in TIME_DEPENDENT_METRICS) {
                                    projectedBreakdown?.get(metric)
                                } else null
                                MetricRow(
                                    name = metric,
                                    score = score,
                                    projectedScore = projectedScore
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    // Assumptions Card (expandable)
                    if (projectionAssumptions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        AssumptionsCard(assumptions = projectionAssumptions)
                    }

                    // Activity Planner — S2 context (metric-specific improvements)
                    Spacer(modifier = Modifier.height(16.dp))
                    Season2ActivityPlannerCard(prediction = pred, metrics = s2Metrics)

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

            // Legal Disclaimer
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
                        "Season 1 tier detected from on-chain claim history. Activity analysis based on current wallet data. This is general information only and is not financial advice."
                    else
                        "This prediction is a speculative estimate based on Season 1 patterns and on-chain activity. Actual Season 2 criteria have not been announced and may differ significantly. This is not financial advice. Do not make financial decisions based on these predictions.",
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
private fun Season1TierCard(tier: AirdropTier, isUserTier: Boolean = false) {
    val tierColor = getTierColor(tier)
    val data = SEASON1_DATA[tier] ?: return
    val nf = NumberFormat.getNumberInstance(Locale.US)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUserTier) tierColor else tierColor.copy(alpha = 0.6f)
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
            // Top: badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = if (isUserTier) 0.25f else 0.2f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (isUserTier) "YOUR TIER" else "SEASON 1",
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
    confidence: String?,
    currentScore: Double? = null
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

            // Bottom: User score or dynamic threshold with pace
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
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                // Dynamic threshold with pace indicator
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        val paceText = if (currentScore != null && scoreThreshold > 0) {
                            val pacePercent = ((currentScore / scoreThreshold) * 100).toInt()
                                .coerceIn(0, 999)
                            "~$scoreThreshold+ (pace: $pacePercent%)"
                        } else {
                            "~$scoreThreshold+"
                        }
                        Text(
                            text = "Score needed: $paceText",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

// --- Season 1 User Position Card ---

@Composable
private fun Season1UserPositionCard(tier: AirdropTier, analysis: Season1Engine.Season1Analysis) {
    val tierColor = getTierColor(tier)
    val nf = NumberFormat.getNumberInstance(Locale.US)
    val data = SEASON1_DATA[tier]

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Your Season 1 Position",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = tier.displayName,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = tierColor
                    )
                    data?.let {
                        Text(
                            text = "${nf.format(it.skrPerWallet)} SKR claimed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Top ${String.format("%.1f", 100 - analysis.tierPercentile)}%",
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

            // Activity score bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Activity Score",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = String.format("%.0f", analysis.overallActivityScore),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = SeekerBlue
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            val animatedProgress by animateFloatAsState(
                targetValue = (analysis.overallActivityScore / 100.0).toFloat(),
                animationSpec = tween(durationMillis = 1200),
                label = "s1score"
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
}

// --- Season 1 Activity Only Card (no tier detected) ---

@Composable
private fun Season1ActivityOnlyCard(analysis: Season1Engine.Season1Analysis) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Your On-Chain Activity",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Season 1 claim not found on this wallet. This may happen if you claimed on a different wallet, or if the claim hasn't been processed yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Activity score bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Activity Score",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = String.format("%.0f", analysis.overallActivityScore),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = SeekerBlue
                )
            }
            Spacer(modifier = Modifier.height(4.dp))

            val animatedProgress by animateFloatAsState(
                targetValue = (analysis.overallActivityScore / 100.0).toFloat(),
                animationSpec = tween(durationMillis = 1200),
                label = "s1activityScore"
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
}

// --- Season 1 Highlights Card ---

@Composable
private fun Season1HighlightsCard(highlights: List<Season1Engine.ActivityHighlight>) {
    if (highlights.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Activity Highlights",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            highlights.forEach { highlight ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (highlight.isStrength) Icons.Filled.Star
                        else Icons.Filled.Info,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (highlight.isStrength) SeekerGold
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = highlight.label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = if (highlight.isStrength) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = highlight.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// --- Season Comparison Card ---

@Composable
private fun SeasonComparisonCard(comparison: SeasonComparison) {
    val trendColor = when (comparison.trend) {
        Trend.UP -> SolanaGreen
        Trend.DOWN -> Color(0xFFE57373)
        Trend.STABLE -> SeekerBlue
        Trend.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val trendIcon = when (comparison.trend) {
        Trend.UP -> Icons.Filled.KeyboardArrowUp
        Trend.DOWN -> Icons.Filled.KeyboardArrowDown
        else -> Icons.AutoMirrored.Filled.TrendingUp
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Season 1 vs Season 2",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Tier comparison row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "S1",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = comparison.season1Tier?.displayName ?: "?",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = comparison.season1Tier?.let { getTierColor(it) }
                            ?: MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${String.format("%.0f", comparison.season1Percentile)}th %",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Trend arrow
                Icon(
                    imageVector = trendIcon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = trendColor
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "S2",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = comparison.season2Tier?.displayName ?: "?",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = comparison.season2Tier?.let { getTierColor(it) }
                            ?: MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${String.format("%.0f", comparison.season2Percentile)}th %",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Summary text
            Text(
                text = comparison.summary,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = trendColor,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// --- Season 1 Summary Card ---

@Composable
private fun Season1SummaryCard(userTier: AirdropTier? = null) {
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
                val isHighlighted = tier == userTier

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isHighlighted) Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(SolanaGreen.copy(alpha = 0.12f))
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                            else Modifier.padding(vertical = 6.dp)
                        ),
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
                        color = if (isHighlighted) SolanaGreen
                        else MaterialTheme.colorScheme.onSurface,
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
                        color = if (isHighlighted) SolanaGreen else tierColor,
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
                    color = if (isHighlighted) SolanaGreen else tierColor,
                    trackColor = tierColor.copy(alpha = 0.1f)
                )
            }
        }
    }
}

// --- Shared Components ---

@Composable
private fun MetricRow(name: String, score: Double, projectedScore: Double? = null) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (projectedScore != null && projectedScore != score) {
                // Show current → projected for time-dependent metrics
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = String.format("%.0f", score),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            score >= 70 -> SolanaGreen
                            score >= 40 -> SeekerGold
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = " \u2192 ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "~${String.format("%.0f", projectedScore)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = SolanaGreen.copy(alpha = 0.7f)
                    )
                }
            } else {
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

// --- Season 1 Scoring Model Insights ---

@Composable
private fun Season1ActivityPlannerCard(analysis: Season1Engine.Season1Analysis) {
    val observations = mutableListOf<Triple<String, String, Boolean>>() // label, observation, isLow

    // Check what's missing/low in S1 context
    val highlights = analysis.activityHighlights
    val highlightLabels = highlights.map { it.label }.toSet()

    // Low activity
    if ("Low Activity" in highlightLabels || highlights.none { it.label in listOf("Power User", "Active Participant", "Regular User") }) {
        observations.add(Triple(
            "Transaction Count: Low",
            "This metric measures on-chain transactions (swaps, transfers, interactions). It has a 30% weight in the S1 scoring model.",
            true
        ))
    }

    // No NFTs
    if (highlights.none { it.label in listOf("NFT Collector", "NFT Holder") }) {
        observations.add(Triple(
            "NFT Holdings: None detected",
            "This metric checks for NFT ownership in the wallet. It has a 10% weight in the scoring model.",
            true
        ))
    }

    // Low token diversity
    if (highlights.none { it.label in listOf("DeFi Explorer", "Token Collector") }) {
        observations.add(Triple(
            "Token Diversity: Low",
            "This metric counts unique SPL tokens held. It has a 15% weight in the scoring model.",
            true
        ))
    }

    // Low dApp engagement
    if (highlights.none { it.label in listOf("Protocol Pioneer", "dApp Explorer") }) {
        observations.add(Triple(
            "dApp Interactions: Low",
            "This metric tracks unique program interactions. It has a 25% weight in the scoring model.",
            true
        ))
    }

    // Late arrival
    if ("Late Arrival" in highlightLabels) {
        observations.add(Triple(
            "Wallet Age: Recent",
            "This metric measures wallet age. It has a 20% weight in the scoring model. Wallet age increases naturally over time.",
            true
        ))
    }

    // Already strong
    if (analysis.overallActivityScore >= 70 && observations.isEmpty()) {
        observations.add(Triple(
            "Strong Activity Profile",
            "Your Season 1 on-chain activity scores well across all measured metrics.",
            false
        ))
    }

    if (observations.isEmpty()) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Scoring Model Insights",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "How your on-chain activity maps to this app\u2019s scoring model. This model is speculative and may not reflect actual criteria.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            observations.forEach { (label, observation, isLow) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (isLow) Icons.Filled.Info else Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp),
                        tint = if (isLow) Color(0xFFFFA726)
                        else SolanaGreen
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = observation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// --- Season 2 Scoring Model Insights ---

@Suppress("UNUSED_PARAMETER")
@Composable
private fun Season2ActivityPlannerCard(
    prediction: PredictorEngine.PredictorResult,
    metrics: PredictorEngine.ActivityMetrics?
) {
    val observations = mutableListOf<Triple<String, String, Boolean>>() // label, observation, isLow

    val breakdown = prediction.breakdown

    // Check each metric score and describe the model's assessment
    // Staking (weight 20%)
    val stakingScore = breakdown["SKR Staking"] ?: 0.0
    if (stakingScore < 50) {
        observations.add(Triple(
            "SKR Staking Score: ${String.format("%.0f", stakingScore)}/100",
            "This metric checks whether SKR is staked. It has a 20% weight in the scoring model.",
            true
        ))
    }

    // Season 1 Tier (weight 15%)
    val s1Score = breakdown["Season 1 Tier"] ?: 0.0
    if (s1Score == 0.0) {
        observations.add(Triple(
            "Season 1 Tier: Not detected",
            "This metric uses your S1 airdrop tier. It has a 15% weight. Use the Season 1 tab to detect your tier.",
            true
        ))
    }

    // Transactions (weight 15%)
    val txScore = breakdown["Transactions"] ?: 0.0
    if (txScore < 40) {
        observations.add(Triple(
            "Transaction Score: ${String.format("%.0f", txScore)}/100",
            "This metric counts on-chain transactions. It has a 15% weight in the scoring model.",
            true
        ))
    }

    // dApp Usage (weight 10%)
    val dappScore = breakdown["dApp Usage"] ?: 0.0
    if (dappScore < 40) {
        observations.add(Triple(
            "dApp Usage Score: ${String.format("%.0f", dappScore)}/100",
            "This metric tracks unique program interactions. It has a 10% weight in the scoring model.",
            true
        ))
    }

    // Programs Used (weight 12%)
    val progScore = breakdown["Programs Used"] ?: 0.0
    if (progScore < 40) {
        observations.add(Triple(
            "Programs Score: ${String.format("%.0f", progScore)}/100",
            "This metric counts distinct Solana programs interacted with. It has a 12% weight.",
            true
        ))
    }

    // Wallet Age (weight 10%)
    val ageScore = breakdown["Wallet Age"] ?: 0.0
    if (ageScore < 30) {
        observations.add(Triple(
            "Wallet Age Score: ${String.format("%.0f", ageScore)}/100",
            "This metric measures wallet age. It has a 10% weight. This increases naturally over time.",
            true
        ))
    }

    // .skr Domain (weight 5%)
    val domainScore = breakdown[".skr Domain"] ?: 0.0
    if (domainScore == 0.0) {
        observations.add(Triple(
            ".skr Domain: Not detected",
            "This metric checks for .skr domain ownership. It has a 5% weight in the scoring model.",
            true
        ))
    }

    // Token Diversity (weight 8%)
    val tokenScore = breakdown["Token Diversity"] ?: 0.0
    if (tokenScore < 40) {
        observations.add(Triple(
            "Token Diversity Score: ${String.format("%.0f", tokenScore)}/100",
            "This metric counts unique SPL tokens held. It has an 8% weight in the scoring model.",
            true
        ))
    }

    // NFTs (weight 5%)
    val nftScore = breakdown["NFTs"] ?: 0.0
    if (nftScore < 20) {
        observations.add(Triple(
            "NFT Score: ${String.format("%.0f", nftScore)}/100",
            "This metric checks for NFT ownership. It has a 5% weight in the scoring model.",
            true
        ))
    }

    // Already strong
    if (prediction.compositeScore >= 75 && observations.size <= 1) {
        observations.clear()
        observations.add(Triple(
            "Strong Activity Profile",
            "Your on-chain activity scores well across all measured metrics in this model.",
            false
        ))
    }

    if (observations.isEmpty()) return

    // Show max 5 most impactful observations
    val topObservations = observations.take(5)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "Scoring Model Insights",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "How your activity maps to this app\u2019s scoring model. This model is speculative and may not reflect actual Season 2 criteria. Not financial advice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            topObservations.forEach { (label, observation, isLow) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (isLow) Icons.Filled.Info else Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp).padding(top = 2.dp),
                        tint = if (isLow) Color(0xFFFFA726)
                        else SolanaGreen
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = observation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

// --- Season Progress Card ---

@Composable
private fun SeasonProgressCard(progress: SeasonProgress.Progress) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Season Progress",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (progress.phase) {
                        SeasonProgress.Phase.PRE_SEASON -> "Pre-season"
                        SeasonProgress.Phase.POST_SEASON -> "Season ended"
                        SeasonProgress.Phase.ACTIVE -> "Day ${progress.daysSinceStart} of ${progress.totalDays}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val animatedProgress by animateFloatAsState(
                targetValue = progress.fractionComplete.toFloat(),
                animationSpec = tween(durationMillis = 1000),
                label = "seasonProgress"
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

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = progress.startDate.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Text(
                    text = "${String.format("%.0f", progress.percentComplete)}% complete",
                    style = MaterialTheme.typography.labelSmall,
                    color = SeekerBlue,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = progress.endDate.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "\u2139\uFE0F Assumed dates \u2014 actual Season 2 dates not yet announced",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// --- Dual Score Card (Current + Projected) ---

@Composable
private fun DualScoreCard(
    currentResult: PredictorEngine.PredictorResult,
    projectedResult: PredictorEngine.PredictorResult,
    paceStatus: PredictorEngine.PaceStatus?,
    hasProjection: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Your Activity Score",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                // Pace badge
                paceStatus?.let { pace ->
                    val (paceText, paceColor) = when (pace) {
                        PredictorEngine.PaceStatus.AHEAD -> "Ahead" to SolanaGreen
                        PredictorEngine.PaceStatus.ON_TRACK -> "On track" to SeekerBlue
                        PredictorEngine.PaceStatus.BEHIND -> "Behind" to Color(0xFFFFA726)
                        PredictorEngine.PaceStatus.TOO_EARLY -> "Too early" to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(paceColor.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = paceText,
                            style = MaterialTheme.typography.labelSmall,
                            color = paceColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Current score (primary, large)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Season to Date",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%.0f", currentResult.compositeScore),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = SeekerBlue
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Top ${String.format("%.1f", 100 - currentResult.percentile)}%",
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

            Spacer(modifier = Modifier.height(10.dp))

            val animatedCurrent by animateFloatAsState(
                targetValue = (currentResult.compositeScore / 100.0).toFloat(),
                animationSpec = tween(durationMillis = 1200),
                label = "currentScore"
            )

            LinearProgressIndicator(
                progress = { animatedCurrent },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = SeekerBlue,
                trackColor = SeekerBlue.copy(alpha = 0.15f)
            )

            // Projected score (secondary)
            if (hasProjection && projectedResult.compositeScore != currentResult.compositeScore) {
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Projected End of Season",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "~${String.format("%.0f", projectedResult.compositeScore)}",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = SolanaGreen.copy(alpha = 0.8f)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = projectedResult.predictedTier.displayName,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = getTierColor(projectedResult.predictedTier)
                        )
                        Text(
                            text = "projected tier",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val animatedProjected by animateFloatAsState(
                    targetValue = (projectedResult.compositeScore / 100.0).toFloat(),
                    animationSpec = tween(durationMillis = 1200),
                    label = "projectedScore"
                )

                LinearProgressIndicator(
                    progress = { animatedProjected },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = SolanaGreen.copy(alpha = 0.6f),
                    trackColor = SolanaGreen.copy(alpha = 0.1f)
                )
            }
        }
    }
}

// --- Assumptions Card (expandable) ---

@Composable
private fun AssumptionsCard(assumptions: List<String>) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Projection Assumptions",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp
                    else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                assumptions.forEach { assumption ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "\u2022",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = assumption,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
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
