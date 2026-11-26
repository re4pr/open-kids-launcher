package io.github.re4pr.kidslauncher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.re4pr.kidslauncher.guard.KidGuardController
import io.github.re4pr.kidslauncher.guard.KidGuardService
import io.github.re4pr.kidslauncher.model.LauncherTab
import io.github.re4pr.kidslauncher.skin.SkinThemeEngine
import io.github.re4pr.kidslauncher.ui.AvatarCustomizationDialog
import io.github.re4pr.kidslauncher.ui.KidHomeScreen
import io.github.re4pr.kidslauncher.ui.LauncherViewModel
import io.github.re4pr.kidslauncher.ui.ParentControlCenterDialog
import io.github.re4pr.kidslauncher.ui.ParentPasscodeGateDialog

class MainActivity : ComponentActivity() {
    private val viewModel: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyImmersiveKidLock(viewModel.settings.value.localRestrictions.strictLauncherLockEnabled)

        setContent {
            MaterialTheme {
                val settings by viewModel.settings.collectAsState()
                val policy by viewModel.effectivePolicy.collectAsState()
                val selectedTab by viewModel.selectedTab.collectAsState()
                val approvedApps by viewModel.approvedKidTiles.collectAsState()
                val recents by viewModel.recents.collectAsState()
                val pluginItems by viewModel.pluginItems.collectAsState()
                val availableSkins by viewModel.availableSkins.collectAsState()
                val installedApps by viewModel.installedApps.collectAsState()
                val familyLinkSummary by viewModel.familyLinkSummary.collectAsState()

                var showAvatarStudio by remember { mutableStateOf(false) }
                var showParentPinGate by remember { mutableStateOf(false) }
                var showParentCenter by remember { mutableStateOf(false) }

                LaunchedEffect(settings.localRestrictions.strictLauncherLockEnabled) {
                    applyImmersiveKidLock(settings.localRestrictions.strictLauncherLockEnabled)
                    KidGuardService.syncState(
                        this@MainActivity,
                        settings.localRestrictions.strictLauncherLockEnabled,
                    )
                }

                val activeSkin = remember(availableSkins, settings.activeSkinId) {
                    availableSkins.firstOrNull { it.id == settings.activeSkinId }
                        ?: availableSkins.first()
                }
                val palette = remember(activeSkin, policy.activeMode, settings.customSkinLabels) {
                    SkinThemeEngine.resolvePalette(
                        skin = activeSkin,
                        mode = policy.activeMode,
                        customLabels = settings.customSkinLabels,
                    )
                }

                // Keep child safely inside the launcher when Back is pressed
                BackHandler(enabled = true) {
                    if (selectedTab != LauncherTab.HOME) {
                        viewModel.selectTab(LauncherTab.HOME)
                    }
                }

                KidHomeScreen(
                    palette = palette,
                    avatar = settings.avatar,
                    policy = policy,
                    availableSkins = availableSkins,
                    customSkinLabels = settings.customSkinLabels,
                    selectedTab = selectedTab,
                    onSelectTab = viewModel::selectTab,
                    onQuickSelectSkin = { skinId ->
                        viewModel.repository.updateSettings { it.copy(activeSkinId = skinId) }
                    },
                    approvedApps = approvedApps,
                    recentRecords = recents,
                    pluginItems = pluginItems,
                    loadAppIcon = viewModel::loadAppIcon,
                    onLaunchApp = viewModel::launchKidApp,
                    onLaunchRecent = viewModel::launchRecent,
                    onLaunchShelfItem = viewModel::launchShelfItem,
                    onOpenAvatarStudio = { showAvatarStudio = true },
                    onRequestParentAccess = { showParentPinGate = true },
                )

                if (showAvatarStudio) {
                    AvatarCustomizationDialog(
                        initialAvatar = settings.avatar,
                        availableSkins = availableSkins,
                        activeSkinId = settings.activeSkinId,
                        customSkinLabels = settings.customSkinLabels,
                        onSave = { avatar, skinId ->
                            viewModel.saveAvatarAndSkin(avatar, skinId)
                            showAvatarStudio = false
                        },
                        onDismiss = { showAvatarStudio = false },
                    )
                }

                if (showParentPinGate) {
                    ParentPasscodeGateDialog(
                        isFirstTimeSetup = !viewModel.hasParentPasscode(),
                        onVerifyPasscode = { candidate ->
                            val ok = viewModel.verifyParentPasscode(candidate)
                            if (ok) {
                                showParentPinGate = false
                                showParentCenter = true
                            }
                            ok
                        },
                        onCreatePasscode = { newCode ->
                            viewModel.setParentPasscode(newCode)
                            showParentPinGate = false
                            showParentCenter = true
                        },
                        onDismiss = { showParentPinGate = false },
                    )
                }

                if (showParentCenter) {
                    ParentControlCenterDialog(
                        settings = settings,
                        policy = policy,
                        installedApps = installedApps,
                        availableSkins = availableSkins,
                        familyLinkSummary = familyLinkSummary,
                        onUpdateSettings = viewModel.repository::updateSettings,
                        onTriggerTidyUpMinutes = viewModel::triggerTidyUpMinutes,
                        onAddBonusMinutes = viewModel::addBonusMinutes,
                        onResetTodayUsage = viewModel::resetTodayUsage,
                        onChangePasscode = viewModel::setParentPasscode,
                        onImportCustomSkinJson = viewModel.repository::addOrUpdateCustomSkin,
                        onRemoveCustomSkin = viewModel.repository::removeCustomSkin,
                        onRefreshPlugins = viewModel::refreshPluginFeeds,
                        onOpenFamilyLinkSystemSettings = {
                            KidGuardController.grantTemporaryAdminExit(120_000L)
                            viewModel.openFamilyLinkSystemSettings()
                        },
                        onSwitchDefaultHomeApp = {
                            KidGuardController.grantTemporaryAdminExit(120_000L)
                            runCatching {
                                startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                        onExitToSystemLauncher = {
                            KidGuardController.grantTemporaryAdminExit(120_000L)
                            showParentCenter = false
                            launchAlternateHomeOrHomeChooser()
                        },
                        onClose = { showParentCenter = false },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(KidGuardService.EXTRA_BLOCKED_SWITCH_NOTICE, false)) {
            val now = System.currentTimeMillis()
            if (now - KidGuardController.lastBlockedNoticeEpochMs > 4_000L) {
                KidGuardController.lastBlockedNoticeEpochMs = now
                Toast.makeText(this, "Ask a parent to unlock Admin Menu to switch apps", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onReturnedToLauncher()
        val strictLock = viewModel.settings.value.localRestrictions.strictLauncherLockEnabled
        applyImmersiveKidLock(strictLock)
        KidGuardService.syncState(this, strictLock)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveKidLock(viewModel.settings.value.localRestrictions.strictLauncherLockEnabled)
        }
    }

    private fun applyImmersiveKidLock(enabled: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.navigationBars())
        } else {
            controller.show(WindowInsetsCompat.Type.navigationBars())
        }
    }

    private fun launchAlternateHomeOrHomeChooser() {
        val homeQuery = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val candidates = packageManager.queryIntentActivities(homeQuery, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.packageName != packageName }
        val fallback = candidates.firstOrNull()
        if (fallback != null) {
            val targetIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                setClassName(fallback.activityInfo.packageName, fallback.activityInfo.name)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (runCatching { startActivity(targetIntent) }.isSuccess) return
        }
        runCatching {
            startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
