package com.seekerverify.app.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.model.CheckInStreak
import com.seekerverify.app.ui.theme.SeekerBlue
import com.seekerverify.app.ui.theme.SeekerGold
import com.seekerverify.app.ui.theme.SolanaGreen
import com.seekerverify.app.ui.viewmodel.IdentityViewModel
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

@Composable
fun IdentityScreen(
    walletAddress: String,
    rpcUrl: String,
    viewModel: IdentityViewModel = viewModel()
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }

    val memberNumber by viewModel.memberNumber.collectAsState()
    val sgtMintAddress by viewModel.sgtMintAddress.collectAsState()
    val skrDomain by viewModel.skrDomain.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    // Check-in streak
    var streak by remember { mutableStateOf(prefs.getCheckInStreak()) }
    var checkedInToday by remember {
        val today = LocalDate.now().toString()
        mutableStateOf(streak.lastCheckInDate == today)
    }

    LaunchedEffect(walletAddress) {
        viewModel.loadIdentity(walletAddress, rpcUrl)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Seeker Identity",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Identity Card with gradient border ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(listOf(SeekerBlue, SolanaGreen)),
                    shape = RoundedCornerShape(20.dp)
                ),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header row: verified icon + title + share
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = "Verified Seeker",
                            modifier = Modifier.size(28.dp),
                            tint = SolanaGreen
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Seeker Genesis Token",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = {
                        val shareText = buildString {
                            append("I'm Seeker")
                            memberNumber?.let {
                                append(" #${NumberFormat.getNumberInstance(Locale.US).format(it)}")
                            }
                            skrDomain?.let {
                                append(" ($it)")
                            }
                            append(" \uD83D\uDE80\n")
                            append("Verified SGT holder on Solana Seeker\n")
                            append("#SeekerVerify #SolanaSeeker")
                        }
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share Identity"))
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share",
                            tint = SeekerBlue,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = SeekerBlue)
                    }
                } else {
                    // Large member number display
                    Text(
                        text = "SEEKER",
                        style = MaterialTheme.typography.labelSmall.copy(
                            letterSpacing = 3.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = memberNumber?.let {
                            "#${NumberFormat.getNumberInstance(Locale.US).format(it)}"
                        } ?: "---",
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = SeekerGold
                    )

                    // .skr domain display
                    skrDomain?.let { domain ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(SeekerBlue.copy(alpha = 0.12f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "\uD83C\uDF10", // globe emoji
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = domain,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = SeekerBlue
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Info rows
                    InfoRow(label = "WALLET", value = "${walletAddress.take(6)}...${walletAddress.takeLast(6)}")

                    sgtMintAddress?.let { mint ->
                        Spacer(modifier = Modifier.height(12.dp))
                        InfoRow(label = "SGT MINT", value = "${mint.take(6)}...${mint.takeLast(6)}")
                    }
                }

                // Verification badge
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(SolanaGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = SolanaGreen
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Verified Seeker Owner",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = SolanaGreen
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- Daily Check-In Card ---
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Daily Check-In",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Streak counter
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(SeekerGold.copy(alpha = 0.15f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "\uD83D\uDD25",
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${streak.currentStreak}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = SeekerGold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StreakStat(label = "Current", value = "${streak.currentStreak} days")
                    StreakStat(label = "Longest", value = "${streak.longestStreak} days")
                    StreakStat(label = "Total", value = "${streak.totalCheckIns}")
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (checkedInToday) {
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = SolanaGreen
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Checked in today!")
                    }
                } else {
                    Button(
                        onClick = {
                            val today = LocalDate.now().toString()
                            val yesterday = LocalDate.now().minusDays(1).toString()
                            val newCurrentStreak = if (streak.lastCheckInDate == yesterday) {
                                streak.currentStreak + 1
                            } else {
                                1
                            }
                            val newStreak = CheckInStreak(
                                currentStreak = newCurrentStreak,
                                longestStreak = maxOf(streak.longestStreak, newCurrentStreak),
                                lastCheckInDate = today,
                                totalCheckIns = streak.totalCheckIns + 1
                            )
                            prefs.saveCheckInStreak(newStreak)
                            streak = newStreak
                            checkedInToday = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = SeekerBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Check In", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Error display
        error?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 2.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun StreakStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
