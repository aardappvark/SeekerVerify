package com.seekerverify.app.ui.screens

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
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seekerverify.app.ui.theme.SeekerBlue
import com.seekerverify.app.ui.theme.SeekerGold
import com.seekerverify.app.ui.theme.SolanaGreen
import com.seekerverify.app.ui.theme.SolanaPurple
import com.seekerverify.app.ui.viewmodel.PortfolioViewModel
import java.text.NumberFormat
import java.util.Locale

@Composable
fun PortfolioScreen(
    walletAddress: String,
    rpcUrl: String,
    viewModel: PortfolioViewModel = viewModel()
) {
    val solBalance by viewModel.solBalance.collectAsState()
    val stakedSol by viewModel.stakedSol.collectAsState()
    val skrBalance by viewModel.skrBalance.collectAsState()
    val stakedSkr by viewModel.stakedSkr.collectAsState()
    val cooldownSkr by viewModel.cooldownSkr.collectAsState()
    val isStaked by viewModel.isStaked.collectAsState()
    val estimatedApy by viewModel.estimatedApy.collectAsState()
    val skrPriceUsd by viewModel.skrPriceUsd.collectAsState()
    val solPriceUsd by viewModel.solPriceUsd.collectAsState()
    val totalValueUsd by viewModel.totalValueUsd.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(walletAddress) {
        viewModel.loadPortfolio(walletAddress, rpcUrl)
    }

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
            IconButton(onClick = { viewModel.loadPortfolio(walletAddress, rpcUrl) }) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh",
                    tint = SeekerBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
                        text = "Rewards compound automatically via share price appreciation.",
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

        // Price info row
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            solPriceUsd?.let { price ->
                PriceChip(label = "SOL", price = formatUsdSmall(price))
            }
            skrPriceUsd?.let { price ->
                PriceChip(label = "SKR", price = formatUsdSmall(price))
            }
        }

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

        Spacer(modifier = Modifier.height(24.dp))
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
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
