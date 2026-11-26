package io.github.re4pr.kidslauncher.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import io.github.re4pr.kidslauncher.model.AvatarConfig
import io.github.re4pr.kidslauncher.model.LauncherMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private object PuppetSpriteCache {
    private val map = mutableMapOf<String, ImageBitmap?>()

    fun load(context: Context, assetPath: String): ImageBitmap? {
        if (assetPath.isBlank()) return null
        return synchronized(map) {
            map.getOrPut(assetPath) {
                runCatching {
                    context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
}

/**
 * Google Kids Space–style 2.5D Airbrushed Skeletal Puppet + Real 3D-Shaded Props & 16 Companion Pets!
 *
 * Combines:
 * - Multi-stop radial & linear airbrush-shaded 2.5D vector character anatomy (plush chibi face,
 *   lush multi-ring Disney/storybook iris eyes with curved lashes & multi-stage blinking, flowing
 *   gradient hair locks & Elsa braids, multi-tier tulle/satin/mermaid gowns, hoodies & jeans).
 * - Articulated 2.5D skeletal joint hierarchy (breathing torso, swaying skirt, nodding head,
 *   parallax depth shift on horizontal drag, flapping 3D feathered/butterfly wings, waving arm).
 * - High-resolution 3D-shaded sprite overlays (`crown.webp`, `magic_wand.webp`, `pegasus_wing.webp`,
 *   `butterfly_wings.webp`, `ribbon_bow.webp`, `headphones.webp`, `sunglasses.webp`) and 16 adorable
 *   3D-shaded sidekick pets (`winged_unicorn.webp`, `golden_puppy.webp`, `emerald_dragon.webp`, etc.).
 */
@Composable
fun Avatar3DStage(
    avatar: AvatarConfig,
    mode: LauncherMode,
    rimHighlightColor: Color = Color(0xFF9EE8FF),
    modifier: Modifier = Modifier,
    onTapAvatar: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var parallaxTilt by remember { mutableFloatStateOf(0f) }
    var cheerStartSec by remember { mutableFloatStateOf(-10f) }
    val startNs = remember { System.nanoTime() }
    var elapsedSec by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameNs ->
                elapsedSec = ((frameNs - startNs) / 1_000_000_000.0).toFloat()
            }
        }
    }

    val smoothParallax by animateFloatAsState(
        targetValue = parallaxTilt,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "parallax25D",
    )

    // Continuous C-infinity sine signals driven by monotonic elapsedSec — ZERO loop resets or snaps!
    val breath = sin(elapsedSec * 2.4f)
    val wavePhase = elapsedSec * 2.4f

    // Preload 3D-shaded sprite overlays & selected 3D companion pet
    val petPath = AvatarCatalog.petEntries.getOrElse(avatar.petIndex) { AvatarCatalog.petEntries[0] }.second
    val petBitmap = remember(petPath) { PuppetSpriteCache.load(context, petPath) }
    val pegasusWingBmp = remember { PuppetSpriteCache.load(context, "puppet/props/pegasus_wing.webp") }
    val butterflyWingsBmp = remember { PuppetSpriteCache.load(context, "puppet/props/butterfly_wings.webp") }
    val crownBmp = remember { PuppetSpriteCache.load(context, "puppet/props/crown.webp") }
    val wandBmp = remember { PuppetSpriteCache.load(context, "puppet/props/magic_wand.webp") }
    val ribbonBmp = remember { PuppetSpriteCache.load(context, "puppet/props/ribbon_bow.webp") }
    val hibiscusBmp = remember { PuppetSpriteCache.load(context, "puppet/props/hibiscus.webp") }
    val headphonesBmp = remember { PuppetSpriteCache.load(context, "puppet/props/headphones.webp") }
    val sunglassesBmp = remember { PuppetSpriteCache.load(context, "puppet/props/sunglasses.webp") }
    val sparklesBmp = remember { PuppetSpriteCache.load(context, "puppet/props/sparkles.webp") }
    val snowflakeBmp = remember { PuppetSpriteCache.load(context, "puppet/props/snowflake.webp") }

    val skinBase = AvatarCatalog.skinTones.getOrElse(avatar.skinToneIndex) { AvatarCatalog.skinTones[1] }
    val hairBase = AvatarCatalog.hairColors.getOrElse(avatar.hairColorIndex) { AvatarCatalog.hairColors[0] }.second
    val irisBase = AvatarCatalog.eyeColors.getOrElse(avatar.eyeColorIndex) { AvatarCatalog.eyeColors[0] }.second
    val rawOutfit = AvatarCatalog.outfits.getOrElse(avatar.outfitIndex) { AvatarCatalog.outfits[0] }.second
    val dye = AvatarCatalog.outfitColorOverrides.getOrElse(avatar.outfitColorIndex) { AvatarCatalog.outfitColorOverrides[0] }.second
    val outfitBase = when {
        mode == LauncherMode.BEDTIME -> Color(0xFF6C63FF)
        dye.isSpecified -> dye
        else -> rawOutfit
    }
    val effectiveOutfitIdx = if (mode == LauncherMode.BEDTIME) 11 else avatar.outfitIndex

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { parallaxTilt = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        parallaxTilt = (parallaxTilt + dragAmount * 0.012f).coerceIn(-1f, 1f)
                    },
                )
            }
            .pointerInput(onTapAvatar) {
                detectTapGestures(
                    onTap = {
                        cheerStartSec = elapsedSec
                        onTapAvatar?.invoke()
                    }
                )
            }
    ) {
        val w = size.width
        val h = size.height
        val unit = minOf(w, h)
        val cheerElapsed = elapsedSec - cheerStartSec
        val isCheering = cheerElapsed in 0f..0.95f
        val jumpOffset = if (isCheering) {
            -sin((cheerElapsed / 0.95f) * PI.toFloat()).coerceAtLeast(0f) * unit * 0.14f
        } else {
            0f
        }

        val hasPet = petBitmap != null && avatar.petIndex > 0
        val charCx = if (hasPet) w * 0.39f else w * 0.50f
        val groundY = h * 0.86f
        val charScale = unit / 320f

        // 1. Soft Ambient Ground Shadow under Character
        val shadowScale = 1f + (jumpOffset / (unit * 0.45f))
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x55081026), Color.Transparent),
                center = Offset(charCx, groundY),
                radius = 68f * charScale * shadowScale,
            ),
            topLeft = Offset(charCx - 66f * charScale * shadowScale, groundY - 14f * charScale),
            size = Size(132f * charScale * shadowScale, 28f * charScale),
        )

        // 2. Skeletal Character Root Transform
        withTransform({
            translate(left = charCx, top = groundY + jumpOffset)
            scale(scaleX = charScale, scaleY = charScale, pivot = Offset.Zero)
        }) {
            val pxShift = smoothParallax * 11f
            val headTiltDeg = sin(wavePhase) * 2.6f + smoothParallax * 4.5f
            val bodyBobY = breath * 2.4f

            // A. Back Capes & Animated Flapping 3D Wings (behind hair & body!)
            drawBackWingsAndCapes(
                wingsIndex = avatar.wingsBackIndex,
                outfitIndex = effectiveOutfitIdx,
                outfitColor = outfitBase,
                wavePhase = wavePhase,
                pxShift = pxShift,
                bodyBobY = bodyBobY,
                pegasusWingBmp = pegasusWingBmp,
                butterflyWingsBmp = butterflyWingsBmp,
            )

            // B. Back Hair Layer (behind shoulders & dress!)
            drawHairBackLayer(
                styleIndex = avatar.hairStyleIndex,
                hairBase = hairBase,
                wavePhase = wavePhase,
                pxShift = pxShift,
                bodyBobY = bodyBobY,
            )

            // C. Left (Resting) Arm behind torso edge
            drawSculptedArm25D(
                isRight = false,
                skinBase = skinBase,
                sleeveColor = outfitBase,
                outfitIdx = effectiveOutfitIdx,
                wavePhase = wavePhase,
                isCheering = isCheering,
                mode = mode,
                bodyBobY = bodyBobY,
                handPropIndex = 0,
                wandBmp = null,
                snowflakeBmp = null,
            )

            // D. Airbrushed Legs, Footwear & Fairy-Tale Dress / Hoodie / Mermaid Gown!
            drawOutfitAndLegs25D(
                outfitIdx = effectiveOutfitIdx,
                outfitBase = outfitBase,
                skinBase = skinBase,
                wavePhase = wavePhase,
                bodyBobY = bodyBobY,
                pxShift = pxShift,
            )

            // E. Right Waving Arm + Held 3D Magic Wand / Scepter!
            drawSculptedArm25D(
                isRight = true,
                skinBase = skinBase,
                sleeveColor = outfitBase,
                outfitIdx = effectiveOutfitIdx,
                wavePhase = wavePhase,
                isCheering = isCheering,
                mode = mode,
                bodyBobY = bodyBobY,
                handPropIndex = if (mode == LauncherMode.BEDTIME) 0 else avatar.handFacePropIndex,
                wandBmp = wandBmp,
                snowflakeBmp = snowflakeBmp,
            )

            // F. Neck + Soft Chin Drop Shadow
            val skinShadow = lerp(skinBase, Color(0xFF7B3B26), 0.30f)
            drawRoundRect(
                brush = Brush.verticalGradient(listOf(skinShadow, skinBase)),
                topLeft = Offset(-13f + pxShift * 0.35f, -158f + bodyBobY),
                size = Size(26f, 28f),
                cornerRadius = CornerRadius(12f, 12f),
            )

            // G. Articulated 2.5D Head Bone (Plush Chibi Cheeks, Disney Lash Eyes, Nose, Smile, Bangs, 3D Crown!)
            withTransform({
                translate(left = pxShift * 0.75f, top = -194f + bodyBobY)
                rotate(degrees = headTiltDeg, pivot = Offset(0f, 34f))
            }) {
                drawPlushHeadAndFace25D(
                    avatar = avatar,
                    mode = mode,
                    skinBase = skinBase,
                    hairBase = hairBase,
                    irisBase = irisBase,
                    nowMs = (elapsedSec * 1000f).toLong(),
                    parallax = smoothParallax,
                    sunglassesBmp = sunglassesBmp,
                )

                // Silky Front Bangs & Side Framing Locks (with forehead cast shadow + glossy halo!)
                drawHairFrontLayer(
                    styleIndex = avatar.hairStyleIndex,
                    hairBase = hairBase,
                    wavePhase = wavePhase,
                )

                // 3D-Shaded Crowns, Unicorn Horns, Bows & Hats!
                drawHeadwear25D(
                    accessoryIndex = avatar.accessoryIndex,
                    mode = mode,
                    crownBmp = crownBmp,
                    ribbonBmp = ribbonBmp,
                    hibiscusBmp = hibiscusBmp,
                    headphonesBmp = headphonesBmp,
                )
            }

            // H. Magical Sparkle Aura (when cheering or Sparkle Aura selected)
            if (isCheering || avatar.handFacePropIndex == 7) {
                if (sparklesBmp != null) {
                    val pulse = 0.75f + 0.25f * sin(wavePhase * 2f)
                    drawSpriteCentered(sparklesBmp, Offset(-68f, -210f + breath * 6f), 42f * pulse)
                    drawSpriteCentered(sparklesBmp, Offset(74f, -155f - breath * 6f), 36f * pulse)
                }
            }
        }

        // 3. High-Resolution 3D-Shaded Sidekick Pet Stage (Continuous C-infinity smooth motion, zero resets!)
        if (hasPet && petBitmap != null) {
            val petCx = w * 0.76f
            val isFloatingPet = avatar.petIndex in listOf(1, 6, 8, 9, 12)
            val smoothFloat = sin(elapsedSec * 2.1f)
            val petBob = if (isFloatingPet) {
                smoothFloat * unit * 0.026f - unit * 0.045f
            } else {
                // Smooth gentle breathing rise/settle with zero sharp cusps or resets
                (smoothFloat - 1f) * 0.5f * unit * 0.016f
            }
            val petJump = if (isCheering) jumpOffset * 0.85f else 0f
            val petSize = unit * 0.40f

            // Soft ground shadow beneath 3D pet
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x4D081026), Color.Transparent),
                    center = Offset(petCx, groundY + 4f),
                    radius = petSize * 0.44f,
                ),
                topLeft = Offset(petCx - petSize * 0.42f, groundY - 8f),
                size = Size(petSize * 0.84f, 20f),
            )

            // Smooth squash-and-stretch breathing + gentle head-sway driven by monotonic elapsedSec!
            val petBreath = sin(elapsedSec * 2.1f + 0.6f)
            val squashX = 1f + petBreath * 0.022f
            val squashY = 1f - petBreath * 0.022f
            val petTilt = sin(elapsedSec * 1.45f) * 3.8f - smoothParallax * 5f

            withTransform({
                translate(left = petCx, top = groundY + petBob + petJump - petSize * 0.46f)
                rotate(degrees = petTilt, pivot = Offset(0f, petSize * 0.35f))
                scale(scaleX = squashX, scaleY = squashY, pivot = Offset(0f, petSize * 0.45f))
            }) {
                drawImage(
                    image = petBitmap,
                    dstOffset = IntOffset((-petSize * 0.5f).roundToInt(), (-petSize * 0.5f).roundToInt()),
                    dstSize = IntSize(petSize.roundToInt(), petSize.roundToInt()),
                )
            }
        }
    }
}

private fun DrawScope.drawPlushHeadAndFace25D(
    avatar: AvatarConfig,
    mode: LauncherMode,
    skinBase: Color,
    hairBase: Color,
    irisBase: Color,
    nowMs: Long,
    parallax: Float,
    sunglassesBmp: ImageBitmap?,
) {
    val skinHighlight = lerp(skinBase, Color.White, 0.32f)
    val skinEdge = lerp(skinBase, Color(0xFFB55D3E), 0.24f)
    val skinOutline = lerp(skinBase, Color(0xFF5A2A1A), 0.38f)

    // Ears with warm inner-ear blush
    for (side in listOf(-1f, 1f)) {
        val earCenter = Offset(side * 54f, 6f)
        drawCircle(color = skinEdge, radius = 12.5f, center = earCenter)
        drawCircle(color = lerp(skinBase, Color(0xFFFF7A96), 0.38f), radius = 6.5f, center = earCenter)
    }

    // Plush Storybook Chibi Head Silhouette (full soft lower cheeks + gentle chin)
    val headPath = Path().apply {
        moveTo(0f, -54f)
        cubicTo(38f, -54f, 54f, -30f, 53f, -2f)
        cubicTo(55f, 24f, 42f, 44f, 16f, 48f)
        quadraticTo(0f, 51f, -16f, 48f)
        cubicTo(-42f, 44f, -55f, 24f, -53f, -2f)
        cubicTo(-54f, -30f, -38f, -54f, 0f, -54f)
        close()
    }
    drawPath(
        path = headPath,
        brush = Brush.radialGradient(
            colors = listOf(skinHighlight, skinBase, skinEdge),
            center = Offset(-12f + parallax * 6f, -14f),
            radius = 76f,
        ),
    )
    drawPath(path = headPath, color = skinOutline, style = Stroke(width = 2.2f))

    val faceShiftX = parallax * 5.5f

    // Cheeks & Freckles
    when (avatar.cheekStyleIndex) {
        1, 4 -> {
            val blushColor = if (avatar.cheekStyleIndex == 4) Color(0x66FF477E) else Color(0x60FF7597)
            for (side in listOf(-1f, 1f)) {
                val c = Offset(side * 31f + faceShiftX, 17f)
                drawOval(
                    brush = Brush.radialGradient(listOf(blushColor, Color.Transparent), center = c, radius = 16f),
                    topLeft = Offset(c.x - 16f, c.y - 10f),
                    size = Size(32f, 20f),
                )
            }
        }
        2 -> { // Cute Freckles + warm blush
            val freckleCol = lerp(skinOutline, Color(0xFF8D4F32), 0.5f).copy(alpha = 0.65f)
            for (side in listOf(-1f, 1f)) {
                val bx = side * 29f + faceShiftX
                drawCircle(freckleCol, 1.9f, Offset(bx - 5f, 15f))
                drawCircle(freckleCol, 1.9f, Offset(bx + 1f, 18f))
                drawCircle(freckleCol, 1.8f, Offset(bx + 6f, 14.5f))
            }
        }
        3 -> { // Golden Star Cheeks
            for (side in listOf(-1f, 1f)) {
                drawTinyStar(Offset(side * 32f + faceShiftX, 17f), 6.5f, Color(0xFFFFD166))
            }
        }
    }

    // Multi-Layer Disney/Anime Storybook Eyes!
    val blinkCycle = (nowMs % 3800L)
    val isBlinking = mode == LauncherMode.BEDTIME || mode == LauncherMode.LOCKED || blinkCycle in 3640L..3780L
    val lashColor = Color(0xFF23192D)
    val browColor = lerp(hairBase, Color(0xFF23192D), 0.35f)

    for (side in listOf(-1f, 1f)) {
        val ex = side * 21.5f + faceShiftX
        val ey = 3.5f

        // Expressive Arched Eyebrows
        val browPath = Path().apply {
            moveTo(ex - side * 12f, ey - 18f)
            quadraticTo(ex, ey - 24f, ex + side * 12f, ey - 17f)
        }
        drawPath(browPath, color = browColor, style = Stroke(width = 3.6f, cap = StrokeCap.Round))

        val isWinkEye = (side > 0f && avatar.eyeStyleIndex == 3)
        if (isBlinking || avatar.eyeStyleIndex == 2 || isWinkEye) {
            // Peaceful closed / happy smiling lash curve
            val closedArch = Path().apply {
                val dir = if (mode == LauncherMode.BEDTIME || mode == LauncherMode.LOCKED) 1f else -1f
                moveTo(ex - 12f, ey + 1f)
                quadraticTo(ex, ey + dir * 9f, ex + 12f, ey + 1f)
            }
            drawPath(closedArch, color = lashColor, style = Stroke(width = 3.6f, cap = StrokeCap.Round))
            continue
        }

        val eyeW = 24.5f
        val eyeH = when (avatar.eyeStyleIndex) {
            4 -> 18.5f
            5 -> 24.5f
            else -> 21.5f
        }

        // 1. Soft Sclera with subtle upper-lid shadow
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFDCE6F5), Color.White, Color.White),
                startY = ey - eyeH * 0.5f,
                endY = ey + eyeH * 0.5f,
            ),
            topLeft = Offset(ex - eyeW * 0.5f, ey - eyeH * 0.5f),
            size = Size(eyeW, eyeH),
        )

        // 2. Luminous 3-Stop Gradient Iris
        val irisR = eyeH * 0.44f
        val irisCenter = Offset(ex + parallax * 2.2f, ey + 0.5f)
        val irisDeep = lerp(irisBase, Color(0xFF0B132B), 0.42f)
        val irisGlow = lerp(irisBase, Color.White, 0.52f)
        drawCircle(
            brush = Brush.verticalGradient(
                colors = listOf(irisDeep, irisBase, irisGlow),
                startY = irisCenter.y - irisR,
                endY = irisCenter.y + irisR,
            ),
            radius = irisR,
            center = irisCenter,
        )

        // 3. Velvety Dark Pupil
        drawCircle(color = Color(0xFF14111F), radius = irisR * 0.50f, center = irisCenter)

        // 4. Studio Oval Catchlight + Lower Sparkle Dot
        drawOval(
            color = Color.White,
            topLeft = Offset(irisCenter.x - irisR * 0.58f, irisCenter.y - irisR * 0.62f),
            size = Size(irisR * 0.56f, irisR * 0.46f),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.92f),
            radius = irisR * 0.20f,
            center = Offset(irisCenter.x + irisR * 0.36f, irisCenter.y + irisR * 0.34f),
        )

        // 5. Lush Curved Upper Eyelash Arch + Winged Outer Flick!
        val lashPath = Path().apply {
            moveTo(ex - eyeW * 0.52f, ey + 1f)
            quadraticTo(ex, ey - eyeH * 0.68f, ex + eyeW * 0.52f, ey + 1f)
        }
        drawPath(lashPath, color = lashColor, style = Stroke(width = 3.8f, cap = StrokeCap.Round))
        if (avatar.eyeStyleIndex == 0 || avatar.eyeStyleIndex == 1) {
            // Delicate Disney outer lash flick
            drawLine(
                color = lashColor,
                start = Offset(ex + side * eyeW * 0.46f, ey - 2f),
                end = Offset(ex + side * (eyeW * 0.64f), ey - 7f),
                strokeWidth = 3.0f,
                cap = StrokeCap.Round,
            )
        }
    }

    // Cute Sculpted Button Nose (soft shadow crescent + pearly highlight dot)
    drawArc(
        color = skinOutline.copy(alpha = 0.55f),
        startAngle = 25f,
        sweepAngle = 130f,
        useCenter = false,
        topLeft = Offset(-4.5f + faceShiftX, 11f),
        size = Size(9f, 5.5f),
        style = Stroke(width = 2.0f, cap = StrokeCap.Round),
    )
    drawCircle(color = Color.White.copy(alpha = 0.45f), radius = 2.2f, center = Offset(-1f + faceShiftX, 11.5f))

    // Sweet Storybook Smile!
    if (mode == LauncherMode.BEDTIME) {
        drawArc(
            color = Color(0xFFB5445E),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(-7f + faceShiftX, 23f),
            size = Size(14f, 7f),
            style = Stroke(width = 2.6f, cap = StrokeCap.Round),
        )
    } else {
        val mouthPath = Path().apply {
            moveTo(-11f + faceShiftX, 24f)
            quadraticTo(faceShiftX, 35f, 11f + faceShiftX, 24f)
            quadraticTo(faceShiftX, 28f, -11f + faceShiftX, 24f)
            close()
        }
        drawPath(mouthPath, color = Color(0xFFD63C64))
        drawArc(
            color = Color(0xFF8E2442),
            startAngle = 12f,
            sweepAngle = 156f,
            useCenter = false,
            topLeft = Offset(-11.5f + faceShiftX, 20f),
            size = Size(23f, 11f),
            style = Stroke(width = 2.6f, cap = StrokeCap.Round),
        )
    }

    // Glasses / 3D Sunglasses
    when {
        avatar.handFacePropIndex == 4 && sunglassesBmp != null && mode != LauncherMode.BEDTIME -> {
            drawSpriteCentered(sunglassesBmp, Offset(faceShiftX, 3f), 84f)
        }
        mode == LauncherMode.SCHOOL_TIME || avatar.handFacePropIndex in listOf(5, 6) -> {
            val frameCol = if (avatar.handFacePropIndex == 6) Color(0xFFFF4D94) else Color(0xFF2B3A67)
            drawCircle(frameCol, radius = 15.5f, center = Offset(-21f + faceShiftX, 3.5f), style = Stroke(width = 3.2f))
            drawCircle(frameCol, radius = 15.5f, center = Offset(21f + faceShiftX, 3.5f), style = Stroke(width = 3.2f))
            drawLine(frameCol, Offset(-5.5f + faceShiftX, 3.5f), Offset(5.5f + faceShiftX, 3.5f), strokeWidth = 3.2f)
        }
    }
}

private fun DrawScope.drawHairBackLayer(
    styleIndex: Int,
    hairBase: Color,
    wavePhase: Float,
    pxShift: Float,
    bodyBobY: Float,
) {
    val rootDark = lerp(hairBase, Color(0xFF160F24), 0.38f)
    val tipLight = lerp(hairBase, Color.White, 0.24f)
    val hairBrush = Brush.verticalGradient(
        colors = listOf(rootDark, hairBase, tipLight),
        startY = -245f,
        endY = -75f,
    )
    val sway = sin(wavePhase) * 6f - pxShift * 0.5f
    val headTopY = -194f + bodyBobY

    when (styleIndex) {
        0, 11 -> { // Flowing Princess Waves
            val waves = Path().apply {
                moveTo(-48f, headTopY - 12f)
                cubicTo(-72f, headTopY + 35f, -64f + sway, headTopY + 95f, -52f + sway, headTopY + 128f)
                quadraticTo(0f + sway, headTopY + 144f, 52f + sway, headTopY + 128f)
                cubicTo(64f + sway, headTopY + 95f, 72f, headTopY + 35f, 48f, headTopY - 12f)
                close()
            }
            drawPath(waves, brush = hairBrush)
        }
        1 -> { // Thick Elsa-Style Side Braid cascading over left shoulder!
            for (seg in 0..5) {
                val sy = headTopY + 28f + seg * 16f
                val sx = -42f - seg * 2.2f + sway * (seg * 0.15f)
                val r = 16f - seg * 1.3f
                drawOval(brush = hairBrush, topLeft = Offset(sx - r, sy - r * 0.85f), size = Size(r * 2f, r * 1.75f))
                drawOval(color = rootDark.copy(alpha = 0.35f), topLeft = Offset(sx - r, sy - r * 0.85f), size = Size(r * 2f, r * 1.75f), style = Stroke(width = 1.8f))
            }
            // Snowflake hair tie at braid tip
            drawCircle(Color(0xFF7CE0FF), radius = 7f, center = Offset(-54f + sway * 0.8f, headTopY + 114f))
        }
        2 -> { // Twin Ribbon Braids
            for (side in listOf(-1f, 1f)) {
                for (seg in 0..4) {
                    val sy = headTopY + 30f + seg * 15f
                    val sx = side * (44f + seg * 2f) + sway * 0.5f
                    val r = 13.5f - seg * 1.1f
                    drawCircle(brush = hairBrush, radius = r, center = Offset(sx, sy))
                }
                drawCircle(Color(0xFFFF6BB5), radius = 6.5f, center = Offset(side * 52f + sway * 0.5f, headTopY + 96f))
            }
        }
        3 -> { // High Bouncy Ponytail
            val tail = Path().apply {
                moveTo(10f, headTopY - 38f)
                cubicTo(58f, headTopY - 62f, 84f + sway, headTopY + 5f, 64f + sway, headTopY + 68f)
                cubicTo(42f + sway, headTopY + 52f, 44f, headTopY - 12f, 6f, headTopY - 26f)
                close()
            }
            drawPath(tail, brush = hairBrush)
        }
        4 -> { // Twin Pigtails
            for (side in listOf(-1f, 1f)) {
                val pt = Path().apply {
                    moveTo(side * 42f, headTopY - 24f)
                    cubicTo(side * 82f, headTopY - 32f, side * 86f + sway, headTopY + 36f, side * 64f + sway, headTopY + 68f)
                    quadraticTo(side * 46f + sway, headTopY + 40f, side * 38f, headTopY - 10f)
                    close()
                }
                drawPath(pt, brush = hairBrush)
            }
        }
        5 -> { // Fluffy Cloud Curls Halo
            for (i in 0..8) {
                val ang = (PI * 0.85 + i * PI * 1.3 / 8.0).toFloat()
                drawCircle(brush = hairBrush, radius = 24f, center = Offset(cos(ang) * 52f, headTopY + sin(ang) * 44f + 6f))
            }
        }
        6 -> { // Sleek Wavy Bob
            drawRoundRect(
                brush = hairBrush,
                topLeft = Offset(-58f + sway * 0.3f, headTopY - 22f),
                size = Size(116f, 82f),
                cornerRadius = CornerRadius(38f, 38f),
            )
        }
        7 -> { // Cute Space Buns
            drawCircle(brush = hairBrush, radius = 22f, center = Offset(-44f, headTopY - 38f))
            drawCircle(brush = hairBrush, radius = 22f, center = Offset(44f, headTopY - 38f))
        }
    }
}

private fun DrawScope.drawHairFrontLayer(
    styleIndex: Int,
    hairBase: Color,
    wavePhase: Float,
) {
    val rootDark = lerp(hairBase, Color(0xFF160F24), 0.35f)
    val midBright = lerp(hairBase, Color.White, 0.18f)
    val haloShine = lerp(hairBase, Color.White, 0.58f).copy(alpha = 0.55f)

    // Soft cast shadow from bangs onto upper forehead
    val shadowPath = Path().apply {
        moveTo(-50f, -14f)
        quadraticTo(-10f, -34f, 50f, -14f)
        quadraticTo(10f, -20f, -50f, -14f)
        close()
    }
    drawPath(shadowPath, color = Color(0x243A1810))

    // Lush Side-Swept Storybook Bangs & Crown Volume
    val bangsPath = Path().apply {
        moveTo(-55f, -4f)
        cubicTo(-58f, -44f, -28f, -64f, 2f, -63f)
        cubicTo(32f, -64f, 58f, -42f, 55f, -4f)
        // Side cheek-framing locks
        quadraticTo(52f, 16f, 46f, 22f)
        quadraticTo(48f, -12f, 34f, -22f)
        // Swept bangs curve across forehead
        cubicTo(16f, -12f, -12f, -14f, -34f, -24f)
        quadraticTo(-48f, -12f, -46f, 22f)
        quadraticTo(-52f, 16f, -55f, -4f)
        close()
    }
    drawPath(
        path = bangsPath,
        brush = Brush.verticalGradient(
            colors = listOf(rootDark, hairBase, midBright),
            startY = -64f,
            endY = 22f,
        ),
    )

    if (styleIndex == 8) {
        // Royal Crown Braid scallops across top of hair
        for (b in -3..3) {
            drawCircle(color = midBright, radius = 8.5f, center = Offset(b * 13f, -52f + (b * b) * 0.9f))
            drawCircle(color = rootDark.copy(alpha = 0.4f), radius = 8.5f, center = Offset(b * 13f, -52f + (b * b) * 0.9f), style = Stroke(width = 1.6f))
        }
    }

    // Silky Curved Anime/Pixar Hair Halo Highlight Ribbon!
    val shineArc = Path().apply {
        moveTo(-34f, -36f)
        quadraticTo(0f, -48f, 34f, -36f)
    }
    drawPath(shineArc, color = haloShine, style = Stroke(width = 5.5f, cap = StrokeCap.Round))
}

private fun DrawScope.drawOutfitAndLegs25D(
    outfitIdx: Int,
    outfitBase: Color,
    skinBase: Color,
    wavePhase: Float,
    bodyBobY: Float,
    pxShift: Float,
) {
    val outfitLight = lerp(outfitBase, Color.White, 0.38f)
    val outfitDeep = lerp(outfitBase, Color(0xFF140C2E), 0.32f)
    val swayX = sin(wavePhase) * 4.5f
    val isPants = outfitIdx in listOf(6, 7, 8, 10, 11)

    if (outfitIdx == 3) {
        // Ocean Mermaid Shimmer Gown + Iridescent Tail Flukes!
        val tailBrush = Brush.verticalGradient(
            colors = listOf(outfitLight, outfitBase, Color(0xFF7BF1E6)),
            startY = -140f,
            endY = 0f,
        )
        val mermaidPath = Path().apply {
            moveTo(-24f, -138f + bodyBobY)
            lineTo(24f, -138f + bodyBobY)
            cubicTo(34f, -88f, 22f + swayX, -42f, 14f + swayX, -22f)
            // Flared dual mermaid fins at bottom!
            quadraticTo(46f + swayX, -12f, 52f + swayX, 4f)
            quadraticTo(18f + swayX, 2f, 0f + swayX, -10f)
            quadraticTo(-18f + swayX, 2f, -52f + swayX, 4f)
            quadraticTo(-46f + swayX, -12f, -14f + swayX, -22f)
            cubicTo(-22f + swayX, -42f, -34f, -88f, -24f, -138f + bodyBobY)
            close()
        }
        drawPath(mermaidPath, brush = tailBrush)
        // Seashell bodice + pearl waist belt
        drawCircle(Color(0xFFFF9EBB), radius = 13f, center = Offset(-11f, -124f + bodyBobY))
        drawCircle(Color(0xFFFF9EBB), radius = 13f, center = Offset(11f, -124f + bodyBobY))
        for (p in -3..3) {
            drawCircle(Color.White, radius = 3.5f, center = Offset(p * 7.5f, -102f + bodyBobY))
        }
        return
    }

    if (isPants) {
        // Sculpted 2.5D Legs, Jeans/Pajama Pants & High-Top Sneakers / Bunny Slippers!
        val pantsCol = when (outfitIdx) {
            6, 8 -> Color(0xFF3B6EA5) // Denim jeans
            10 -> Color(0xFFDDE7F2)   // Astronaut suit legs
            else -> outfitDeep
        }
        for (side in listOf(-1f, 1f)) {
            val lx = side * 17f
            drawRoundRect(
                color = pantsCol,
                topLeft = Offset(lx - 10.5f, -78f + bodyBobY * 0.4f),
                size = Size(21f, 68f),
                cornerRadius = CornerRadius(10f, 10f),
            )
            // Cuffed ankle + cute sneakers / bunny slippers
            if (outfitIdx == 11) {
                // Fluffy white bunny bedtime slippers!
                drawOval(Color.White, topLeft = Offset(lx - 15f, -16f), size = Size(30f, 19f))
                drawOval(Color.White, topLeft = Offset(lx - 10f, -26f), size = Size(7f, 13f))
                drawOval(Color.White, topLeft = Offset(lx + 3f, -26f), size = Size(7f, 13f))
            } else {
                drawRoundRect(outfitBase, topLeft = Offset(lx - 14f, -17f), size = Size(28f, 17f), cornerRadius = CornerRadius(9f, 9f))
                drawRoundRect(Color.White, topLeft = Offset(lx - 15f, -5f), size = Size(30f, 6f), cornerRadius = CornerRadius(3f, 3f))
            }
        }
        // Hoodie / Jacket Torso
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(outfitLight, outfitBase, outfitDeep), startY = -142f, endY = -68f),
            topLeft = Offset(-31f, -142f + bodyBobY),
            size = Size(62f, 74f),
            cornerRadius = CornerRadius(20f, 20f),
        )
        // Golden Star Chest Emblem
        drawTinyStar(Offset(0f, -108f + bodyBobY), 11f, Color(0xFFFFE066))
        return
    }

    // Fairy-Tale Ballgowns, Rainbow Tulle, Ballerina Tutu & School Pleated Skirt!
    // Cute legs & satin shoes peeking underneath
    val tightsCol = if (outfitIdx == 5) Color.White else skinBase
    for (side in listOf(-1f, 1f)) {
        val lx = side * 16f
        drawRoundRect(tightsCol, Offset(lx - 7.5f, -46f), Size(15f, 38f), CornerRadius(7f, 7f))
        drawOval(outfitDeep, Offset(lx - 12f, -14f), Size(24f, 15f))
    }

    // Fitted Sweetheart Bodice
    val bodicePath = Path().apply {
        moveTo(-26f, -142f + bodyBobY)
        quadraticTo(0f, -134f + bodyBobY, 26f, -142f + bodyBobY)
        lineTo(22f, -96f + bodyBobY)
        lineTo(-22f, -96f + bodyBobY)
        close()
    }
    drawPath(bodicePath, brush = Brush.verticalGradient(listOf(outfitLight, outfitBase), -142f, -96f))

    // Multi-Tier Scalloped / Pleated Ballgown Skirt!
    val skirtWidth = when (outfitIdx) {
        5 -> 72f // Wide Swan Ballerina Tutu
        9 -> 44f // School Skirt
        else -> 64f
    }
    val skirtBottomY = when (outfitIdx) {
        5 -> -54f
        9 -> -44f
        else -> -16f
    }

    val skirtPath = Path().apply {
        moveTo(-22f, -98f + bodyBobY)
        lineTo(22f, -98f + bodyBobY)
        cubicTo(46f, -76f, skirtWidth + swayX, skirtBottomY - 22f, skirtWidth + swayX, skirtBottomY)
        // Scalloped fairy-tale lace hem!
        val scallops = 5
        val totalW = skirtWidth * 2f
        for (s in 0 until scallops) {
            val x1 = (skirtWidth - (s + 0.5f) * (totalW / scallops)) + swayX
            val x2 = (skirtWidth - (s + 1.0f) * (totalW / scallops)) + swayX
            quadraticTo(x1, skirtBottomY + 11f, x2, skirtBottomY)
        }
        cubicTo(-skirtWidth + swayX, skirtBottomY - 22f, -46f, -76f, -22f, -98f + bodyBobY)
        close()
    }

    val skirtBrush = if (outfitIdx == 2) {
        // Pastel Rainbow Unicorn Tulle Gradient!
        Brush.horizontalGradient(
            listOf(Color(0xFFFF85C2), Color(0xFFD08BFF), Color(0xFF7CE0FF), Color(0xFF9BF6FF))
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(outfitLight, outfitBase, outfitDeep),
            startY = -98f,
            endY = skirtBottomY + 10f,
        )
    }
    drawPath(skirtPath, brush = skirtBrush)
    drawPath(skirtPath, color = Color.White.copy(alpha = 0.55f), style = Stroke(width = 2.2f, join = StrokeJoin.Round))

    // Waist Ribbon / Pearl Belt / Diamond Crystal
    drawRoundRect(
        color = Color.White.copy(alpha = 0.85f),
        topLeft = Offset(-23f, -101f + bodyBobY),
        size = Size(46f, 7f),
        cornerRadius = CornerRadius(4f, 4f),
    )
    if (outfitIdx == 0) {
        // Frost Crystal Center Gem
        drawTinyStar(Offset(0f, -120f + bodyBobY), 8f, Color(0xFFE0FBFC))
    } else if (outfitIdx == 1) {
        // Royal Pearl Necklace
        for (p in -2..2) {
            drawCircle(Color.White, radius = 3.8f, center = Offset(p * 7.5f, -138f + bodyBobY + kotlin.math.abs(p) * 1.5f))
        }
    }
}

private fun DrawScope.drawSculptedArm25D(
    isRight: Boolean,
    skinBase: Color,
    sleeveColor: Color,
    outfitIdx: Int,
    wavePhase: Float,
    isCheering: Boolean,
    mode: LauncherMode,
    bodyBobY: Float,
    handPropIndex: Int,
    wandBmp: ImageBitmap?,
    snowflakeBmp: ImageBitmap?,
) {
    val side = if (isRight) 1f else -1f
    val shoulderPivot = Offset(side * 27f, -134f + bodyBobY)
    val sleeveLight = lerp(sleeveColor, Color.White, 0.35f)

    val armAngle = if (!isRight) {
        16f + sin(wavePhase) * 5f
    } else if (mode == LauncherMode.BEDTIME) {
        -18f
    } else {
        val speedMult = if (isCheering || mode == LauncherMode.TIDY_UP) 2f else 1f
        -118f + sin(wavePhase * speedMult) * 16f
    }

    withTransform({
        translate(shoulderPivot.x, shoulderPivot.y)
        rotate(degrees = armAngle, pivot = Offset.Zero)
    }) {
        // Tapered soft skin arm + round hand
        drawRoundRect(
            color = skinBase,
            topLeft = Offset(-6.5f, 0f),
            size = Size(13f, 44f),
            cornerRadius = CornerRadius(6.5f, 6.5f),
        )
        drawCircle(color = skinBase, radius = 8.5f, center = Offset(0f, 45f))

        // Puff Princess Sleeve Cap
        drawCircle(
            brush = Brush.radialGradient(listOf(sleeveLight, sleeveColor), center = Offset(-2f, 4f), radius = 16f),
            radius = if (outfitIdx in listOf(0, 1, 2, 4)) 14.5f else 11.5f,
            center = Offset(0f, 5f),
        )

        // Held 3D Magic Wand / Snowflake Wand / Heart Scepter!
        if (isRight) {
            when (handPropIndex) {
                1 -> if (wandBmp != null) {
                    withTransform({
                        translate(10f, 56f)
                        rotate(135f, Offset.Zero)
                    }) {
                        drawSpriteCentered(wandBmp, Offset.Zero, 62f)
                    }
                }
                2 -> { // Snowflake Ice Wand
                    drawLine(Color(0xFF90E0EF), Offset(0f, 45f), Offset(18f, 76f), strokeWidth = 4.5f, cap = StrokeCap.Round)
                    if (snowflakeBmp != null) {
                        drawSpriteCentered(snowflakeBmp, Offset(21f, 80f), 34f)
                    }
                }
                3 -> { // Royal Heart Scepter
                    drawLine(Color(0xFFFFD166), Offset(0f, 45f), Offset(18f, 78f), strokeWidth = 5f, cap = StrokeCap.Round)
                    drawCircle(Color(0xFFFF4D8D), radius = 10f, center = Offset(20f, 82f))
                }
            }
        }
    }
}

private fun DrawScope.drawBackWingsAndCapes(
    wingsIndex: Int,
    outfitIndex: Int,
    outfitColor: Color,
    wavePhase: Float,
    pxShift: Float,
    bodyBobY: Float,
    pegasusWingBmp: ImageBitmap?,
    butterflyWingsBmp: ImageBitmap?,
) {
    val flapDeg = sin(wavePhase * 2f) * 11f
    val shoulderCenterY = -126f + bodyBobY

    when (wingsIndex) {
        1, 3, 4 -> if (pegasusWingBmp != null) {
            val tint = when (wingsIndex) {
                3 -> ColorFilter.tint(Color(0xFFA8F0FF), androidx.compose.ui.graphics.BlendMode.Modulate)
                4 -> ColorFilter.tint(Color(0xFFFFE8A3), androidx.compose.ui.graphics.BlendMode.Modulate)
                else -> null
            }
            // Right Feathered 3D Wing
            withTransform({
                translate(18f - pxShift * 0.4f, shoulderCenterY)
                rotate(flapDeg - 8f, Offset(-20f, 10f))
            }) {
                drawSpriteCentered(pegasusWingBmp, Offset(36f, -10f), 108f, tint)
            }
            // Left Mirrored Feathered 3D Wing
            withTransform({
                translate(-18f - pxShift * 0.4f, shoulderCenterY)
                scale(scaleX = -1f, scaleY = 1f, pivot = Offset.Zero)
                rotate(flapDeg - 8f, Offset(-20f, 10f))
            }) {
                drawSpriteCentered(pegasusWingBmp, Offset(36f, -10f), 108f, tint)
            }
        }
        2 -> if (butterflyWingsBmp != null) {
            val flapScaleX = 0.86f + 0.14f * sin(wavePhase * 2f)
            withTransform({
                translate(-pxShift * 0.4f, shoulderCenterY + 6f)
                scale(scaleX = flapScaleX, scaleY = 1f, pivot = Offset.Zero)
            }) {
                drawSpriteCentered(butterflyWingsBmp, Offset.Zero, 185f)
            }
        }
        5, 6, 7 -> {
            val capeCol = when (wingsIndex) {
                5 -> Color(0xFFC9184A)
                6 -> Color(0x997CE0FF)
                else -> Color(0xFF5A189A)
            }
            val cape = Path().apply {
                moveTo(-26f, -138f + bodyBobY)
                lineTo(26f, -138f + bodyBobY)
                quadraticTo(62f - pxShift, -55f, 54f - pxShift, -14f)
                quadraticTo(0f - pxShift, -4f, -54f - pxShift, -14f)
                quadraticTo(-62f - pxShift, -55f, -26f, -138f + bodyBobY)
                close()
            }
            drawPath(cape, color = capeCol)
        }
        else -> if (outfitIndex == 0 || outfitIndex == 7) {
            // Subtle sheer frost cape on Frost Crystal Gown / red cape on Superhero
            val defaultCape = if (outfitIndex == 0) Color(0x6698F5FF) else Color(0xFFE63946)
            val cape = Path().apply {
                moveTo(-24f, -136f + bodyBobY)
                lineTo(24f, -136f + bodyBobY)
                lineTo(48f - pxShift, -20f)
                quadraticTo(0f, -10f, -48f - pxShift, -20f)
                close()
            }
            drawPath(cape, color = defaultCape)
        }
    }
}

private fun DrawScope.drawHeadwear25D(
    accessoryIndex: Int,
    mode: LauncherMode,
    crownBmp: ImageBitmap?,
    ribbonBmp: ImageBitmap?,
    hibiscusBmp: ImageBitmap?,
    headphonesBmp: ImageBitmap?,
) {
    if (mode == LauncherMode.BEDTIME) {
        // Cozy Starry Bedtime Nightcap!
        val cap = Path().apply {
            moveTo(-42f, -38f)
            quadraticTo(0f, -108f, 58f, -58f)
            quadraticTo(28f, -48f, 38f, -38f)
            close()
        }
        drawPath(cap, brush = Brush.linearGradient(listOf(Color(0xFF7B61FF), Color(0xFF4EA8DE))))
        drawCircle(Color(0xFFFFE066), radius = 10f, center = Offset(58f, -58f))
        return
    }

    when (accessoryIndex) {
        1 -> if (crownBmp != null) {
            drawSpriteCentered(crownBmp, Offset(0f, -66f), 68f)
        }
        2 -> { // Golden Spiral Unicorn Horn + Pastel Ears & Blossoms!
            for (side in listOf(-1f, 1f)) {
                val ear = Path().apply {
                    moveTo(side * 20f, -48f)
                    quadraticTo(side * 44f, -76f, side * 38f, -40f)
                    close()
                }
                drawPath(ear, Color.White)
                drawCircle(Color(0xFFFF85C2), radius = 7.5f, center = Offset(side * 16f, -50f))
            }
            val horn = Path().apply {
                moveTo(-9f, -50f)
                lineTo(0f, -98f)
                lineTo(9f, -50f)
                close()
            }
            drawPath(horn, brush = Brush.horizontalGradient(listOf(Color(0xFFFFB703), Color(0xFFFFF3B0), Color(0xFFFB8500))))
            drawCircle(Color(0xFF80ED99), radius = 7f, center = Offset(0f, -49f))
        }
        3 -> { // Crystal Ice Tiara
            val tiara = Path().apply {
                moveTo(-30f, -48f)
                lineTo(-14f, -68f)
                lineTo(0f, -84f)
                lineTo(14f, -68f)
                lineTo(30f, -48f)
                close()
            }
            drawPath(tiara, brush = Brush.verticalGradient(listOf(Color.White, Color(0xFF7CE0FF))))
            drawTinyStar(Offset(0f, -66f), 7f, Color.White)
        }
        4 -> if (hibiscusBmp != null) {
            drawSpriteCentered(hibiscusBmp, Offset(36f, -38f), 44f)
        }
        5 -> if (ribbonBmp != null) {
            drawSpriteCentered(ribbonBmp, Offset(0f, -58f), 58f)
        }
        6 -> { // Flower Wreath
            val cols = listOf(Color(0xFFFF70A6), Color(0xFFFFD670), Color(0xFF70D6FF), Color(0xFFC77DFF), Color(0xFFFF9770))
            for (i in -2..2) {
                drawCircle(cols[i + 2], radius = 8.5f, center = Offset(i * 15f, -46f + kotlin.math.abs(i) * 2.5f))
                drawCircle(Color.White, radius = 3f, center = Offset(i * 15f, -46f + kotlin.math.abs(i) * 2.5f))
            }
        }
        7 -> { // Cute Cat Ears
            for (side in listOf(-1f, 1f)) {
                val ear = Path().apply {
                    moveTo(side * 16f, -48f)
                    lineTo(side * 38f, -80f)
                    lineTo(side * 46f, -40f)
                    close()
                }
                drawPath(ear, Color(0xFF2B2D42))
                val inner = Path().apply {
                    moveTo(side * 22f, -48f)
                    lineTo(side * 36f, -71f)
                    lineTo(side * 41f, -43f)
                    close()
                }
                drawPath(inner, Color(0xFFFF8FA3))
            }
        }
        8 -> { // Fluffy Bunny Ears!
            for (side in listOf(-1f, 1f)) {
                withTransform({
                    translate(side * 22f, -50f)
                    rotate(side * 12f, Offset.Zero)
                }) {
                    drawOval(Color.White, Offset(-10f, -46f), Size(20f, 50f))
                    drawOval(Color(0xFFFFB3C6), Offset(-5.5f, -38f), Size(11f, 34f))
                }
            }
        }
        9 -> { // Wizard Star Hat
            val hat = Path().apply {
                moveTo(-48f, -46f)
                lineTo(0f, -108f)
                lineTo(48f, -46f)
                close()
            }
            drawPath(hat, Color(0xFF5E35B1))
            drawOval(Color(0xFF4527A0), Offset(-58f, -52f), Size(116f, 16f))
            drawTinyStar(Offset(0f, -74f), 9f, Color(0xFFFFE066))
        }
        10 -> if (headphonesBmp != null) {
            drawSpriteCentered(headphonesBmp, Offset(0f, -8f), 124f)
        }
        11 -> { // Pearl Hairclips
            drawCircle(Color.White, radius = 5.5f, center = Offset(34f, -26f))
            drawCircle(Color(0xFFFFB5E8), radius = 4.5f, center = Offset(41f, -19f))
        }
    }
}

private fun DrawScope.drawSpriteCentered(
    bitmap: ImageBitmap,
    center: Offset,
    sizePx: Float,
    colorFilter: ColorFilter? = null,
) {
    val half = sizePx * 0.5f
    drawImage(
        image = bitmap,
        dstOffset = IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()),
        dstSize = IntSize(sizePx.roundToInt().coerceAtLeast(1), sizePx.roundToInt().coerceAtLeast(1)),
        colorFilter = colorFilter,
    )
}

private fun DrawScope.drawTinyStar(center: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(p, color = color)
}
