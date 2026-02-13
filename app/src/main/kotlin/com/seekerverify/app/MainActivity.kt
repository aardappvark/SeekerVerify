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
import com.seekerverify.app.ui.navigation.AppNavigation
import com.seekerverify.app.ui.screens.WalletConnectScreen
import com.seekerverify.app.ui.theme.SeekerBlue
import com.seekerverify.app.ui.theme.SeekerRed
import com.seekerverify.app.ui.theme.SeekerVerifyTheme
import com.seekerverify.app.wallet.WalletManager
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var activityResultSender: ActivityResultSender

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityResultSender = ActivityResultSender(this)

        setContent {
            SeekerVerifyTheme {
                SeekerVerifyApp(activityResultSender)
            }
        }
    }
}

@Composable
fun SeekerVerifyApp(activityResultSender: ActivityResultSender) {
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }
    val scope = rememberCoroutineScope()

    var isWalletConnected by remember { mutableStateOf(prefs.isWalletConnected()) }
    var walletAddress by remember { mutableStateOf(prefs.getWalletAddress() ?: "") }
    var isConnecting by remember { mutableStateOf(false) }
    var connectError by remember { mutableStateOf<String?>(null) }

    // SGT Gate state
    var sgtCheckState by remember { mutableStateOf<SgtCheckState>(SgtCheckState.Idle) }

    // Determine RPC URL based on user preference
    val heliusApiKey = context.getString(R.string.helius_api_key)
    val rpcProvider = prefs.getRpcProvider()
    val rpcUrl = when {
        rpcProvider == "helius" && heliusApiKey.isNotEmpty() -> AppConfig.Rpc.heliusUrl(heliusApiKey)
        else -> AppConfig.Rpc.PUBLIC_MAINNET
    }

    if (!isWalletConnected) {
        // Show wallet connect screen
        WalletConnectScreen(
            onConnect = {
                isConnecting = true
                connectError = null
                scope.launch {
                    val result = WalletManager.signIn(activityResultSender)
                    result.fold(
                        onSuccess = { connectResult ->
                            Log.d(TAG, "Wallet signed in: ${connectResult.publicKeyBase58.take(8)}...")
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
                    isConnecting = false
                }
            },
            isConnecting = isConnecting,
            errorMessage = connectError
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
                                    prefs.setSgtStatus(true, info.memberNumber, info.sgtMintAddress)
                                    sgtCheckState = SgtCheckState.Verified
                                } else {
                                    Log.w(TAG, "No SGT found")
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
                AppNavigation(
                    walletAddress = walletAddress,
                    rpcUrl = rpcUrl,
                    onDisconnect = {
                        prefs.disconnectWallet()
                        isWalletConnected = false
                        walletAddress = ""
                        sgtCheckState = SgtCheckState.Idle
                    }
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
            text = "Verifying Seeker Genesis Token...",
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
