package com.seekerverify.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seekerverify.app.model.AirdropTier
import com.seekerverify.app.ui.theme.SeekerGold
import com.seekerverify.app.ui.theme.TierLuminary
import com.seekerverify.app.ui.theme.TierProspector
import com.seekerverify.app.ui.theme.TierScout
import com.seekerverify.app.ui.theme.TierSovereign
import com.seekerverify.app.ui.theme.TierVanguard
import kotlinx.coroutines.delay

private val CYCLE_TIERS = listOf(
    AirdropTier.SCOUT,
    AirdropTier.PROSPECTOR,
    AirdropTier.VANGUARD,
    AirdropTier.LUMINARY,
    AirdropTier.SOVEREIGN
)

/**
 * Animated tier reveal with cycling text, scale-up pulse, and glow burst.
 * Only plays once per fresh prediction run.
 */
@Composable
fun TierRevealAnimation(
    predictedTier: AirdropTier,
    onRevealComplete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var phase by remember { mutableStateOf(RevealPhase.CYCLING) }
    var cycleIndex by remember { mutableIntStateOf(0) }
    val view = LocalView.current

    // Phase 1: Rapid cycling through tier names
    LaunchedEffect(Unit) {
        // Fast cycling (80ms each, 15 cycles ~1.2s)
        repeat(15) { i ->
            cycleIndex = i % CYCLE_TIERS.size
            delay(80L + i * 8L) // Gradually slow down
        }
        // Slower cycling, converging on actual tier
        val targetIdx = CYCLE_TIERS.indexOf(predictedTier).coerceAtLeast(0)
        repeat(5) { i ->
            cycleIndex = (targetIdx + CYCLE_TIERS.size - 4 + i) % CYCLE_TIERS.size
            delay(200L + i * 60L)
        }
        cycleIndex = targetIdx
        phase = RevealPhase.LOCKED
        delay(100)
        // Haptic on reveal
        try {
            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        } catch (_: Exception) { }
        phase = RevealPhase.GLOW
        delay(1200)
        phase = RevealPhase.DONE
        onRevealComplete()
    }

    val displayTier = when (phase) {
        RevealPhase.CYCLING -> CYCLE_TIERS[cycleIndex]
        else -> predictedTier
    }

    val tierColor = tierToColor(displayTier)

    // Scale animation
    val scale by animateFloatAsState(
        targetValue = when (phase) {
            RevealPhase.CYCLING -> 0.95f
            RevealPhase.LOCKED -> 1.15f
            RevealPhase.GLOW -> 1.0f
            RevealPhase.DONE -> 1.0f
        },
        animationSpec = tween(
            durationMillis = when (phase) {
                RevealPhase.LOCKED -> 200
                RevealPhase.GLOW -> 600
                else -> 100
            },
            easing = FastOutSlowInEasing
        ),
        label = "tierScale"
    )

    // Glow alpha
    val glowAlpha by animateFloatAsState(
        targetValue = when (phase) {
            RevealPhase.GLOW -> 1f
            RevealPhase.DONE -> 0f
            else -> 0f
        },
        animationSpec = tween(
            durationMillis = if (phase == RevealPhase.DONE) 800 else 300
        ),
        label = "glowAlpha"
    )

    // Text alpha — flickers during cycling
    val textAlpha = when (phase) {
        RevealPhase.CYCLING -> if (cycleIndex % 2 == 0) 0.7f else 1f
        else -> 1f
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Glow burst behind text
        if (glowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(80.dp)
                    .alpha(glowAlpha * 0.6f)
                    .clip(RoundedCornerShape(40.dp))
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                tierColor.copy(alpha = 0.4f),
                                tierColor.copy(alpha = 0.1f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .scale(scale)
                .alpha(textAlpha),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = displayTier.displayName.uppercase(),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Black,
                    letterSpacing = 4.sp
                ),
                color = tierColor,
                textAlign = TextAlign.Center
            )
            if (phase == RevealPhase.GLOW || phase == RevealPhase.DONE) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Predicted Tier",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private enum class RevealPhase {
    CYCLING, LOCKED, GLOW, DONE
}

private fun tierToColor(tier: AirdropTier): Color = when (tier) {
    AirdropTier.SOVEREIGN -> TierSovereign
    AirdropTier.LUMINARY -> TierLuminary
    AirdropTier.VANGUARD -> TierVanguard
    AirdropTier.PROSPECTOR -> TierProspector
    AirdropTier.SCOUT -> TierScout
    AirdropTier.DEVELOPER -> SeekerGold
}
