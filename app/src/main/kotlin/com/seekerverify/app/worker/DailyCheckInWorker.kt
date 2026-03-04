package com.seekerverify.app.worker

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.seekerverify.app.data.AppPreferences
import com.seekerverify.app.service.NotificationService
import java.time.LocalDate

/**
 * WorkManager worker that fires a check-in reminder notification
 * if the user has an active streak and hasn't checked in today.
 */
class DailyCheckInWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val prefs = AppPreferences(applicationContext)

            // Only notify if user has notifications enabled
            if (!prefs.isNotificationsEnabled()) return Result.success()

            // Only notify if there's an active streak
            val streak = prefs.getCheckInStreak()
            if (streak.currentStreak <= 0) return Result.success()

            // Only notify if they haven't checked in today
            val today = LocalDate.now().toString()
            if (streak.lastCheckInDate == today) return Result.success()

            // Fire the notification
            NotificationService.createChannel(applicationContext)
            NotificationService.showCheckInReminder(applicationContext)

            Log.d("DailyCheckInWorker", "Check-in reminder sent (streak: ${streak.currentStreak})")
            Result.success()
        } catch (e: Exception) {
            Log.e("DailyCheckInWorker", "Worker error: ${e.message}", e)
            Result.success() // Don't retry — notifications are best-effort
        }
    }
}
