package com.seekerverify.app

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.midmightbit.sgt.SgtChecker
import com.midmightbit.sgt.SgtConstants
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.service.GeoAnalyticsService
import com.seekerverify.app.service.NotificationService
import com.seekerverify.app.worker.DailyCheckInWorker
import com.seekerverify.app.worker.TierChangeWorker
import com.seekerverify.app.worker.WidgetRefreshWorker
import com.seekerverify.app.ui.navigation.AppNavigation
import com.seekerverify.app.ui.screens.OnboardingScreen
import com.seekerverify.app.ui.screens.WalletConnectScreen
import com.seekerverify.app.ui.theme.SeekerBlue
import com.seekerverify.app.ui.theme.SeekerRed
import com.seekerverify.app.ui.theme.SeekerVerifyTheme
import androidx.compose.foundation.isSystemInDarkTheme
import com.seekerverify.app.wallet.WalletManager
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private var activityResultSender: ActivityResultSender? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            activityResultSender = ActivityResultSender(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create ActivityResultSender: ${e.message}")
        }

        try {
            NotificationService.createChannel(this)
        } catch (_: Exception) { }

        // Initialize analytics with API key from local.properties (via resValue)
        try {
            val initPrefs = AppPreferences(this)
            GeoAnalyticsService.init(getString(R.string.analytics_api_key))
            GeoAnalyticsService.setEnabled(initPrefs.isAnalyticsEnabled())
        } catch (_: Exception) { }

        setContent {
            val themePrefs = remember { AppPreferences(this) }
            var themeMode by remember { mutableStateOf(themePrefs.getThemeMode()) }
            val systemDark = isSystemInDarkTheme()
            val isAmoled = themeMode == "amoled"
            val isDark = when (themeMode) {
                "light" -> false
                "system" -> systemDark
                "amoled" -> true
                else -> true // "dark" is default
            }
            SeekerVerifyTheme(darkTheme = isDark, isAmoled = isAmoled) {
                SeekerVerifyApp(
                    activityResultSender = activityResultSender,
                    onThemeChanged = { mode ->
                        themePrefs.setThemeMode(mode)
                        themeMode = mode
                    },
                    currentThemeMode = themeMode
                )
            }
        }
    }
}

@Composable
fun SeekerVerifyApp(
    activityResultSender: ActivityResultSender?,
    onThemeChanged: (String) -> Unit = {},
    currentThemeMode: String = "dark"
) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()

    var isWalletConnected by remember { mutableStateOf(prefs.isWalletConnected()) }
    var walletAddress by remember { mutableStateOf(prefs.getWalletAddress() ?: "") }
    val isConnecting by WalletManager.isConnecting.collectAsState()
    var connectError by remember { mutableStateOf<String?>(null) }
    var hasCompletedOnboarding by remember { mutableStateOf(prefs.hasCompletedOnboarding()) }

    // SGT Gate state
    var sgtCheckState by remember { mutableStateOf<SgtCheckState>(SgtCheckState.Idle) }

    // Determine RPC URL based on user preference
    val heliusApiKey = context.getString(R.string.helius_api_key)
    val rpcProvider = prefs.getRpcProvider()
    val rpcUrl = when {
        rpcProvider == "helius" && heliusApiKey.isNotEmpty() -> AppConfig.Rpc.heliusUrl(heliusApiKey)
        else -> AppConfig.Rpc.PUBLIC_MAINNET
    }

    if (!hasCompletedOnboarding) {
        OnboardingScreen(
            onComplete = {
                prefs.setHasCompletedOnboarding(true)
                hasCompletedOnboarding = true
                GeoAnalyticsService.track(GeoAnalyticsService.Events.ONBOARDING_COMPLETED)
            }
        )
    } else if (!isWalletConnected && sgtCheckState !is SgtCheckState.GuestMode) {
        // Show wallet connect screen
        WalletConnectScreen(
            onConnect = {
                val currentSender = activityResultSender
                if (currentSender == null) {
                    connectError = "Wallet adapter not available"
                    return@WalletConnectScreen
                }
                connectError = null
                scope.launch {
                    val result = WalletManager.signIn(currentSender)
                    result.fold(
                        onSuccess = { connectResult ->
                            Log.d(TAG, "Wallet signed in: ${connectResult.publicKeyBase58.take(8)}...")
                            GeoAnalyticsService.track(GeoAnalyticsService.Events.WALLET_CONNECTED)
                            prefs.saveWalletConnection(
                                connectResult.publicKeyBase58,
                                connectResult.walletName
                            )
                            walletAddress = connectResult.publicKeyBase58
                            isWalletConnected = true
                            sgtCheckState = SgtCheckState.Checking
                        },
                        onFailure = { e ->
                            Log.e(TAG, "Wallet sign-in failed: ${e.message}", e)
                            connectError = e.message ?: "Failed to sign in with wallet"
                        }
                    )
                }
            },
            onExploreAsGuest = {
                Log.d(TAG, "Entering guest mode")
                GeoAnalyticsService.track(GeoAnalyticsService.Events.GUEST_MODE_ENTERED)
                sgtCheckState = SgtCheckState.GuestMode
            },
            isConnecting = isConnecting,
            errorMessage = connectError
        )
    } else if (sgtCheckState is SgtCheckState.GuestMode) {
        // Guest mode: show app with reduced functionality
        AppNavigation(
            walletAddress = "",
            rpcUrl = rpcUrl,
            isGuestMode = true,
            onDisconnect = {
                sgtCheckState = SgtCheckState.Idle
            },
            onConnectWallet = {
                sgtCheckState = SgtCheckState.Idle
            },
            onThemeChanged = onThemeChanged,
            currentThemeMode = currentThemeMode,
            activityResultSender = activityResultSender
        )
    } else {
        // SGT Gate: handle Idle state transition via LaunchedEffect
        // (must not mutate state during composition)
        if (sgtCheckState is SgtCheckState.Idle) {
            LaunchedEffect(Unit) {
                if (prefs.hasSgt() && !prefs.shouldRecheckSgt()) {
                    Log.d(TAG, "SGT cached, skipping recheck")
                    sgtCheckState = SgtCheckState.Verified
                } else {
                    Log.d(TAG, "SGT needs check")
                    sgtCheckState = SgtCheckState.Checking
                }
            }
            // Show checking screen while we decide
            SgtCheckingScreen()
        }

        when (sgtCheckState) {
            SgtCheckState.Idle -> {
                // Handled above
            }

            SgtCheckState.GuestMode -> {
                // Handled in outer else-if branch
            }

            SgtCheckState.Checking -> {
                SgtCheckingScreen()
                LaunchedEffect(walletAddress) {
                    Log.d(TAG, "SGT gate check for ${walletAddress.take(8)}...")
                    try {
                        val result = SgtChecker.getWalletSgtInfo(walletAddress, rpcUrl)
                        result.fold(
                            onSuccess = { info ->
                                if (info.hasSgt) {
                                    Log.d(TAG, "SGT verified: Seeker #${info.memberNumber}")
                                    GeoAnalyticsService.track(GeoAnalyticsService.Events.SGT_VERIFIED)
                                    prefs.setSgtStatus(true, info.memberNumber, info.sgtMintAddress)
                                    sgtCheckState = SgtCheckState.Verified
                                } else {
                                    Log.w(TAG, "No SGT found")
                                    GeoAnalyticsService.track(GeoAnalyticsService.Events.SGT_NOT_FOUND)
                                    sgtCheckState = SgtCheckState.NoSgt
                                }
                            },
                            onFailure = { e ->
                                Log.e(TAG, "SGT check failed: ${e.message}", e)
                                if (prefs.hasSgt()) {
                                    sgtCheckState = SgtCheckState.Verified
                                } else {
                                    sgtCheckState = SgtCheckState.Error(e.message ?: "Unknown error")
                                }
                            }
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "SGT check exception: ${e.message}", e)
                        if (prefs.hasSgt()) {
                            sgtCheckState = SgtCheckState.Verified
                        } else {
                            sgtCheckState = SgtCheckState.Error(e.message ?: "Unknown error")
                        }
                    }
                }
            }

            SgtCheckState.Verified -> {
                LaunchedEffect(Unit) {
                    GeoAnalyticsService.track(GeoAnalyticsService.Events.APP_OPEN)
                    // Schedule daily check-in reminder worker
                    try {
                        val workRequest = PeriodicWorkRequestBuilder<DailyCheckInWorker>(
                            24, TimeUnit.HOURS
                        ).build()
                        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                            "daily_check_in_reminder",
                            ExistingPeriodicWorkPolicy.KEEP,
                            workRequest
                        )
                    } catch (_: Exception) { }
                    // Schedule widget refresh worker
                    try {
                        val widgetWork = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
                            30, TimeUnit.MINUTES
                        ).build()
                        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                            "widget_refresh",
                            ExistingPeriodicWorkPolicy.KEEP,
                            widgetWork
                        )
                    } catch (_: Exception) { }
                    // Schedule tier change notification worker
                    try {
                        val tierWork = PeriodicWorkRequestBuilder<TierChangeWorker>(
                            6, TimeUnit.HOURS
                        ).build()
                        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                            "tier_change_check",
                            ExistingPeriodicWorkPolicy.KEEP,
                            tierWork
                        )
                    } catch (_: Exception) { }
                }
                AppNavigation(
                    walletAddress = walletAddress,
                    rpcUrl = rpcUrl,
                    isGuestMode = false,
                    onDisconnect = {
                        GeoAnalyticsService.track(GeoAnalyticsService.Events.WALLET_DISCONNECTED)
                        prefs.disconnectWallet()
                        isWalletConnected = false
                        walletAddress = ""
                        sgtCheckState = SgtCheckState.Idle
                    },
                    onThemeChanged = onThemeChanged,
                    currentThemeMode = currentThemeMode,
                    activityResultSender = activityResultSender
                )
            }

            SgtCheckState.NoSgt -> {
                NoSgtScreen(
                    onDisconnect = {
                        prefs.disconnectWallet()
                        isWalletConnected = false
                        walletAddress = ""
                        sgtCheckState = SgtCheckState.Idle
                    }
                )
            }

            is SgtCheckState.Error -> {
                SgtErrorScreen(
                    message = (sgtCheckState as SgtCheckState.Error).message,
                    onRetry = { sgtCheckState = SgtCheckState.Checking },
                    onDisconnect = {
                        prefs.disconnectWallet()
                        isWalletConnected = false
                        walletAddress = ""
                        sgtCheckState = SgtCheckState.Idle
                    }
                )
            }
        }
    }
}

sealed class SgtCheckState {
    object Idle : SgtCheckState()
    object Checking : SgtCheckState()
    object Verified : SgtCheckState()
    object GuestMode : SgtCheckState()
    object NoSgt : SgtCheckState()
    data class Error(val message: String) : SgtCheckState()
}

@Composable
private fun SgtCheckingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(color = SeekerBlue, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Verifying Genesis Token via Seed Vault...",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun NoSgtScreen(onDisconnect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = SeekerRed
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "No Seeker Genesis Token Found",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "This app is exclusively for Seeker device owners. " +
                "Connect a wallet that holds a Seeker Genesis Token (SGT) to continue.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Disconnect & Try Another Wallet")
        }
    }
}

@Composable
private fun SgtErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = SeekerRed
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Verification Error",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = SeekerBlue),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry Verification")
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Filled.LinkOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Disconnect Wallet")
        }
    }
}

private const val TAG = "SeekerVerify"
