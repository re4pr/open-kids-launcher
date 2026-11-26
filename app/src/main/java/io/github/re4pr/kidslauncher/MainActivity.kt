package io.github.re4pr.kidslauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
                        onOpenFamilyLinkSystemSettings = viewModel::openFamilyLinkSystemSettings,
                        onClose = { showParentCenter = false },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onReturnedToLauncher()
    }
}
