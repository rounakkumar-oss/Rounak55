package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.VoiceState
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGlowListening
import com.example.ui.theme.JarvisGlowSpeaking
import com.example.ui.theme.JarvisGlowThinking
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArcReactorVisualizer(
    voiceState: VoiceState,
    size: Dp = 190.dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ArcReactorAnim")

    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RingRotation"
    )

    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreatheScale"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "CorePulse"
    )

    val primaryColor = when (voiceState) {
        is VoiceState.Listening -> JarvisGlowListening
        is VoiceState.Thinking -> JarvisGlowThinking
        is VoiceState.Speaking -> JarvisGlowSpeaking
        is VoiceState.Idle -> JarvisCyan
    }

    val dynamicWaveLevel = when (voiceState) {
        is VoiceState.Listening -> voiceState.rmsLevel
        is VoiceState.Speaking -> corePulse * 0.45f
        is VoiceState.Thinking -> 0.35f
        is VoiceState.Idle -> 0.1f
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .testTag("arc_reactor_button")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = size / 2),
                onClick = onClick
            )
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val baseRadius = this.size.minDimension / 2 - 12.dp.toPx()

            // 1. Outermost audio wave pulse ring
            val waveRadius = baseRadius * (1f + dynamicWaveLevel * 0.3f)
            drawCircle(
                color = primaryColor.copy(alpha = (0.25f * (1f - dynamicWaveLevel * 0.5f)).coerceIn(0.05f, 0.4f)),
                radius = waveRadius,
                center = center,
                style = Stroke(width = 3.dp.toPx())
            )

            // 2. Outer Rotating Dashed Ring
            val outerRadius = baseRadius * 0.96f
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.15f),
                        primaryColor,
                        primaryColor.copy(alpha = 0.2f),
                        primaryColor
                    ),
                    center = center
                ),
                radius = outerRadius,
                center = center,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f), rotation)
                )
            )

            // 3. Middle Sci-Fi Segment Ticks (Arc Reactor Core Pattern)
            val middleRadius = baseRadius * 0.78f
            val segments = 12
            for (i in 0 until segments) {
                val angleRad = Math.toRadians((i * (360.0 / segments) + rotation.toDouble()))
                val r1 = middleRadius - 8.dp.toPx()
                val r2 = middleRadius + 4.dp.toPx()
                val startX = center.x + (r1 * cos(angleRad)).toFloat()
                val startY = center.y + (r1 * sin(angleRad)).toFloat()
                val endX = center.x + (r2 * cos(angleRad)).toFloat()
                val endY = center.y + (r2 * sin(angleRad)).toFloat()

                drawLine(
                    color = primaryColor.copy(alpha = 0.7f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.5.dp.toPx()
                )
            }

            // 4. Middle Concentric Ring
            drawCircle(
                color = primaryColor.copy(alpha = 0.5f),
                radius = middleRadius,
                center = center,
                style = Stroke(width = 1.8.dp.toPx())
            )

            // 5. Inner Core Glow Circle
            val coreRadius = baseRadius * 0.52f * (if (voiceState is VoiceState.Thinking) corePulse else breatheScale)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.85f),
                        primaryColor.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius * 1.3f
                ),
                radius = coreRadius,
                center = center
            )

            // 6. Center Hub Border
            drawCircle(
                color = primaryColor,
                radius = coreRadius * 0.85f,
                center = center,
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // Center Icon
        when (voiceState) {
            is VoiceState.Listening -> {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Listening",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.28f)
                )
            }
            is VoiceState.Speaking -> {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Speaking",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.28f)
                )
            }
            is VoiceState.Thinking -> {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Thinking",
                    tint = Color.White,
                    modifier = Modifier.size(size * 0.24f)
                )
            }
            is VoiceState.Idle -> {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Tap to speak",
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(size * 0.28f)
                )
            }
        }
    }
}
