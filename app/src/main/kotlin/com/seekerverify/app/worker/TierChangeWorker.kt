package com.seekerverify.app.worker

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.service.NotificationService

/**
 * WorkManager worker that fires a notification when the user's predicted
 * Season 2 airdrop tier changes between prediction runs.
 */
class TierChangeWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val prefs = AppPreferences(applicationContext)

            if (!prefs.isNotificationsEnabled()) return Result.success()
            if (!prefs.hasPrediction()) return Result.success()

            val cached = prefs.getPredictionCache() ?: return Result.success()
            val currentTier = cached.predictedTierName

            val lastNotified = prefs.getLastNotifiedTier()
            if (lastNotified == null) {
                // First run — seed with current tier, don't notify
                prefs.setLastNotifiedTier(currentTier)
                return Result.success()
            }

            if (currentTier != lastNotified) {
                NotificationService.createChannel(applicationContext)
                NotificationService.showTierChangeNotification(applicationContext, currentTier)
                prefs.setLastNotifiedTier(currentTier)
                Log.d("TierChangeWorker", "Tier changed: $lastNotified → $currentTier")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e("TierChangeWorker", "Worker error: ${e.message}", e)
            Result.success()
        }
    }
}
