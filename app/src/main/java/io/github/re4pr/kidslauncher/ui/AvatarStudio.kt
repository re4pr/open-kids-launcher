package io.github.re4pr.kidslauncher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.re4pr.kidslauncher.model.AvatarConfig
import io.github.re4pr.kidslauncher.model.LauncherMode
import io.github.re4pr.kidslauncher.model.SkinDefinition

object AvatarCatalog {
    val skinTones = listOf(
        Color(0xFFFFDFC4),
        Color(0xFFF0C8A0),
        Color(0xFFD8A070),
        Color(0xFFB67849),
        Color(0xFF8C5332),
        Color(0xFF5C3824),
    )

    val hairStyles = listOf(
        "Sunny Curls",
        "Twin Puffs",
        "Royal Braid",
        "Wavy Bob",
        "Star Spikes",
        "Long Flow",
    )

    val hairColors = listOf(
        "Chestnut" to Color(0xFF5A3825),
        "Golden" to Color(0xFFE6B84C),
        "Midnight" to Color(0xFF1F222E),
        "Frost Silver" to Color(0xFFDDF3FF),
        "Unicorn Pink" to Color(0xFFFF70B8),
        "Lavender" to Color(0xFF9D7BFF),
        "Mermaid Teal" to Color(0xFF1AC8B8),
    )

    val eyeStyles = listOf(
        "Sparkle",
        "Happy Arc",
        "Cheerful Wink",
        "Big Wonder",
    )

    val outfits = listOf(
        "Frost Cape" to Color(0xFF34B3F1),
        "Unicorn Hoodie" to Color(0xFFC77DFF),
        "Princess Gown" to Color(0xFFFF5D9E),
        "Mermaid Shimmer" to Color(0xFF00B4A2),
        "Star Explorer" to Color(0xFFFF9F1C),
        "Cozy Pajamas" to Color(0xFF6C63FF),
    )

    val accessories = listOf(
        "None",
        "Snowflake Tiara",
        "Unicorn Horn",
        "Royal Crown",
        "Seashell Clip",
        "Star Hat",
        "Headphones",
    )

    val pets = listOf(
        "None",
        "Snow Fox",
        "Mini Unicorn",
        "Baby Dragon",
        "Coral Seahorse",
    )
}

@Composable
fun KidAvatarCanvas(
    avatar: AvatarConfig,
    mode: LauncherMode,
    sizeDp: Dp = 110.dp,
    modifier: Modifier = Modifier,
) {
    val infinite = rememberInfiniteTransition(label = "avatarBounce")
    val bobDp by infinite.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bobOffset",
    )

    val skinColor = AvatarCatalog.skinTones.getOrElse(avatar.skinToneIndex) { AvatarCatalog.skinTones[1] }
    val hairColor = AvatarCatalog.hairColors.getOrElse(avatar.hairColorIndex) { AvatarCatalog.hairColors[0] }.second
    val outfitColor = if (mode == LauncherMode.BEDTIME) {
        Color(0xFF5E60CE)
    } else {
        AvatarCatalog.outfits.getOrElse(avatar.outfitIndex) { AvatarCatalog.outfits[0] }.second
    }

    Canvas(
        modifier = modifier
            .size(sizeDp)
            .offset(y = bobDp.dp)
    ) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        val headCy = h * 0.46f
        val headR = w * 0.25f

        // Soft glowing backdrop halo
        drawCircle(
            color = Color.White.copy(alpha = 0.26f),
            radius = w * 0.45f,
            center = Offset(cx, h * 0.52f),
        )

        // Back hair layer for long styles
        if (avatar.hairStyleIndex in listOf(2, 3, 5)) {
            drawRoundRect(
                color = hairColor,
                topLeft = Offset(cx - headR * 1.15f, headCy - headR * 0.4f),
                size = Size(headR * 2.3f, headR * 2.15f),
                cornerRadius = CornerRadius(headR * 0.7f, headR * 0.7f),
            )
        }
        if (avatar.hairStyleIndex == 1) {
            // Twin puffs
            drawCircle(color = hairColor, radius = headR * 0.52f, center = Offset(cx - headR * 1.05f, headCy - headR * 0.55f))
            drawCircle(color = hairColor, radius = headR * 0.52f, center = Offset(cx + headR * 1.05f, headCy - headR * 0.55f))
        }

        // Outfit / torso
        val bodyPath = Path().apply {
            moveTo(cx - headR * 0.95f, h * 0.92f)
            quadraticTo(cx - headR * 0.85f, h * 0.68f, cx, h * 0.68f)
            quadraticTo(cx + headR * 0.85f, h * 0.68f, cx + headR * 0.95f, h * 0.92f)
            close()
        }
        drawPath(bodyPath, color = outfitColor)
        // Collar sparkle trim
        drawCircle(color = Color.White.copy(alpha = 0.8f), radius = headR * 0.14f, center = Offset(cx, h * 0.74f))

        // Head
        drawCircle(color = skinColor, radius = headR, center = Offset(cx, headCy))

        // Rosy cheeks
        drawCircle(color = Color(0xFFFF8FA3).copy(alpha = 0.45f), radius = headR * 0.18f, center = Offset(cx - headR * 0.52f, headCy + headR * 0.18f))
        drawCircle(color = Color(0xFFFF8FA3).copy(alpha = 0.45f), radius = headR * 0.18f, center = Offset(cx + headR * 0.52f, headCy + headR * 0.18f))

        // Front hair bangs
        drawFrontHair(avatar.hairStyleIndex, hairColor, cx, headCy, headR)

        // Eyes & mode-reactive expression
        drawEyesAndMouth(avatar.eyeStyleIndex, mode, cx, headCy, headR)

        // Mode-reactive school glasses
        if (mode == LauncherMode.SCHOOL_TIME) {
            val glassR = headR * 0.24f
            drawCircle(Color(0xFF1D3557), radius = glassR, center = Offset(cx - headR * 0.36f, headCy), style = Stroke(width = 3.5f))
            drawCircle(Color(0xFF1D3557), radius = glassR, center = Offset(cx + headR * 0.36f, headCy), style = Stroke(width = 3.5f))
            drawLine(Color(0xFF1D3557), Offset(cx - headR * 0.12f, headCy), Offset(cx + headR * 0.12f, headCy), strokeWidth = 3.5f)
        }

        // Hat / Accessory (or sleepy nightcap star in Bedtime)
        if (mode == LauncherMode.BEDTIME) {
            val cap = Path().apply {
                moveTo(cx - headR * 0.85f, headCy - headR * 0.75f)
                quadraticTo(cx, headCy - headR * 1.85f, cx + headR * 0.95f, headCy - headR * 1.15f)
                lineTo(cx + headR * 0.75f, headCy - headR * 0.72f)
                close()
            }
            drawPath(cap, color = Color(0xFF7B61FF))
            drawCircle(Color(0xFFFFE066), radius = headR * 0.18f, center = Offset(cx + headR * 0.95f, headCy - headR * 1.15f))
        } else {
            drawAccessory(avatar.accessoryIndex, cx, headCy, headR)
        }

        // Companion Pet Badge
        if (avatar.petIndex > 0) {
            drawCompanionPet(avatar.petIndex, Offset(w * 0.83f, h * 0.78f), headR * 0.44f)
        }
    }
}

private fun DrawScope.drawFrontHair(styleIdx: Int, color: Color, cx: Float, cy: Float, r: Float) {
    when (styleIdx) {
        0 -> { // Sunny Curls
            for (i in -2..2) {
                drawCircle(color, radius = r * 0.36f, center = Offset(cx + i * r * 0.38f, cy - r * 0.82f))
            }
        }
        4 -> { // Star Spikes
            val spikes = Path().apply {
                moveTo(cx - r, cy - r * 0.45f)
                lineTo(cx - r * 0.55f, cy - r * 1.25f)
                lineTo(cx - r * 0.15f, cy - r * 0.78f)
                lineTo(cx + r * 0.2f, cy - r * 1.3f)
                lineTo(cx + r * 0.55f, cy - r * 0.78f)
                lineTo(cx + r * 0.95f, cy - r * 1.1f)
                lineTo(cx + r, cy - r * 0.45f)
                close()
            }
            drawPath(spikes, color)
        }
        else -> { // Smooth swept bangs
            val bangs = Path().apply {
                moveTo(cx - r * 1.02f, cy - r * 0.25f)
                quadraticTo(cx, cy - r * 1.35f, cx + r * 1.02f, cy - r * 0.25f)
                quadraticTo(cx + r * 0.3f, cy - r * 0.65f, cx - r * 1.02f, cy - r * 0.25f)
                close()
            }
            drawPath(bangs, color)
        }
    }
}

private fun DrawScope.drawEyesAndMouth(eyeStyle: Int, mode: LauncherMode, cx: Float, cy: Float, r: Float) {
    val leftEye = Offset(cx - r * 0.36f, cy)
    val rightEye = Offset(cx + r * 0.36f, cy)
    val dark = Color(0xFF1F2430)

    if (mode == LauncherMode.BEDTIME || mode == LauncherMode.LOCKED) {
        // Peaceful sleeping closed eyes
        drawArc(dark, startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = Offset(leftEye.x - r * 0.16f, leftEye.y - r * 0.1f), size = Size(r * 0.32f, r * 0.2f), style = Stroke(3f, cap = StrokeCap.Round))
        drawArc(dark, startAngle = 20f, sweepAngle = 140f, useCenter = false, topLeft = Offset(rightEye.x - r * 0.16f, rightEye.y - r * 0.1f), size = Size(r * 0.32f, r * 0.2f), style = Stroke(3f, cap = StrokeCap.Round))
    } else {
        when (eyeStyle) {
            1 -> { // Happy arcs
                drawArc(dark, 200f, 140f, false, Offset(leftEye.x - r * 0.16f, leftEye.y - r * 0.08f), Size(r * 0.32f, r * 0.24f), style = Stroke(3.5f, cap = StrokeCap.Round))
                drawArc(dark, 200f, 140f, false, Offset(rightEye.x - r * 0.16f, rightEye.y - r * 0.08f), Size(r * 0.32f, r * 0.24f), style = Stroke(3.5f, cap = StrokeCap.Round))
            }
            2 -> { // Cheerful wink
                drawCircle(dark, radius = r * 0.13f, center = leftEye)
                drawCircle(Color.White, radius = r * 0.05f, center = Offset(leftEye.x - r * 0.04f, leftEye.y - r * 0.04f))
                drawArc(dark, 200f, 140f, false, Offset(rightEye.x - r * 0.16f, rightEye.y - r * 0.08f), Size(r * 0.32f, r * 0.24f), style = Stroke(3.5f, cap = StrokeCap.Round))
            }
            else -> { // Sparkle / Wonder eyes
                val radius = if (eyeStyle == 3) r * 0.16f else r * 0.13f
                drawCircle(dark, radius = radius, center = leftEye)
                drawCircle(dark, radius = radius, center = rightEye)
                drawCircle(Color.White, radius = radius * 0.38f, center = Offset(leftEye.x - radius * 0.3f, leftEye.y - radius * 0.3f))
                drawCircle(Color.White, radius = radius * 0.38f, center = Offset(rightEye.x - radius * 0.3f, rightEye.y - radius * 0.3f))
            }
        }
    }

    // Friendly smile
    drawArc(
        color = Color(0xFF9E2A47),
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(cx - r * 0.26f, cy + r * 0.18f),
        size = Size(r * 0.52f, r * 0.32f),
        style = Stroke(width = 3.5f, cap = StrokeCap.Round),
    )
}

private fun DrawScope.drawAccessory(accessoryIndex: Int, cx: Float, cy: Float, r: Float) {
    when (accessoryIndex) {
        1 -> { // Snowflake Tiara
            val tiara = Path().apply {
                moveTo(cx - r * 0.55f, cy - r * 0.92f)
                lineTo(cx, cy - r * 1.42f)
                lineTo(cx + r * 0.55f, cy - r * 0.92f)
                close()
            }
            drawPath(tiara, Color(0xFF80E5FF))
            drawCircle(Color.White, radius = r * 0.11f, center = Offset(cx, cy - r * 1.22f))
        }
        2 -> { // Unicorn Horn
            val horn = Path().apply {
                moveTo(cx - r * 0.18f, cy - r * 0.92f)
                lineTo(cx, cy - r * 1.68f)
                lineTo(cx + r * 0.18f, cy - r * 0.92f)
                close()
            }
            drawPath(horn, Color(0xFFFFE066))
            drawCircle(Color(0xFFFF80CA), radius = r * 0.15f, center = Offset(cx - r * 0.25f, cy - r * 0.92f))
            drawCircle(Color(0xFF7AE582), radius = r * 0.15f, center = Offset(cx + r * 0.25f, cy - r * 0.92f))
        }
        3 -> { // Royal Crown
            val crown = Path().apply {
                moveTo(cx - r * 0.55f, cy - r * 0.88f)
                lineTo(cx - r * 0.55f, cy - r * 1.32f)
                lineTo(cx - r * 0.2f, cy - r * 1.08f)
                lineTo(cx, cy - r * 1.45f)
                lineTo(cx + r * 0.2f, cy - r * 1.08f)
                lineTo(cx + r * 0.55f, cy - r * 1.32f)
                lineTo(cx + r * 0.55f, cy - r * 0.88f)
                close()
            }
            drawPath(crown, Color(0xFFFFCA28))
        }
        4 -> { // Seashell Clip
            drawCircle(Color(0xFFFF8A80), radius = r * 0.24f, center = Offset(cx + r * 0.68f, cy - r * 0.62f))
            drawCircle(Color(0xFFFFF59D), radius = r * 0.09f, center = Offset(cx + r * 0.68f, cy - r * 0.62f))
        }
        5 -> { // Star Hat
            val hat = Path().apply {
                moveTo(cx - r * 0.8f, cy - r * 0.85f)
                lineTo(cx, cy - r * 1.65f)
                lineTo(cx + r * 0.8f, cy - r * 0.85f)
                close()
            }
            drawPath(hat, Color(0xFF5E35B1))
            drawCircle(Color(0xFFFFEE58), radius = r * 0.14f, center = Offset(cx, cy - r * 1.25f))
        }
        6 -> { // Headphones
            drawArc(Color(0xFF00E5FF), 180f, 180f, false, Offset(cx - r * 1.1f, cy - r * 1.1f), Size(r * 2.2f, r * 2.1f), style = Stroke(6f))
            drawCircle(Color(0xFF00B8D4), radius = r * 0.26f, center = Offset(cx - r * 1.05f, cy))
            drawCircle(Color(0xFF00B8D4), radius = r * 0.26f, center = Offset(cx + r * 1.05f, cy))
        }
    }
}

private fun DrawScope.drawCompanionPet(petIndex: Int, center: Offset, r: Float) {
    val bodyColor = when (petIndex) {
        1 -> Color(0xFFE0F7FA) // Snow Fox
        2 -> Color(0xFFF8BBD0) // Mini Unicorn
        3 -> Color(0xFFA5D6A7) // Baby Dragon
        else -> Color(0xFFFFAB91) // Coral Seahorse
    }
    drawCircle(Color.White.copy(alpha = 0.85f), radius = r * 1.18f, center = center)
    drawCircle(bodyColor, radius = r, center = center)
    drawCircle(Color(0xFF263238), radius = r * 0.15f, center = Offset(center.x - r * 0.3f, center.y - r * 0.1f))
    drawCircle(Color(0xFF263238), radius = r * 0.15f, center = Offset(center.x + r * 0.3f, center.y - r * 0.1f))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarCustomizationDialog(
    initialAvatar: AvatarConfig,
    availableSkins: List<SkinDefinition>,
    activeSkinId: String,
    customSkinLabels: Map<String, String>,
    onSave: (AvatarConfig, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initialAvatar) }
    var chosenSkin by remember { mutableStateOf(activeSkinId) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFFFFFBFE),
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Customize Your Buddy & World!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF2B194D),
                )
                Spacer(Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .size(148.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE9E2FF)),
                    contentAlignment = Alignment.Center,
                ) {
                    KidAvatarCanvas(
                        avatar = draft,
                        mode = LauncherMode.FREE_PLAY,
                        sizeDp = 132.dp,
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft.childNickname,
                    onValueChange = { draft = draft.copy(childNickname = it.take(20)) },
                    label = { Text("Your Nickname") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                )

                Spacer(Modifier.height(16.dp))
                SectionTitle("Choose Your Theme World")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableSkins.forEach { skin ->
                        val label = customSkinLabels[skin.id]?.takeIf { it.isNotBlank() } ?: skin.name
                        FilterChip(
                            selected = chosenSkin == skin.id,
                            onClick = { chosenSkin = skin.id },
                            label = { Text(label, fontWeight = FontWeight.Bold) },
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                SectionTitle("Skin Tone")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvatarCatalog.skinTones.forEachIndexed { idx, color ->
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (draft.skinToneIndex == idx) 3.dp else 1.dp,
                                    color = if (draft.skinToneIndex == idx) Color(0xFF6200EE) else Color.LightGray,
                                    shape = CircleShape,
                                )
                                .clickable { draft = draft.copy(skinToneIndex = idx) }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                SectionTitle("Hairstyle")
                ChoiceChips(
                    options = AvatarCatalog.hairStyles,
                    selectedIndex = draft.hairStyleIndex,
                    onSelect = { draft = draft.copy(hairStyleIndex = it) },
                )

                Spacer(Modifier.height(14.dp))
                SectionTitle("Hair Color")
                ChoiceChips(
                    options = AvatarCatalog.hairColors.map { it.first },
                    selectedIndex = draft.hairColorIndex,
                    onSelect = { draft = draft.copy(hairColorIndex = it) },
                )

                Spacer(Modifier.height(14.dp))
                SectionTitle("Eyes")
                ChoiceChips(
                    options = AvatarCatalog.eyeStyles,
                    selectedIndex = draft.eyeStyleIndex,
                    onSelect = { draft = draft.copy(eyeStyleIndex = it) },
                )

                Spacer(Modifier.height(14.dp))
                SectionTitle("Outfit")
                ChoiceChips(
                    options = AvatarCatalog.outfits.map { it.first },
                    selectedIndex = draft.outfitIndex,
                    onSelect = { draft = draft.copy(outfitIndex = it) },
                )

                Spacer(Modifier.height(14.dp))
                SectionTitle("Crown, Horn & Hat")
                ChoiceChips(
                    options = AvatarCatalog.accessories,
                    selectedIndex = draft.accessoryIndex,
                    onSelect = { draft = draft.copy(accessoryIndex = it) },
                )

                Spacer(Modifier.height(14.dp))
                SectionTitle("Sidekick Pet")
                ChoiceChips(
                    options = AvatarCatalog.pets,
                    selectedIndex = draft.petIndex,
                    onSelect = { draft = draft.copy(petIndex = it) },
                )

                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(
                        onClick = { onSave(draft, chosenSkin) },
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text("Save My Look!", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF3E2C63),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { idx, label ->
            FilterChip(
                selected = selectedIndex == idx,
                onClick = { onSelect(idx) },
                label = { Text(label) },
            )
        }
    }
}
