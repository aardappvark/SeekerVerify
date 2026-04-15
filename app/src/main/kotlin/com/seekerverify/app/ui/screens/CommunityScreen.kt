package com.seekerverify.app.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Rocket
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seekerverify.app.service.GeoAnalyticsService
import com.seekerverify.app.service.LeaderboardData
import com.seekerverify.app.ui.components.GlassCard
import com.seekerverify.app.ui.components.GuestModeBanner
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.data.CheckInBackupManager
import com.seekerverify.app.ui.util.hapticTap
import com.seekerverify.app.ui.theme.SeekerBlue
import com.seekerverify.app.ui.theme.SeekerGold
import com.seekerverify.app.ui.theme.SolanaGreen
import com.seekerverify.app.ui.theme.SolanaPurple
import com.seekerverify.app.ui.viewmodel.CommunityViewModel
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun CommunityScreen(
    walletAddress: String,
    rpcUrl: String,
    isGuestMode: Boolean = false,
    onConnectWallet: () -> Unit = {},
    viewModel: CommunityViewModel = viewModel()
) {
    val view = LocalView.current
    val prefs = remember { AppPreferences(view.context) }
    val totalSeekers by viewModel.totalSeekers.collectAsState()
    val userPosition by viewModel.userPosition.collectAsState()
    val percentile by viewModel.percentile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // Staking stats
    val activeStakers by viewModel.activeStakers.collectAsState()
    val totalStakedDisplay by viewModel.totalStakedDisplay.collectAsState()
    val stakingParticipation by viewModel.stakingParticipation.collectAsState()

    // Leaderboard
    val leaderboard by viewModel.leaderboard.collectAsState()

    // Fleet Mode (statistical mode — most common staked SKR amount)
    val fleetModeSkr by viewModel.fleetModeSkr.collectAsState()

    // User vs fleet comparison
    val userStakedSkr by viewModel.userStakedSkr.collectAsState()

    // Data is pre-loaded from AppNavigation
    LaunchedEffect(Unit) {
        GeoAnalyticsService.track(GeoAnalyticsService.Events.COMMUNITY_VIEWED)
    }

    // Guest mode flag for hiding personal position
    val showPersonalPosition = !isGuestMode

    val numberFormat = NumberFormat.getNumberInstance(Locale.US)

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = {
            view.hapticTap(prefs)
            viewModel.loadCommunity(walletAddress, rpcUrl)
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
        Text(
            text = "Seeker Fleet",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Fleet Overview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SolanaPurple),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.Rocket,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "TOTAL SEEKERS",
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
                    Text(
                        text = numberFormat.format(totalSeekers),
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = "devices in the fleet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isGuestMode) {
            GuestModeBanner(onConnectWallet = onConnectWallet, message = "Connect wallet to see your fleet position")
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Your Position Card (hidden in guest mode)
        if (!showPersonalPosition) {
            // Skip personal position in guest mode
        } else {
        GlassCard {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SeekerGold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Leaderboard,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = SeekerGold
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Your Fleet Position",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        userPosition?.let {
                            Text(
                                text = "#${numberFormat.format(it)} of ${numberFormat.format(totalSeekers)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                percentile?.let { pct ->
                    Spacer(modifier = Modifier.height(16.dp))

                    // Percentile bar
                    Text(
                        text = "Earlier than ${String.format("%.1f", pct)}% of Seekers",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = SolanaGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val animatedProgress by animateFloatAsState(
                        targetValue = (pct / 100.0).toFloat(),
                        animationSpec = tween(durationMillis = 1000),
                        label = "percentile"
                    )

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp)),
                        color = SolanaGreen,
                        trackColor = SolanaGreen.copy(alpha = 0.15f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Adopter tier label
                    val tierLabel = when {
                        pct >= 99 -> "OG Seeker \uD83D\uDC51"
                        pct >= 95 -> "Early Pioneer \uD83C\uDF1F"
                        pct >= 80 -> "Early Adopter \uD83D\uDE80"
                        pct >= 50 -> "Fleet Member \u2693"
                        else -> "Seeker \uD83D\uDEF0\uFE0F"
                    }
                    Text(
                        text = tierLabel,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = SeekerGold
                    )
                }
            }
        }
        } // end showPersonalPosition else

        // Fleet Mode loading OR You vs Fleet card
        if (!isGuestMode) {
            Spacer(modifier = Modifier.height(16.dp))
            Crossfade(
                targetState = isLoading,
                animationSpec = tween(300),
                label = "fleetTransition"
            ) { loading ->
                if (loading) {
                    FleetModeLoadingCard()
                } else if (userStakedSkr > 0) {
                    val fleetAvgSkr = if ((activeStakers ?: 0) > 0 && (totalStakedDisplay ?: 0.0) > 0.0) {
                        (totalStakedDisplay ?: 0.0) / (activeStakers ?: 1)
                    } else 0.0

                    if (fleetAvgSkr > 0) {
                        YouVsFleetCard(
                            userStakedSkr = userStakedSkr,
                            fleetAvgSkr = fleetAvgSkr,
                            fleetModeSkr = fleetModeSkr
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Community Stats Grid — real on-chain data
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val stakersText = activeStakers?.let { count ->
                if (count == 0) null // likely 429 failure — show refresh
                else when {
                    count >= 1000 -> "${numberFormat.format(count / 1000)}K"
                    else -> numberFormat.format(count)
                }
            }
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Groups,
                label = "Active Stakers",
                value = stakersText,
                color = SeekerBlue,
                isLoading = isLoading && activeStakers == null,
                onRefresh = { viewModel.loadCommunity(walletAddress, rpcUrl) }
            )

            val stakedText = stakingParticipation?.let { pct ->
                "${String.format("%.1f", pct)}%"
            }
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Rocket,
                label = "SKR Staked",
                value = stakedText,
                color = SolanaPurple,
                isLoading = isLoading && stakingParticipation == null,
                onRefresh = { viewModel.loadCommunity(walletAddress, rpcUrl) }
            )
        }

        // Total staked amount
        totalStakedDisplay?.let { staked ->
            if (staked > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                val stakedAmount = when {
                    staked >= 1_000_000_000 -> "${String.format("%.1f", staked / 1_000_000_000)}B"
                    staked >= 1_000_000 -> "${numberFormat.format((staked / 1_000_000).toLong())}M"
                    else -> numberFormat.format(staked.toLong())
                }
                Text(
                    text = "$stakedAmount SKR staked across the network",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Leaderboard Section
        leaderboard?.let { lb ->
            if (lb.totalParticipants > 0) {
                Spacer(modifier = Modifier.height(16.dp))

                GlassCard {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Leaderboard,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = SeekerGold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Community Leaderboard",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${numberFormat.format(lb.totalParticipants)} predictions this week",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Tier distribution bars
                        val tierOrder = listOf("Sovereign", "Luminary", "Vanguard", "Prospector", "Scout")
                        val tierColors = mapOf(
                            "Sovereign" to SeekerGold,
                            "Luminary" to androidx.compose.ui.graphics.Color(0xFFFF6B35),
                            "Vanguard" to SolanaGreen,
                            "Prospector" to SolanaPurple,
                            "Scout" to SeekerBlue
                        )
                        val maxCount = lb.tierDistribution.maxOfOrNull { it.count } ?: 1L
                        val grandTotal = lb.tierDistribution.sumOf { it.count }.coerceAtLeast(1L)

                        for (tierName in tierOrder) {
                            val entry = lb.tierDistribution.find { it.tier == tierName }
                            val count = entry?.count ?: 0
                            val fraction = count.toFloat() / maxCount.toFloat()
                            val pctOfFleet = count * 100.0 / grandTotal

                            TierBar(
                                tierName = tierName,
                                fraction = fraction,
                                pctOfFleet = pctOfFleet,
                                color = tierColors[tierName] ?: SeekerBlue
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Opt-in button / status
                        var isOptedIn by remember { mutableStateOf(prefs.isLeaderboardOptedIn()) }

                        if (isOptedIn) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = SolanaGreen
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Sharing anonymous predictions",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SolanaGreen
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    view.hapticTap(prefs)
                                    isOptedIn = true
                                    prefs.setLeaderboardOptedIn(true)
                                    // Persist to device backup so setting survives uninstall
                                    if (walletAddress.isNotEmpty()) {
                                        CheckInBackupManager.saveSettingsBackup(view.context, walletAddress)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, SeekerGold.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = SeekerGold
                                )
                            ) {
                                Text(
                                    text = "Opt In to Share Predictions",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Note: dApp Activity card removed (2026-04-15) to keep the Community
        // screen focused on "where do I stand in the fleet?" and to stay
        // consistent with the app's privacy-first positioning. Analytics events
        // are still tracked via GeoAnalyticsService for the public verification
        // endpoint, but the results are no longer reflected back at users.

        Spacer(modifier = Modifier.height(24.dp))

        // Note about data
        Text(
            text = "Fleet data is approximated from on-chain records and refreshed periodically.",
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
private fun YouVsFleetCard(
    userStakedSkr: Double,
    fleetAvgSkr: Double,
    fleetModeSkr: Double? = null
) {
    // Max across all three values for proportional bars
    val maxVal = maxOf(userStakedSkr, fleetAvgSkr, fleetModeSkr ?: 0.0).coerceAtLeast(1.0)

    fun formatSkr(v: Double): String = when {
        v >= 1_000_000 -> "${String.format("%.1f", v / 1_000_000)}M SKR"
        v >= 1_000 -> "${String.format("%.1f", v / 1_000)}K SKR"
        else -> "${String.format("%.0f", v)} SKR"
    }

    GlassCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "You vs Fleet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "SKR staked",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            // User bar
            val userFraction by animateFloatAsState(
                targetValue = (userStakedSkr / maxVal).toFloat().coerceIn(0.02f, 1f),
                animationSpec = tween(900),
                label = "userBar"
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "You",
                    style = MaterialTheme.typography.labelMedium,
                    color = SeekerBlue,
                    modifier = Modifier.width(72.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(userFraction)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(SeekerBlue)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatSkr(userStakedSkr),
                    style = MaterialTheme.typography.labelSmall,
                    color = SeekerBlue,
                    modifier = Modifier.width(80.dp),
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fleet avg bar
            val fleetFraction by animateFloatAsState(
                targetValue = (fleetAvgSkr / maxVal).toFloat().coerceIn(0.02f, 1f),
                animationSpec = tween(900),
                label = "fleetBar"
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Fleet Avg",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(72.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fleetFraction)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.outline)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatSkr(fleetAvgSkr),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(80.dp),
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fleet Mode bar — statistical mode (most common staked amount)
            if (fleetModeSkr != null && fleetModeSkr > 0) {
                val modeFraction by animateFloatAsState(
                    targetValue = (fleetModeSkr / maxVal).toFloat().coerceIn(0.02f, 1f),
                    animationSpec = tween(900),
                    label = "modeBar"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Fleet Mode",
                        style = MaterialTheme.typography.labelMedium,
                        color = SeekerGold,
                        modifier = Modifier.width(72.dp)
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(modeFraction)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(SeekerGold)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatSkr(fleetModeSkr),
                        style = MaterialTheme.typography.labelSmall,
                        color = SeekerGold,
                        modifier = Modifier.width(80.dp),
                        textAlign = TextAlign.End
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Most common staked amount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun FleetModeLoadingCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "fleetMode")

    val pulsingAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val counterValue by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 99999f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "counter"
    )

    val scanProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scan"
    )

    GlassCard {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Fleet Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = SeekerBlue
                )
                Spacer(modifier = Modifier.width(8.dp))
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = SeekerBlue,
                    strokeWidth = 2.dp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Computing fleet mode...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulsingAlpha)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Scanning bar — You
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "You",
                    style = MaterialTheme.typography.labelMedium,
                    color = SeekerBlue.copy(alpha = 0.6f),
                    modifier = Modifier.width(72.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(scanProgress)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        SeekerBlue.copy(alpha = 0.3f),
                                        SeekerBlue.copy(alpha = 0.7f)
                                    )
                                )
                            )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${counterValue.toLong() % 10000}",
                    style = MaterialTheme.typography.labelSmall,
                    color = SeekerBlue.copy(alpha = 0.5f),
                    modifier = Modifier.width(80.dp),
                    textAlign = TextAlign.End
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scanning bar — Fleet
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Fleet",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.width(72.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(((scanProgress + 0.3f) % 1f))
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    )
                                )
                            )
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(counterValue.toLong() + 4321) % 10000}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.width(80.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun TierBar(
    tierName: String,
    fraction: Float,
    pctOfFleet: Double,
    color: androidx.compose.ui.graphics.Color
) {
    val animatedFraction by animateFloatAsState(
        targetValue = fraction.coerceAtLeast(0.02f),
        animationSpec = tween(800),
        label = "bar_$tierName"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = tierName,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(90.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedFraction)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${String.format("%.0f", pctOfFleet)}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String?,
    color: androidx.compose.ui.graphics.Color,
    isLoading: Boolean = false,
    onRefresh: (() -> Unit)? = null
) {
    GlassCard(modifier = modifier, cornerRadius = 12.dp) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = color
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = color,
                    strokeWidth = 2.dp
                )
            } else {
                // No data and not loading — show dash with refresh hint
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (onRefresh != null) Modifier.clickable { onRefresh() } else Modifier
                ) {
                    Text(
                        text = "\u2014",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (onRefresh != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
