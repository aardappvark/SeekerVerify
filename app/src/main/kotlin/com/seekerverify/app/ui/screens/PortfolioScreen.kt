package com.seekerverify.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.ui.util.hapticTap
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seekerverify.app.service.GeoAnalyticsService
import com.seekerverify.app.ui.theme.SeekerBlue
import com.seekerverify.app.ui.theme.SeekerGold
import com.seekerverify.app.ui.theme.SeekerRed
import com.seekerverify.app.ui.theme.SolanaGreen
import com.seekerverify.app.ui.theme.SolanaPurple
import com.seekerverify.app.ui.components.GlassCard
import com.seekerverify.app.ui.components.GuestModeBanner
import com.seekerverify.app.model.TransactionRecord
import com.seekerverify.app.ui.viewmodel.PortfolioViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun PortfolioScreen(
    walletAddress: String,
    rpcUrl: String,
    isGuestMode: Boolean = false,
    onConnectWallet: () -> Unit = {},
    viewModel: PortfolioViewModel = viewModel()
) {
    val view = LocalView.current
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }

    val solBalance by viewModel.solBalance.collectAsState()
    val stakedSol by viewModel.stakedSol.collectAsState()
    val skrBalance by viewModel.skrBalance.collectAsState()
    val stakedSkr by viewModel.stakedSkr.collectAsState()
    val cooldownSkr by viewModel.cooldownSkr.collectAsState()
    val isStaked by viewModel.isStaked.collectAsState()
    val estimatedApy by viewModel.estimatedApy.collectAsState()
    val originalDeposit by viewModel.originalDeposit.collectAsState()
    val stakingRewards by viewModel.stakingRewards.collectAsState()
    val stakingPnlPercent by viewModel.stakingPnlPercent.collectAsState()
    val estDailyYield by viewModel.estDailyYield.collectAsState()
    val estMonthlyYield by viewModel.estMonthlyYield.collectAsState()
    val estAnnualYield by viewModel.estAnnualYield.collectAsState()
    val estYtdYield by viewModel.estYtdYield.collectAsState()
    val skrPriceUsd by viewModel.skrPriceUsd.collectAsState()
    val solPriceUsd by viewModel.solPriceUsd.collectAsState()
    val totalValueUsd by viewModel.totalValueUsd.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val sharePriceHistory by viewModel.sharePriceHistory.collectAsState()
    val transactionHistory by viewModel.transactionHistory.collectAsState()

    // Data is pre-loaded from AppNavigation
    LaunchedEffect(Unit) {
        GeoAnalyticsService.track(GeoAnalyticsService.Events.PORTFOLIO_VIEWED)
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = {
            view.hapticTap(prefs)
            viewModel.loadPortfolio(walletAddress, rpcUrl)
        }
    )

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Portfolio",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!isGuestMode) {
                IconButton(onClick = {
                    view.hapticTap(prefs)
                    viewModel.loadPortfolio(walletAddress, rpcUrl)
                }) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Refresh",
                        tint = SeekerBlue
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isGuestMode) {
            GuestModeBanner(onConnectWallet = onConnectWallet)
            Spacer(modifier = Modifier.height(16.dp))

            // --- Total Value Header (placeholder) ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = SeekerBlue.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "TOTAL VALUE",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "---",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // --- SOL Section (placeholder) ---
            Text(
                text = "SOL",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            BalanceCard(icon = Icons.Filled.CurrencyExchange, iconTint = SolanaPurple, label = "SOL Balance", amount = "--- SOL", usdValue = null)
            Spacer(modifier = Modifier.height(10.dp))
            BalanceCard(icon = Icons.Filled.Lock, iconTint = SolanaPurple, label = "Staked SOL", amount = "--- SOL", usdValue = null)

            Spacer(modifier = Modifier.height(20.dp))

            // --- SKR Section (placeholder) ---
            Text(
                text = "SKR",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )
            BalanceCard(icon = Icons.Filled.AccountBalance, iconTint = SeekerBlue, label = "Liquid Balance", amount = "--- SKR", usdValue = null)
            Spacer(modifier = Modifier.height(10.dp))

            // Staked SKR placeholder
            GlassCard(cornerRadius = 12.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SeekerGold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Savings, null, Modifier.size(24.dp), SeekerGold.copy(alpha = 0.4f))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Staked SKR", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("--- SKR", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SolanaGreen.copy(alpha = 0.08f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("---% APY", style = MaterialTheme.typography.labelMedium, color = SolanaGreen.copy(alpha = 0.3f), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connect your wallet to view SOL and SKR balances, staking positions, and portfolio value.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
        } else {

        // Total Value Header Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SeekerBlue),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TOTAL VALUE",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(4.dp))

                if (isLoading) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    totalValueUsd?.let { usd ->
                        Text(
                            text = formatUsd(usd),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } ?: run {
                        // No USD price available, show token totals
                        Text(
                            text = "Portfolio",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Staking yield sparkline (only when user has staking data)
        if (isStaked && sharePriceHistory.size >= 2) {
            StakingSparklineCard(snapshots = sharePriceHistory)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- SOL Section ---
        Text(
            text = "SOL",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // SOL Liquid Balance Card
        BalanceCard(
            icon = Icons.Filled.CurrencyExchange,
            iconTint = SolanaPurple,
            label = "SOL Balance",
            amount = formatSolAmount(solBalance) + " SOL",
            usdValue = solPriceUsd?.let { formatUsd(solBalance * it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Staked SOL Card
        BalanceCard(
            icon = Icons.Filled.Lock,
            iconTint = SolanaPurple,
            label = "Staked SOL",
            amount = formatSolAmount(stakedSol) + " SOL",
            usdValue = solPriceUsd?.let { formatUsd(stakedSol * it) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // --- SKR Section ---
        Text(
            text = "SKR",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )

        // SKR Liquid Balance Card
        BalanceCard(
            icon = Icons.Filled.AccountBalance,
            iconTint = SeekerBlue,
            label = "Liquid Balance",
            amount = formatSkrAmount(skrBalance) + " SKR",
            usdValue = skrPriceUsd?.let { formatUsd(skrBalance * it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // SKR Staking Card
        GlassCard(cornerRadius = 12.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SeekerGold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Savings,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = SeekerGold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Staked SKR",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatSkrAmount(stakedSkr) + " SKR",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    // APY badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SolanaGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${String.format("%.1f", estimatedApy)}% APY",
                            style = MaterialTheme.typography.labelMedium,
                            color = SolanaGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (isStaked) {
                    skrPriceUsd?.let { price ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatUsd(stakedSkr * price),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 60.dp)
                        )
                    }

                    // Cooldown row (if any SKR in cooldown)
                    if (cooldownSkr > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SeekerGold.copy(alpha = 0.08f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Unstaking: ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatSkrAmount(cooldownSkr) + " SKR",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SeekerGold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Rewards accrue via SKR inflation every 48 hours and compound automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (!isLoading) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No active stake found. Stake SKR to earn rewards.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Staking Yield Card — always shown when staked
        if (isStaked) {
            Spacer(modifier = Modifier.height(10.dp))

            var yieldExpanded by remember { mutableStateOf(false) }

            GlassCard(cornerRadius = 12.dp) {
                Column(
                    modifier = Modifier
                        .clickable { yieldExpanded = !yieldExpanded }
                        .padding(16.dp)
                ) {
                    // Header row with expand/collapse
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Seeker (SKR) Staking Yield",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = if (yieldExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (yieldExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Headline: Est. APY + monthly projection (always visible)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Est. APY",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${String.format("%.1f", estimatedApy)}%",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = SolanaGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Est. Monthly yield (headline)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Est. Monthly",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "+${formatSkrAmount(estMonthlyYield)} SKR",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SolanaGreen
                            )
                            skrPriceUsd?.let { price ->
                                Text(
                                    text = "+${formatUsd(estMonthlyYield * price)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SolanaGreen.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Est. YTD yield (headline)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Est. YTD",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "+${formatSkrAmount(estYtdYield)} SKR",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SolanaGreen
                            )
                            skrPriceUsd?.let { price ->
                                Text(
                                    text = "+${formatUsd(estYtdYield * price)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SolanaGreen.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Expanded detail view
                    AnimatedVisibility(
                        visible = yieldExpanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            // Projected Yield section
                            Text(
                                text = "PROJECTED YIELD",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Daily
                            YieldRow(
                                label = "Daily",
                                skrAmount = estDailyYield,
                                skrPriceUsd = skrPriceUsd
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Monthly
                            YieldRow(
                                label = "Monthly",
                                skrAmount = estMonthlyYield,
                                skrPriceUsd = skrPriceUsd
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Annual
                            YieldRow(
                                label = "Annual",
                                skrAmount = estAnnualYield,
                                skrPriceUsd = skrPriceUsd
                            )

                            // On-chain P&L (if cost_basis available)
                            if (originalDeposit > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "UNREALISED P&L",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Cost Basis
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Original Deposit",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatSkrAmount(originalDeposit) + " SKR",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Current Value
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Current Value",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = formatSkrAmount(stakedSkr) + " SKR",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Yield
                                val yieldColor = if (stakingRewards >= 0) SolanaGreen else SeekerRed
                                val yieldSign = if (stakingRewards >= 0) "+" else ""
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Yield",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "$yieldSign${formatSkrAmount(stakingRewards)} SKR (${yieldSign}${String.format("%.1f", stakingPnlPercent)}%)",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                fontWeight = FontWeight.Bold
                                            ),
                                            color = yieldColor
                                        )
                                        skrPriceUsd?.let { price ->
                                            Text(
                                                text = "${yieldSign}${formatUsd(stakingRewards * price)}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = yieldColor.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "Projections based on current APY. Actual yield varies with network conditions. Not financial advice.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }

        // SKR Price Card
        Spacer(modifier = Modifier.height(16.dp))

        skrPriceUsd?.let { skrPrice ->
            GlassCard(cornerRadius = 12.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SKR Price",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatUsdSmall(skrPrice),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    solPriceUsd?.let { solPrice ->
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "SOL Price",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatUsd(solPrice),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }

        } // end else (non-guest content)

        // Error display
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

        // Recent on-chain activity (populated when Predictor runs)
        if (transactionHistory.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            RecentActivityCard(transactions = transactionHistory)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Financial disclaimer
        Text(
            text = "Prices sourced from third-party APIs and may not reflect current market values. APY is variable, based on current on-chain data, and not guaranteed. This is not financial advice.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
    }

    PullRefreshIndicator(
        refreshing = isLoading,
        state = pullRefreshState,
        modifier = Modifier.align(Alignment.TopCenter),
        contentColor = SeekerBlue
    )
    }
}

@Composable
private fun YieldRow(
    label: String,
    skrAmount: Double,
    skrPriceUsd: Double?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "+${formatSkrAmount(skrAmount)} SKR",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = SolanaGreen
            )
            skrPriceUsd?.let { price ->
                Text(
                    text = "+${formatUsd(skrAmount * price)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SolanaGreen.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun BalanceCard(
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    label: String,
    amount: String,
    usdValue: String?
) {
    GlassCard(cornerRadius = 12.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = iconTint
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = amount,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            usdValue?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PriceChip(label: String, price: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "$label: $price",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RecentActivityCard(transactions: List<TransactionRecord>) {
    val displayed = transactions.take(12)
    val now = System.currentTimeMillis() / 1000L // current epoch seconds

    fun relativeTime(epochSec: Long): String {
        val diff = now - epochSec
        return when {
            diff < 3600 -> "${diff / 60}m ago"
            diff < 86400 -> "${diff / 3600}h ago"
            diff < 86400 * 30 -> "${diff / 86400}d ago"
            diff < 86400 * 365 -> "${diff / (86400 * 30)}mo ago"
            else -> "${diff / (86400 * 365)}y ago"
        }
    }

    GlassCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Recent Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Parsed from blockchain",
                        style = MaterialTheme.typography.labelSmall,
                        color = SeekerBlue
                    )
                }
                Text(
                    text = "${transactions.size} txs analyzed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            displayed.forEach { tx ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Success / fail indicator
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (tx.success) SolanaGreen else SeekerRed)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    // Program names
                    Text(
                        text = tx.topPrograms.joinToString(", "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (tx.isDapp)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    // Relative time
                    Text(
                        text = if (tx.blockTime > 0) relativeTime(tx.blockTime) else "—",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (transactions.size > 12) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "+ ${transactions.size - 12} more transactions analyzed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun StakingSparklineCard(snapshots: List<com.seekerverify.app.model.SharePriceSnapshot>) {
    var selectedDays by remember { mutableStateOf(7) }
    val cutoff = System.currentTimeMillis() - selectedDays * 86_400_000L
    val filtered = snapshots
        .filter { it.timestamp >= cutoff }
        .sortedBy { it.timestamp }
        .takeIf { it.size >= 2 } ?: return

    val prices = filtered.map { it.sharePrice.toFloat() }
    val minPrice = prices.min()
    val maxPrice = prices.max()
    val range = (maxPrice - minPrice).coerceAtLeast(1f)

    val lineColor = SeekerBlue
    val fillStart = SeekerBlue.copy(alpha = 0.3f)
    val fillEnd = SeekerBlue.copy(alpha = 0f)

    GlassCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Staking Yield Trend",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "SKR share price · ${filtered.size} data points",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (days in listOf(7, 30)) {
                        val selected = selectedDays == days
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) SeekerBlue else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selectedDays = days }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${days}d",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) androidx.compose.ui.graphics.Color.White
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                val w = size.width
                val h = size.height
                val n = prices.size

                fun xOf(i: Int) = if (n == 1) w / 2 else i.toFloat() / (n - 1) * w
                fun yOf(p: Float) = h - (p - minPrice) / range * h

                // Build fill path
                val fillPath = Path().apply {
                    moveTo(xOf(0), h)
                    lineTo(xOf(0), yOf(prices[0]))
                    for (i in 1 until n) lineTo(xOf(i), yOf(prices[i]))
                    lineTo(xOf(n - 1), h)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(listOf(fillStart, fillEnd))
                )

                // Build line path
                val linePath = Path().apply {
                    moveTo(xOf(0), yOf(prices[0]))
                    for (i in 1 until n) lineTo(xOf(i), yOf(prices[i]))
                }
                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )

                // End-point dot
                drawCircle(
                    color = lineColor,
                    radius = 5f,
                    center = Offset(xOf(n - 1), yOf(prices[n - 1]))
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val earliest = filtered.first()
                val latest = filtered.last()
                val earliestDisplay = String.format("%.6f", earliest.sharePrice / 1_000_000_000.0)
                val latestDisplay = String.format("%.6f", latest.sharePrice / 1_000_000_000.0)
                val delta = if (earliest.sharePrice > 0) {
                    (latest.sharePrice - earliest.sharePrice) * 100.0 / earliest.sharePrice
                } else 0.0
                Text(
                    text = earliestDisplay,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (delta >= 0) "+${String.format("%.3f", delta)}%" else "${String.format("%.3f", delta)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (delta >= 0) SolanaGreen else SeekerRed,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = latestDisplay,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun formatSkrAmount(amount: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale.US)
    nf.minimumFractionDigits = 0
    nf.maximumFractionDigits = if (amount < 1) 4 else 2
    return nf.format(amount)
}

private fun formatSolAmount(amount: Double): String {
    val nf = NumberFormat.getNumberInstance(Locale.US)
    nf.minimumFractionDigits = 0
    nf.maximumFractionDigits = if (amount < 0.01) 6 else if (amount < 1) 4 else 4
    return nf.format(amount)
}

private fun formatUsd(amount: Double): String {
    val nf = NumberFormat.getCurrencyInstance(Locale.US)
    nf.minimumFractionDigits = 2
    nf.maximumFractionDigits = 2
    return nf.format(amount)
}

private fun formatUsdSmall(amount: Double): String {
    return if (amount < 0.01) {
        String.format("$%.6f", amount)
    } else if (amount < 1) {
        String.format("$%.4f", amount)
    } else {
        String.format("$%.2f", amount)
    }
}
