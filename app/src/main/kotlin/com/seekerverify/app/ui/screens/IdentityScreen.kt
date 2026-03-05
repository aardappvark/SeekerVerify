package com.seekerverify.app.ui.screens

import android.content.Intent
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalView
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.model.CheckInStreak
import com.seekerverify.app.ui.util.hapticLongPress
import com.seekerverify.app.ui.util.hapticTap
import com.seekerverify.app.service.GeoAnalyticsService
import com.seekerverify.app.ui.theme.SeekerBlue
import com.seekerverify.app.ui.theme.SeekerGold
import com.seekerverify.app.ui.theme.SolanaGreen
import com.seekerverify.app.engine.AchievementEngine
import com.seekerverify.app.engine.HealthScoreEngine
import com.seekerverify.app.model.Achievement
import com.seekerverify.app.ui.components.GlassCard
import com.seekerverify.app.ui.components.GuestModeBanner
import com.seekerverify.app.ui.viewmodel.IdentityViewModel
import com.seekerverify.app.data.CheckInBackupManager
import com.seekerverify.app.wallet.WalletManager
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun IdentityScreen(
    walletAddress: String,
    rpcUrl: String,
    isGuestMode: Boolean = false,
    onConnectWallet: () -> Unit = {},
    viewModel: IdentityViewModel = viewModel(),
    activityResultSender: ActivityResultSender? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { AppPreferences(context) }

    val memberNumber by viewModel.memberNumber.collectAsState()
    val sgtMintAddress by viewModel.sgtMintAddress.collectAsState()
    val skrDomain by viewModel.skrDomain.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val scope = rememberCoroutineScope()

    // Check-in streak — restore from device backup if local prefs are empty
    var streak by remember {
        var s = prefs.getCheckInStreak()
        if (s.totalCheckIns == 0 && walletAddress.isNotEmpty()) {
            val backup = CheckInBackupManager.restoreBackup(context, walletAddress)
            if (backup != null && backup.streak.totalCheckIns > 0) {
                prefs.saveCheckInStreak(backup.streak)
                backup.lastOnChainSignature?.let { sig ->
                    backup.lastOnChainDate?.let { date ->
                        prefs.setLastOnChainCheckIn(sig, date)
                    }
                }
                s = backup.streak
            }
        }
        // Restore achievements from device backup if none stored locally
        if (prefs.getUnlockedAchievements().isEmpty() && walletAddress.isNotEmpty()) {
            CheckInBackupManager.restoreAchievements(context, walletAddress)
        }
        // Restore user settings (leaderboard opt-in etc.) from device backup
        if (walletAddress.isNotEmpty()) {
            CheckInBackupManager.restoreSettings(context, walletAddress)
        }
        mutableStateOf(s)
    }
    var checkedInToday by remember {
        val today = LocalDate.now().toString()
        mutableStateOf(streak.lastCheckInDate == today)
    }

    // On-chain check-in state
    var isVerifyingOnChain by remember { mutableStateOf(false) }
    var onChainSignature by remember { mutableStateOf(prefs.getLastOnChainCheckInSignature()) }
    var onChainDate by remember { mutableStateOf(prefs.getLastOnChainCheckInDate()) }
    var onChainError by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now().toString()
    val isVerifiedOnChainToday = onChainDate == today

    // On-chain check-in restoration: scan Solana for SV:CI memos after wallet connects.
    // Uses Helius RPC if available (higher rate limits) to avoid 429 contention with other callers.
    var hasAttemptedOnChainRestore by remember { mutableStateOf(false) }
    LaunchedEffect(walletAddress) {
        if (!hasAttemptedOnChainRestore && walletAddress.isNotEmpty()) {
            hasAttemptedOnChainRestore = true
            // Use Helius if API key available (avoids 429 contention with public RPC)
            val heliusKey = try { context.getString(com.seekerverify.app.R.string.helius_api_key) } catch (_: Exception) { "" }
            val scanRpcUrl = if (heliusKey.isNotEmpty()) {
                com.seekerverify.app.AppConfig.Rpc.heliusUrl(heliusKey)
            } else {
                rpcUrl
            }
            // Short delay if using Helius (dedicated endpoint), longer if sharing public RPC
            val delayMs = if (heliusKey.isNotEmpty()) 5_000L else 45_000L
            kotlinx.coroutines.delay(delayMs)
            val result = com.seekerverify.app.rpc.CheckInRpcClient.restoreFromChain(walletAddress, scanRpcUrl)
            if (result != null && result.streak.totalCheckIns > 0) {
                // Compare with current state — on-chain may have more history than today's single check-in
                val currentStreak = prefs.getCheckInStreak()
                if (result.streak.totalCheckIns > currentStreak.totalCheckIns) {
                    prefs.saveCheckInStreak(result.streak)
                    prefs.setLastOnChainCheckIn(result.lastSignature, result.lastDate)
                    streak = result.streak
                    checkedInToday = result.streak.lastCheckInDate == LocalDate.now().toString()
                    onChainSignature = result.lastSignature
                    onChainDate = result.lastDate
                    // Re-save to device backup so future restores are faster
                    CheckInBackupManager.saveBackup(
                        context, walletAddress, result.streak,
                        result.lastSignature, result.lastDate
                    )
                }
            }
        }
    }

    // Data is pre-loaded from AppNavigation

    val pullRefreshState = rememberPullRefreshState(
        refreshing = isLoading,
        onRefresh = {
            view.hapticTap(prefs)
            viewModel.loadIdentity(walletAddress, rpcUrl)
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
            text = "Seeker Identity",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (isGuestMode) {
            GuestModeBanner(onConnectWallet = onConnectWallet)
            Spacer(modifier = Modifier.height(16.dp))

            // Guest mode: show placeholder identity card
            GlassCard(cornerRadius = 20.dp) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = SeekerBlue.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Guest Mode",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connect your wallet to see your Seeker identity, SGT number, and check-in streak.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            return@Column
        }

        // --- Identity Card with gradient border ---
        GlassCard(cornerRadius = 20.dp) {
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
                        view.hapticTap(prefs)
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

                // Seed Vault verification badges
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(SolanaGreen.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = SolanaGreen
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Seed Vault Verified",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = SolanaGreen
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(SeekerBlue.copy(alpha = 0.12f))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = SeekerBlue
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Genesis Token",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = SeekerBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Private keys secured in hardware \u2022 Never exposed to apps",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- Daily Check-In Card ---
        GlassCard {
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

                    Spacer(modifier = Modifier.height(10.dp))

                    // On-chain verification
                    if (isVerifiedOnChainToday && onChainSignature != null) {
                        // Already verified on-chain today
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(SolanaGreen.copy(alpha = 0.1f))
                                .border(1.dp, SolanaGreen.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = SolanaGreen
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "On-Chain Verified",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = SolanaGreen
                                )
                                val sig = onChainSignature ?: ""
                                val truncSig = if (sig.length > 16) "${sig.take(8)}...${sig.takeLast(8)}" else sig
                                Text(
                                    text = "Tx: $truncSig",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                                Text(
                                    text = "Check-in recorded on Solana via Memo",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            // Open on Solscan
                            IconButton(onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW,
                                        android.net.Uri.parse("https://solscan.io/tx/$onChainSignature"))
                                    context.startActivity(intent)
                                } catch (_: Exception) { }
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.Share,
                                    contentDescription = "View on Solscan",
                                    modifier = Modifier.size(18.dp),
                                    tint = SolanaGreen
                                )
                            }
                        }
                    } else if (!isGuestMode && activityResultSender != null) {
                        // Show "Verify On-Chain" button
                        OutlinedButton(
                            onClick = {
                                if (isVerifyingOnChain) return@OutlinedButton
                                isVerifyingOnChain = true
                                onChainError = null
                                val memo = "SV:CI:$today:${streak.currentStreak}"
                                scope.launch {
                                    val result = WalletManager.signAndSendMemo(
                                        sender = activityResultSender,
                                        rpcUrl = rpcUrl,
                                        memo = memo
                                    )
                                    result.fold(
                                        onSuccess = { sig ->
                                            prefs.setLastOnChainCheckIn(sig, today)
                                            onChainSignature = sig
                                            onChainDate = today
                                            GeoAnalyticsService.track(GeoAnalyticsService.Events.ONCHAIN_CHECKIN)
                                            CheckInBackupManager.saveBackup(
                                                context, walletAddress, streak, sig, today
                                            )
                                        },
                                        onFailure = { e ->
                                            onChainError = e.message ?: "Signing failed"
                                        }
                                    )
                                    isVerifyingOnChain = false
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isVerifyingOnChain,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, SolanaGreen.copy(alpha = 0.6f)
                            )
                        ) {
                            if (isVerifyingOnChain) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = SolanaGreen,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Signing via Seed Vault...",
                                    color = SolanaGreen
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Verified,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = SolanaGreen
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Verify On-Chain",
                                    color = SolanaGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        if (onChainError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = onChainError ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            view.hapticLongPress(prefs)
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
                            CheckInBackupManager.saveBackup(
                                context, walletAddress, newStreak,
                                onChainSignature, onChainDate
                            )
                            GeoAnalyticsService.track(GeoAnalyticsService.Events.CHECK_IN)
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

        // --- Seeker Health Score ---
        Spacer(modifier = Modifier.height(20.dp))
        val healthResult = remember(streak, skrDomain) { HealthScoreEngine.compute(prefs, hasDomain = skrDomain != null) }
        HealthScoreCard(healthResult)

        // --- Achievements Badge Grid ---
        Spacer(modifier = Modifier.height(20.dp))

        // Evaluate achievements
        val achievementState = AchievementEngine.AchievementState(
            totalCheckIns = streak.totalCheckIns,
            currentStreak = streak.currentStreak,
            hasDomain = skrDomain != null,
            hasPrediction = prefs.hasPrediction(),
            hasUsedSimulator = prefs.hasUsedSimulator(),
            hasViewedHistory = prefs.hasViewedHistory(),
            hasTierUpgrade = prefs.hasTierUpgrade()
        )
        val unlockedFromEngine = AchievementEngine.evaluate(achievementState)
        val savedAchievements = prefs.getUnlockedAchievements()
        val allUnlocked = savedAchievements + unlockedFromEngine

        // Persist any newly discovered achievements + back up to device
        if (allUnlocked.size > savedAchievements.size) {
            prefs.saveUnlockedAchievements(allUnlocked)
            if (walletAddress.isNotEmpty()) {
                CheckInBackupManager.saveAchievementBackup(context, walletAddress)
            }
        }

        GlassCard {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Achievements",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${allUnlocked.size} / ${Achievement.entries.size} unlocked",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Badge grid (2 columns)
                val achievements = Achievement.entries.toList()
                for (i in achievements.indices step 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AchievementBadge(
                            achievement = achievements[i],
                            isUnlocked = achievements[i] in allUnlocked,
                            modifier = Modifier.weight(1f)
                        )
                        if (i + 1 < achievements.size) {
                            AchievementBadge(
                                achievement = achievements[i + 1],
                                isUnlocked = achievements[i + 1] in allUnlocked,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    if (i + 2 < achievements.size) {
                        Spacer(modifier = Modifier.height(8.dp))
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

    PullRefreshIndicator(
        refreshing = isLoading,
        state = pullRefreshState,
        modifier = Modifier.align(Alignment.TopCenter),
        contentColor = SeekerBlue
    )
    }
}

@Composable
private fun AchievementBadge(
    achievement: Achievement,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isUnlocked) SeekerGold.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surface
            )
            .padding(10.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = achievement.badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = if (isUnlocked) SeekerGold
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = achievement.title,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = if (isUnlocked) FontWeight.Bold else FontWeight.Normal
                ),
                color = if (isUnlocked) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
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

@Composable
private fun HealthScoreCard(result: HealthScoreEngine.HealthResult) {
    val scoreColor = when {
        result.totalScore >= 70 -> SolanaGreen
        result.totalScore >= 40 -> SeekerGold
        else -> com.seekerverify.app.ui.theme.SeekerRed
    }
    val animatedProgress by animateFloatAsState(
        targetValue = result.totalScore / 100f,
        animationSpec = tween(1200),
        label = "healthScore"
    )

    GlassCard {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Seeker Health Score",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Circular progress ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(140.dp)
            ) {
                Canvas(modifier = Modifier.size(140.dp)) {
                    val strokeWidth = 12.dp.toPx()
                    // Track
                    drawArc(
                        color = scoreColor.copy(alpha = 0.15f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    // Progress
                    drawArc(
                        color = scoreColor,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${result.totalScore}",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = scoreColor
                    )
                    Text(
                        text = "/100",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Component checklist
            result.components.forEach { component ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (component.completed) Icons.Filled.CheckCircle else Icons.Filled.Verified,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (component.completed) SolanaGreen else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = component.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (component.completed) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${component.points}/${component.maxPoints}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (component.completed) SolanaGreen else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
