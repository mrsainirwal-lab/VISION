package com.vision.assistant

import android.graphics.Paint
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class VisionState { IDLE, LISTENING, SPEAKING }

const val PARTICLE_COUNT = 100_000

// Points on a sphere. Later, other shapes can be added here (e.g. hand-driven shapes).
object ParticleShape {
    val sphere: FloatArray by lazy { buildSphere(PARTICLE_COUNT) }

    private fun buildSphere(n: Int): FloatArray {
        val out = FloatArray(n * 3)
        val golden = PI * (3.0 - sqrt(5.0))
        for (i in 0 until n) {
            val y = 1.0 - (i + 0.5) / n * 2.0
            val r = sqrt(1.0 - y * y)
            val theta = golden * i
            out[i * 3] = (cos(theta) * r).toFloat()
            out[i * 3 + 1] = y.toFloat()
            out[i * 3 + 2] = (sin(theta) * r).toFloat()
        }
        return out
    }
}

@Composable
fun HologramOrb(state: VisionState, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orb")

    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "spin"
    )

    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val color = when (state) {
        VisionState.SPEAKING -> Color(0xFFFF3B3B)
        VisionState.LISTENING -> Color(0xFF3BFF9B)
        VisionState.IDLE -> Color(0xFF3B9BFF)
    }

    // Reused buffers so we don't create new arrays every frame
    val screenPoints = remember { FloatArray(PARTICLE_COUNT * 2) }
    val paint = remember {
        Paint().apply {
            isAntiAlias = false
            strokeCap = Paint.Cap.ROUND
        }
    }

    Canvas(modifier = modifier) {
        val src = ParticleShape.sphere
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f * 0.85f * pulse

        val cosY = cos(spin)
        val sinY = sin(spin)
        val tilt = 0.35f
        val cosX = cos(tilt)
        val sinX = sin(tilt)

        for (i in 0 until PARTICLE_COUNT) {
            val x = src[i * 3]
            val y = src[i * 3 + 1]
            val z = src[i * 3 + 2]

            // Rotate around Y (spin)
            val x1 = x * cosY + z * sinY
            val z1 = -x * sinY + z * cosY

            // Tilt around X
            val y2 = y * cosX - z1 * sinX
            val z2 = y * sinX + z1 * cosX

            // Simple perspective: points closer to the viewer look a bit bigger
            val persp = 2f / (2f + z2)

            screenPoints[i * 2] = cx + x1 * radius * persp
            screenPoints[i * 2 + 1] = cy + y2 * radius * persp
        }

        paint.color = color.toArgb()
        paint.alpha = (200 + 55 * (pulse - 0.92f) / 0.16f).toInt().coerceIn(0, 255)
        paint.strokeWidth = 3f

        drawContext.canvas.nativeCanvas.drawPoints(screenPoints, 0, PARTICLE_COUNT * 2, paint)
    }
}