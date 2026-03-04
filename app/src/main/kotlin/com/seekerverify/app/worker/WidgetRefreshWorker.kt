package com.seekerverify.app.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.seekerverify.app.AppConfig
import com.seekerverify.app.R
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.rpc.SolRpcClient
import com.seekerverify.app.rpc.SkrRpcClient
import com.seekerverify.app.rpc.StakingRpcClient
import com.seekerverify.app.widget.SeekerWidgetProvider

class WidgetRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val prefs = AppPreferences(applicationContext)
            val walletAddress = prefs.getWalletAddress() ?: return Result.success()

            val heliusApiKey = applicationContext.getString(R.string.helius_api_key)
            val rpcProvider = prefs.getRpcProvider()
            val rpcUrl = when {
                rpcProvider == "helius" && heliusApiKey.isNotEmpty() -> AppConfig.Rpc.heliusUrl(heliusApiKey)
                else -> AppConfig.Rpc.PUBLIC_MAINNET
            }

            var solStr = "--"
            var skrStr = "--"

            try {
                SolRpcClient.getSolBalance(walletAddress, rpcUrl).onSuccess { info ->
                    solStr = String.format("%.2f", info.solBalance + info.stakedSol)
                }
            } catch (_: Exception) { }

            try {
                val liquidResult = SkrRpcClient.getSkrBalance(walletAddress, rpcUrl)
                val stakingResult = StakingRpcClient.getStakingInfo(walletAddress, rpcUrl)
                var total = 0.0
                liquidResult.onSuccess { total += it.displayAmount }
                stakingResult.onSuccess { total += it.stakedDisplay + it.cooldownDisplay }
                if (total > 0) skrStr = String.format("%.0f", total)
            } catch (_: Exception) { }

            // Read cached tier
            val widgetPrefs = applicationContext.getSharedPreferences("widget_data", Context.MODE_PRIVATE)
            val tier = widgetPrefs.getString("widget_tier", "--") ?: "--"

            SeekerWidgetProvider.writeWidgetData(
                applicationContext,
                tier = tier,
                solBalance = solStr,
                skrBalance = skrStr
            )

            Result.success()
        } catch (e: Exception) {
            Log.e("WidgetRefreshWorker", "Failed to refresh widget: ${e.message}")
            Result.retry()
        }
    }
}
