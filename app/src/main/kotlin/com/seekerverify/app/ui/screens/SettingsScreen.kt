package com.seekerverify.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalView
import com.seekerverify.app.AppConfig
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.data.CheckInBackupManager
import com.seekerverify.app.service.GeoAnalyticsService
import com.seekerverify.app.ui.util.hapticTap
import com.seekerverify.app.ui.components.GlassCard
import com.seekerverify.app.ui.components.GuestModeBanner
import com.seekerverify.app.ui.theme.SeekerBlue
import com.seekerverify.app.ui.theme.SeekerRed
import com.seekerverify.app.ui.theme.SolanaGreen

@Composable
fun SettingsScreen(
    walletAddress: String,
    isGuestMode: Boolean = false,
    onDisconnect: () -> Unit,
    onConnectWallet: () -> Unit = onDisconnect,
    onThemeChanged: (String) -> Unit = {},
    currentThemeMode: String = "dark",
    onShowOnboarding: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { AppPreferences(context) }

    LaunchedEffect(Unit) {
        GeoAnalyticsService.track(GeoAnalyticsService.Events.SETTINGS_OPENED)
    }

    var useHelius by remember { mutableStateOf(prefs.getRpcProvider() == "helius") }
    var analyticsEnabled by remember { mutableStateOf(prefs.isAnalyticsEnabled()) }
    var notificationsEnabled by remember { mutableStateOf(prefs.isNotificationsEnabled()) }
    var leaderboardOptedIn by remember { mutableStateOf(prefs.isLeaderboardOptedIn()) }
    var hapticsEnabled by remember { mutableStateOf(prefs.isHapticsEnabled()) }
    var yieldPeriod by remember { mutableStateOf(prefs.getYieldPeriod()) }
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0"
        } catch (_: Exception) { "1.0.0" }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(20.dp))

        // --- Appearance Section ---
        SectionLabel("Appearance")

        GlassCard(cornerRadius = 12.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.DarkMode,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = SeekerBlue
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Theme selector: Dark / AMOLED / Light / System
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("dark" to "Dark", "amoled" to "AMOLED", "light" to "Light", "system" to "System").forEach { (mode, label) ->
                        val isSelected = currentThemeMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) SeekerBlue.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isSelected) SeekerBlue else MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    view.hapticTap(prefs)
                                    onThemeChanged(mode)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) SeekerBlue
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Haptics toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Vibration,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = SeekerBlue
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Haptic Feedback",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Vibration on button presses",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hapticsEnabled,
                        onCheckedChange = { checked ->
                            view.hapticTap(prefs)
                            hapticsEnabled = checked
                            prefs.setHapticsEnabled(checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SeekerBlue,
                            checkedTrackColor = SeekerBlue.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Wallet Section ---
        if (isGuestMode) {
            GuestModeBanner(onConnectWallet = onConnectWallet)
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            SectionLabel("Wallet")

            GlassCard(cornerRadius = 12.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    SettingsRow(
                        icon = Icons.Filled.Verified,
                        label = "Connected Wallet",
                        value = "${walletAddress.take(6)}...${walletAddress.takeLast(6)}"
                    )

                    prefs.getMemberNumber()?.let { memberNum ->
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        SettingsRow(
                            icon = Icons.Filled.Verified,
                            label = "Seeker Number",
                            value = "#${String.format("%,d", memberNum)}"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // --- RPC Section ---
        SectionLabel("Network")

        GlassCard(cornerRadius = 12.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = SeekerBlue
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Use Helius RPC",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (useHelius) "Higher rate limits" else "Public RPC (rate limited)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = useHelius,
                        onCheckedChange = { checked ->
                            view.hapticTap(prefs)
                            useHelius = checked
                            prefs.setRpcProvider(if (checked) "helius" else "public")
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SeekerBlue,
                            checkedTrackColor = SeekerBlue.copy(alpha = 0.4f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (useHelius) {
                        "Using Helius free tier (1M credits/month). Faster and more reliable."
                    } else {
                        "Using public Solana RPC. Free but rate-limited to ~5 requests/second."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Staking Section ---
        SectionLabel("Staking")

        GlassCard(cornerRadius = 12.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = SeekerBlue
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Yield Period",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("ytd" to "Year to Date", "fy" to "Financial Year").forEach { (mode, label) ->
                        val isSelected = yieldPeriod == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) SeekerBlue.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isSelected) SeekerBlue else MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    view.hapticTap(prefs)
                                    yieldPeriod = mode
                                    prefs.setYieldPeriod(mode)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (isSelected) SeekerBlue
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (yieldPeriod == "fy") "Jul 1 \u2013 Jun 30 (Australian financial year)"
                    else "Jan 1 \u2013 Dec 31 (calendar year)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Data Section ---
        SectionLabel("Data")

        GlassCard(cornerRadius = 12.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsRow(
                    icon = Icons.Filled.Storage,
                    label = "SGT Cache",
                    value = if (prefs.hasSgt() && !prefs.shouldRecheckSgt()) "Valid" else "Needs refresh"
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "SGT verification is cached for 24 hours to minimize RPC calls.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = SeekerBlue
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Anonymous Analytics",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Country-level usage stats — no wallet, IP, or personal data stored",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = analyticsEnabled,
                        onCheckedChange = { checked ->
                            view.hapticTap(prefs)
                            analyticsEnabled = checked
                            prefs.setAnalyticsEnabled(checked)
                            GeoAnalyticsService.setEnabled(checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SeekerBlue,
                            checkedTrackColor = SeekerBlue.copy(alpha = 0.4f)
                        )
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = SeekerBlue
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Check-in Reminders",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Daily reminder to maintain your streak",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { checked ->
                            view.hapticTap(prefs)
                            notificationsEnabled = checked
                            prefs.setNotificationsEnabled(checked)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SeekerBlue,
                            checkedTrackColor = SeekerBlue.copy(alpha = 0.4f)
                        )
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Leaderboard,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = SeekerBlue
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Community Leaderboard",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Share anonymous tier data with the community",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = leaderboardOptedIn,
                        onCheckedChange = { checked ->
                            view.hapticTap(prefs)
                            leaderboardOptedIn = checked
                            prefs.setLeaderboardOptedIn(checked)
                            // Persist to device backup so setting survives uninstall
                            if (walletAddress.isNotEmpty()) {
                                CheckInBackupManager.saveSettingsBackup(context, walletAddress)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SeekerBlue,
                            checkedTrackColor = SeekerBlue.copy(alpha = 0.4f)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- About Section ---
        SectionLabel("About")

        GlassCard(cornerRadius = 12.dp) {
            Column(modifier = Modifier.padding(16.dp)) {
                SettingsRow(
                    icon = Icons.Filled.Info,
                    label = "Version",
                    value = versionName
                )
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // How to Use — re-shows onboarding carousel
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            view.hapticTap(prefs)
                            onShowOnboarding()
                        }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = SeekerBlue
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "How to Use",
                        style = MaterialTheme.typography.bodyLarge,
                        color = SeekerBlue
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Text(
                    text = "Seeker Verify",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your Seeker device companion. Verify SGT ownership, track SKR portfolio, and view on-chain activity analysis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "By MidMightBit Games (Australia)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Zero server cost. All data on-device + on-chain. Anonymous usage analytics (country-level, no wallet/IP stored) help improve the dApp.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                // Legal links
                val uriHandler = LocalUriHandler.current
                val baseUrl = AppConfig.Identity.URI

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.text.ClickableText(
                        text = androidx.compose.ui.text.AnnotatedString("Privacy Policy"),
                        style = MaterialTheme.typography.bodySmall.copy(color = SeekerBlue),
                        onClick = { uriHandler.openUri("$baseUrl/privacy.html") }
                    )
                    Text(
                        text = "  \u2022  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    androidx.compose.foundation.text.ClickableText(
                        text = androidx.compose.ui.text.AnnotatedString("Terms of Service"),
                        style = MaterialTheme.typography.bodySmall.copy(color = SeekerBlue),
                        onClick = { uriHandler.openUri("$baseUrl/terms.html") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Regulatory disclaimer
                Text(
                    text = "Not affiliated with Solana Foundation, Solana Mobile Inc., or any token issuer. Not authorised or regulated by any financial regulator. Does not provide financial advice.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- Delete All Data (not shown in guest mode) ---
        var showDeleteDialog by remember { mutableStateOf(false) }
        if (!isGuestMode) {

        OutlinedButton(
            onClick = {
                view.hapticTap(prefs)
                showDeleteDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = SeekerRed
            )
        ) {
            Icon(
                imageVector = Icons.Filled.DeleteForever,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Delete All Data", fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Permanently erases all cached data, preferences, and streak history from this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete All Data?") },
                text = {
                    Text("This will permanently erase all cached data, preferences, streak history, and disconnect your wallet. This action cannot be undone.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog = false
                            prefs.deleteAllData()
                            onDisconnect()
                        }
                    ) {
                        Text("Delete Everything", color = SeekerRed)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        } // end !isGuestMode

        Spacer(modifier = Modifier.weight(1f))

        Spacer(modifier = Modifier.height(24.dp))

        if (isGuestMode) {
            // Connect Wallet button in guest mode
            Button(
                onClick = {
                    view.hapticTap(prefs)
                    onConnectWallet()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SeekerBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect Wallet", fontWeight = FontWeight.Bold)
            }
        } else {
            // Disconnect button
            Button(
                onClick = {
                    view.hapticTap(prefs)
                    onDisconnect()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SeekerRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LinkOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Disconnect Wallet", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Disconnecting will clear all cached data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = SeekerBlue
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
