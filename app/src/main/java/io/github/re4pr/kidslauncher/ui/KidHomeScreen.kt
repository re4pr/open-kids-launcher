package io.github.re4pr.kidslauncher.ui

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.re4pr.kidslauncher.model.AppCategory
import io.github.re4pr.kidslauncher.model.AvatarConfig
import io.github.re4pr.kidslauncher.model.EffectivePolicyState
import io.github.re4pr.kidslauncher.model.LauncherMode
import io.github.re4pr.kidslauncher.model.LauncherTab
import io.github.re4pr.kidslauncher.model.RecentLaunchRecord
import io.github.re4pr.kidslauncher.model.ShelfMediaItem
import io.github.re4pr.kidslauncher.model.SkinDefinition
import io.github.re4pr.kidslauncher.skin.AnimatedSkyBackground
import io.github.re4pr.kidslauncher.skin.ResolvedSkinPalette

data class KidAppTile(
    val packageName: String,
    val displayName: String,
    val category: AppCategory,
    val favorite: Boolean,
    val isSuspendedByOs: Boolean,
    val allowedInSchoolTime: Boolean,
    val allowedInBedtime: Boolean,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KidHomeScreen(
    palette: ResolvedSkinPalette,
    avatar: AvatarConfig,
    policy: EffectivePolicyState,
    availableSkins: List<SkinDefinition>,
    customSkinLabels: Map<String, String>,
    selectedTab: LauncherTab,
    onSelectTab: (LauncherTab) -> Unit,
    onQuickSelectSkin: (String) -> Unit,
    approvedApps: List<KidAppTile>,
    recentRecords: List<RecentLaunchRecord>,
    pluginItems: List<ShelfMediaItem>,
    loadAppIcon: (String) -> Drawable?,
    onLaunchApp: (KidAppTile) -> Unit,
    onLaunchRecent: (RecentLaunchRecord) -> Unit,
    onLaunchShelfItem: (ShelfMediaItem) -> Unit,
    onOpenAvatarStudio: () -> Unit,
    onRequestParentAccess: () -> Unit,
) {
    val context = LocalContext.current

    AnimatedSkyBackground(palette = palette) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            // Compact Top Glass Bar on Non-Home Tabs; Full Scenic 3D Hero Stage inside Home Tab!
            if (selectedTab != LauncherTab.HOME) {
                CompactTopBar(
                    palette = palette,
                    avatar = avatar,
                    policy = policy,
                    onOpenAvatarStudio = onOpenAvatarStudio,
                    onParentTapHint = {
                        Toast.makeText(context, "Parents: press and hold lock icon", Toast.LENGTH_SHORT).show()
                    },
                    onParentLongPress = onRequestParentAccess,
                )
                Spacer(Modifier.height(10.dp))
            }

            // Main Content Area (Mode-reactive)
            Box(modifier = Modifier.weight(1f)) {
                when {
                    policy.activeMode == LauncherMode.LOCKED -> {
                        ModeState3DCard(
                            palette = palette,
                            avatar = avatar,
                            mode = LauncherMode.LOCKED,
                            headline = "Time for a Screen Break!",
                            body = "Great job today! Take a stretch, play outside, or ask a parent if you need more time.",
                            countdownText = null,
                            onParentTapHint = {
                                Toast.makeText(context, "Parents: press and hold lock icon", Toast.LENGTH_SHORT).show()
                            },
                            onParentLongPress = onRequestParentAccess,
                        )
                    }
                    policy.activeMode == LauncherMode.TIDY_UP -> {
                        val mins = policy.tidyUpSecondsRemaining / 60
                        val secs = policy.tidyUpSecondsRemaining % 60
                        ModeState3DCard(
                            palette = palette,
                            avatar = avatar,
                            mode = LauncherMode.TIDY_UP,
                            headline = "Tidy-Up Time!",
                            body = "Let's put toys & books back in their spots! Playtime unlocks when tidy-up finishes.",
                            countdownText = if (policy.tidyUpSecondsRemaining > 0) {
                                "%02d:%02d".format(mins, secs)
                            } else {
                                "Tidy-Up Active"
                            },
                            onParentTapHint = {
                                Toast.makeText(context, "Parents: press and hold lock icon", Toast.LENGTH_SHORT).show()
                            },
                            onParentLongPress = onRequestParentAccess,
                        )
                    }
                    policy.activeMode == LauncherMode.BEDTIME && selectedTab == LauncherTab.HOME -> {
                        BedtimeHome3DCard(
                            palette = palette,
                            avatar = avatar,
                            allowAudio = LauncherTab.AUDIO in policy.allowedTabs,
                            onOpenAudio = { onSelectTab(LauncherTab.AUDIO) },
                            onParentTapHint = {
                                Toast.makeText(context, "Parents: press and hold lock icon", Toast.LENGTH_SHORT).show()
                            },
                            onParentLongPress = onRequestParentAccess,
                        )
                    }
                    else -> {
                        AnimatedContent(targetState = selectedTab, label = "tabSwitch") { tab ->
                            when (tab) {
                                LauncherTab.HOME -> MainKidMenuContent(
                                    palette = palette,
                                    avatar = avatar,
                                    policy = policy,
                                    availableSkins = availableSkins,
                                    customSkinLabels = customSkinLabels,
                                    onQuickSelectSkin = onQuickSelectSkin,
                                    onOpenAvatarStudio = onOpenAvatarStudio,
                                    onParentTapHint = {
                                        Toast.makeText(context, "Parents: press and hold lock icon", Toast.LENGTH_SHORT).show()
                                    },
                                    onParentLongPress = onRequestParentAccess,
                                    approvedApps = approvedApps,
                                    recentRecords = recentRecords,
                                    pluginItems = pluginItems,
                                    loadAppIcon = loadAppIcon,
                                    onLaunchRecent = onLaunchRecent,
                                    onLaunchShelfItem = onLaunchShelfItem,
                                    onLaunchApp = onLaunchApp,
                                )
                                LauncherTab.GAMES -> CategoryAppsScreen(
                                    title = if (policy.activeMode == LauncherMode.SCHOOL_TIME) "Learning Games" else "Games (${filterAppsForTab(approvedApps, AppCategory.GAMES, policy.activeMode).size})",
                                    emptyMessage = "No games enabled right now — ask a parent to hold the lock icon to manage apps!",
                                    palette = palette,
                                    apps = filterAppsForTab(approvedApps, AppCategory.GAMES, policy.activeMode),
                                    shelfItems = pluginItems.filter { it.category == AppCategory.GAMES },
                                    loadAppIcon = loadAppIcon,
                                    onLaunchApp = onLaunchApp,
                                    onLaunchShelfItem = onLaunchShelfItem,
                                )
                                LauncherTab.VIDEOS -> CategoryAppsScreen(
                                    title = "Shows & Movies",
                                    emptyMessage = "No video apps enabled yet — ask a parent to enable video apps or Continue Watching feeds!",
                                    palette = palette,
                                    apps = filterAppsForTab(approvedApps, AppCategory.VIDEOS, policy.activeMode),
                                    shelfItems = pluginItems.filter { it.category == AppCategory.VIDEOS },
                                    loadAppIcon = loadAppIcon,
                                    onLaunchApp = onLaunchApp,
                                    onLaunchShelfItem = onLaunchShelfItem,
                                )
                                LauncherTab.BOOKS -> BooksMenuScreen(
                                    palette = palette,
                                    apps = filterAppsForTab(approvedApps, AppCategory.BOOKS, policy.activeMode),
                                    bookItems = pluginItems.filter { it.category == AppCategory.BOOKS },
                                    loadAppIcon = loadAppIcon,
                                    onLaunchApp = onLaunchApp,
                                    onLaunchShelfItem = onLaunchShelfItem,
                                )
                                LauncherTab.AUDIO -> CategoryAppsScreen(
                                    title = "Music & Stories",
                                    emptyMessage = "Ask a parent to enable your offline Yoto or music player here!",
                                    palette = palette,
                                    apps = filterAppsForTab(approvedApps, AppCategory.AUDIO, policy.activeMode),
                                    shelfItems = pluginItems.filter { it.category == AppCategory.AUDIO },
                                    loadAppIcon = loadAppIcon,
                                    onLaunchApp = onLaunchApp,
                                    onLaunchShelfItem = onLaunchShelfItem,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Dynamic Kid-Friendly Floating Glass Dock
            KidFloatingNavBar(
                palette = palette,
                allowedTabs = policy.allowedTabs,
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
            )
        }
    }
}

private fun filterAppsForTab(
    apps: List<KidAppTile>,
    targetCategory: AppCategory,
    mode: LauncherMode,
): List<KidAppTile> {
    val byCat = apps.filter { app ->
        if (targetCategory == AppCategory.GAMES) {
            app.category == AppCategory.GAMES || app.category == AppCategory.LEARNING
        } else {
            app.category == targetCategory
        }
    }
    return when (mode) {
        LauncherMode.SCHOOL_TIME -> byCat.filter { it.allowedInSchoolTime || it.category == AppCategory.LEARNING || it.category == AppCategory.BOOKS }
        LauncherMode.BEDTIME -> byCat.filter { it.allowedInBedtime || it.category == AppCategory.AUDIO }
        else -> byCat
    }.sortedWith(compareByDescending<KidAppTile> { it.favorite }.thenBy { it.displayName.lowercase() })
}

/**
 * Google Kids Space–inspired Scenic 3D Hero Stage at the top of the Main Menu!
 * Shows the child's animated 3D character + 3D pet standing right inside the living
 * Castle / Unicorn / Princess / Mermaid world, alongside glassmorphic theme switchers.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Scenic3DHeroStage(
    palette: ResolvedSkinPalette,
    avatar: AvatarConfig,
    policy: EffectivePolicyState,
    availableSkins: List<SkinDefinition>,
    customSkinLabels: Map<String, String>,
    onQuickSelectSkin: (String) -> Unit,
    onOpenAvatarStudio: () -> Unit,
    onParentTapHint: () -> Unit,
    onParentLongPress: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(236.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: Full-Body Real-Time 3D Avatar + Pet standing in the foreground landscape!
            Box(
                modifier = Modifier
                    .width(276.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Avatar3DStage(
                    avatar = avatar,
                    mode = policy.activeMode,
                    rimHighlightColor = palette.accentSecondary,
                    modifier = Modifier.fillMaxSize(),
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = palette.accentPrimary,
                    border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.85f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .clickable(onClick = onOpenAvatarStudio),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("3D Dress Up", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Right: Floating Frosted Glass Greeting & Theme World Picker
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Surface(
                        shape = RoundedCornerShape(26.dp),
                        color = Color.Black.copy(alpha = 0.34f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.42f)),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                            Text(
                                text = "Hi, ${avatar.childNickname}!",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = palette.badgeHighlight,
                                ) {
                                    Text(
                                        text = "${palette.displayName} • ${policy.activeMode.title}",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    )
                                }
                                if (policy.dailyBudgetMinutes > 0 && policy.activeMode == LauncherMode.FREE_PLAY) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = Color.White.copy(alpha = 0.24f),
                                    ) {
                                        Text(
                                            text = "${policy.remainingMinutesToday}m playtime",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Subtle Hidden Parent Gate Lock Icon (Hold to unlock)
                    ParentLockBadge(onTap = onParentTapHint, onLongPress = onParentLongPress)
                }

                // Quick World Skin Switcher Pills right in the Hero Sky!
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    items(availableSkins, key = { it.id }) { skin ->
                        val isCurrent = skin.id == palette.id
                        val label = customSkinLabels[skin.id]?.takeIf { it.isNotBlank() } ?: skin.name
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isCurrent) palette.accentPrimary else Color.Black.copy(alpha = 0.36f),
                            border = BorderStroke(
                                width = if (isCurrent) 2.dp else 1.dp,
                                color = if (isCurrent) Color.White else Color.White.copy(alpha = 0.45f),
                            ),
                            modifier = Modifier.clickable { onQuickSelectSkin(skin.id) },
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Palette,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactTopBar(
    palette: ResolvedSkinPalette,
    avatar: AvatarConfig,
    policy: EffectivePolicyState,
    onOpenAvatarStudio: () -> Unit,
    onParentTapHint: () -> Unit,
    onParentLongPress: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.34f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.38f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = palette.accentPrimary,
                modifier = Modifier.clickable(onClick = onOpenAvatarStudio),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text("${avatar.childNickname}'s 3D Buddy", color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${palette.displayName} • ${policy.activeMode.title}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            ParentLockBadge(onTap = onParentTapHint, onLongPress = onParentLongPress)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ParentLockBadge(onTap: () -> Unit, onLongPress: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.28f))
            .combinedClickable(onClick = onTap, onLongClick = onLongPress),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Lock,
            contentDescription = "Parent Controls (Hold)",
            tint = Color.White.copy(alpha = 0.78f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun MainKidMenuContent(
    palette: ResolvedSkinPalette,
    avatar: AvatarConfig,
    policy: EffectivePolicyState,
    availableSkins: List<SkinDefinition>,
    customSkinLabels: Map<String, String>,
    onQuickSelectSkin: (String) -> Unit,
    onOpenAvatarStudio: () -> Unit,
    onParentTapHint: () -> Unit,
    onParentLongPress: () -> Unit,
    approvedApps: List<KidAppTile>,
    recentRecords: List<RecentLaunchRecord>,
    pluginItems: List<ShelfMediaItem>,
    loadAppIcon: (String) -> Drawable?,
    onLaunchRecent: (RecentLaunchRecord) -> Unit,
    onLaunchShelfItem: (ShelfMediaItem) -> Unit,
    onLaunchApp: (KidAppTile) -> Unit,
) {
    val recentGames = recentRecords.filter { it.category == AppCategory.GAMES || it.category == AppCategory.LEARNING }.take(8)
    val recentAudio = recentRecords.filter { it.category == AppCategory.AUDIO }.take(8)
    val recentVideos = recentRecords.filter { it.category == AppCategory.VIDEOS }.take(8)
    val continueWatchingPlugins = pluginItems.filter { it.category == AppCategory.VIDEOS }.take(8)
    val audioPlugins = pluginItems.filter { it.category == AppCategory.AUDIO }.take(8)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        // Hero Stage with 3D Avatar + Scenic World!
        item {
            Scenic3DHeroStage(
                palette = palette,
                avatar = avatar,
                policy = policy,
                availableSkins = availableSkins,
                customSkinLabels = customSkinLabels,
                onQuickSelectSkin = onQuickSelectSkin,
                onOpenAvatarStudio = onOpenAvatarStudio,
                onParentTapHint = onParentTapHint,
                onParentLongPress = onParentLongPress,
            )
        }

        if (policy.activeMode == LauncherMode.SCHOOL_TIME) {
            item {
                val studyApps = approvedApps.filter { it.allowedInSchoolTime || it.category == AppCategory.LEARNING || it.category == AppCategory.BOOKS }
                GlassShelfContainer(palette = palette) {
                    ShelfSectionTitle("School & Study Apps", Icons.Rounded.School, palette.textPrimary)
                    Spacer(Modifier.height(10.dp))
                    if (studyApps.isEmpty()) {
                        Text(
                            text = "School Time is active! Ask a parent to mark learning apps for School Time.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.textPrimary,
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(studyApps, key = { it.packageName }) { app ->
                                BouncyAppCard(app = app, palette = palette, loadAppIcon = loadAppIcon, onClick = { onLaunchApp(app) })
                            }
                        }
                    }
                }
            }
        } else {
            // 1. Last Played / Featured Games Shelf
            item {
                val fallbackGames = filterAppsForTab(approvedApps, AppCategory.GAMES, policy.activeMode).take(8)
                GlassShelfContainer(palette = palette) {
                    ShelfSectionTitle(
                        title = if (recentGames.isNotEmpty()) "Jump Back In — Games" else "Your Games",
                        icon = Icons.Rounded.SportsEsports,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(10.dp))
                    when {
                        recentGames.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(recentGames, key = { it.id }) { rec ->
                                RecentCard(rec, palette, loadAppIcon) { onLaunchRecent(rec) }
                            }
                        }
                        fallbackGames.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(fallbackGames, key = { it.packageName }) { app ->
                                BouncyAppCard(app, palette, loadAppIcon) { onLaunchApp(app) }
                            }
                        }
                        else -> Text("No games found yet!", color = palette.textPrimary)
                    }
                }
            }

            // 2. Continue Watching — Shows & Movies Shelf
            item {
                val fallbackVideos = filterAppsForTab(approvedApps, AppCategory.VIDEOS, policy.activeMode).take(8)
                GlassShelfContainer(palette = palette) {
                    ShelfSectionTitle(
                        title = "Shows & Movies",
                        icon = Icons.Rounded.Movie,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(10.dp))
                    when {
                        continueWatchingPlugins.isNotEmpty() || recentVideos.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(continueWatchingPlugins, key = { it.id }) { item ->
                                PluginPosterCard(item, palette) { onLaunchShelfItem(item) }
                            }
                            items(recentVideos, key = { it.id }) { rec ->
                                RecentCard(rec, palette, loadAppIcon) { onLaunchRecent(rec) }
                            }
                        }
                        fallbackVideos.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(fallbackVideos, key = { it.packageName }) { app ->
                                BouncyAppCard(app, palette, loadAppIcon) { onLaunchApp(app) }
                            }
                        }
                        else -> Text("Open Netflix, Nuvio, or connect a Continue Watching feed in Parent Settings!", color = palette.textPrimary)
                    }
                }
            }

            // 3. Music & Audio Stories Shelf
            item {
                val fallbackAudio = filterAppsForTab(approvedApps, AppCategory.AUDIO, policy.activeMode).take(8)
                GlassShelfContainer(palette = palette) {
                    ShelfSectionTitle(
                        title = "Music & Audio Stories",
                        icon = Icons.Rounded.MusicNote,
                        color = palette.textPrimary,
                    )
                    Spacer(Modifier.height(10.dp))
                    when {
                        audioPlugins.isNotEmpty() || recentAudio.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(audioPlugins, key = { it.id }) { item ->
                                PluginPosterCard(item, palette) { onLaunchShelfItem(item) }
                            }
                            items(recentAudio, key = { it.id }) { rec ->
                                RecentCard(rec, palette, loadAppIcon) { onLaunchRecent(rec) }
                            }
                        }
                        fallbackAudio.isNotEmpty() -> LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(fallbackAudio, key = { it.packageName }) { app ->
                                BouncyAppCard(app, palette, loadAppIcon) { onLaunchApp(app) }
                            }
                        }
                        else -> Text("Your offline Yoto & music players appear right here!", color = palette.textPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassShelfContainer(
    palette: ResolvedSkinPalette,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(palette.cardCornerDp.dp),
        color = palette.surfaceCard.copy(alpha = 0.78f),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.65f)),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
            content()
        }
    }
}

@Composable
private fun CategoryAppsScreen(
    title: String,
    emptyMessage: String,
    palette: ResolvedSkinPalette,
    apps: List<KidAppTile>,
    shelfItems: List<ShelfMediaItem>,
    loadAppIcon: (String) -> Drawable?,
    onLaunchApp: (KidAppTile) -> Unit,
    onLaunchShelfItem: (ShelfMediaItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.34f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
        }

        if (shelfItems.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(bottom = 16.dp),
            ) {
                items(shelfItems, key = { it.id }) { item ->
                    PluginPosterCard(item, palette) { onLaunchShelfItem(item) }
                }
            }
        }

        if (apps.isEmpty() && shelfItems.isEmpty()) {
            GlassShelfContainer(palette = palette) {
                Text(emptyMessage, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = palette.textPrimary)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 152.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                    FloatingGridWrapper(index = index) {
                        BouncyAppCard(
                            app = app,
                            palette = palette,
                            loadAppIcon = loadAppIcon,
                            onClick = { onLaunchApp(app) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BooksMenuScreen(
    palette: ResolvedSkinPalette,
    apps: List<KidAppTile>,
    bookItems: List<ShelfMediaItem>,
    loadAppIcon: (String) -> Drawable?,
    onLaunchApp: (KidAppTile) -> Unit,
    onLaunchShelfItem: (ShelfMediaItem) -> Unit,
) {
    if (apps.isNotEmpty() || bookItems.isNotEmpty()) {
        CategoryAppsScreen(
            title = "Storybooks & Reading",
            emptyMessage = "",
            palette = palette,
            apps = apps,
            shelfItems = bookItems,
            loadAppIcon = loadAppIcon,
            onLaunchApp = onLaunchApp,
            onLaunchShelfItem = onLaunchShelfItem,
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Card(
                shape = RoundedCornerShape(palette.cardCornerDp.dp),
                colors = CardDefaults.cardColors(containerColor = palette.surfaceCard.copy(alpha = 0.90f)),
                border = BorderStroke(2.dp, Color.White.copy(alpha = 0.75f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier.fillMaxWidth(0.76f),
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoStories,
                        contentDescription = null,
                        tint = palette.accentPrimary,
                        modifier = Modifier.size(72.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Your Magical Bookshelf",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Storybooks are coming soon! Parents can connect any e-book reader app or OPDS/JSON book library plugin whenever ready.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.textPrimary.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun FloatingGridWrapper(index: Int, content: @Composable () -> Unit) {
    val infinite = rememberInfiniteTransition(label = "cardFloat_$index")
    val dy by infinite.animateFloat(
        initialValue = if (index % 2 == 0) -2.5f else 2.5f,
        targetValue = if (index % 2 == 0) 2.5f else -2.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_200 + (index % 3) * 350, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "floatDy",
    )
    Box(modifier = Modifier.offset(y = dy.dp)) {
        content()
    }
}

@Composable
private fun BouncyAppCard(
    app: KidAppTile,
    palette: ResolvedSkinPalette,
    loadAppIcon: (String) -> Drawable?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.91f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "cardBounce",
    )
    val iconBitmap = remember(app.packageName) { loadAppIcon(app.packageName)?.toImageBitmap() }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isSuspendedByOs) {
                palette.surfaceCard.copy(alpha = 0.55f)
            } else {
                Color.White.copy(alpha = 0.96f)
            }
        ),
        border = BorderStroke(1.5.dp, palette.accentSecondary.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .width(154.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            if (app.favorite) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = "Favorite",
                    tint = Color(0xFFFFB300),
                    modifier = Modifier.size(22.dp).align(Alignment.TopEnd),
                )
            }
            if (app.isSuspendedByOs) {
                Icon(
                    imageVector = Icons.Rounded.PauseCircle,
                    contentDescription = "Paused by Family Link",
                    tint = Color(0xFFE53935),
                    modifier = Modifier.size(22.dp).align(Alignment.TopStart),
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = app.displayName,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(palette.accentPrimary, palette.accentSecondary)
                                )
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(38.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = app.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF1A2138),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (app.isSuspendedByOs) {
                    Text(
                        text = "Paused",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD32F2F),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentCard(
    record: RecentLaunchRecord,
    palette: ResolvedSkinPalette,
    loadAppIcon: (String) -> Drawable?,
    onClick: () -> Unit,
) {
    val iconBitmap = remember(record.packageName) {
        record.packageName.takeIf { it.isNotBlank() }?.let { loadAppIcon(it)?.toImageBitmap() }
    }
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        border = BorderStroke(1.5.dp, palette.accentSecondary.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .width(172.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (record.imageUrl.isNotBlank()) {
                AsyncImage(
                    model = record.imageUrl,
                    contentDescription = record.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp)
                        .clip(RoundedCornerShape(16.dp)),
                )
            } else if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = record.title,
                    modifier = Modifier.size(68.dp).clip(RoundedCornerShape(18.dp)),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = record.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1A2138),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (record.subtitle.isNotBlank()) {
                Text(
                    text = record.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4A5568),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PluginPosterCard(
    item: ShelfMediaItem,
    palette: ResolvedSkinPalette,
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.96f)),
        border = BorderStroke(1.5.dp, palette.accentPrimary.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .width(214.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.accentPrimary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                if (item.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = palette.accentPrimary, modifier = Modifier.size(44.dp))
                }
            }
            if (item.progressFraction > 0f) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { item.progressFraction },
                    color = palette.accentPrimary,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF1A2138),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4A5568),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun KidFloatingNavBar(
    palette: ResolvedSkinPalette,
    allowedTabs: Set<LauncherTab>,
    selectedTab: LauncherTab,
    onSelectTab: (LauncherTab) -> Unit,
) {
    val tabs = LauncherTab.entries.filter { it in allowedTabs }
    Surface(
        shape = RoundedCornerShape(36.dp),
        color = palette.surfaceCard.copy(alpha = 0.90f),
        border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.8f)),
        shadowElevation = 12.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selectedTab
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "navScale",
                )
                val icon = when (tab) {
                    LauncherTab.HOME -> Icons.Rounded.Home
                    LauncherTab.GAMES -> Icons.Rounded.SportsEsports
                    LauncherTab.VIDEOS -> Icons.Rounded.Movie
                    LauncherTab.BOOKS -> Icons.Rounded.AutoStories
                    LauncherTab.AUDIO -> Icons.Rounded.MusicNote
                }
                Row(
                    modifier = Modifier
                        .scale(scale)
                        .clip(RoundedCornerShape(26.dp))
                        .background(if (isSelected) palette.accentPrimary else Color.Transparent)
                        .clickable { onSelectTab(tab) }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = tab.label,
                        tint = if (isSelected) Color.White else palette.textPrimary,
                        modifier = Modifier.size(26.dp),
                    )
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) Color.White else palette.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeState3DCard(
    palette: ResolvedSkinPalette,
    avatar: AvatarConfig,
    mode: LauncherMode,
    headline: String,
    body: String,
    countdownText: String?,
    onParentTapHint: () -> Unit,
    onParentLongPress: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            ParentLockBadge(onTap = onParentTapHint, onLongPress = onParentLongPress)
        }
        Card(
            shape = RoundedCornerShape(palette.cardCornerDp.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surfaceCard.copy(alpha = 0.90f)),
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth(0.76f).align(Alignment.Center),
        ) {
            Row(
                modifier = Modifier.padding(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Avatar3DStage(
                    avatar = avatar,
                    mode = mode,
                    rimHighlightColor = palette.accentSecondary,
                    modifier = Modifier.size(210.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headline,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = palette.textPrimary,
                    )
                    if (countdownText != null) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = palette.accentPrimary,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Rounded.CleaningServices, contentDescription = null, tint = Color.White)
                                Text(
                                    text = countdownText,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.textPrimary.copy(alpha = 0.88f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BedtimeHome3DCard(
    palette: ResolvedSkinPalette,
    avatar: AvatarConfig,
    allowAudio: Boolean,
    onOpenAudio: () -> Unit,
    onParentTapHint: () -> Unit,
    onParentLongPress: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            ParentLockBadge(onTap = onParentTapHint, onLongPress = onParentLongPress)
        }
        Card(
            shape = RoundedCornerShape(palette.cardCornerDp.dp),
            colors = CardDefaults.cardColors(containerColor = palette.surfaceCard.copy(alpha = 0.86f)),
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth(0.78f).align(Alignment.Center),
        ) {
            Row(
                modifier = Modifier.padding(28.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Avatar3DStage(
                    avatar = avatar,
                    mode = LauncherMode.BEDTIME,
                    rimHighlightColor = Color(0xFFFFE699),
                    modifier = Modifier.size(210.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = palette.accentSecondary)
                        Text(
                            text = "Sweet Dreams, ${avatar.childNickname}!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = palette.textPrimary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (allowAudio) {
                            "Games & videos are tucked in for the night. You can still listen to calm bedtime stories or music!"
                        } else {
                            "It's bedtime! Rest well and your games & shows will be ready in the morning."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.textPrimary.copy(alpha = 0.88f),
                    )
                    if (allowAudio) {
                        Spacer(Modifier.height(16.dp))
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = palette.accentPrimary,
                            modifier = Modifier.clickable(onClick = onOpenAudio),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.Black)
                                Text("Open Bedtime Stories & Audio", fontWeight = FontWeight.ExtraBold, color = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShelfSectionTitle(title: String, icon: ImageVector, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            color = color,
        )
    }
}

private fun Drawable.toImageBitmap() = runCatching {
    if (this is BitmapDrawable && bitmap != null) {
        return@runCatching bitmap.asImageBitmap()
    }
    val w = intrinsicWidth.takeIf { it > 0 } ?: 128
    val h = intrinsicHeight.takeIf { it > 0 } ?: 128
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    bmp.asImageBitmap()
}.getOrNull()
