package io.github.re4pr.kidslauncher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.re4pr.kidslauncher.model.AppCategory
import io.github.re4pr.kidslauncher.model.ContentPluginConfig
import io.github.re4pr.kidslauncher.model.EffectivePolicyState
import io.github.re4pr.kidslauncher.model.HomeAssistantConfig
import io.github.re4pr.kidslauncher.model.InstalledAppEntry
import io.github.re4pr.kidslauncher.model.LauncherMode
import io.github.re4pr.kidslauncher.model.LauncherSettings
import io.github.re4pr.kidslauncher.model.ManagedAppRule
import io.github.re4pr.kidslauncher.model.PluginKind
import io.github.re4pr.kidslauncher.model.SkinDefinition
import java.util.UUID

@Composable
fun ParentPasscodeGateDialog(
    isFirstTimeSetup: Boolean,
    onVerifyPasscode: (String) -> Boolean,
    onCreatePasscode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var confirmCode by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isFirstTimeSetup) "Set Parent Master Code" else "Parent Master Code",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isFirstTimeSetup) {
                        "Create a secret parent master code (minimum 4 characters) to protect schedules, apps, and integrations."
                    } else {
                        "Enter your parent master code to unlock Parent Control Center."
                    }
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it
                        errorText = null
                    },
                    label = { Text("Master Code") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                if (isFirstTimeSetup) {
                    OutlinedTextField(
                        value = confirmCode,
                        onValueChange = {
                            confirmCode = it
                            errorText = null
                        },
                        label = { Text("Confirm Master Code") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
                if (errorText != null) {
                    Text(text = errorText!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isFirstTimeSetup) {
                        when {
                            code.trim().length < 4 -> errorText = "Use at least 4 characters."
                            code.trim() != confirmCode.trim() -> errorText = "Codes do not match."
                            else -> onCreatePasscode(code.trim())
                        }
                    } else {
                        if (!onVerifyPasscode(code)) {
                            errorText = "Incorrect master code."
                        }
                    }
                }
            ) {
                Text(if (isFirstTimeSetup) "Save & Open" else "Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
fun ParentControlCenterDialog(
    settings: LauncherSettings,
    policy: EffectivePolicyState,
    installedApps: List<InstalledAppEntry>,
    availableSkins: List<SkinDefinition>,
    familyLinkSummary: String,
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onTriggerTidyUpMinutes: (Int) -> Unit,
    onAddBonusMinutes: (Int) -> Unit,
    onResetTodayUsage: () -> Unit,
    onChangePasscode: (String) -> Unit,
    onImportCustomSkinJson: (String) -> Result<SkinDefinition>,
    onRemoveCustomSkin: (String) -> Unit,
    onRefreshPlugins: () -> Unit,
    onOpenFamilyLinkSystemSettings: () -> Unit,
    onClose: () -> Unit,
) {
    var selectedSection by remember { mutableIntStateOf(0) }
    val sections = listOf(
        "Timings & Modes",
        "Apps & Menus",
        "Skins & Themes",
        "Home Assistant & Family",
        "Plugins & Books",
    )

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Parent Control Center",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                        )
                        Text(
                            text = "Active: ${policy.activeMode.title} (${policy.activeSourceLabel}) • Used ${policy.usedMinutesToday}/${policy.dailyBudgetMinutes}m today",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close")
                    }
                }

                ScrollableTabRow(selectedTabIndex = selectedSection, edgePadding = 16.dp) {
                    sections.forEachIndexed { idx, title ->
                        Tab(
                            selected = selectedSection == idx,
                            onClick = { selectedSection = idx },
                            text = { Text(title, fontWeight = FontWeight.SemiBold) },
                        )
                    }
                }

                HorizontalDivider()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp)
                ) {
                    when (selectedSection) {
                        0 -> TimingsAndModesTab(
                            settings = settings,
                            policy = policy,
                            onUpdateSettings = onUpdateSettings,
                            onTriggerTidyUpMinutes = onTriggerTidyUpMinutes,
                            onAddBonusMinutes = onAddBonusMinutes,
                            onResetTodayUsage = onResetTodayUsage,
                            onChangePasscode = onChangePasscode,
                        )
                        1 -> AppsManagementTab(
                            settings = settings,
                            installedApps = installedApps,
                            onUpdateSettings = onUpdateSettings,
                        )
                        2 -> SkinsManagementTab(
                            settings = settings,
                            availableSkins = availableSkins,
                            onUpdateSettings = onUpdateSettings,
                            onImportCustomSkinJson = onImportCustomSkinJson,
                            onRemoveCustomSkin = onRemoveCustomSkin,
                        )
                        3 -> HomeAssistantAndFamilyTab(
                            settings = settings,
                            policy = policy,
                            familyLinkSummary = familyLinkSummary,
                            onUpdateSettings = onUpdateSettings,
                            onOpenFamilyLinkSystemSettings = onOpenFamilyLinkSystemSettings,
                        )
                        4 -> PluginsAndBooksTab(
                            settings = settings,
                            onUpdateSettings = onUpdateSettings,
                            onRefreshPlugins = onRefreshPlugins,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimingsAndModesTab(
    settings: LauncherSettings,
    policy: EffectivePolicyState,
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onTriggerTidyUpMinutes: (Int) -> Unit,
    onAddBonusMinutes: (Int) -> Unit,
    onResetTodayUsage: () -> Unit,
    onChangePasscode: (String) -> Unit,
) {
    val context = LocalContext.current
    val local = settings.localRestrictions
    var budgetText by remember(local.dailyScreenTimeBudgetMinutes) {
        mutableStateOf(local.dailyScreenTimeBudgetMinutes.toString())
    }
    var bedtimeStart by remember(local.bedtimeSchedule) {
        mutableStateOf("%02d:%02d".format(local.bedtimeSchedule.startHour, local.bedtimeSchedule.startMinute))
    }
    var bedtimeEnd by remember(local.bedtimeSchedule) {
        mutableStateOf("%02d:%02d".format(local.bedtimeSchedule.endHour, local.bedtimeSchedule.endMinute))
    }
    var schoolStart by remember(local.schoolSchedule) {
        mutableStateOf("%02d:%02d".format(local.schoolSchedule.startHour, local.schoolSchedule.startMinute))
    }
    var schoolEnd by remember(local.schoolSchedule) {
        mutableStateOf("%02d:%02d".format(local.schoolSchedule.endHour, local.schoolSchedule.endMinute))
    }
    var newPin by remember { mutableStateOf("") }
    var pinStatus by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Instant Mode Override", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = local.manualModeOverride == null && local.tidyUpEndsAtEpochMs <= System.currentTimeMillis(),
                onClick = {
                    onUpdateSettings {
                        it.copy(localRestrictions = it.localRestrictions.copy(manualModeOverride = null, tidyUpEndsAtEpochMs = 0L))
                    }
                },
                label = { Text("Auto (Follow Schedule)") },
            )
            LauncherMode.entries.forEach { mode ->
                FilterChip(
                    selected = local.manualModeOverride == mode,
                    onClick = {
                        onUpdateSettings {
                            it.copy(localRestrictions = it.localRestrictions.copy(manualModeOverride = mode))
                        }
                    },
                    label = { Text(mode.title) },
                )
            }
            OutlinedButton(onClick = { onTriggerTidyUpMinutes(15) }) {
                Text("Start 15m Tidy-Up")
            }
        }

        HorizontalDivider()

        Text("Daily Screen Time Budget", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = budgetText,
                onValueChange = { txt ->
                    budgetText = txt.filter { it.isDigit() }.take(4)
                    val mins = budgetText.toIntOrNull()
                    if (mins != null) {
                        onUpdateSettings {
                            it.copy(localRestrictions = it.localRestrictions.copy(dailyScreenTimeBudgetMinutes = mins))
                        }
                    }
                },
                label = { Text("Daily Limit (minutes, 0 = unlimited)") },
                singleLine = true,
                modifier = Modifier.width(260.dp),
            )
            OutlinedButton(onClick = { onAddBonusMinutes(15) }) { Text("+15m Bonus Today") }
            OutlinedButton(onClick = onResetTodayUsage) { Text("Reset Used (${policy.usedMinutesToday}m)") }
        }

        HorizontalDivider()

        // Bedtime Schedule
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(
                checked = local.bedtimeSchedule.enabled,
                onCheckedChange = { enabled ->
                    onUpdateSettings {
                        it.copy(localRestrictions = it.localRestrictions.copy(bedtimeSchedule = local.bedtimeSchedule.copy(enabled = enabled)))
                    }
                },
            )
            Text("Bedtime Schedule (HH:MM)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = bedtimeStart,
                onValueChange = { txt ->
                    bedtimeStart = txt
                    parseHhMm(txt)?.let { (h, m) ->
                        onUpdateSettings {
                            it.copy(localRestrictions = it.localRestrictions.copy(bedtimeSchedule = it.localRestrictions.bedtimeSchedule.copy(startHour = h, startMinute = m)))
                        }
                    }
                },
                label = { Text("Bedtime Starts") },
                singleLine = true,
                modifier = Modifier.width(150.dp),
            )
            OutlinedTextField(
                value = bedtimeEnd,
                onValueChange = { txt ->
                    bedtimeEnd = txt
                    parseHhMm(txt)?.let { (h, m) ->
                        onUpdateSettings {
                            it.copy(localRestrictions = it.localRestrictions.copy(bedtimeSchedule = it.localRestrictions.bedtimeSchedule.copy(endHour = h, endMinute = m)))
                        }
                    }
                },
                label = { Text("Wake Up") },
                singleLine = true,
                modifier = Modifier.width(150.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = local.allowBedtimeAudio,
                    onCheckedChange = { allow ->
                        onUpdateSettings {
                            it.copy(localRestrictions = it.localRestrictions.copy(allowBedtimeAudio = allow))
                        }
                    },
                )
                Text("Allow Bedtime Audio")
            }
        }

        HorizontalDivider()

        // School Time Schedule
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(
                checked = local.schoolSchedule.enabled,
                onCheckedChange = { enabled ->
                    onUpdateSettings {
                        it.copy(localRestrictions = it.localRestrictions.copy(schoolSchedule = local.schoolSchedule.copy(enabled = enabled)))
                    }
                },
            )
            Text("Weekday School / Study Window (Mon–Fri)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = schoolStart,
                onValueChange = { txt ->
                    schoolStart = txt
                    parseHhMm(txt)?.let { (h, m) ->
                        onUpdateSettings {
                            it.copy(localRestrictions = it.localRestrictions.copy(schoolSchedule = it.localRestrictions.schoolSchedule.copy(startHour = h, startMinute = m)))
                        }
                    }
                },
                label = { Text("School Starts") },
                singleLine = true,
                modifier = Modifier.width(150.dp),
            )
            OutlinedTextField(
                value = schoolEnd,
                onValueChange = { txt ->
                    schoolEnd = txt
                    parseHhMm(txt)?.let { (h, m) ->
                        onUpdateSettings {
                            it.copy(localRestrictions = it.localRestrictions.copy(schoolSchedule = it.localRestrictions.schoolSchedule.copy(endHour = h, endMinute = m)))
                        }
                    }
                },
                label = { Text("School Ends") },
                singleLine = true,
                modifier = Modifier.width(150.dp),
            )
        }

        HorizontalDivider()

        Text("Launcher & Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            ) {
                Text("Choose Default Home App")
            }
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
            ) {
                Text("Grant Usage Stats (Optional)")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it; pinStatus = null },
                label = { Text("Update Master Code") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.width(220.dp),
            )
            Button(
                onClick = {
                    if (newPin.trim().length >= 4) {
                        onChangePasscode(newPin.trim())
                        newPin = ""
                        pinStatus = "Master code updated."
                    } else {
                        pinStatus = "Minimum 4 characters."
                    }
                }
            ) {
                Text("Save New Code")
            }
            if (pinStatus != null) {
                Text(pinStatus!!, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppsManagementTab(
    settings: LauncherSettings,
    installedApps: List<InstalledAppEntry>,
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(installedApps, query) {
        if (query.isBlank()) installedApps
        else installedApps.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search installed apps (e.g. Netflix, Nuvio, Yoto, games...)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(filtered, key = { it.packageName }) { app ->
                val rule = settings.appRules[app.packageName] ?: ManagedAppRule(
                    packageName = app.packageName,
                    category = app.suggestedCategory,
                    allowed = false,
                )
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (rule.allowed) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = rule.allowed,
                                onCheckedChange = { allowed ->
                                    onUpdateSettings { curr ->
                                        curr.copy(appRules = curr.appRules + (app.packageName to rule.copy(allowed = allowed)))
                                    }
                                },
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(app.label, fontWeight = FontWeight.Bold)
                                Text(
                                    text = buildString {
                                        append(app.packageName)
                                        if (app.isSuspendedByOs) append(" • Paused by Family Link/OS")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (app.isSuspendedByOs) Color(0xFFD32F2F) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    onUpdateSettings { curr ->
                                        curr.copy(appRules = curr.appRules + (app.packageName to rule.copy(favorite = !rule.favorite)))
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (rule.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = "Favorite",
                                    tint = if (rule.favorite) Color(0xFFFFB300) else Color.Gray,
                                )
                            }
                        }
                        if (rule.allowed) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 6.dp),
                            ) {
                                AppCategory.entries.forEach { cat ->
                                    FilterChip(
                                        selected = rule.category == cat,
                                        onClick = {
                                            onUpdateSettings { curr ->
                                                curr.copy(appRules = curr.appRules + (app.packageName to rule.copy(category = cat)))
                                            }
                                        },
                                        label = { Text(cat.label) },
                                    )
                                }
                                FilterChip(
                                    selected = rule.allowedInSchoolTime,
                                    onClick = {
                                        onUpdateSettings { curr ->
                                            curr.copy(appRules = curr.appRules + (app.packageName to rule.copy(allowedInSchoolTime = !rule.allowedInSchoolTime)))
                                        }
                                    },
                                    label = { Text("School OK") },
                                )
                                FilterChip(
                                    selected = rule.allowedInBedtime,
                                    onClick = {
                                        onUpdateSettings { curr ->
                                            curr.copy(appRules = curr.appRules + (app.packageName to rule.copy(allowedInBedtime = !rule.allowedInBedtime)))
                                        }
                                    },
                                    label = { Text("Bedtime OK") },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkinsManagementTab(
    settings: LauncherSettings,
    availableSkins: List<SkinDefinition>,
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onImportCustomSkinJson: (String) -> Result<SkinDefinition>,
    onRemoveCustomSkin: (String) -> Unit,
) {
    var customSkinInput by remember { mutableStateOf("") }
    var importFeedback by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Active Skin & Custom Display Labels",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "Tip: You can customize any theme's local display title for your child (e.g., 'Frozen' or 'Unicorn') or import new JSON skin packs contributed by the community.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        availableSkins.forEach { skin ->
            val currentLabel = settings.customSkinLabels[skin.id].orEmpty()
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FilterChip(
                        selected = settings.activeSkinId == skin.id,
                        onClick = { onUpdateSettings { it.copy(activeSkinId = skin.id) } },
                        label = { Text(if (settings.activeSkinId == skin.id) "Active" else "Select") },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${skin.name} (${skin.id})", fontWeight = FontWeight.Bold)
                        Text(skin.tagline, style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedTextField(
                        value = currentLabel,
                        onValueChange = { custom ->
                            onUpdateSettings { curr ->
                                val nextMap = if (custom.isBlank()) {
                                    curr.customSkinLabels - skin.id
                                } else {
                                    curr.customSkinLabels + (skin.id to custom)
                                }
                                curr.copy(customSkinLabels = nextMap)
                            }
                        },
                        label = { Text("Custom Display Name") },
                        placeholder = { Text(skin.name) },
                        singleLine = true,
                        modifier = Modifier.width(220.dp),
                    )
                    if (!skin.isBuiltIn) {
                        IconButton(onClick = { onRemoveCustomSkin(skin.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete Skin")
                        }
                    }
                }
            }
        }

        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Import Custom Skin Pack (JSON)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(
                onClick = {
                    customSkinInput = """
                        {
                          "id": "space_galaxy",
                          "name": "Space Galaxy",
                          "tagline": "Rockets, planets & cosmic nebulas",
                          "skyTopHex": "#0B0C2A",
                          "skyMidHex": "#2C1654",
                          "skyBottomHex": "#6F42C1",
                          "surfaceCardHex": "#F3EEFF",
                          "accentPrimaryHex": "#7B2CBF",
                          "accentSecondaryHex": "#00F5D4",
                          "badgeHighlightHex": "#9D4EDD",
                          "textPrimaryHex": "#1A0933",
                          "textOnSkyHex": "#FFFFFF",
                          "particleStyle": "STARLIGHT",
                          "cardCornerDp": 28
                        }
                    """.trimIndent()
                }
            ) {
                Text("Load Sample JSON Template")
            }
        }
        OutlinedTextField(
            value = customSkinInput,
            onValueChange = { customSkinInput = it; importFeedback = null },
            label = { Text("Paste SkinDefinition JSON") },
            minLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val res = onImportCustomSkinJson(customSkinInput)
                    importFeedback = res.fold(
                        onSuccess = { "Imported skin '${it.name}'!" },
                        onFailure = { "Invalid JSON: ${it.message}" },
                    )
                }
            ) {
                Text("Save Custom Skin")
            }
            if (importFeedback != null) {
                Text(importFeedback!!, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HomeAssistantAndFamilyTab(
    settings: LauncherSettings,
    policy: EffectivePolicyState,
    familyLinkSummary: String,
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onOpenFamilyLinkSystemSettings: () -> Unit,
) {
    val ha: HomeAssistantConfig = settings.homeAssistant
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Switch(
                checked = ha.enabled,
                onCheckedChange = { en -> onUpdateSettings { it.copy(homeAssistant = ha.copy(enabled = en)) } },
            )
            Column {
                Text("Native Home Assistant Live Control (WebSocket + REST)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Status: ${policy.haStatusMessage}", style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedTextField(
            value = ha.serverUrl,
            onValueChange = { v -> onUpdateSettings { it.copy(homeAssistant = ha.copy(serverUrl = v)) } },
            label = { Text("Home Assistant URL") },
            placeholder = { Text("http://homeassistant.local:8123") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ha.accessToken,
            onValueChange = { v -> onUpdateSettings { it.copy(homeAssistant = ha.copy(accessToken = v)) } },
            label = { Text("Long-Lived Access Token") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = ha.modeEntityId,
                onValueChange = { v -> onUpdateSettings { it.copy(homeAssistant = ha.copy(modeEntityId = v)) } },
                label = { Text("Mode Entity ID (optional)") },
                placeholder = { Text("input_select.kids_tablet_mode") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = ha.lockSwitchEntityId,
                onValueChange = { v -> onUpdateSettings { it.copy(homeAssistant = ha.copy(lockSwitchEntityId = v)) } },
                label = { Text("Lock Switch Entity ID (optional)") },
                placeholder = { Text("input_boolean.kids_tablet_lock") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = ha.tidyUpSwitchEntityId,
                onValueChange = { v -> onUpdateSettings { it.copy(homeAssistant = ha.copy(tidyUpSwitchEntityId = v)) } },
                label = { Text("Tidy-Up Switch Entity ID") },
                placeholder = { Text("input_boolean.kids_tablet_tidy_up") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = ha.dailyBudgetEntityId,
                onValueChange = { v -> onUpdateSettings { it.copy(homeAssistant = ha.copy(dailyBudgetEntityId = v)) } },
                label = { Text("Daily Budget Minutes Entity ID") },
                placeholder = { Text("input_number.kids_tablet_minutes") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = ha.deviceIdentifier,
            onValueChange = { v -> onUpdateSettings { it.copy(homeAssistant = ha.copy(deviceIdentifier = v)) } },
            label = { Text("Event Device Identifier (fires kids_launcher_event)") },
            placeholder = { Text("kids_tablet") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider()

        Text("Google Family Link & OS Supervision Bridge", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(familyLinkSummary, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = settings.familyLinkBridgeEnabled,
                    onCheckedChange = { en -> onUpdateSettings { it.copy(familyLinkBridgeEnabled = en) } },
                )
                Spacer(Modifier.width(8.dp))
                Text("Sync Family Link App Pauses")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = settings.hideSuspendedApps,
                    onCheckedChange = { hide -> onUpdateSettings { it.copy(hideSuspendedApps = hide) } },
                )
                Text("Hide OS-paused apps completely")
            }
            OutlinedButton(onClick = onOpenFamilyLinkSystemSettings) {
                Text("Open System Parental Controls")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginsAndBooksTab(
    settings: LauncherSettings,
    onUpdateSettings: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onRefreshPlugins: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Customizable Content & Book Shelf Plugins", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onRefreshPlugins) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Refresh Feeds")
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val newPlugin = ContentPluginConfig(
                        id = "plugin_${UUID.randomUUID().toString().take(6)}",
                        name = "Continue Watching Feed (JSON)",
                        kind = PluginKind.JSON_FEED,
                        targetCategory = AppCategory.VIDEOS,
                        endpointUrlOrEntityId = "",
                        itemsJsonArrayKey = "items",
                    )
                    onUpdateSettings { it.copy(plugins = it.plugins + newPlugin) }
                }
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Add JSON Video/Audio Feed (e.g. Nuvio / Media API)")
            }
            OutlinedButton(
                onClick = {
                    val newPlugin = ContentPluginConfig(
                        id = "ha_shelf_${UUID.randomUUID().toString().take(6)}",
                        name = "Home Assistant Media Shelf",
                        kind = PluginKind.HOME_ASSISTANT_ENTITY,
                        targetCategory = AppCategory.VIDEOS,
                        endpointUrlOrEntityId = "sensor.continue_watching",
                        itemsJsonArrayKey = "items",
                    )
                    onUpdateSettings { it.copy(plugins = it.plugins + newPlugin) }
                }
            ) {
                Text("+ Add HA Entity Shelf")
            }
            OutlinedButton(
                onClick = {
                    val newPlugin = ContentPluginConfig(
                        id = "books_${UUID.randomUUID().toString().take(6)}",
                        name = "Book Service Shelf",
                        kind = PluginKind.BOOK_OPDS_OR_JSON,
                        targetCategory = AppCategory.BOOKS,
                        endpointUrlOrEntityId = "",
                        itemsJsonArrayKey = "books",
                    )
                    onUpdateSettings { it.copy(plugins = it.plugins + newPlugin) }
                }
            ) {
                Text("+ Add Book Service Connector")
            }
        }

        if (settings.plugins.isEmpty()) {
            Text(
                text = "No external shelf plugins configured yet. Add a JSON feed or Home Assistant sensor shelf above whenever you want to populate Continue Watching, Audio playlists, or Books dynamically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        settings.plugins.forEach { plugin ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = plugin.enabled,
                            onCheckedChange = { en ->
                                onUpdateSettings { curr ->
                                    curr.copy(plugins = curr.plugins.map { if (it.id == plugin.id) it.copy(enabled = en) else it })
                                }
                            },
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = plugin.name,
                            onValueChange = { nm ->
                                onUpdateSettings { curr ->
                                    curr.copy(plugins = curr.plugins.map { if (it.id == plugin.id) it.copy(name = nm) else it })
                                }
                            },
                            label = { Text("Plugin Name") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                onUpdateSettings { curr -> curr.copy(plugins = curr.plugins.filterNot { it.id == plugin.id }) }
                            }
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove plugin")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = plugin.endpointUrlOrEntityId,
                            onValueChange = { url ->
                                onUpdateSettings { curr ->
                                    curr.copy(plugins = curr.plugins.map { if (it.id == plugin.id) it.copy(endpointUrlOrEntityId = url) else it })
                                }
                            },
                            label = {
                                Text(
                                    if (plugin.kind == PluginKind.HOME_ASSISTANT_ENTITY) "HA Entity ID"
                                    else "JSON Feed URL"
                                )
                            },
                            placeholder = {
                                Text(
                                    if (plugin.kind == PluginKind.HOME_ASSISTANT_ENTITY) "sensor.continue_watching"
                                    else "https://media.example.local/api/continue-watching"
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = plugin.fallbackPackageName,
                            onValueChange = { pkg ->
                                onUpdateSettings { curr ->
                                    curr.copy(plugins = curr.plugins.map { if (it.id == plugin.id) it.copy(fallbackPackageName = pkg) else it })
                                }
                            },
                            label = { Text("Target App Package (optional)") },
                            singleLine = true,
                            modifier = Modifier.width(230.dp),
                        )
                    }

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AppCategory.entries.filter { it != AppCategory.LEARNING }.forEach { cat ->
                            FilterChip(
                                selected = plugin.targetCategory == cat,
                                onClick = {
                                    onUpdateSettings { curr ->
                                        curr.copy(plugins = curr.plugins.map { if (it.id == plugin.id) it.copy(targetCategory = cat) else it })
                                    }
                                },
                                label = { Text("Menu: ${cat.label}") },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun parseHhMm(raw: String): Pair<Int, Int>? {
    val parts = raw.trim().split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h to m
}
