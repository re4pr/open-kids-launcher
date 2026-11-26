package io.github.re4pr.kidslauncher.ui

import android.app.Application
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.re4pr.kidslauncher.data.AppCatalogService
import io.github.re4pr.kidslauncher.data.SettingsRepository
import io.github.re4pr.kidslauncher.model.AppCategory
import io.github.re4pr.kidslauncher.model.AvatarConfig
import io.github.re4pr.kidslauncher.model.EffectivePolicyState
import io.github.re4pr.kidslauncher.model.InstalledAppEntry
import io.github.re4pr.kidslauncher.model.LauncherMode
import io.github.re4pr.kidslauncher.model.LauncherSettings
import io.github.re4pr.kidslauncher.model.LauncherTab
import io.github.re4pr.kidslauncher.model.RecentLaunchRecord
import io.github.re4pr.kidslauncher.model.ShelfMediaItem
import io.github.re4pr.kidslauncher.model.SkinDefinition
import io.github.re4pr.kidslauncher.plugin.PluginEngine
import io.github.re4pr.kidslauncher.provider.FamilyLinkBridgeProvider
import io.github.re4pr.kidslauncher.provider.HomeAssistantControlProvider
import io.github.re4pr.kidslauncher.provider.LocalScheduleProvider
import io.github.re4pr.kidslauncher.provider.PolicyResolver
import io.github.re4pr.kidslauncher.security.PinSecurity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    val repository = SettingsRepository(application)
    private val appCatalog = AppCatalogService(application)
    private val pluginEngine = PluginEngine()

    private val localProvider = LocalScheduleProvider()
    private val haProvider = HomeAssistantControlProvider()
    private val familyLinkProvider = FamilyLinkBridgeProvider(application) {
        refreshInstalledApps()
    }

    private val builtInSkins: List<SkinDefinition> = repository.loadBuiltInSkins()

    private val _installedApps = MutableStateFlow<List<InstalledAppEntry>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppEntry>> = _installedApps.asStateFlow()

    private val _pluginItems = MutableStateFlow<List<ShelfMediaItem>>(emptyList())
    val pluginItems: StateFlow<List<ShelfMediaItem>> = _pluginItems.asStateFlow()

    private val _selectedTab = MutableStateFlow(LauncherTab.HOME)
    val selectedTab: StateFlow<LauncherTab> = _selectedTab.asStateFlow()

    private val _tickEpochMs = MutableStateFlow(System.currentTimeMillis())
    private var childAppLaunchedAtEpochMs: Long? = null

    val settings: StateFlow<LauncherSettings> = repository.settings
    val recents: StateFlow<List<RecentLaunchRecord>> = repository.recents

    val availableSkins: StateFlow<List<SkinDefinition>> = combine(
        MutableStateFlow(builtInSkins),
        repository.customSkins,
    ) { builtIn, custom ->
        builtIn + custom
    }.stateIn(viewModelScope, SharingStarted.Eagerly, builtInSkins)

    val familyLinkSummary: StateFlow<String> = combine(
        familyLinkProvider.directive,
        settings,
    ) { dir, cfg ->
        if (cfg.familyLinkBridgeEnabled) dir.statusDetail else "Family Link bridge disabled"
    }.stateIn(viewModelScope, SharingStarted.Eagerly, "Ready")

    val effectivePolicy: StateFlow<EffectivePolicyState> = combine(
        settings,
        localProvider.directive,
        haProvider.directive,
        familyLinkProvider.directive,
        _tickEpochMs,
    ) { cfg, localDir, haDir, flDir, nowMs ->
        val usedMins = repository.getUsedSecondsToday() / 60
        val baseBudget = haDir.dailyBudgetOverrideMinutes
            ?: cfg.localRestrictions.dailyScreenTimeBudgetMinutes
        val totalBudget = if (baseBudget > 0) baseBudget + cfg.localRestrictions.bonusMinutesToday else 0

        val directives = buildList {
            add(localDir)
            if (cfg.homeAssistant.enabled) add(haDir)
            if (cfg.familyLinkBridgeEnabled) add(flDir)
        }
        val (mode, source) = PolicyResolver.resolveModePriority(directives)
        val allowedTabs = PolicyResolver.allowedTabsForMode(mode, cfg.localRestrictions.allowBedtimeAudio)
        val tidyRemaining = ((cfg.localRestrictions.tidyUpEndsAtEpochMs - nowMs) / 1000L)
            .coerceAtLeast(0L)
            .toInt()

        EffectivePolicyState(
            activeMode = mode,
            activeSourceLabel = source,
            remainingMinutesToday = if (totalBudget > 0) (totalBudget - usedMins).coerceAtLeast(0) else 0,
            usedMinutesToday = usedMins,
            dailyBudgetMinutes = totalBudget,
            tidyUpSecondsRemaining = tidyRemaining,
            allowedTabs = allowedTabs,
            haConnected = haDir.statusConnected,
            haStatusMessage = haDir.statusDetail,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, EffectivePolicyState())

    val approvedKidTiles: StateFlow<List<KidAppTile>> = combine(
        _installedApps,
        settings,
    ) { installed, cfg ->
        installed.mapNotNull { entry ->
            val rule = cfg.appRules[entry.packageName]
            val isAllowed = rule?.allowed ?: entry.defaultAllowed
            if (!isAllowed) return@mapNotNull null
            if (cfg.familyLinkBridgeEnabled && cfg.hideSuspendedApps && entry.isSuspendedByOs) {
                return@mapNotNull null
            }
            val effectiveCategory = rule?.category ?: entry.suggestedCategory
            KidAppTile(
                packageName = entry.packageName,
                displayName = rule?.customLabel?.ifBlank { entry.label } ?: entry.label,
                category = effectiveCategory,
                favorite = rule?.favorite ?: false,
                isSuspendedByOs = cfg.familyLinkBridgeEnabled && entry.isSuspendedByOs,
                allowedInSchoolTime = rule?.allowedInSchoolTime ?: (effectiveCategory == AppCategory.LEARNING),
                allowedInBedtime = rule?.allowedInBedtime ?: (effectiveCategory == AppCategory.AUDIO),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        familyLinkProvider.start()
        refreshInstalledApps()

        viewModelScope.launch {
            settings.collect { cfg ->
                haProvider.applyConfig(cfg.homeAssistant)
                familyLinkProvider.refreshStatus(cfg.familyLinkBridgeEnabled)
                evaluateSchedulesNow(cfg)
                refreshPluginFeeds()
            }
        }

        viewModelScope.launch {
            haProvider.entityStatesFlow.collect {
                if (settings.value.plugins.any { p -> p.enabled }) {
                    refreshPluginFeeds()
                }
            }
        }

        // Periodic schedule & countdown evaluator
        viewModelScope.launch {
            while (true) {
                delay(1_000)
                val now = System.currentTimeMillis()
                _tickEpochMs.value = now
                evaluateSchedulesNow(settings.value)
                val allowed = effectivePolicy.value.allowedTabs
                if (_selectedTab.value !in allowed) {
                    _selectedTab.value = LauncherTab.HOME
                }
            }
        }
    }

    private fun evaluateSchedulesNow(cfg: LauncherSettings) {
        val usedMins = repository.getUsedSecondsToday() / 60
        val haBudget = haProvider.directive.value.dailyBudgetOverrideMinutes
        val baseBudget = haBudget ?: cfg.localRestrictions.dailyScreenTimeBudgetMinutes
        val effectiveBudget = if (baseBudget > 0) baseBudget + cfg.localRestrictions.bonusMinutesToday else 0
        localProvider.evaluate(
            config = cfg.localRestrictions,
            usedMinutesToday = usedMins,
            budgetMinutes = effectiveBudget,
        )
    }

    fun refreshInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _installedApps.value = appCatalog.queryInstalledLaunchableApps()
        }
    }

    fun refreshPluginFeeds() {
        viewModelScope.launch {
            val cfg = settings.value
            _pluginItems.value = pluginEngine.fetchAllPluginItems(
                configs = cfg.plugins,
                haEntitySnapshot = haProvider.entityStatesFlow.value,
            )
        }
    }

    fun selectTab(tab: LauncherTab) {
        if (tab in effectivePolicy.value.allowedTabs) {
            _selectedTab.value = tab
        }
    }

    fun loadAppIcon(packageName: String): Drawable? = appCatalog.loadAppIcon(packageName)

    fun launchKidApp(tile: KidAppTile) {
        if (tile.isSuspendedByOs) return
        if (appCatalog.launchApp(tile.packageName)) {
            childAppLaunchedAtEpochMs = System.currentTimeMillis()
            repository.recordRecent(
                RecentLaunchRecord(
                    id = "app:${tile.packageName}",
                    title = tile.displayName,
                    subtitle = tile.category.label,
                    category = tile.category,
                    packageName = tile.packageName,
                    lastPlayedEpochMs = System.currentTimeMillis(),
                )
            )
            haProvider.notifyActivity(
                eventType = "app_launched",
                payload = mapOf("package" to tile.packageName, "label" to tile.displayName, "category" to tile.category.name),
            )
        }
    }

    fun launchRecent(record: RecentLaunchRecord) {
        val launched = if (record.launchUri.isNotBlank()) {
            appCatalog.launchShelfItem(
                ShelfMediaItem(
                    id = record.id,
                    pluginId = "recent",
                    title = record.title,
                    subtitle = record.subtitle,
                    category = record.category,
                    imageUrl = record.imageUrl,
                    launchUri = record.launchUri,
                    packageName = record.packageName,
                )
            )
        } else {
            appCatalog.launchApp(record.packageName)
        }
        if (launched) {
            childAppLaunchedAtEpochMs = System.currentTimeMillis()
            repository.recordRecent(record.copy(lastPlayedEpochMs = System.currentTimeMillis()))
        }
    }

    fun launchShelfItem(item: ShelfMediaItem) {
        if (appCatalog.launchShelfItem(item)) {
            childAppLaunchedAtEpochMs = System.currentTimeMillis()
            repository.recordRecent(
                RecentLaunchRecord(
                    id = "item:${item.id}",
                    title = item.title,
                    subtitle = item.subtitle,
                    category = item.category,
                    packageName = item.packageName,
                    launchUri = item.launchUri,
                    imageUrl = item.imageUrl,
                    progressFraction = item.progressFraction,
                    lastPlayedEpochMs = System.currentTimeMillis(),
                )
            )
            haProvider.notifyActivity(
                eventType = "media_shelf_launched",
                payload = mapOf("title" to item.title, "category" to item.category.name),
            )
        }
    }

    fun onReturnedToLauncher() {
        val departedAt = childAppLaunchedAtEpochMs
        if (departedAt != null) {
            val elapsedSec = ((System.currentTimeMillis() - departedAt) / 1000L)
                .coerceIn(0L, 4 * 3600L)
                .toInt()
            repository.addUsedSecondsToday(elapsedSec)
            childAppLaunchedAtEpochMs = null
        }
        refreshInstalledApps()
        evaluateSchedulesNow(settings.value)
    }

    fun saveAvatarAndSkin(avatar: AvatarConfig, skinId: String) {
        repository.updateSettings {
            it.copy(avatar = avatar, activeSkinId = skinId)
        }
    }

    fun hasParentPasscode(): Boolean =
        settings.value.pinHashHex.isNotBlank() && settings.value.pinSaltHex.isNotBlank()

    fun verifyParentPasscode(candidate: String): Boolean =
        PinSecurity.verifyPasscode(candidate, settings.value.pinHashHex, settings.value.pinSaltHex)

    fun setParentPasscode(newPasscode: String) {
        val salt = PinSecurity.generateSaltHex()
        val hash = PinSecurity.hashPasscode(newPasscode, salt)
        repository.updateSettings {
            it.copy(pinHashHex = hash, pinSaltHex = salt)
        }
    }

    fun triggerTidyUpMinutes(minutes: Int) {
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        repository.updateSettings {
            it.copy(
                localRestrictions = it.localRestrictions.copy(
                    manualModeOverride = null,
                    tidyUpEndsAtEpochMs = endsAt,
                )
            )
        }
    }

    fun addBonusMinutes(minutes: Int) {
        repository.updateSettings {
            it.copy(
                localRestrictions = it.localRestrictions.copy(
                    bonusMinutesToday = it.localRestrictions.bonusMinutesToday + minutes,
                )
            )
        }
    }

    fun resetTodayUsage() {
        repository.resetTodayUsage()
        evaluateSchedulesNow(settings.value)
    }

    fun openFamilyLinkSystemSettings() {
        familyLinkProvider.openSystemParentalControls()
    }

    override fun onCleared() {
        super.onCleared()
        haProvider.stop()
        familyLinkProvider.stop()
    }
}
