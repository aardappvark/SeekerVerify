package com.seekerverify.app.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.seekerverify.app.model.Achievement
import com.seekerverify.app.model.CheckInStreak
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Backs up check-in streak data to a JSON file in Downloads/ via MediaStore.
 * This file persists across app uninstalls (unlike EncryptedSharedPreferences).
 *
 * On reinstall, the app can restore the streak from this backup file.
 */
object CheckInBackupManager {

    private const val TAG = "SeekerVerify"
    private const val BACKUP_FILENAME = "seekerverify_checkin_backup.json"
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    @Serializable
    data class CheckInBackup(
        val walletAddress: String,
        val streak: CheckInStreak,
        val lastOnChainSignature: String? = null,
        val lastOnChainDate: String? = null,
        val updatedAt: Long = System.currentTimeMillis(),
        // S1/S2 prediction data (raw JSON strings from AppPreferences)
        val season1ResultJson: String? = null,
        val season1AnalysisCacheJson: String? = null,
        val predictionCacheJson: String? = null,
        val predictionHistoryJson: String? = null,
        val transactionHistoryJson: String? = null,
        // Achievement backup with integrity hash
        val unlockedAchievements: List<String>? = null,
        val achievementsHmac: String? = null,
        // User settings that should survive uninstall
        val leaderboardOptedIn: Boolean? = null
    )

    /**
     * Save check-in + prediction data to a backup file in Downloads/.
     * Uses MediaStore on API 29+ (no permissions needed).
     * Includes S1/S2 prediction data if available.
     */
    fun saveBackup(context: Context, walletAddress: String, streak: CheckInStreak,
                   onChainSig: String? = null, onChainDate: String? = null) {
        try {
            val prefs = AppPreferences(context)

            // Read achievements and compute HMAC for integrity
            val achievementNames = prefs.getUnlockedAchievements().map { it.name }
            val hmac = if (achievementNames.isNotEmpty()) {
                computeAchievementsHmac(achievementNames.toSet(), walletAddress)
            } else null

            val backup = CheckInBackup(
                walletAddress = walletAddress,
                streak = streak,
                lastOnChainSignature = onChainSig,
                lastOnChainDate = onChainDate,
                season1ResultJson = prefs.getRawSeason1Result(),
                season1AnalysisCacheJson = prefs.getRawSeason1AnalysisCache(),
                predictionCacheJson = prefs.getRawPredictionCache(),
                predictionHistoryJson = prefs.getRawPredictionHistory(),
                transactionHistoryJson = prefs.getRawTransactionHistory(),
                unlockedAchievements = achievementNames.ifEmpty { null },
                achievementsHmac = hmac,
                leaderboardOptedIn = prefs.isLeaderboardOptedIn()
            )
            val backupJson = json.encodeToString(backup)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // API 29+: Use MediaStore (no permission needed)
                val resolver = context.contentResolver

                // Delete existing backup first
                deleteExistingBackup(context)

                val contentValues = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, BACKUP_FILENAME)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { os ->
                        os.write(backupJson.toByteArray(Charsets.UTF_8))
                    }
                }
                Log.d(TAG, "Check-in backup saved to Downloads/")
            } else {
                // Fallback: write to app external files dir (deleted on uninstall, but better than nothing)
                val dir = context.getExternalFilesDir(null) ?: return
                val file = java.io.File(dir, BACKUP_FILENAME)
                file.writeText(backupJson, Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save check-in backup: ${e.message}")
        }
    }

    /**
     * Restore check-in data from backup file in Downloads/.
     * Returns null if no backup found or wallet doesn't match.
     */
    fun restoreBackup(context: Context, walletAddress: String): CheckInBackup? {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val projection = arrayOf(MediaStore.Downloads._ID)
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(BACKUP_FILENAME)

                resolver.query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    projection, selection, selectionArgs, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                        val uri = android.content.ContentUris.withAppendedId(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                        )
                        resolver.openInputStream(uri)?.use { input ->
                            val backupJson = input.bufferedReader().readText()
                            val backup = json.decodeFromString<CheckInBackup>(backupJson)
                            if (backup.walletAddress == walletAddress) {
                                Log.d(TAG, "Check-in backup restored from Downloads/")
                                return backup
                            } else {
                                Log.d(TAG, "Backup wallet mismatch: ${backup.walletAddress.take(8)} != ${walletAddress.take(8)}")
                            }
                        }
                    }
                }
            } else {
                val dir = context.getExternalFilesDir(null) ?: return null
                val file = java.io.File(dir, BACKUP_FILENAME)
                if (file.exists()) {
                    val backupJson = file.readText(Charsets.UTF_8)
                    val backup = json.decodeFromString<CheckInBackup>(backupJson)
                    if (backup.walletAddress == walletAddress) {
                        return backup
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore check-in backup: ${e.message}")
        }
        return null
    }

    /**
     * Quick save after prediction runs — updates the backup with latest S1/S2 data.
     * Reads current streak from prefs so caller doesn't need to provide it.
     */
    fun savePredictionBackup(context: Context, walletAddress: String) {
        try {
            val prefs = AppPreferences(context)
            val streak = prefs.getCheckInStreak()
            val onChainSig = prefs.getLastOnChainCheckInSignature()
            val onChainDate = prefs.getLastOnChainCheckInDate()
            saveBackup(context, walletAddress, streak, onChainSig, onChainDate)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save prediction backup: ${e.message}")
        }
    }

    /**
     * Restore prediction data (S1/S2) from backup into AppPreferences.
     * Returns true if prediction data was restored.
     */
    fun restorePredictionData(context: Context, walletAddress: String): Boolean {
        val backup = restoreBackup(context, walletAddress) ?: return false
        val hasPredictionData = backup.predictionCacheJson != null ||
            backup.season1ResultJson != null

        if (hasPredictionData) {
            val prefs = AppPreferences(context)
            prefs.restorePredictionData(
                season1ResultJson = backup.season1ResultJson,
                predictionCacheJson = backup.predictionCacheJson,
                season1AnalysisCacheJson = backup.season1AnalysisCacheJson,
                predictionHistoryJson = backup.predictionHistoryJson,
                transactionHistoryJson = backup.transactionHistoryJson
            )
            Log.d(TAG, "Prediction data restored from device backup")
        }
        return hasPredictionData
    }

    /**
     * Restore user settings (like leaderboard opt-in) from device backup.
     * Called on startup after wallet connect to recover preferences after reinstall.
     */
    fun restoreSettings(context: Context, walletAddress: String) {
        val backup = restoreBackup(context, walletAddress) ?: return
        val prefs = AppPreferences(context)
        backup.leaderboardOptedIn?.let { optedIn ->
            prefs.setLeaderboardOptedIn(optedIn)
            Log.d(TAG, "Leaderboard opt-in restored from device backup: $optedIn")
        }
    }

    /**
     * Quick save after settings change — updates backup with latest settings.
     */
    fun saveSettingsBackup(context: Context, walletAddress: String) {
        try {
            val prefs = AppPreferences(context)
            val streak = prefs.getCheckInStreak()
            val onChainSig = prefs.getLastOnChainCheckInSignature()
            val onChainDate = prefs.getLastOnChainCheckInDate()
            saveBackup(context, walletAddress, streak, onChainSig, onChainDate)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save settings backup: ${e.message}")
        }
    }

    /**
     * Quick save after achievement unlock — updates backup with latest achievements.
     */
    fun saveAchievementBackup(context: Context, walletAddress: String) {
        try {
            val prefs = AppPreferences(context)
            val streak = prefs.getCheckInStreak()
            val onChainSig = prefs.getLastOnChainCheckInSignature()
            val onChainDate = prefs.getLastOnChainCheckInDate()
            saveBackup(context, walletAddress, streak, onChainSig, onChainDate)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save achievement backup: ${e.message}")
        }
    }

    /**
     * Restore achievements from device backup.
     * Verifies HMAC integrity before restoring. Returns true if restored.
     */
    fun restoreAchievements(context: Context, walletAddress: String): Boolean {
        val backup = restoreBackup(context, walletAddress) ?: return false
        val names = backup.unlockedAchievements ?: return false
        if (names.isEmpty()) return false

        // Verify HMAC integrity — reject tampered backups
        if (!verifyAchievementsHmac(names, backup.achievementsHmac, walletAddress)) {
            Log.w(TAG, "Achievement backup HMAC verification failed — skipping restore")
            return false
        }

        // Parse achievement names back to enum values
        val achievements = names.mapNotNull {
            try { Achievement.valueOf(it) } catch (_: Exception) { null }
        }.toSet()
        if (achievements.isEmpty()) return false

        // Merge with existing (don't lose any)
        val prefs = AppPreferences(context)
        val existing = prefs.getUnlockedAchievements()
        val merged = existing + achievements
        if (merged.size > existing.size) {
            prefs.saveUnlockedAchievements(merged)
            Log.d(TAG, "Achievements restored from device backup: ${achievements.size} achievements")
            return true
        }
        return false
    }

    /**
     * HMAC-SHA256 of sorted achievement names, keyed by wallet address.
     * Prevents cross-wallet backup theft and casual tampering.
     */
    private fun computeAchievementsHmac(achievements: Set<String>, walletAddress: String): String {
        val payload = achievements.sorted().joinToString(",")
        val keyBytes = "SeekerVerify:$walletAddress".toByteArray(Charsets.UTF_8)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        val hmacBytes = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }

    private fun verifyAchievementsHmac(achievements: List<String>, hmac: String?, walletAddress: String): Boolean {
        if (hmac == null) return false
        val expected = computeAchievementsHmac(achievements.toSet(), walletAddress)
        return expected == hmac
    }

    private fun deleteExistingBackup(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(BACKUP_FILENAME)
                resolver.delete(MediaStore.Downloads.EXTERNAL_CONTENT_URI, selection, selectionArgs)
            }
        } catch (_: Exception) { }
    }
}
