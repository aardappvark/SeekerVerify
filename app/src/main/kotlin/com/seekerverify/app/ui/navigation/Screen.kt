package com.seekerverify.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Identity  : Screen("identity",  "Identity",  Icons.Filled.Badge)
    object Portfolio : Screen("portfolio", "Portfolio", Icons.Filled.AccountBalance)
    object Predictor : Screen("predictor", "Predictor", Icons.AutoMirrored.Filled.TrendingUp)
    object Community : Screen("community", "Community", Icons.Filled.Groups)
    object Settings  : Screen("settings",  "Settings",  Icons.Filled.Settings)
}
