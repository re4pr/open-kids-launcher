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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
        Color(0xFFFFE7D3),
        Color(0xFFF6D2B4),
        Color(0xFFE7B892),
        Color(0xFFD2996C),
        Color(0xFFB4784E),
        Color(0xFF935A37),
        Color(0xFF704127),
        Color(0xFF4E2B1A),
    )

    val cheekStyles = listOf(
        "None",
        "Rosy Blush",
        "Cute Freckles",
        "Star Cheeks",
        "Berry Glow",
    )

    val eyeStyles = listOf(
        "Pixar Lash",
        "Big Sparkle",
        "Happy Smile",
        "Playful Wink",
        "Dreamy Almond",
        "Starry Wide",
    )

    val eyeColors = listOf(
        "Sapphire Blue" to Color(0xFF2575FC),
        "Emerald Green" to Color(0xFF16A085),
        "Warm Chestnut" to Color(0xFF6E3F24),
        "Honey Amber" to Color(0xFFD48817),
        "Amethyst Violet" to Color(0xFF8E44AD),
        "Ocean Aqua" to Color(0xFF00B4D8),
        "Rose Quartz" to Color(0xFFE05697),
        "Deep Espresso" to Color(0xFF2B1E1A),
    )

    val hairStyles = listOf(
        "Long Wavy Locks",
        "Twin Twisted Braids",
        "High Bouncy Ponytail",
        "Twin Pigtail Ribbons",
        "Princess Half-Up Curls",
        "Fluffy Cloud Curls",
        "Side-Swept Bob",
        "Space Buns & Bangs",
        "Royal Crown Braid",
        "Short Pixie Crop",
        "Swept Hero Spikes",
        "Curtain Waves",
    )

    val hairColors = listOf(
        "Warm Chestnut" to Color(0xFF5C3824),
        "Honey Blonde" to Color(0xFFE8BE5A),
        "Platinum Frost" to Color(0xFFE8F6FF),
        "Midnight Black" to Color(0xFF1F222E),
        "Auburn Copper" to Color(0xFFA84224),
        "Unicorn Pink" to Color(0xFFFF6BB5),
        "Cotton Candy" to Color(0xFFFFA6DC),
        "Royal Lavender" to Color(0xFF9E7BFF),
        "Mermaid Teal" to Color(0xFF14C8B8),
        "Sky Blue" to Color(0xFF48B2FF),
        "Mint Sorbet" to Color(0xFF5CE6B8),
        "Sunset Rose" to Color(0xFFFF6E7F),
    )

    // Outfits: index determines 3D silhouette (pleated gown, mermaid fin, hoodie+jeans, tutu, blazer+skirt, etc.)
    val outfits = listOf(
        "Frost Queen Gown" to Color(0xFF32B5F4),        // 0: pleated gown + crystal sash
        "Princess Ballgown" to Color(0xFFFF5D9E),       // 1: puffed-sleeve ballgown
        "Unicorn Hoodie & Jeans" to Color(0xFFC77DFF),  // 2: hoodie + jeans + sneakers
        "Mermaid Fin Gown" to Color(0xFF00BFA5),        // 3: seashell bodice + mermaid tail fin hem
        "Fairy Blossom Dress" to Color(0xFFFF85C0),     // 4: petal pleated dress
        "Ballerina Tutu" to Color(0xFFFFB3D9),          // 5: wide layered tutu + ballet slippers
        "Explorer Jacket & Pants" to Color(0xFFFF9F1C), // 6: jacket + cargo pants + boots
        "Superhero Suit" to Color(0xFFE63946),          // 7: hero suit + belt
        "Denim Overalls" to Color(0xFF457B9D),          // 8: overalls + tee + sneakers
        "School Blazer & Skirt" to Color(0xFF2B4C7E),   // 9: blazer + white collar + pleated skirt
        "Space Astronaut" to Color(0xFFEDF2F4),         // 10: space suit + cyan trim
        "Starlight Pajamas" to Color(0xFF6C63FF),       // 11: cozy two-piece pajamas
    )

    val outfitColorOverrides = listOf(
        "Signature" to Color.Unspecified,
        "Ice Crystal" to Color(0xFF38BDF8),
        "Rose Pink" to Color(0xFFFF5CA8),
        "Royal Violet" to Color(0xFF9B5DE5),
        "Lagoon Teal" to Color(0xFF00BBF9),
        "Emerald Mint" to Color(0xFF10B981),
        "Sunshine Gold" to Color(0xFFFBBF24),
        "Coral Peach" to Color(0xFFFF7F6B),
        "Ruby Red" to Color(0xFFEF4444),
        "Pearl White" to Color(0xFFF8FAFC),
    )

    val accessories = listOf(
        "None",
        "Crystal Ice Tiara",
        "Golden Unicorn Horn",
        "Royal Jewelled Crown",
        "Flower Wreath",
        "Coral Pearl Hairpin",
        "Pink Ribbon Bow",
        "Cute Cat Ears",
        "Fluffy Bunny Ears",
        "Wizard Star Hat",
        "Glow Headphones",
        "Space Visor Halo",
    )

    val wingsAndBack = listOf(
        "None",
        "Fairy Butterfly Wings",
        "Feathered Pegasus Wings",
        "Crystal Ice Wings",
        "Royal Velvet Cape",
        "Shimmer Frost Cape",
        "Cute Backpack",
        "Star Jetpack",
    )

    val handAndFaceProps = listOf(
        "None",
        "Magic Star Wand",
        "Crystal Ice Wand",
        "Royal Gem Scepter",
        "Pop Star Mic",
        "Round Glasses",
        "Pink Heart Shades",
        "Gold Star Shades",
    )

    val pets = listOf(
        "None",
        "Arctic Snow Fox",
        "Winged Unicorn",
        "Baby Crystal Dragon",
        "Golden Puppy",
        "Fluffy Kitten",
        "Coral Seahorse",
        "Playful Dolphin",
        "Cuddly Bunny",
        "Baby Panda",
        "Snowy Owl",
        "Rainbow Butterfly",
        "Friendly Mini Robot",
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
    var activeCategoryTab by remember { mutableIntStateOf(0) }

    val studioTabs = listOf(
        "Face & Eyes",
        "Hair",
        "Outfits",
        "Crowns & Wings",
        "Wands & Shades",
        "3D Pets",
        "World",
    )

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
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(8.dp),
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFFFFFBFE),
            tonalElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // Left Pane: Live 360° Sculpted 3D Character + Pet Turntable Stage!
                Column(
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "3D Character & Pet Studio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF2B194D),
                    )
                    Text(
                        text = "Swipe left/right to spin 360° • Tap to jump!",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF6E5B9A),
                    )
                    Spacer(Modifier.height(8.dp))

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

                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = draft.childNickname,
                        onValueChange = { draft = draft.copy(childNickname = it.take(20)) },
                        label = { Text("Your Name") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Right Pane: Categorized Tabs with Deep Customization!
                Column(
                    modifier = Modifier
                        .weight(0.56f)
                        .fillMaxHeight(),
                ) {
                    ScrollableTabRow(
                        selectedTabIndex = activeCategoryTab,
                        edgePadding = 4.dp,
                        containerColor = Color(0xFFF4EEFF),
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)),
                    ) {
                        studioTabs.forEachIndexed { idx, title ->
                            Tab(
                                selected = activeCategoryTab == idx,
                                onClick = { activeCategoryTab = idx },
                                text = { Text(title, fontWeight = FontWeight.Bold) },
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        when (activeCategoryTab) {
                            0 -> { // Face & Eyes
                                SectionTitle("Skin Tone (${AvatarCatalog.skinTones.size} tones)")
                                ColorSwatchRow(
                                    colors = AvatarCatalog.skinTones,
                                    selectedIndex = draft.skinToneIndex,
                                    onSelect = { draft = draft.copy(skinToneIndex = it) },
                                )
                                SectionTitle("Cheeks & Freckles")
                                ChoiceChips(
                                    options = AvatarCatalog.cheekStyles,
                                    selectedIndex = draft.cheekStyleIndex,
                                    onSelect = { draft = draft.copy(cheekStyleIndex = it) },
                                )
                                SectionTitle("Eye Expression")
                                ChoiceChips(
                                    options = AvatarCatalog.eyeStyles,
                                    selectedIndex = draft.eyeStyleIndex,
                                    onSelect = { draft = draft.copy(eyeStyleIndex = it) },
                                )
                                SectionTitle("Eye Iris Color")
                                ColorSwatchRow(
                                    colors = AvatarCatalog.eyeColors.map { it.second },
                                    selectedIndex = draft.eyeColorIndex,
                                    onSelect = { draft = draft.copy(eyeColorIndex = it) },
                                )
                                ChoiceChips(
                                    options = AvatarCatalog.eyeColors.map { it.first },
                                    selectedIndex = draft.eyeColorIndex,
                                    onSelect = { draft = draft.copy(eyeColorIndex = it) },
                                )
                            }
                            1 -> { // Hair
                                SectionTitle("Sculpted 3D Hairstyle (${AvatarCatalog.hairStyles.size} styles)")
                                ChoiceChips(
                                    options = AvatarCatalog.hairStyles,
                                    selectedIndex = draft.hairStyleIndex,
                                    onSelect = { draft = draft.copy(hairStyleIndex = it) },
                                )
                                SectionTitle("Hair Color (${AvatarCatalog.hairColors.size} shades)")
                                ColorSwatchRow(
                                    colors = AvatarCatalog.hairColors.map { it.second },
                                    selectedIndex = draft.hairColorIndex,
                                    onSelect = { draft = draft.copy(hairColorIndex = it) },
                                )
                                ChoiceChips(
                                    options = AvatarCatalog.hairColors.map { it.first },
                                    selectedIndex = draft.hairColorIndex,
                                    onSelect = { draft = draft.copy(hairColorIndex = it) },
                                )
                            }
                            2 -> { // Outfits & Dress Dyes
                                SectionTitle("3D Outfit & Dress Style (${AvatarCatalog.outfits.size} outfits)")
                                ChoiceChips(
                                    options = AvatarCatalog.outfits.map { it.first },
                                    selectedIndex = draft.outfitIndex,
                                    onSelect = { draft = draft.copy(outfitIndex = it) },
                                )
                                SectionTitle("Outfit Color Dye")
                                ChoiceChips(
                                    options = AvatarCatalog.outfitColorOverrides.map { it.first },
                                    selectedIndex = draft.outfitColorIndex,
                                    onSelect = { draft = draft.copy(outfitColorIndex = it) },
                                )
                            }
                            3 -> { // Crowns, Horns & Wings
                                SectionTitle("Crowns, Horns & Headwear (${AvatarCatalog.accessories.size} items)")
                                ChoiceChips(
                                    options = AvatarCatalog.accessories,
                                    selectedIndex = draft.accessoryIndex,
                                    onSelect = { draft = draft.copy(accessoryIndex = it) },
                                )
                                SectionTitle("3D Animated Wings, Capes & Backpacks")
                                ChoiceChips(
                                    options = AvatarCatalog.wingsAndBack,
                                    selectedIndex = draft.wingsBackIndex,
                                    onSelect = { draft = draft.copy(wingsBackIndex = it) },
                                )
                            }
                            4 -> { // Wands & Shades
                                SectionTitle("Held Magic Wands, Scepters & Glasses")
                                ChoiceChips(
                                    options = AvatarCatalog.handAndFaceProps,
                                    selectedIndex = draft.handFacePropIndex,
                                    onSelect = { draft = draft.copy(handFacePropIndex = it) },
                                )
                            }
                            5 -> { // 3D Pets
                                SectionTitle("Choose Your 3D Sidekick Pet (${AvatarCatalog.pets.size - 1} pets!)")
                                ChoiceChips(
                                    options = AvatarCatalog.pets,
                                    selectedIndex = draft.petIndex,
                                    onSelect = { draft = draft.copy(petIndex = it) },
                                )
                            }
                            6 -> { // Theme World
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
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))
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
            .padding(bottom = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSwatchRow(
    colors: List<Color>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        colors.forEachIndexed { idx, color ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (selectedIndex == idx) 3.5.dp else 1.dp,
                        color = if (selectedIndex == idx) Color(0xFF6200EE) else Color.Gray.copy(alpha = 0.45f),
                        shape = CircleShape,
                    )
                    .clickable { onSelect(idx) }
            )
        }
    }
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
