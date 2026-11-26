package io.github.re4pr.kidslauncher.skin

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import io.github.re4pr.kidslauncher.model.ParticleStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun AnimatedSkyBackground(
    palette: ResolvedSkinPalette,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "skyParticles")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particlePhase",
    )
    val twinkle by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "twinkleAlpha",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.skyTop, palette.skyMid, palette.skyBottom)
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val count = 18
            for (i in 0 until count) {
                val seedX = ((i * 73 + 19) % 100) / 100f
                val speed = if (i % 2 == 0) 1f else 0.65f
                val rawY = ((i * 37) / 100f + phase * speed) % 1f
                val yFrac = if (palette.particleStyle == ParticleStyle.OCEAN_BUBBLES) {
                    1f - rawY
                } else {
                    rawY
                }
                val sway = sin((phase * 2f * PI + i).toFloat()) * 24f
                val center = Offset(
                    x = (seedX * size.width + sway).coerceIn(0f, size.width),
                    y = yFrac * size.height,
                )
                val radius = 10f + (i % 4) * 6f
                val alpha = if (i % 3 == 0) twinkle else 0.55f

                when (palette.particleStyle) {
                    ParticleStyle.SNOWFLAKES -> drawSnowflake(center, radius, Color.White.copy(alpha = alpha))
                    ParticleStyle.RAINBOW_SPARKLES -> {
                        val sparkleColor = when (i % 3) {
                            0 -> Color(0xFFFFF3B0)
                            1 -> palette.accentSecondary
                            else -> Color.White
                        }
                        drawSparkleStar(center, radius, sparkleColor.copy(alpha = alpha))
                    }
                    ParticleStyle.CROWNS_AND_HEARTS -> {
                        if (i % 2 == 0) {
                            drawMiniCrown(center, radius * 1.1f, Color(0xFFFFE082).copy(alpha = alpha))
                        } else {
                            drawSparkleStar(center, radius * 0.9f, Color(0xFFFFC1E3).copy(alpha = alpha))
                        }
                    }
                    ParticleStyle.OCEAN_BUBBLES -> {
                        drawCircle(
                            color = Color.White.copy(alpha = alpha * 0.6f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 2.5f),
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = alpha * 0.7f),
                            radius = radius * 0.25f,
                            center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
                        )
                    }
                    ParticleStyle.STARLIGHT -> {
                        drawSparkleStar(center, radius * 0.85f, Color(0xFFFFE699).copy(alpha = alpha))
                    }
                }
            }
            if (palette.particleStyle == ParticleStyle.STARLIGHT) {
                // Gentle crescent moon in upper-right sky
                val moonCenter = Offset(size.width * 0.86f, size.height * 0.12f)
                drawCircle(color = Color(0xFFFFF1A8).copy(alpha = 0.9f), radius = 38f, center = moonCenter)
                drawCircle(
                    color = palette.skyTop,
                    radius = 32f,
                    center = Offset(moonCenter.x - 14f, moonCenter.y - 8f),
                )
            }
        }
        content()
    }
}

private fun DrawScope.drawSnowflake(center: Offset, radius: Float, color: Color) {
    for (arm in 0 until 6) {
        val angle = (arm * PI / 3.0).toFloat()
        val end = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
        drawLine(color = color, start = center, end = end, strokeWidth = 2.4f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawSparkleStar(center: Offset, radius: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(path, color = color)
}

private fun DrawScope.drawMiniCrown(center: Offset, sizePx: Float, color: Color) {
    val half = sizePx * 0.75f
    val path = Path().apply {
        moveTo(center.x - half, center.y + half * 0.5f)
        lineTo(center.x - half, center.y - half * 0.4f)
        lineTo(center.x - half * 0.35f, center.y + 1f)
        lineTo(center.x, center.y - half * 0.75f)
        lineTo(center.x + half * 0.35f, center.y + 1f)
        lineTo(center.x + half, center.y - half * 0.4f)
        lineTo(center.x + half, center.y + half * 0.5f)
        close()
    }
    drawPath(path, color = color)
}
