package com.seekerverify.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.seekerverify.app.MainActivity
import com.seekerverify.app.R

class SeekerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        private const val WIDGET_PREFS = "widget_data"
        private const val KEY_TIER = "widget_tier"
        private const val KEY_SOL = "widget_sol"
        private const val KEY_SKR = "widget_skr"
        private const val KEY_UPDATED = "widget_updated"

        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            val tier = prefs.getString(KEY_TIER, "--") ?: "--"
            val sol = prefs.getString(KEY_SOL, "--") ?: "--"
            val skr = prefs.getString(KEY_SKR, "--") ?: "--"
            val updated = prefs.getString(KEY_UPDATED, "Tap to open") ?: "Tap to open"

            val views = RemoteViews(context.packageName, R.layout.widget_seeker)
            views.setTextViewText(R.id.widget_tier, tier)
            views.setTextViewText(R.id.widget_sol_balance, sol)
            views.setTextViewText(R.id.widget_skr_balance, skr)
            views.setTextViewText(R.id.widget_updated, updated)

            // Click opens app
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun writeWidgetData(
            context: Context,
            tier: String,
            solBalance: String,
            skrBalance: String
        ) {
            val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            prefs.edit()
                .putString(KEY_TIER, tier)
                .putString(KEY_SOL, solBalance)
                .putString(KEY_SKR, skrBalance)
                .putString(KEY_UPDATED, "Updated $timeStr")
                .apply()

            // Trigger all widget updates
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                android.content.ComponentName(context, SeekerWidgetProvider::class.java)
            )
            for (id in ids) {
                updateWidget(context, mgr, id)
            }
        }
    }
}
