package io.github.re4pr.kidslauncher.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
    val context = LocalContext.current
    var draft by remember { mutableStateOf(initialAvatar) }
    var chosenSkin by remember { mutableStateOf(activeSkinId) }

    val previewSkin = remember(availableSkins, chosenSkin) {
        availableSkins.firstOrNull { it.id == chosenSkin } ?: availableSkins.first()
    }
    val previewBackdrop = remember(previewSkin.sceneAssetPath) {
        previewSkin.sceneAssetPath.takeIf { it.isNotBlank() }?.let { path ->
            runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
            }.getOrNull()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
                .padding(12.dp),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFFFFFBFE),
            tonalElevation = 10.dp,
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                // Left Pane: Live Interactive 360° 3D Stage on Selected Theme World!
                Column(
                    modifier = Modifier.weight(0.42f).fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "3D Buddy Studio",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF2B194D),
                    )
                    Text(
                        text = "Drag to spin 360° • Tap to jump!",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF6E5B9A),
                    )
                    Spacer(Modifier.height(10.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(Color(0xFF1F2B52)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (previewBackdrop != null) {
                            Image(
                                bitmap = previewBackdrop,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Avatar3DStage(
                            avatar = draft,
                            mode = LauncherMode.FREE_PLAY,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = draft.childNickname,
                        onValueChange = { draft = draft.copy(childNickname = it.take(20)) },
                        label = { Text("Your Nickname") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Right Pane: Kid-Friendly Wardrobe & Theme World Controls
                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight(),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
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
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (draft.skinToneIndex == idx) 3.5.dp else 1.dp,
                                            color = if (draft.skinToneIndex == idx) Color(0xFF6200EE) else Color.LightGray,
                                            shape = CircleShape,
                                        )
                                        .clickable { draft = draft.copy(skinToneIndex = idx) }
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        SectionTitle("3D Hairstyle")
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
                        SectionTitle("3D Outfit")
                        ChoiceChips(
                            options = AvatarCatalog.outfits.map { it.first },
                            selectedIndex = draft.outfitIndex,
                            onSelect = { draft = draft.copy(outfitIndex = it) },
                        )

                        Spacer(Modifier.height(14.dp))
                        SectionTitle("3D Crown, Horn & Hat")
                        ChoiceChips(
                            options = AvatarCatalog.accessories,
                            selectedIndex = draft.accessoryIndex,
                            onSelect = { draft = draft.copy(accessoryIndex = it) },
                        )

                        Spacer(Modifier.height(14.dp))
                        SectionTitle("3D Sidekick Pet")
                        ChoiceChips(
                            options = AvatarCatalog.pets,
                            selectedIndex = draft.petIndex,
                            onSelect = { draft = draft.copy(petIndex = it) },
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    ) {
                        OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(20.dp)) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = { onSave(draft, chosenSkin) },
                            shape = RoundedCornerShape(20.dp),
                        ) {
                            Text("Save My 3D Look!", fontWeight = FontWeight.ExtraBold)
                        }
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
