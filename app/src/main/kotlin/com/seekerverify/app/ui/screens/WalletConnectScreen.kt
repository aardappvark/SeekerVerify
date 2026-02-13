package com.seekerverify.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.seekerverify.app.AppConfig
import com.seekerverify.app.ui.theme.SeekerBlue
import com.seekerverify.app.ui.theme.SolanaGreen

@Composable
fun WalletConnectScreen(
    onConnect: () -> Unit,
    isConnecting: Boolean = false,
    errorMessage: String? = null
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
            imageVector = Icons.Filled.Verified,
            contentDescription = "Seeker Verify",
            modifier = Modifier.size(80.dp),
            tint = SeekerBlue
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Seeker Verify",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Your Seeker device companion",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        if (isConnecting) {
            CircularProgressIndicator(color = SeekerBlue)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connecting to wallet...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Button(
                onClick = onConnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SeekerBlue
                )
            ) {
                Text(
                    text = "Connect Wallet",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        errorMessage?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Requires a Seeker Genesis Token (SGT)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Pre-connection consent notice
        Text(
            text = "By connecting, your public wallet address will be stored locally on this device to display on-chain data. No private keys are accessed. No data is sent to external servers beyond Solana RPC endpoints.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        val uriHandler = LocalUriHandler.current
        val baseUrl = AppConfig.Identity.URI

        // Privacy Policy & Terms links
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            androidx.compose.foundation.text.ClickableText(
                text = androidx.compose.ui.text.AnnotatedString("Privacy Policy"),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = SeekerBlue,
                    textAlign = TextAlign.Center
                ),
                onClick = { uriHandler.openUri("$baseUrl/privacy.html") }
            )
            Text(
                text = "  \u2022  ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.foundation.text.ClickableText(
                text = androidx.compose.ui.text.AnnotatedString("Terms of Service"),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = SeekerBlue,
                    textAlign = TextAlign.Center
                ),
                onClick = { uriHandler.openUri("$baseUrl/terms.html") }
            )
        }
    }
}
