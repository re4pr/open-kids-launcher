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
        Color(0xFFFFEAD9),
        Color(0xFFFAD7BD),
        Color(0xFFEEC09B),
        Color(0xFFD99E73),
        Color(0xFFBC7D52),
        Color(0xFF985D38),
        Color(0xFF744327),
        Color(0xFF522E1B),
    )

    val cheekStyles = listOf(
        "None",
        "Rosy Blush",
        "Cute Freckles",
        "Star Cheeks",
        "Heart Cheeks",
    )

    val eyeStyles = listOf(
        "Storybook Lash",
        "Big Sparkle",
        "Happy Smile",
        "Playful Wink",
        "Dreamy Almond",
        "Starry Eyes",
    )

    val eyeColors = listOf(
        "Sapphire Blue" to Color(0xFF1D74F5),
        "Emerald Green" to Color(0xFF12A580),
        "Warm Chestnut" to Color(0xFF754224),
        "Honey Amber" to Color(0xFFD98A16),
        "Amethyst Violet" to Color(0xFF8C42BD),
        "Ocean Aqua" to Color(0xFF00B4D8),
        "Rose Quartz" to Color(0xFFE35296),
        "Espresso" to Color(0xFF33211D),
    )

    val hairStyles = listOf(
        "Flowing Princess Waves",
        "Frost Queen Side Braid",
        "Twin Ribbon Braids",
        "High Bouncy Ponytail",
        "Twin Pigtails",
        "Fluffy Cloud Curls",
        "Side-Swept Bob",
        "Space Buns & Bangs",
        "Royal Crown Braid",
        "Short Pixie Crop",
        "Playful Spikes",
        "Half-Up Fairy Curls",
    )

    val hairColors = listOf(
        "Pearl Blonde" to Color(0xFFF5EBDC),
        "Honey Blonde" to Color(0xFFEBBF52),
        "Warm Chestnut" to Color(0xFF7A4B2E),
        "Platinum Ice" to Color(0xFFDDF2FF),
        "Midnight Black" to Color(0xFF2B2E42),
        "Auburn Copper" to Color(0xFFC45530),
        "Unicorn Pink" to Color(0xFFFF66B3),
        "Cotton Candy" to Color(0xFFFFA3DC),
        "Royal Lavender" to Color(0xFFA682FF),
        "Mermaid Teal" to Color(0xFF18D2C0),
        "Sky Blue" to Color(0xFF4DB4FF),
        "Strawberry Coral" to Color(0xFFFF6B7A),
    )

    val outfits = listOf(
        "Frost Crystal Gown" to Color(0xFF30B5F6),
        "Princess Rose Ballgown" to Color(0xFFFF5E9E),
        "Unicorn Rainbow Tulle" to Color(0xFFC77DFF),
        "Mermaid Pearl Gown" to Color(0xFF00BFA5),
        "Fairy Blossom Dress" to Color(0xFFFF85C2),
        "Swan Ballerina Tutu" to Color(0xFFFFADD6),
        "Star Hoodie & Jeans" to Color(0xFF9B72FF),
        "Superhero Suit" to Color(0xFFE63946),
        "Denim Overalls & Tee" to Color(0xFF457B9D),
        "School Blazer & Skirt" to Color(0xFF2B4C7E),
        "Space Explorer Suit" to Color(0xFFE2ECF5),
        "Starlight Pajamas" to Color(0xFF6C63FF),
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
        "Royal Jewelled Crown",
        "Golden Unicorn Horn",
        "Crystal Ice Tiara",
        "Tropical Blossom",
        "Satin Ribbon Bow",
        "Flower Wreath",
        "Cute Cat Ears",
        "Fluffy Bunny Ears",
        "Wizard Star Hat",
        "3D Headphones",
        "Pearl Hairclips",
    )

    val wingsAndBack = listOf(
        "None",
        "Feathered Pegasus Wings",
        "Butterfly Fairy Wings",
        "Frost Crystal Wings",
        "Golden Angel Wings",
        "Royal Velvet Cape",
        "Shimmer Ice Cape",
        "Starry Cape",
    )

    val handAndFaceProps = listOf(
        "None",
        "3D Magic Star Wand",
        "Snowflake Ice Wand",
        "Royal Heart Scepter",
        "Cool 3D Sunglasses",
        "Round Glasses",
        "Pink Heart Shades",
        "Sparkle Aura",
    )

    // 16 High-Resolution 3D-Shaded Companion Pets (`assets/puppet/pets/<slug>.webp`)
    val petEntries = listOf(
        "None" to "",
        "Winged Baby Unicorn" to "puppet/pets/winged_unicorn.webp",
        "Golden Puppy" to "puppet/pets/golden_puppy.webp",
        "Royal Poodle" to "puppet/pets/royal_poodle.webp",
        "Tabby Kitten" to "puppet/pets/tabby_kitten.webp",
        "Fluffy Bunny" to "puppet/pets/fluffy_bunny.webp",
        "Emerald Dragon" to "puppet/pets/emerald_dragon.webp",
        "Baby Arctic Seal" to "puppet/pets/arctic_seal.webp",
        "Lagoon Dolphin" to "puppet/pets/lagoon_dolphin.webp",
        "Friendly Sea Turtle" to "puppet/pets/sea_turtle.webp",
        "Royal White Swan" to "puppet/pets/royal_swan.webp",
        "Pink Flamingo" to "puppet/pets/pink_flamingo.webp",
        "Snowy Owl" to "puppet/pets/snowy_owl.webp",
        "Chubby Baby Panda" to "puppet/pets/baby_panda.webp",
        "Cuddly Koala" to "puppet/pets/cuddly_koala.webp",
        "Cute Hamster" to "puppet/pets/cute_hamster.webp",
        "Star Companion Bot" to "puppet/pets/star_robot.webp",
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
        "Pets (16)",
        "Outfits",
        "Hair",
        "Face & Eyes",
        "Crowns & Wings",
        "Wands & Shades",
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
                // Left Pane: Live 2.5D Storybook Character + 3D Pet Stage!
                Column(
                    modifier = Modifier
                        .weight(0.44f)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Character & Pet Studio",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF2B194D),
                    )
                    Text(
                        text = "Swipe to tilt 2.5D • Tap to jump & sparkle!",
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

                // Right Pane: Categorized Customization Tabs
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
                            0 -> { // 16 3D Pets!
                                SectionTitle("Choose Your 3D Sidekick Pet (${AvatarCatalog.petEntries.size - 1} pets!)")
                                ChoiceChips(
                                    options = AvatarCatalog.petEntries.map { it.first },
                                    selectedIndex = draft.petIndex.coerceIn(0, AvatarCatalog.petEntries.lastIndex),
                                    onSelect = { draft = draft.copy(petIndex = it) },
                                )
                            }
                            1 -> { // Outfits & Dyes
                                SectionTitle("Storybook Dress & Outfit (${AvatarCatalog.outfits.size} styles)")
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
                            2 -> { // Hair
                                SectionTitle("Hairstyle (${AvatarCatalog.hairStyles.size} styles)")
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
                            3 -> { // Face & Eyes
                                SectionTitle("Skin Tone")
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
                            }
                            4 -> { // Crowns & Wings
                                SectionTitle("Crowns, Horns & Headwear")
                                ChoiceChips(
                                    options = AvatarCatalog.accessories,
                                    selectedIndex = draft.accessoryIndex,
                                    onSelect = { draft = draft.copy(accessoryIndex = it) },
                                )
                                SectionTitle("Animated 3D Wings & Capes")
                                ChoiceChips(
                                    options = AvatarCatalog.wingsAndBack,
                                    selectedIndex = draft.wingsBackIndex,
                                    onSelect = { draft = draft.copy(wingsBackIndex = it) },
                                )
                            }
                            5 -> { // Wands & Shades
                                SectionTitle("Magic Wands, Sunglasses & Sparkles")
                                ChoiceChips(
                                    options = AvatarCatalog.handAndFaceProps,
                                    selectedIndex = draft.handFacePropIndex,
                                    onSelect = { draft = draft.copy(handFacePropIndex = it) },
                                )
                            }
                            6 -> { // Theme World
                                SectionTitle("Choose Your Living Theme World")
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
                            Text("Save My Look!", fontWeight = FontWeight.ExtraBold)
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
