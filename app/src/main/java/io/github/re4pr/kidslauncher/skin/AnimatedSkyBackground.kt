package io.github.re4pr.kidslauncher.skin

import android.graphics.BitmapFactory
import android.graphics.Matrix as AndroidMatrix
import android.net.Uri
import android.view.TextureView
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import io.github.re4pr.kidslauncher.model.ParticleStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Rock-steady Living Storybook World Backdrop:
 * - Keeps the panoramic castle/unicorn/mermaid landscape 100% still (zero camera wobble/panning!).
 * - Animates rich, continuous, theme-specific world life directly inside the scene using a
 *   monotonic vsync clock (zero loop resets):
 *   - **Frost Kingdom:** Rippling green-and-violet Aurora Borealis sky curtains, pulsing warm castle
 *     windows, crystal bridge glints, and multi-depth falling snowflakes.
 *   - **Rainbow Unicorn:** Cascading waterfall foam streaks & rising lagoon mist, pulsing double-rainbow
 *     shimmer, fluttering 3D butterflies gliding across the wildflower meadow, and floating dandelion wishes.
 *   - **Royal Princess:** Warm glowing golden sky lanterns rising gently behind the palace, sparkling
 *     courtyard fountain spray, and drifting pink rose petals.
 *   - **Ocean Mermaid:** Sweeping underwater sunbeams, colorful coral fish & sea turtles swimming smoothly
 *     across the reef, glowing giant pearl pulses, and rising bubble streams.
 *   - **Bedtime Kingdom:** Periodic shooting stars across the night sky, twinkling constellations,
 *     and lazy drifting golden fireflies.
 * - Also supports custom `.mp4` / `.webm` video skins via [ExoPlayer].
 */
@Composable
fun AnimatedSkyBackground(
    palette: ResolvedSkinPalette,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Monotonic continuous clock (never resets or jumps!)
    val startNs = remember { System.nanoTime() }
    var timeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNs ->
                timeSec = ((frameNs - startNs) / 1_000_000_000.0).toFloat()
            }
        }
    }

    val butterflyBmp = remember {
        runCatching {
            context.assets.open("puppet/props/butterfly_wings.webp").use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
        }.getOrNull()
    }

    val activeVideoUri: Uri? = remember(palette.customSceneUri, palette.videoAssetPath) {
        when {
            palette.customSceneUri.endsWith(".mp4", ignoreCase = true) ||
                palette.customSceneUri.endsWith(".webm", ignoreCase = true) ->
                Uri.parse(palette.customSceneUri)
            palette.videoAssetPath.isNotBlank() ->
                Uri.parse("asset:///${palette.videoAssetPath.trimStart('/')}")
            else -> null
        }
    }

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            playWhenReady = true
        }
    }

    LaunchedEffect(activeVideoUri) {
        if (activeVideoUri != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(activeVideoUri))
            exoPlayer.prepare()
            exoPlayer.play()
        } else {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (activeVideoUri != null) exoPlayer.play()
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(palette.skyTop, palette.skyMid, palette.skyBottom)
                )
            )
    ) {
        // 1. Rock-Steady Crisp High-Res Master Landscape (ZERO camera movement!)
        Crossfade(targetState = palette.customSceneUri to palette.sceneAssetPath, label = "steadyScene") { (customUri, assetPath) ->
            when {
                customUri.isNotBlank() && activeVideoUri == null -> {
                    AsyncImage(
                        model = customUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                assetPath.isNotBlank() -> {
                    val bmp = remember(assetPath) {
                        runCatching {
                            context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                        }.getOrNull()
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        // Optional custom MP4 skin layer
        if (activeVideoUri != null) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    TextureView(ctx).also { tv ->
                        exoPlayer.setVideoTextureView(tv)
                        val applyCenterCrop = { videoW: Int, videoH: Int ->
                            val viewW = tv.width
                            val viewH = tv.height
                            if (viewW > 0 && viewH > 0 && videoW > 0 && videoH > 0) {
                                val viewRatio = viewW.toFloat() / viewH.toFloat()
                                val videoRatio = videoW.toFloat() / videoH.toFloat()
                                val sx = if (videoRatio > viewRatio) videoRatio / viewRatio else 1f
                                val sy = if (viewRatio > videoRatio) viewRatio / videoRatio else 1f
                                val m = AndroidMatrix()
                                m.setScale(sx, sy, viewW / 2f, viewH / 2f)
                                tv.setTransform(m)
                            }
                        }
                        exoPlayer.addListener(object : Player.Listener {
                            override fun onVideoSizeChanged(videoSize: VideoSize) {
                                applyCenterCrop(videoSize.width, videoSize.height)
                            }
                        })
                    }
                },
            )
        }

        // 2. Rich In-World Living Scene Layer (Aurora, Waterfall, Sky Lanterns, Swimming Fish, Butterflies, Shooting Stars!)
        Canvas(modifier = Modifier.fillMaxSize()) {
            when (palette.particleStyle) {
                ParticleStyle.SNOWFLAKES -> drawFrostKingdomLife(timeSec)
                ParticleStyle.RAINBOW_SPARKLES -> drawRainbowUnicornLife(timeSec, butterflyBmp)
                ParticleStyle.CROWNS_AND_HEARTS -> drawRoyalPrincessLife(timeSec)
                ParticleStyle.OCEAN_BUBBLES -> drawOceanMermaidLife(timeSec)
                ParticleStyle.STARLIGHT -> drawBedtimeDreamLife(timeSec)
            }

            // Subtle contrast vignette for crisp readability
            drawRect(
                brush = Brush.verticalGradient(
                    0.0f to Color.Black.copy(alpha = 0.14f),
                    0.34f to Color.Transparent,
                    1.0f to palette.skyTop.copy(alpha = 0.40f),
                )
            )
        }

        content()
    }
}

/**
 * Frost Kingdom: Waving Northern Lights aurora curtains, twinkling ice-palace lights, and drifting snow!
 */
private fun DrawScope.drawFrostKingdomLife(t: Float) {
    val w = size.width
    val h = size.height

    // Waving Aurora Borealis ribbons across the upper sky
    for (ribbon in 0..2) {
        val auroraColor = if (ribbon % 2 == 0) Color(0x4038F9D7) else Color(0x36A18CD1)
        val path = Path()
        val baseY = h * (0.10f + ribbon * 0.07f)
        path.moveTo(0f, baseY)
        val steps = 12
        for (s in 1..steps) {
            val x = w * (s.toFloat() / steps)
            val wave = sin(t * 0.9f + s * 0.65f + ribbon * 1.4f) * (h * 0.045f)
            path.lineTo(x, baseY + wave)
        }
        path.lineTo(w, baseY + h * 0.14f)
        path.lineTo(0f, baseY + h * 0.14f)
        path.close()
        drawPath(
            path = path,
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, auroraColor, Color.Transparent),
                startY = baseY - h * 0.05f,
                endY = baseY + h * 0.15f,
            ),
        )
    }

    // Warm pulsing golden ice-palace window lights & spire sparkles
    val palaceGlow = 0.45f + 0.45f * (0.5f + 0.5f * sin(t * 1.8f))
    drawRadialGlow(Offset(w * 0.68f, h * 0.30f), w * 0.09f, Color(0xFFFFD166).copy(alpha = 0.22f * palaceGlow))
    drawSparkleCross(Offset(w * 0.685f, h * 0.06f), 14f * palaceGlow, Color.White)

    // Multi-depth softly drifting snowflakes
    for (i in 0 until 30) {
        val speed = 18f + (i % 4) * 11f
        val startX = ((i * 79 + 13) % 100) / 100f * w
        val startY = ((i * 43 + 7) % 100) / 100f * h
        val y = (startY + t * speed) % (h + 24f) - 12f
        val x = (startX + sin(t * 0.8f + i) * 26f + t * 8f) % w
        val r = 4f + (i % 4) * 2.8f
        val alpha = 0.45f + 0.45f * (0.5f + 0.5f * sin(t * 2f + i))
        drawSnowflakeCrystal(Offset(x, y), r, Color.White.copy(alpha = alpha))
    }
}

/**
 * Rainbow Unicorn: Cascading waterfall streaks & mist spray, fluttering 3D butterflies, and floating wishes!
 */
private fun DrawScope.drawRainbowUnicornLife(t: Float, butterflyBmp: ImageBitmap?) {
    val w = size.width
    val h = size.height

    // 1. Cascading Animated Waterfall Streaks & Rising Splash Mist (aligned to waterfall at x=0.53..0.67, y=0.45..0.72)
    val wfLeft = w * 0.535f
    val wfWidth = w * 0.125f
    val wfTop = h * 0.45f
    val wfBot = h * 0.71f
    for (stream in 0 until 14) {
        val sx = wfLeft + (stream / 13f) * wfWidth + sin(stream * 2.1f) * 3f
        val fallProg = ((t * (0.85f + (stream % 3) * 0.22f) + stream * 0.17f) % 1f)
        val sy = wfTop + fallProg * (wfBot - wfTop)
        val streakLen = (wfBot - wfTop) * 0.26f
        val endY = (sy + streakLen).coerceAtMost(wfBot)
        val alpha = sin(fallProg * PI).toFloat() * 0.55f
        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = Offset(sx, sy),
            end = Offset(sx - 4f, endY),
            strokeWidth = 3.2f,
            cap = StrokeCap.Round,
        )
    }
    // Misty splash clouds pulsing at base of waterfall
    for (m in 0..3) {
        val mx = wfLeft + wfWidth * (0.2f + m * 0.22f) + sin(t * 1.4f + m) * 10f
        val my = wfBot + sin(t * 2.1f + m * 1.5f) * 5f
        val mr = 22f + sin(t * 1.7f + m) * 6f
        drawRadialGlow(Offset(mx, my), mr * 1.8f, Color.White.copy(alpha = 0.34f))
    }

    // 2. Fluttering 3D Butterflies gliding in gentle figure-8 paths across the flower meadow!
    if (butterflyBmp != null) {
        for (b in 0..2) {
            val baseCx = w * (0.42f + b * 0.22f)
            val baseCy = h * (0.56f + (b % 2) * 0.14f)
            val bx = baseCx + sin(t * (0.55f + b * 0.12f) + b * 2f) * (w * 0.08f)
            val by = baseCy + sin(t * (1.1f + b * 0.2f) + b) * (h * 0.045f)
            val wingFlap = 0.45f + 0.55f * kotlin.math.abs(sin(t * 6.5f + b * 1.3f))
            val bSize = 34f + b * 5f
            withTransform({
                translate(bx, by)
                rotate(sin(t * 0.9f + b) * 15f, Offset.Zero)
                scale(scaleX = wingFlap, scaleY = 1f, pivot = Offset.Zero)
            }) {
                drawImage(
                    image = butterflyBmp,
                    dstOffset = IntOffset((-bSize * 0.5f).roundToInt(), (-bSize * 0.5f).roundToInt()),
                    dstSize = IntSize(bSize.roundToInt(), bSize.roundToInt()),
                )
            }
        }
    }

    // 3. Floating Golden Dandelion Wishes & Rainbow Sparkles
    for (i in 0 until 22) {
        val speedY = 14f + (i % 3) * 7f
        val startX = ((i * 67 + 19) % 100) / 100f * w
        val startY = ((i * 53 + 11) % 100) / 100f * h
        val y = (startY - t * speedY).mod(h + 30f) - 15f
        val x = (startX + sin(t * 0.7f + i) * 32f).mod(w)
        val pulse = 0.4f + 0.6f * (0.5f + 0.5f * sin(t * 2.4f + i))
        drawRadialGlow(Offset(x, y), 14f, Color(0xFFFFF3B0).copy(alpha = 0.45f * pulse))
        drawSparkleCross(Offset(x, y), 7f * pulse, Color.White.copy(alpha = 0.9f * pulse))
    }
}

/**
 * Royal Princess: Rising glowing Golden Sky Lanterns, sparkling fountain spray, and floating rose petals!
 */
private fun DrawScope.drawRoyalPrincessLife(t: Float) {
    val w = size.width
    val h = size.height

    // 1. Warm Tangled-style Golden Sky Lanterns rising smoothly into the sunset clouds!
    for (i in 0 until 16) {
        val speed = 15f + (i % 4) * 6f
        val startX = (0.24f + ((i * 61) % 72) / 100f) * w
        val startY = ((i * 47) % 100) / 100f * (h * 0.75f)
        val y = (startY - t * speed).mod(h * 0.75f)
        val x = startX + sin(t * 0.65f + i * 1.3f) * 20f
        val scaleDepth = 0.55f + (i % 4) * 0.22f
        val lw = 14f * scaleDepth
        val lh = 19f * scaleDepth
        val envelope = sin((y / (h * 0.75f)).coerceIn(0f, 1f) * PI).toFloat()
        val flicker = (0.82f + 0.18f * sin(t * 5.2f + i * 2f)) * envelope

        // Warm golden halo around lantern
        drawRadialGlow(Offset(x, y), lw * 2.8f, Color(0xFFFFB703).copy(alpha = 0.36f * flicker))
        // Paper lantern body
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFF9F1C).copy(alpha = envelope),
                    Color(0xFFFFE680).copy(alpha = envelope),
                    Color(0xFFFFBF69).copy(alpha = envelope),
                ),
                startY = y - lh * 0.5f,
                endY = y + lh * 0.5f,
            ),
            topLeft = Offset(x - lw * 0.5f, y - lh * 0.5f),
            size = Size(lw, lh),
            cornerRadius = CornerRadius(lw * 0.3f, lw * 0.3f),
        )
        // Bright inner candle flame core
        drawCircle(Color.White.copy(alpha = 0.92f * flicker), radius = lw * 0.22f, center = Offset(x, y + lh * 0.15f))
    }

    // 2. Sparkling Courtyard Fountain Water Arcs (around x=0.64, y=0.60)
    val fx = w * 0.635f
    val fy = h * 0.58f
    for (drop in 0 until 10) {
        val p = ((t * 1.3f + drop * 0.1f) % 1f)
        val dir = if (drop % 2 == 0) 1f else -1f
        val spread = (1 + (drop % 3)) * 9f
        val dx = fx + dir * p * spread
        val dy = fy - sin(p * PI).toFloat() * 24f + p * 18f
        drawCircle(Color.White.copy(alpha = (1f - p) * 0.75f), radius = 2.6f, center = Offset(dx, dy))
    }

    // 3. Soft Drifting Pink Rose Petals
    for (p in 0 until 12) {
        val px = (((p * 83) % 100) / 100f * w + t * 22f + sin(t + p) * 18f).mod(w)
        val py = (((p * 37) % 100) / 100f * h + t * 16f).mod(h)
        withTransform({
            translate(px, py)
            rotate((t * 45f + p * 30f) % 360f, Offset.Zero)
        }) {
            drawOval(Color(0xCCFF85A1), Offset(-6f, -3.5f), Size(12f, 7f))
        }
    }
}

/**
 * Ocean Mermaid: Sweeping sunbeams, swimming colorful reef fish, glowing pearl shimmer & rising bubbles!
 */
private fun DrawScope.drawOceanMermaidLife(t: Float) {
    val w = size.width
    val h = size.height

    // 1. Dancing Underwater Caustic Sunbeams from Ocean Surface
    for (ray in 0..3) {
        val topX = w * (0.36f + ray * 0.14f) + sin(t * 0.6f + ray) * 35f
        val alpha = 0.08f + 0.07f * (0.5f + 0.5f * sin(t * 1.1f + ray * 1.5f))
        val path = Path().apply {
            moveTo(topX - 28f, 0f)
            lineTo(topX + 38f, 0f)
            lineTo(topX + 140f, h * 0.85f)
            lineTo(topX - 130f, h * 0.85f)
            close()
        }
        drawPath(
            path = path,
            brush = Brush.verticalGradient(listOf(Color(0xFFFFF8D6).copy(alpha = alpha), Color.Transparent)),
        )
    }

    // 2. Colorful Swimming Reef Fish gliding across the water with wiggling tail fins!
    val fishPalette = listOf(
        Color(0xFFFF9F1C) to Color(0xFFFFBF69),
        Color(0xFFFF5CA8) to Color(0xFFFFB3D9),
        Color(0xFF38BDF8) to Color(0xFFBAE6FD),
        Color(0xFFFFD166) to Color(0xFF06D6A0),
    )
    for (f in 0 until 7) {
        val goingRight = f % 2 == 0
        val speed = 28f + (f % 3) * 14f
        val rawX = (((f * 190f) + t * speed) % (w + 120f)) - 60f
        val fx = if (goingRight) rawX else (w - rawX)
        val fy = h * (0.18f + (f * 0.085f)) + sin(t * 1.5f + f) * 12f
        val (bodyCol, finCol) = fishPalette[f % fishPalette.size]
        val tailWag = sin(t * 8f + f) * 4f
        val dir = if (goingRight) 1f else -1f

        withTransform({
            translate(fx, fy)
            scale(scaleX = dir, scaleY = 1f, pivot = Offset.Zero)
        }) {
            val tail = Path().apply {
                moveTo(-10f, 0f)
                lineTo(-21f, -8f + tailWag)
                lineTo(-19f, 8f + tailWag)
                close()
            }
            drawPath(tail, finCol)
            drawOval(bodyCol, Offset(-12f, -7f), Size(24f, 14f))
            drawCircle(Color.White, radius = 2.6f, center = Offset(6f, -2f))
            drawCircle(Color(0xFF072AC8), radius = 1.4f, center = Offset(6.8f, -2f))
        }
    }

    // 3. Rising Iridescent Coral Bubbles
    for (b in 0 until 26) {
        val speed = 24f + (b % 4) * 10f
        val bx = (((b * 71 + 9) % 100) / 100f * w) + sin(t * 1.6f + b) * 14f
        val by = (h - ((t * speed + b * 47f) % (h + 30f)))
        val r = 4f + (b % 5) * 2.5f
        drawCircle(Color(0xBBCAF0F8), radius = r, center = Offset(bx, by), style = Stroke(width = 1.8f))
        drawCircle(Color.White.copy(alpha = 0.85f), radius = r * 0.28f, center = Offset(bx - r * 0.32f, by - r * 0.32f))
    }
}

/**
 * Bedtime Kingdom: Periodic shooting stars, twinkling constellations, and lazy golden fireflies!
 */
private fun DrawScope.drawBedtimeDreamLife(t: Float) {
    val w = size.width
    val h = size.height

    // Periodic gentle shooting star every 6 seconds across upper night sky
    val cycle = t % 6.0f
    if (cycle < 1.2f) {
        val p = cycle / 1.2f
        val sx = w * (0.78f - p * 0.42f)
        val sy = h * (0.08f + p * 0.22f)
        val alpha = sin(p * PI).toFloat()
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = alpha), Color.Transparent),
                start = Offset(sx, sy),
                end = Offset(sx + 95f, sy - 48f),
            ),
            start = Offset(sx, sy),
            end = Offset(sx + 95f, sy - 48f),
            strokeWidth = 3f,
            cap = StrokeCap.Round,
        )
        drawSparkleCross(Offset(sx, sy), 9f * alpha, Color(0xFFFFF3B0))
    }

    // Lazy glowing golden fireflies & twinkling stars
    for (i in 0 until 24) {
        val fx = (((i * 67 + 15) % 100) / 100f * w) + sin(t * 0.55f + i * 1.7f) * 28f
        val fy = (((i * 41 + 9) % 100) / 100f * h) + cos(t * 0.48f + i * 1.3f) * 20f
        val glow = 0.30f + 0.70f * (0.5f + 0.5f * sin(t * 1.9f + i * 2.1f))
        drawRadialGlow(Offset(fx, fy), 16f, Color(0xFFFFE169).copy(alpha = 0.42f * glow))
        drawCircle(Color(0xFFFFFBEB).copy(alpha = glow), radius = 2.8f, center = Offset(fx, fy))
    }
}

private fun DrawScope.drawRadialGlow(center: Offset, radius: Float, color: Color) {
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

private fun DrawScope.drawSnowflakeCrystal(center: Offset, radius: Float, color: Color) {
    for (arm in 0 until 6) {
        val angle = (arm * PI / 3.0).toFloat()
        val end = Offset(center.x + cos(angle) * radius, center.y + sin(angle) * radius)
        drawLine(color = color, start = center, end = end, strokeWidth = 2.0f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.drawSparkleCross(center: Offset, radius: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticTo(center.x, center.y, center.x + radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + radius)
        quadraticTo(center.x, center.y, center.x - radius, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    drawPath(p, color = color)
}
