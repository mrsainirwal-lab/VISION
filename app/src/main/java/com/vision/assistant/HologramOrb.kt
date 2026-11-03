package com.vision.assistant

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

enum class VisionState { IDLE, LISTENING, SPEAKING }

@Composable
fun HologramOrb(state: VisionState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")

    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12000, easing = LinearEasing)),
        label = "spin"
    )

    // Blue while idle, red while Vision is speaking (same as the desktop version)
    val base = if (state == VisionState.SPEAKING) Color(0xFFFF3B3B) else Color(0xFF3B9BFF)

    Canvas(modifier = modifier) {
        val c = center
        val r = size.minDimension / 2f * 0.6f * pulse

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(base.copy(alpha = 0.5f), Color.Transparent),
                center = c,
                radius = r * 1.8f
            ),
            radius = r * 1.8f,
            center = c
        )
        drawCircle(color = base.copy(alpha = 0.25f), radius = r, center = c)
        drawCircle(color = base, radius = r, center = c, style = Stroke(width = 3f))

        for (i in 0..2) {
            rotate(degrees = spin + i * 60f, pivot = c) {
                drawOval(
                    color = base.copy(alpha = 0.6f),
                    topLeft = Offset(c.x - r * 1.4f, c.y - r * 0.45f),
                    size = Size(r * 2.8f, r * 0.9f),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}
