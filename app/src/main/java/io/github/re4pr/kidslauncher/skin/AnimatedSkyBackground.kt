package io.github.re4pr.kidslauncher.skin

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.widget.ImageView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import io.github.re4pr.kidslauncher.model.ParticleStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders a living, animated storybook world backdrop:
 * - Supports native animated `.gif` and animated `.webp` assets via Android's [ImageDecoder]
 *   ([AnimatedImageDrawable]) as well as remote/local custom URIs.
 * - Brings panoramic 3D storybook scenes (Ice Palace, Unicorn Kingdom, Royal Palace, Mermaid Lagoon,
 *   and Starry Bedtime Castle) to life with smooth cinemagraph camera pan/zoom, sweeping volumetric
 *   light rays, glowing lanterns/fireflies, and foreground atmospheric motion.
 */
@Composable
fun AnimatedSkyBackground(
    palette: ResolvedSkinPalette,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val infinite = rememberInfiniteTransition(label = "livingWorld")

    // Smooth living camera pan & breathe across the panoramic castle/unicorn landscape
    val cameraPan by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cameraPan",
    )
    val cameraZoom by infinite.animateFloat(
        initialValue = 1.08f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14_000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cameraZoom",
    )
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "particlePhase",
    )
    val glowPulse by infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "glowPulse",
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
        // Layer 1: Scenic 3D Artwork or Native Animated GIF / WebP
        Crossfade(targetState = palette.customSceneUri to palette.sceneAssetPath, label = "sceneSwitch") { (customUri, assetPath) ->
            when {
                customUri.isNotBlank() -> {
                    AsyncImage(
                        model = customUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = cameraZoom
                                scaleY = cameraZoom
                                translationX = cameraPan * 28f
                            },
                    )
                }
                assetPath.endsWith(".gif", ignoreCase = true) || assetPath.endsWith(".webp", ignoreCase = true) -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            ImageView(ctx).apply {
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                runCatching {
                                    val src = ImageDecoder.createSource(ctx.assets, assetPath)
                                    val drawable = ImageDecoder.decodeDrawable(src)
                                    setImageDrawable(drawable)
                                    if (drawable is AnimatedImageDrawable) {
                                        drawable.start()
                                    }
                                }
                            }
                        },
                    )
                }
                assetPath.isNotBlank() -> {
                    val bitmap = remember(assetPath) {
                        runCatching {
                            context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                        }.getOrNull()
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = cameraZoom
                                    scaleY = cameraZoom
                                    translationX = cameraPan * 32f
                                    translationY = cameraPan * -8f
                                },
                        )
                    }
                }
            }
        }

        // Layer 2: Animated Volumetric Sunbeams / Aurora Shimmer + Living Foreground Particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Sweeping magical sunbeams / aurora ribbons across upper sky
            for (beam in 0..2) {
                val beamX = size.width * (0.22f + beam * 0.28f) + sin((phase * 2 * PI + beam).toFloat()) * 60f
                val beamBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.14f * glowPulse),
                        Color.Transparent,
                    ),
                    start = Offset(beamX, 0f),
                    end = Offset(beamX - size.width * 0.18f, size.height * 0.65f),
                )
                val rayPath = Path().apply {
                    moveTo(beamX - 45f, 0f)
                    lineTo(beamX + 65f, 0f)
                    lineTo(beamX - 90f, size.height * 0.68f)
                    lineTo(beamX - 210f, size.height * 0.68f)
                    close()
                }
                drawPath(rayPath, brush = beamBrush)
            }

            // Gentle readability gradient over lower portion so shelves pop cleanly
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.14f),
                    0.36f to Color.Transparent,
                    1.0f to palette.skyTop.copy(alpha = 0.48f),
                )
            )

            // Theme-matched animated foreground magic (snowflakes, floating lanterns, bubbles, fireflies)
            val count = 20
            for (i in 0 until count) {
                val seedX = ((i * 73 + 19) % 100) / 100f
                val speed = if (i % 2 == 0) 1f else 0.62f
                val rawY = ((i * 37) / 100f + phase * speed) % 1f
                val yFrac = when (palette.particleStyle) {
                    ParticleStyle.OCEAN_BUBBLES, ParticleStyle.CROWNS_AND_HEARTS -> 1f - rawY
                    else -> rawY
                }
                val sway = sin((phase * 2f * PI + i).toFloat()) * 28f
                val center = Offset(
                    x = (seedX * size.width + sway).coerceIn(0f, size.width),
                    y = yFrac * size.height,
                )
                val radius = 8f + (i % 4) * 5f
                val alpha = if (i % 3 == 0) glowPulse else 0.52f

                when (palette.particleStyle) {
                    ParticleStyle.SNOWFLAKES -> drawSnowflake(center, radius, Color.White.copy(alpha = alpha))
                    ParticleStyle.RAINBOW_SPARKLES -> {
                        val c = if (i % 2 == 0) Color(0xFFFFF6B8) else Color(0xFFFFB5E8)
                        drawGlowOrb(center, radius * 1.4f, c.copy(alpha = alpha * 0.45f))
                        drawSparkleStar(center, radius, Color.White.copy(alpha = alpha))
                    }
                    ParticleStyle.CROWNS_AND_HEARTS -> {
                        // Warm floating sky-lantern glow orbs & golden sparkles
                        drawGlowOrb(center, radius * 1.8f, Color(0xFFFFD166).copy(alpha = alpha * 0.55f))
                        drawCircle(Color(0xFFFFF3B0).copy(alpha = alpha), radius = radius * 0.45f, center = center)
                    }
                    ParticleStyle.OCEAN_BUBBLES -> {
                        drawCircle(
                            color = Color(0xFFE0FFFF).copy(alpha = alpha * 0.65f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 2.2f),
                        )
                        drawCircle(
                            color = Color.White.copy(alpha = alpha * 0.8f),
                            radius = radius * 0.25f,
                            center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
                        )
                    }
                    ParticleStyle.STARLIGHT -> {
                        // Warm dreamy bedtime fireflies
                        drawGlowOrb(center, radius * 1.6f, Color(0xFFFFE680).copy(alpha = alpha * 0.5f))
                        drawCircle(Color(0xFFFFF8CC).copy(alpha = alpha), radius = radius * 0.32f, center = center)
                    }
                }
            }
        }
        content()
    }
}

private fun DrawScope.drawGlowOrb(center: Offset, radius: Float, color: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color, Color.Transparent),
            center = center,
            radius = radius.coerceAtLeast(2f),
        ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.drawSnowflake(center: Offset, radius: Float, color: Color) {
    for (arm in 0 until 6) {
        val angle = (arm * PI / 3.0).toFloat()
        val end = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
        drawLine(color = color, start = center, end = end, strokeWidth = 2.2f, cap = StrokeCap.Round)
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
