package com.seekerverify.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.seekerverify.app.ui.theme.GlassBorder
import com.seekerverify.app.ui.theme.GlassBorderLight
import com.seekerverify.app.ui.theme.GlassHighlight
import com.seekerverify.app.ui.theme.GlassSurface
import com.seekerverify.app.ui.theme.GlassSurfaceLight
import com.seekerverify.app.ui.theme.GlassWhite

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    content: @Composable () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(cornerRadius)

    val baseBg = if (isDark) GlassSurface else GlassSurfaceLight
    val overlay = if (isDark) GlassWhite else Color(0x08000000)
    val borderColor = if (isDark) GlassBorder else GlassBorderLight
    val highlightColor = if (isDark) GlassHighlight else Color(0x05000000)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(baseBg)
            .background(overlay)
            .border(width = 1.dp, color = borderColor, shape = shape)
    ) {
        // Top-edge highlight shine
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(highlightColor, Color.Transparent),
                            startY = 0f,
                            endY = 80f
                        )
                    )
            )
            // Content
            content()
        }
    }
}
