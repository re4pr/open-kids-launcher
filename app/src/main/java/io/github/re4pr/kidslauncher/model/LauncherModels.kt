package io.github.re4pr.kidslauncher.model

import kotlinx.serialization.Serializable

@Serializable
enum class AppCategory(val label: String) {
    GAMES("Games"),
    VIDEOS("Videos"),
    BOOKS("Books"),
    AUDIO("Audio"),
    LEARNING("Learning"),
}

@Serializable
enum class LauncherTab(val label: String) {
    HOME("Home"),
    GAMES("Games"),
    VIDEOS("Videos"),
    BOOKS("Books"),
    AUDIO("Audio"),
}

@Serializable
enum class LauncherMode(val title: String, val subtitle: String) {
    FREE_PLAY("Playtime!", "Have fun exploring your world!"),
    SCHOOL_TIME("School & Study Time", "Learning & reading apps are open"),
    TIDY_UP("Tidy-Up Time!", "Let's clean up together before playing"),
    BEDTIME("Sweet Dreams", "Time to rest — cozy bedtime mode"),
    LOCKED("Screen Break", "Screen time is paused right now"),
}

@Serializable
data class ManagedAppRule(
    val packageName: String,
    val customLabel: String = "",
    val category: AppCategory = AppCategory.GAMES,
    val allowed: Boolean = false,
    val favorite: Boolean = false,
    val allowedInSchoolTime: Boolean = false,
    val allowedInBedtime: Boolean = false,
    val dailyLimitMinutes: Int = 0, // 0 = uses global budget
)

data class InstalledAppEntry(
    val packageName: String,
    val label: String,
    val suggestedCategory: AppCategory,
    val isSuspendedByOs: Boolean,
    val defaultAllowed: Boolean = false,
)

@Serializable
data class RecentLaunchRecord(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val category: AppCategory,
    val packageName: String = "",
    val launchUri: String = "",
    val imageUrl: String = "",
    val progressFraction: Float = 0f,
    val lastPlayedEpochMs: Long,
)

@Serializable
data class ShelfMediaItem(
    val id: String,
    val pluginId: String,
    val title: String,
    val subtitle: String = "",
    val category: AppCategory,
    val imageUrl: String = "",
    val progressFraction: Float = 0f,
    val launchUri: String = "",
    val packageName: String = "",
    val intentExtras: Map<String, String> = emptyMap(),
)

@Serializable
data class AvatarConfig(
    val childNickname: String = "Explorer",
    val skinToneIndex: Int = 1,
    val cheekStyleIndex: Int = 1,
    val hairStyleIndex: Int = 0,
    val hairColorIndex: Int = 0,
    val eyeStyleIndex: Int = 0,
    val eyeColorIndex: Int = 0,
    val outfitIndex: Int = 0,
    val outfitColorIndex: Int = 0, // 0 = outfit signature color, 1..N = custom dye
    val accessoryIndex: Int = 0,
    val wingsBackIndex: Int = 0,
    val handFacePropIndex: Int = 0,
    val petIndex: Int = 1,
)

@Serializable
data class TimeWindowRule(
    val enabled: Boolean = false,
    val startHour: Int = 20,
    val startMinute: Int = 0,
    val endHour: Int = 7,
    val endMinute: Int = 0,
    val daysOfWeek: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7), // ISO 1=Mon..7=Sun
)

@Serializable
data class LocalRestrictionConfig(
    val manualModeOverride: LauncherMode? = null,
    val dailyScreenTimeBudgetMinutes: Int = 120,
    val bonusMinutesToday: Int = 0,
    val bedtimeSchedule: TimeWindowRule = TimeWindowRule(
        enabled = true,
        startHour = 20,
        startMinute = 0,
        endHour = 7,
        endMinute = 0,
    ),
    val schoolSchedule: TimeWindowRule = TimeWindowRule(
        enabled = false,
        startHour = 8,
        startMinute = 30,
        endHour = 15,
        endMinute = 0,
        daysOfWeek = setOf(1, 2, 3, 4, 5),
    ),
    val tidyUpEndsAtEpochMs: Long = 0L,
    val allowBedtimeAudio: Boolean = true,
    val strictLauncherLockEnabled: Boolean = true,
)

@Serializable
data class HomeAssistantConfig(
    val enabled: Boolean = false,
    val serverUrl: String = "",
    val accessToken: String = "",
    val modeEntityId: String = "",        // e.g., input_select.kids_tablet_mode
    val lockSwitchEntityId: String = "",  // e.g., input_boolean.kids_tablet_lock
    val tidyUpSwitchEntityId: String = "",// e.g., input_boolean.kids_tablet_tidy_up
    val dailyBudgetEntityId: String = "", // e.g., input_number.kids_tablet_daily_minutes
    val publishEventsToHa: Boolean = true,
    val deviceIdentifier: String = "kids_tablet",
)

@Serializable
enum class PluginKind {
    JSON_FEED,
    HOME_ASSISTANT_ENTITY,
    BOOK_OPDS_OR_JSON,
}

@Serializable
data class ContentPluginConfig(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val kind: PluginKind = PluginKind.JSON_FEED,
    val targetCategory: AppCategory = AppCategory.VIDEOS,
    val showOnHomeMenu: Boolean = true,
    val endpointUrlOrEntityId: String = "",
    val authHeaderName: String = "",
    val authHeaderValue: String = "",
    val itemsJsonArrayKey: String = "items",
    val titleField: String = "title",
    val subtitleField: String = "subtitle",
    val imageUrlField: String = "imageUrl",
    val progressField: String = "progress",
    val launchUriField: String = "launchUri",
    val fallbackPackageName: String = "",
    val staticIntentExtras: Map<String, String> = emptyMap(),
)

@Serializable
data class LauncherSettings(
    val activeSkinId: String = "frost_kingdom",
    val customSkinLabels: Map<String, String> = emptyMap(),
    val pinHashHex: String = "",
    val pinSaltHex: String = "",
    val avatar: AvatarConfig = AvatarConfig(),
    val appRules: Map<String, ManagedAppRule> = emptyMap(),
    val localRestrictions: LocalRestrictionConfig = LocalRestrictionConfig(),
    val homeAssistant: HomeAssistantConfig = HomeAssistantConfig(),
    val familyLinkBridgeEnabled: Boolean = true,
    val hideSuspendedApps: Boolean = false,
    val plugins: List<ContentPluginConfig> = emptyList(),
)

data class EffectivePolicyState(
    val activeMode: LauncherMode = LauncherMode.FREE_PLAY,
    val activeSourceLabel: String = "Local Schedule",
    val remainingMinutesToday: Int = 120,
    val usedMinutesToday: Int = 0,
    val dailyBudgetMinutes: Int = 120,
    val tidyUpSecondsRemaining: Int = 0,
    val allowedTabs: Set<LauncherTab> = LauncherTab.entries.toSet(),
    val haConnected: Boolean = false,
    val haStatusMessage: String = "Not enabled",
)
