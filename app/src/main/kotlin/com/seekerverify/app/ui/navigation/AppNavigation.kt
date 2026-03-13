package com.seekerverify.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.seekerverify.app.ui.theme.GlassSurface
import com.seekerverify.app.ui.theme.GlassSurfaceLight
import com.seekerverify.app.ui.theme.SeekerBlue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.ui.util.hapticTap
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.seekerverify.app.ui.screens.CommunityScreen
import com.seekerverify.app.ui.screens.IdentityScreen
import com.seekerverify.app.ui.screens.PortfolioScreen
import com.seekerverify.app.ui.screens.PredictorScreen
import com.seekerverify.app.ui.screens.SettingsScreen
import com.seekerverify.app.ui.viewmodel.CommunityViewModel
import com.seekerverify.app.ui.viewmodel.IdentityViewModel
import com.seekerverify.app.ui.viewmodel.PortfolioViewModel
import com.seekerverify.app.ui.viewmodel.PredictorViewModel
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

@Composable
fun AppNavigation(
    walletAddress: String,
    rpcUrl: String,
    isGuestMode: Boolean = false,
    onDisconnect: () -> Unit,
    onConnectWallet: () -> Unit = onDisconnect,
    onThemeChanged: (String) -> Unit = {},
    currentThemeMode: String = "dark",
    activityResultSender: ActivityResultSender? = null,
    onShowOnboarding: () -> Unit = {}
) {
    val view = LocalView.current
    val context = LocalContext.current
    val prefs = remember { AppPreferences(context) }

    val navController = rememberNavController()
    val screens = listOf(
        Screen.Identity,
        Screen.Predictor,
        Screen.Portfolio,
        Screen.Community,
        Screen.Settings
    )

    // Pre-create ViewModels at navigation level for auto-loading
    val identityVm: IdentityViewModel = viewModel()
    val portfolioVm: PortfolioViewModel = viewModel()
    val communityVm: CommunityViewModel = viewModel()
    val predictorVm: PredictorViewModel = viewModel()

    // Auto-load all data on app open
    LaunchedEffect(walletAddress) {
        if (!isGuestMode && walletAddress.isNotEmpty()) {
            portfolioVm.loadCachedPortfolio() // instant from device storage
            communityVm.loadCachedCommunity() // instant from device storage
            identityVm.loadIdentity(walletAddress, rpcUrl)
            portfolioVm.loadPortfolio(walletAddress, rpcUrl)
            communityVm.loadCommunity(walletAddress, rpcUrl)
            predictorVm.runPrediction(walletAddress, rpcUrl) // populates widget tier
        }
    }

    val isDark = isSystemInDarkTheme()
    val navBarColor = if (isDark) GlassSurface else GlassSurfaceLight

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = navBarColor
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, style = MaterialTheme.typography.labelSmall) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            view.hapticTap(prefs)
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = SeekerBlue,
                            selectedTextColor = SeekerBlue,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = SeekerBlue.copy(alpha = 0.12f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (isGuestMode) Screen.Predictor.route else Screen.Identity.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Identity.route) {
                IdentityScreen(
                    walletAddress = walletAddress,
                    rpcUrl = rpcUrl,
                    isGuestMode = isGuestMode,
                    onConnectWallet = onConnectWallet,
                    viewModel = identityVm,
                    activityResultSender = activityResultSender
                )
            }
            composable(Screen.Portfolio.route) {
                PortfolioScreen(
                    walletAddress = walletAddress,
                    rpcUrl = rpcUrl,
                    isGuestMode = isGuestMode,
                    onConnectWallet = onConnectWallet,
                    viewModel = portfolioVm
                )
            }
            composable(Screen.Predictor.route) {
                PredictorScreen(
                    walletAddress = walletAddress,
                    rpcUrl = rpcUrl,
                    isGuestMode = isGuestMode,
                    onConnectWallet = onConnectWallet,
                    activityResultSender = activityResultSender,
                    viewModel = predictorVm
                )
            }
            composable(Screen.Community.route) {
                CommunityScreen(
                    walletAddress = walletAddress,
                    rpcUrl = rpcUrl,
                    isGuestMode = isGuestMode,
                    onConnectWallet = onConnectWallet,
                    viewModel = communityVm
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    walletAddress = walletAddress,
                    isGuestMode = isGuestMode,
                    onDisconnect = onDisconnect,
                    onConnectWallet = onConnectWallet,
                    onThemeChanged = onThemeChanged,
                    currentThemeMode = currentThemeMode,
                    onShowOnboarding = onShowOnboarding
                )
            }
        }
    }
}
