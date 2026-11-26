package io.github.re4pr.kidslauncher.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.isSpecified
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
 * Studio 3D Animated Storybook Puppet + 16 Plush 3D Companion Pets (v2.1.0).
 *
 * Uses Nano Banana 2.1 high-resolution 3D-rendered character outfits, articulated 3D hairstyles,
 * real 3D closed-eyelash blinking, luminance-preserving hair & dress tint layers, royal 3D crowns,
 * unicorn horns, frost tiaras, magic wands, flapping 3D wings, and 16 plush 3D companion pets
 * driven by a continuous monotonic vsync clock (zero motion resets or jumps).
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

    // Continuous C-infinity sine signals driven by monotonic elapsedSec — ZERO loop resets!
    val breath = sin(elapsedSec * 2.3f)
    val wavePhase = elapsedSec * 2.3f

    val effectiveOutfitIdx = if (mode == LauncherMode.BEDTIME) 11 else avatar.outfitIndex.coerceIn(0, 11)
    val outfitCode = "%02d".format(effectiveOutfitIdx)

    // Hairstyle 0 uses the 1-piece full-body 3D render with flowing shoulder waves;
    // Hairstyles 1..11 composite the clean anatomical collar body + articulated 3D head bone!
    val headAssetTag: String? = remember(avatar.hairStyleIndex) {
        when (avatar.hairStyleIndex) {
            0 -> null // Signature flowing princess waves in outfit_XX.webp
            1, 8 -> "head_01" // Frost side braid / Crown braid
            2 -> "head_02"    // Twin ribbon braids
            3, 9, 10 -> "head_03" // High bouncy ponytail / Sporty up-style
            4 -> "head_04"    // Playful high twin pigtails
            5, 11 -> "head_05" // Fluffy cloud curls / Fairy curls
            6 -> "head_02"    // Twin braids / bob fallback
            7 -> "head_07"    // Double space buns & bangs
            else -> null
        }
    }

    val fullOutfitBmp = remember(outfitCode) { PuppetSpriteCache.load(context, "puppet/outfits/outfit_$outfitCode.webp") }
    val collarBodyBmp = remember(outfitCode) { PuppetSpriteCache.load(context, "puppet/bodies/body_$outfitCode.webp") }
    val outfitHairMask = remember(outfitCode) { PuppetSpriteCache.load(context, "puppet/outfits/outfit_${outfitCode}_hair.webp") }
    val outfitDressMask = remember(outfitCode) { PuppetSpriteCache.load(context, "puppet/outfits/outfit_${outfitCode}_dress.webp") }

    val customHeadBmp = remember(headAssetTag) { headAssetTag?.let { PuppetSpriteCache.load(context, "puppet/heads/$it.webp") } }
    val customHeadHairMask = remember(headAssetTag) { headAssetTag?.let { PuppetSpriteCache.load(context, "puppet/heads/${it}_hair.webp") } }

    val blinkOutfitPatch = remember { PuppetSpriteCache.load(context, "puppet/heads/blink_eyes_patch.webp") }
    val blinkHeadPatch = remember { PuppetSpriteCache.load(context, "puppet/heads/blink_eyes_head.webp") }

    // 3D Props, Wings & 16 Plush 3D Companion Pets
    val petPath = AvatarCatalog.petEntries.getOrElse(avatar.petIndex) { AvatarCatalog.petEntries[0] }.second
    val petBitmap = remember(petPath) { PuppetSpriteCache.load(context, petPath) }
    val pegasusWingBmp = remember { PuppetSpriteCache.load(context, "puppet/props/pegasus_wing.webp") }
    val butterflyWingsBmp = remember { PuppetSpriteCache.load(context, "puppet/props/butterfly_wings.webp") }
    val crownBmp = remember { PuppetSpriteCache.load(context, "puppet/props/crown.webp") }
    val unicornHornBmp = remember { PuppetSpriteCache.load(context, "puppet/props/unicorn_horn.webp") }
    val iceTiaraBmp = remember { PuppetSpriteCache.load(context, "puppet/props/ice_tiara.webp") }
    val wandBmp = remember { PuppetSpriteCache.load(context, "puppet/props/magic_wand.webp") }
    val ribbonBmp = remember { PuppetSpriteCache.load(context, "puppet/props/ribbon_bow.webp") }
    val hibiscusBmp = remember { PuppetSpriteCache.load(context, "puppet/props/hibiscus.webp") }
    val headphonesBmp = remember { PuppetSpriteCache.load(context, "puppet/props/headphones.webp") }
    val sunglassesBmp = remember { PuppetSpriteCache.load(context, "puppet/props/sunglasses.webp") }
    val sparklesBmp = remember { PuppetSpriteCache.load(context, "puppet/props/sparkles.webp") }
    val snowflakeBmp = remember { PuppetSpriteCache.load(context, "puppet/props/snowflake.webp") }

    val hairTintColor: Color? = remember(avatar.hairColorIndex) {
        if (avatar.hairColorIndex <= 0) null
        else AvatarCatalog.hairColors.getOrNull(avatar.hairColorIndex)?.second
    }
    val dressDyeColor: Color? = remember(avatar.outfitColorIndex, mode) {
        if (mode == LauncherMode.BEDTIME || avatar.outfitColorIndex <= 0) null
        else AvatarCatalog.outfitColorOverrides.getOrNull(avatar.outfitColorIndex)?.second?.takeIf { it.isSpecified }
    }
    val irisCustomColor: Color? = remember(avatar.eyeColorIndex) {
        if (avatar.eyeColorIndex <= 0) null
        else AvatarCatalog.eyeColors.getOrNull(avatar.eyeColorIndex)?.second
    }

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
            -sin((cheerElapsed / 0.95f) * PI.toFloat()).coerceAtLeast(0f) * unit * 0.13f
        } else {
            0f
        }

        val hasPet = petBitmap != null && avatar.petIndex > 0
        val charCx = if (hasPet) w * 0.37f else w * 0.50f
        val groundY = h * 0.92f

        // Character sprite bounding box (registered 540x720 = 3:4 aspect; shoes rest at 0.965 * dstH)
        val dstH = (h * 0.93f).coerceAtLeast(60f)
        val dstW = dstH * 0.75f
        val footInSpriteY = dstH * 0.965f

        val nowMs = (elapsedSec * 1000f).toLong()
        val blinkCycle = nowMs % 3900L
        val isBlinking = mode == LauncherMode.BEDTIME ||
            mode == LauncherMode.LOCKED ||
            avatar.eyeStyleIndex == 2 ||
            blinkCycle in 3730L..3880L

        // 1. Soft Ambient Ground Shadow under Character
        val shadowScale = 1f + (jumpOffset / (unit * 0.50f))
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x58071024), Color.Transparent),
                center = Offset(charCx, groundY),
                radius = dstW * 0.36f * shadowScale,
            ),
            topLeft = Offset(charCx - dstW * 0.34f * shadowScale, groundY - dstH * 0.032f),
            size = Size(dstW * 0.68f * shadowScale, dstH * 0.068f),
        )

        val breathScaleX = 1f - breath * 0.010f
        val breathScaleY = 1f + breath * 0.013f
        val bodySwayDeg = sin(wavePhase * 0.75f) * 1.1f + smoothParallax * 2.6f
        val headNodDeg = sin(wavePhase) * 2.4f + smoothParallax * 4.2f
        val pxShift = smoothParallax * (dstW * 0.028f)

        // 2. 2.5D Articulated 3D Character Root (Anchored at her shoes: charCx, groundY + jumpOffset)
        withTransform({
            translate(left = charCx, top = groundY + jumpOffset)
            rotate(degrees = bodySwayDeg, pivot = Offset.Zero)
            scale(scaleX = breathScaleX, scaleY = breathScaleY, pivot = Offset.Zero)
        }) {
            val spriteTopLeft = Offset(-dstW * 0.5f, -footInSpriteY)
            val spriteIntOff = IntOffset(spriteTopLeft.x.roundToInt(), spriteTopLeft.y.roundToInt())
            val spriteIntSize = IntSize(dstW.roundToInt().coerceAtLeast(1), dstH.roundToInt().coerceAtLeast(1))

            // A. Animated Flapping 3D Wings & Capes (behind shoulders!)
            drawBackWings3D(
                wingsIndex = avatar.wingsBackIndex,
                wavePhase = wavePhase,
                pxShift = pxShift,
                shoulderY = spriteTopLeft.y + dstH * 0.51f,
                dstW = dstW,
                pegasusWingBmp = pegasusWingBmp,
                butterflyWingsBmp = butterflyWingsBmp,
            )

            val useSplitHead = customHeadBmp != null && collarBodyBmp != null
            if (!useSplitHead) {
                // B1. Seamless 1-Piece Full-Body 3D Render (Flowing Princess Waves)
                if (fullOutfitBmp != null) {
                    drawImage(
                        image = fullOutfitBmp,
                        dstOffset = spriteIntOff,
                        dstSize = spriteIntSize,
                    )
                }
                if (dressDyeColor != null && outfitDressMask != null) {
                    drawImage(
                        image = outfitDressMask,
                        dstOffset = spriteIntOff,
                        dstSize = spriteIntSize,
                        alpha = 0.78f,
                        colorFilter = ColorFilter.tint(dressDyeColor, BlendMode.Modulate),
                    )
                }
                if (hairTintColor != null && outfitHairMask != null) {
                    drawImage(
                        image = outfitHairMask,
                        dstOffset = spriteIntOff,
                        dstSize = spriteIntSize,
                        alpha = 0.80f,
                        colorFilter = ColorFilter.tint(hairTintColor, BlendMode.Modulate),
                    )
                }

                val eyeCenterY = spriteTopLeft.y + dstH * (222f / 720f)
                drawFaceCustomizationsAndHeadwear(
                    avatar = avatar,
                    mode = mode,
                    isBlinking = isBlinking,
                    blinkPatch = blinkOutfitPatch,
                    irisCustomColor = irisCustomColor,
                    spriteIntOff = spriteIntOff,
                    spriteIntSize = spriteIntSize,
                    eyeCenterY = eyeCenterY,
                    hairTopY = spriteTopLeft.y + dstH * 0.065f,
                    dstW = dstW,
                    crownBmp = crownBmp,
                    unicornHornBmp = unicornHornBmp,
                    iceTiaraBmp = iceTiaraBmp,
                    ribbonBmp = ribbonBmp,
                    hibiscusBmp = hibiscusBmp,
                    headphonesBmp = headphonesBmp,
                    sunglassesBmp = sunglassesBmp,
                )
            } else {
                // B2. Articulated 2-Bone 3D Rig: Clean Anatomical Collar Body + Independent Tilting 3D Head!
                drawImage(
                    image = collarBodyBmp!!,
                    dstOffset = spriteIntOff,
                    dstSize = spriteIntSize,
                )
                if (dressDyeColor != null && outfitDressMask != null) {
                    drawImage(
                        image = outfitDressMask,
                        dstOffset = spriteIntOff,
                        dstSize = spriteIntSize,
                        alpha = 0.78f,
                        colorFilter = ColorFilter.tint(dressDyeColor, BlendMode.Modulate),
                    )
                }

                val neckPivot = Offset(0f, spriteTopLeft.y + dstH * 0.405f)
                withTransform({
                    translate(left = pxShift * 0.45f, top = 0f)
                    rotate(degrees = headNodDeg, pivot = neckPivot)
                }) {
                    drawImage(
                        image = customHeadBmp!!,
                        dstOffset = spriteIntOff,
                        dstSize = spriteIntSize,
                    )
                    if (hairTintColor != null && customHeadHairMask != null) {
                        drawImage(
                            image = customHeadHairMask,
                            dstOffset = spriteIntOff,
                            dstSize = spriteIntSize,
                            alpha = 0.80f,
                            colorFilter = ColorFilter.tint(hairTintColor, BlendMode.Modulate),
                        )
                    }

                    val eyeCenterY = spriteTopLeft.y + dstH * (246f / 720f)
                    drawFaceCustomizationsAndHeadwear(
                        avatar = avatar,
                        mode = mode,
                        isBlinking = isBlinking,
                        blinkPatch = blinkHeadPatch,
                        irisCustomColor = irisCustomColor,
                        spriteIntOff = spriteIntOff,
                        spriteIntSize = spriteIntSize,
                        eyeCenterY = eyeCenterY,
                        hairTopY = spriteTopLeft.y + dstH * 0.060f,
                        dstW = dstW,
                        crownBmp = crownBmp,
                        unicornHornBmp = unicornHornBmp,
                        iceTiaraBmp = iceTiaraBmp,
                        ribbonBmp = ribbonBmp,
                        hibiscusBmp = hibiscusBmp,
                        headphonesBmp = headphonesBmp,
                        sunglassesBmp = sunglassesBmp,
                    )
                }
            }

            // C. Held 3D Magic Star Wand / Ice Wand / Heart Scepter in Right Hand!
            if (mode != LauncherMode.BEDTIME) {
                val handAnchor = Offset(dstW * 0.31f, spriteTopLeft.y + dstH * 0.595f)
                val wandSway = sin(wavePhase * (if (isCheering) 2.2f else 1.1f)) * 12f
                when (avatar.handFacePropIndex) {
                    1 -> if (wandBmp != null) {
                        withTransform({
                            translate(handAnchor.x, handAnchor.y)
                            rotate(wandSway + 12f, Offset(-dstW * 0.06f, dstW * 0.06f))
                        }) {
                            drawSpriteCentered(wandBmp, Offset(dstW * 0.05f, -dstW * 0.11f), dstW * 0.34f)
                        }
                    }
                    2 -> if (snowflakeBmp != null) {
                        withTransform({
                            translate(handAnchor.x, handAnchor.y)
                            rotate(wandSway, Offset.Zero)
                        }) {
                            drawLine(Color(0xFF90E0EF), Offset.Zero, Offset(dstW * 0.11f, -dstW * 0.18f), strokeWidth = 5f, cap = StrokeCap.Round)
                            drawSpriteCentered(snowflakeBmp, Offset(dstW * 0.12f, -dstW * 0.20f), dstW * 0.16f)
                        }
                    }
                    3 -> {
                        withTransform({
                            translate(handAnchor.x, handAnchor.y)
                            rotate(wandSway, Offset.Zero)
                        }) {
                            drawLine(Color(0xFFFFD166), Offset.Zero, Offset(dstW * 0.11f, -dstW * 0.18f), strokeWidth = 5.5f, cap = StrokeCap.Round)
                            drawCircle(Color(0xFFFF4D8D), radius = dstW * 0.045f, center = Offset(dstW * 0.12f, -dstW * 0.20f))
                        }
                    }
                }
            }

            // D. Sparkle Aura (when cheering or Sparkle Aura selected!)
            if ((isCheering || avatar.handFacePropIndex == 7) && sparklesBmp != null) {
                val pulse = 0.78f + 0.22f * sin(wavePhase * 2.2f)
                drawSpriteCentered(sparklesBmp, Offset(-dstW * 0.38f, spriteTopLeft.y + dstH * 0.22f + breath * 6f), dstW * 0.18f * pulse)
                drawSpriteCentered(sparklesBmp, Offset(dstW * 0.39f, spriteTopLeft.y + dstH * 0.42f - breath * 6f), dstW * 0.15f * pulse)
            }
        }

        // 3. Plush 3D Companion Pet Stage (Continuous C-infinity smooth motion, ZERO loop resets!)
        if (hasPet && petBitmap != null) {
            val petCx = w * 0.76f
            val isFloatingPet = avatar.petIndex in listOf(1, 6, 8, 9, 12, 16)
            val smoothFloat = sin(elapsedSec * 2.05f)
            val petBob = if (isFloatingPet) {
                smoothFloat * unit * 0.024f - unit * 0.040f
            } else {
                (smoothFloat - 1f) * 0.5f * unit * 0.015f
            }
            val petJump = if (isCheering) jumpOffset * 0.85f else 0f
            val petSize = unit * 0.44f

            // Ground shadow beneath plush 3D pet
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x50081026), Color.Transparent),
                    center = Offset(petCx, groundY + 2f),
                    radius = petSize * 0.42f,
                ),
                topLeft = Offset(petCx - petSize * 0.40f, groundY - 10f),
                size = Size(petSize * 0.80f, 22f),
            )

            val petBreath = sin(elapsedSec * 2.05f + 0.6f)
            val squashX = 1f + petBreath * 0.020f
            val squashY = 1f - petBreath * 0.020f
            val petTilt = sin(elapsedSec * 1.40f) * 3.4f - smoothParallax * 4.5f

            withTransform({
                translate(left = petCx, top = groundY + petBob + petJump - petSize * 0.46f)
                rotate(degrees = petTilt, pivot = Offset(0f, petSize * 0.35f))
                scale(scaleX = squashX, scaleY = squashY, pivot = Offset(0f, petSize * 0.45f))
            }) {
                drawImage(
                    image = petBitmap,
                    dstOffset = IntOffset((-petSize * 0.5f).roundToInt(), (-petSize * 0.5f).roundToInt()),
                    dstSize = IntSize(petSize.roundToInt().coerceAtLeast(1), petSize.roundToInt().coerceAtLeast(1)),
                )
            }
        }
    }
}

private fun DrawScope.drawFaceCustomizationsAndHeadwear(
    avatar: AvatarConfig,
    mode: LauncherMode,
    isBlinking: Boolean,
    blinkPatch: ImageBitmap?,
    irisCustomColor: Color?,
    spriteIntOff: IntOffset,
    spriteIntSize: IntSize,
    eyeCenterY: Float,
    hairTopY: Float,
    dstW: Float,
    crownBmp: ImageBitmap?,
    unicornHornBmp: ImageBitmap?,
    iceTiaraBmp: ImageBitmap?,
    ribbonBmp: ImageBitmap?,
    hibiscusBmp: ImageBitmap?,
    headphonesBmp: ImageBitmap?,
    sunglassesBmp: ImageBitmap?,
) {
    val halfIpd = dstW * 0.095f

    // 1. Custom Eye Iris Color Glaze (when open) OR Real 3D Closed-Eyelash Blink Patch!
    if (isBlinking && blinkPatch != null) {
        drawImage(
            image = blinkPatch,
            dstOffset = spriteIntOff,
            dstSize = spriteIntSize,
        )
    } else if (irisCustomColor != null) {
        val irisR = dstW * 0.036f
        for (side in listOf(-1f, 1f)) {
            val ic = Offset(side * halfIpd, eyeCenterY)
            drawCircle(color = irisCustomColor.copy(alpha = 0.52f), radius = irisR, center = ic)
            drawCircle(color = Color(0xFF14111F), radius = irisR * 0.46f, center = ic)
            drawCircle(color = Color.White, radius = irisR * 0.24f, center = Offset(ic.x - irisR * 0.32f, ic.y - irisR * 0.34f))
        }
    }

    // 2. Extra Cheek Stickers (Freckles / Stars / Hearts)
    val cheekY = eyeCenterY + dstW * 0.062f
    val cheekX = dstW * 0.125f
    when (avatar.cheekStyleIndex) {
        2 -> {
            val fc = Color(0x998D4F32)
            for (side in listOf(-1f, 1f)) {
                val bx = side * cheekX
                drawCircle(fc, dstW * 0.006f, Offset(bx - dstW * 0.016f, cheekY))
                drawCircle(fc, dstW * 0.006f, Offset(bx, cheekY + dstW * 0.010f))
                drawCircle(fc, dstW * 0.006f, Offset(bx + dstW * 0.016f, cheekY - dstW * 0.002f))
            }
        }
        3 -> {
            for (side in listOf(-1f, 1f)) {
                drawTinyStar(Offset(side * cheekX, cheekY), dstW * 0.022f, Color(0xFFFFD166))
            }
        }
        4 -> {
            for (side in listOf(-1f, 1f)) {
                drawCircle(Color(0x88FF477E), dstW * 0.018f, Offset(side * cheekX, cheekY))
            }
        }
    }

    // 3. Glasses / 3D Sunglasses
    when {
        avatar.handFacePropIndex == 4 && sunglassesBmp != null && mode != LauncherMode.BEDTIME -> {
            drawSpriteCentered(sunglassesBmp, Offset(0f, eyeCenterY), dstW * 0.34f)
        }
        mode == LauncherMode.SCHOOL_TIME || avatar.handFacePropIndex in listOf(5, 6) -> {
            val frameCol = if (avatar.handFacePropIndex == 6) Color(0xFFFF4D94) else Color(0xFF2B3A67)
            val gr = dstW * 0.058f
            drawCircle(frameCol, radius = gr, center = Offset(-halfIpd, eyeCenterY), style = Stroke(width = 3.5f))
            drawCircle(frameCol, radius = gr, center = Offset(halfIpd, eyeCenterY), style = Stroke(width = 3.5f))
            drawLine(frameCol, Offset(-halfIpd + gr, eyeCenterY), Offset(halfIpd - gr, eyeCenterY), strokeWidth = 3.5f)
        }
    }

    // 4. 3D Crowns, Golden Unicorn Horn, Crystal Ice Tiara, Bows & Headphones!
    if (mode == LauncherMode.BEDTIME) {
        return
    }
    when (avatar.accessoryIndex) {
        1 -> if (crownBmp != null) {
            drawSpriteCentered(crownBmp, Offset(0f, hairTopY + dstW * 0.02f), dstW * 0.28f)
        }
        2 -> if (unicornHornBmp != null) {
            drawSpriteCentered(unicornHornBmp, Offset(0f, hairTopY + dstW * 0.03f), dstW * 0.36f)
        }
        3 -> if (iceTiaraBmp != null) {
            drawSpriteCentered(iceTiaraBmp, Offset(0f, hairTopY + dstW * 0.03f), dstW * 0.31f)
        }
        4 -> if (hibiscusBmp != null) {
            drawSpriteCentered(hibiscusBmp, Offset(dstW * 0.15f, hairTopY + dstW * 0.14f), dstW * 0.16f)
        }
        5 -> if (ribbonBmp != null) {
            drawSpriteCentered(ribbonBmp, Offset(0f, hairTopY + dstW * 0.04f), dstW * 0.24f)
        }
        6 -> { // Blossom Wreath
            val cols = listOf(Color(0xFFFF70A6), Color(0xFFFFD670), Color(0xFF70D6FF), Color(0xFFC77DFF), Color(0xFFFF9770))
            for (i in -2..2) {
                val cx = i * dstW * 0.052f
                val cy = hairTopY + dstW * 0.09f + kotlin.math.abs(i) * dstW * 0.008f
                drawCircle(cols[i + 2], radius = dstW * 0.028f, center = Offset(cx, cy))
                drawCircle(Color.White, radius = dstW * 0.010f, center = Offset(cx, cy))
            }
        }
        7 -> { // Cat Ears
            for (side in listOf(-1f, 1f)) {
                val ear = Path().apply {
                    moveTo(side * dstW * 0.06f, hairTopY + dstW * 0.08f)
                    lineTo(side * dstW * 0.15f, hairTopY - dstW * 0.04f)
                    lineTo(side * dstW * 0.19f, hairTopY + dstW * 0.11f)
                    close()
                }
                drawPath(ear, Color(0xFF2B2D42))
            }
        }
        8 -> { // Fluffy Bunny Ears
            for (side in listOf(-1f, 1f)) {
                drawOval(
                    Color.White,
                    Offset(side * dstW * 0.09f - dstW * 0.035f, hairTopY - dstW * 0.11f),
                    Size(dstW * 0.07f, dstW * 0.18f),
                )
                drawOval(
                    Color(0xFFFFB3C6),
                    Offset(side * dstW * 0.09f - dstW * 0.018f, hairTopY - dstW * 0.08f),
                    Size(dstW * 0.036f, dstW * 0.12f),
                )
            }
        }
        9 -> { // Wizard Star Hat
            val hat = Path().apply {
                moveTo(-dstW * 0.18f, hairTopY + dstW * 0.08f)
                lineTo(0f, hairTopY - dstW * 0.16f)
                lineTo(dstW * 0.18f, hairTopY + dstW * 0.08f)
                close()
            }
            drawPath(hat, Color(0xFF5E35B1))
            drawOval(Color(0xFF4527A0), Offset(-dstW * 0.22f, hairTopY + dstW * 0.055f), Size(dstW * 0.44f, dstW * 0.055f))
            drawTinyStar(Offset(0f, hairTopY - dstW * 0.02f), dstW * 0.030f, Color(0xFFFFE066))
        }
        10 -> if (headphonesBmp != null) {
            drawSpriteCentered(headphonesBmp, Offset(0f, eyeCenterY - dstW * 0.04f), dstW * 0.48f)
        }
        11 -> { // Pearl Hairclips
            drawCircle(Color.White, radius = dstW * 0.018f, center = Offset(dstW * 0.13f, hairTopY + dstW * 0.14f))
            drawCircle(Color(0xFFFFB5E8), radius = dstW * 0.014f, center = Offset(dstW * 0.155f, hairTopY + dstW * 0.165f))
        }
    }
}

private fun DrawScope.drawBackWings3D(
    wingsIndex: Int,
    wavePhase: Float,
    pxShift: Float,
    shoulderY: Float,
    dstW: Float,
    pegasusWingBmp: ImageBitmap?,
    butterflyWingsBmp: ImageBitmap?,
) {
    val flapDeg = sin(wavePhase * 2f) * 10f
    when (wingsIndex) {
        1, 3, 4 -> if (pegasusWingBmp != null) {
            val tint = when (wingsIndex) {
                3 -> ColorFilter.tint(Color(0xFFA8F0FF), BlendMode.Modulate)
                4 -> ColorFilter.tint(Color(0xFFFFE8A3), BlendMode.Modulate)
                else -> null
            }
            val wingSize = dstW * 0.40f
            withTransform({
                translate(dstW * 0.08f - pxShift * 0.4f, shoulderY)
                rotate(flapDeg - 8f, Offset(-wingSize * 0.2f, wingSize * 0.1f))
            }) {
                drawSpriteCentered(pegasusWingBmp, Offset(wingSize * 0.36f, -wingSize * 0.08f), wingSize, tint)
            }
            withTransform({
                translate(-dstW * 0.08f - pxShift * 0.4f, shoulderY)
                scale(scaleX = -1f, scaleY = 1f, pivot = Offset.Zero)
                rotate(flapDeg - 8f, Offset(-wingSize * 0.2f, wingSize * 0.1f))
            }) {
                drawSpriteCentered(pegasusWingBmp, Offset(wingSize * 0.36f, -wingSize * 0.08f), wingSize, tint)
            }
        }
        2 -> if (butterflyWingsBmp != null) {
            val flapScaleX = 0.86f + 0.14f * sin(wavePhase * 2f)
            withTransform({
                translate(-pxShift * 0.4f, shoulderY + dstW * 0.04f)
                scale(scaleX = flapScaleX, scaleY = 1f, pivot = Offset.Zero)
            }) {
                drawSpriteCentered(butterflyWingsBmp, Offset.Zero, dstW * 0.72f)
            }
        }
        5, 6, 7 -> {
            val capeCol = when (wingsIndex) {
                5 -> Color(0xFFC9184A)
                6 -> Color(0x997CE0FF)
                else -> Color(0xFF5A189A)
            }
            val cape = Path().apply {
                moveTo(-dstW * 0.12f, shoulderY - dstW * 0.04f)
                lineTo(dstW * 0.12f, shoulderY - dstW * 0.04f)
                quadraticTo(dstW * 0.28f - pxShift, shoulderY + dstW * 0.30f, dstW * 0.24f - pxShift, shoulderY + dstW * 0.52f)
                quadraticTo(-pxShift, shoulderY + dstW * 0.56f, -dstW * 0.24f - pxShift, shoulderY + dstW * 0.52f)
                quadraticTo(-dstW * 0.28f - pxShift, shoulderY + dstW * 0.30f, -dstW * 0.12f, shoulderY - dstW * 0.04f)
                close()
            }
            drawPath(cape, color = capeCol)
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
