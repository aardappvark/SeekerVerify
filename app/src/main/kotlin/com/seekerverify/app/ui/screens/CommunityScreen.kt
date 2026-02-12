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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seekerverify.app.ui.theme.SeekerBlue
import com.seekerverify.app.ui.theme.SeekerGold
import com.seekerverify.app.ui.theme.SolanaGreen
import com.seekerverify.app.ui.theme.SolanaPurple
import com.seekerverify.app.ui.viewmodel.CommunityViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CommunityScreen(
    walletAddress: String,
    rpcUrl: String,
    viewModel: CommunityViewModel = viewModel()
) {
    val totalSeekers by viewModel.totalSeekers.collectAsState()
    val userPosition by viewModel.userPosition.collectAsState()
    val percentile by viewModel.percentile.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    LaunchedEffect(walletAddress) {
        viewModel.loadCommunity(walletAddress, rpcUrl)
    }

    val numberFormat = NumberFormat.getNumberInstance(Locale.US)

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

        // Your Position Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
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

        Spacer(modifier = Modifier.height(16.dp))

        // Community Stats Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Groups,
                label = "Active Stakers",
                value = "~40K",
                color = SeekerBlue
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Rocket,
                label = "SKR Staked",
                value = "~60%",
                color = SolanaPurple
            )
        }

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
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
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
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
