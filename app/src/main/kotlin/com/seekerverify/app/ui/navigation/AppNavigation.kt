package com.seekerverify.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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

@Composable
fun AppNavigation(
    walletAddress: String,
    rpcUrl: String,
    onDisconnect: () -> Unit
) {
    val navController = rememberNavController()
    val screens = listOf(
        Screen.Identity,
        Screen.Portfolio,
        Screen.Predictor,
        Screen.Community,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title, style = MaterialTheme.typography.labelSmall) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Identity.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Identity.route) {
                IdentityScreen(walletAddress = walletAddress, rpcUrl = rpcUrl)
            }
            composable(Screen.Portfolio.route) {
                PortfolioScreen(walletAddress = walletAddress, rpcUrl = rpcUrl)
            }
            composable(Screen.Predictor.route) {
                PredictorScreen(walletAddress = walletAddress, rpcUrl = rpcUrl)
            }
            composable(Screen.Community.route) {
                CommunityScreen(walletAddress = walletAddress, rpcUrl = rpcUrl)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    walletAddress = walletAddress,
                    onDisconnect = onDisconnect
                )
            }
        }
    }
}
