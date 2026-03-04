package com.seekerverify.app.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/**
 * Generates a 1080x1350 (4:5) share card PNG with prediction results.
 */
object ShareCardGenerator {

    fun generateAndShare(
        context: Context,
        tierName: String,
        compositeScore: Double,
        percentile: Double,
        confidence: String?
    ) {
        val bitmap = generateCard(tierName, compositeScore, percentile, confidence)
        val file = saveToCacheDir(context, bitmap)
        shareImage(context, file)
    }

    private fun generateCard(
        tierName: String,
        compositeScore: Double,
        percentile: Double,
        confidence: String?
    ): Bitmap {
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Background gradient
        val bgPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                Color.parseColor("#0D1B2A"), Color.parseColor("#1B2838"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        // Accent gradient line at top
        val accentPaint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, width.toFloat(), 0f,
                Color.parseColor("#14F195"), Color.parseColor("#9945FF"),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), 6f, accentPaint)

        // Title
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0E0")
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SEEKER VERIFY", width / 2f, 180f, titlePaint)

        // Subtitle
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#808080")
            textSize = 32f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Season 2 Airdrop Prediction", width / 2f, 230f, subPaint)

        // Tier name (large)
        val tierPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = getTierColor(tierName)
            textSize = 96f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(tierName.uppercase(), width / 2f, 480f, tierPaint)

        // Score circle
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#14F195")
            style = Paint.Style.STROKE
            strokeWidth = 8f
        }
        val circleRect = RectF(
            width / 2f - 120f, 560f,
            width / 2f + 120f, 800f
        )
        canvas.drawArc(circleRect, -90f, (compositeScore / 100f * 360f).toFloat(), false, circlePaint)

        // Score number inside circle
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 72f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(String.format("%.0f", compositeScore), width / 2f, 700f, scorePaint)

        val scoreLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#808080")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SCORE", width / 2f, 740f, scoreLabel)

        // Percentile
        val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#14F195")
            textSize = 44f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Top ${String.format("%.1f", 100 - percentile)}%", width / 2f, 900f, pctPaint)

        val pctLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#808080")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("of all Seekers", width / 2f, 940f, pctLabel)

        // Confidence
        confidence?.let {
            val confPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#A0A0A0")
                textSize = 32f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Confidence: $it", width / 2f, 1010f, confPaint)
        }

        // Branding
        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#505050")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Seeker Verify \u2022 SeekerVerify.app", width / 2f, 1200f, brandPaint)

        // Disclaimer
        val disclaimerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#404040")
            textSize = 20f
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Speculative estimate. Not financial advice.", width / 2f, 1280f, disclaimerPaint)

        // Bottom accent line
        canvas.drawRect(0f, height - 6f, width.toFloat(), height.toFloat(), accentPaint)

        return bitmap
    }

    private fun getTierColor(tierName: String): Int {
        return when (tierName.lowercase()) {
            "sovereign" -> Color.parseColor("#FFD700")
            "luminary" -> Color.parseColor("#FF6B35")
            "vanguard" -> Color.parseColor("#14F195")
            "prospector" -> Color.parseColor("#9945FF")
            "scout" -> Color.parseColor("#4FC3F7")
            else -> Color.WHITE
        }
    }

    private fun saveToCacheDir(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "share_cards")
        dir.mkdirs()
        val file = File(dir, "prediction_card.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        return file
    }

    private fun shareImage(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "My Season 2 Airdrop Prediction from Seeker Verify #SeekerVerify #SolanaSeeker")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Prediction"))
    }
}
