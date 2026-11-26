package io.github.re4pr.kidslauncher.data

import android.content.Context
import io.github.re4pr.kidslauncher.model.HomeAssistantConfig
import io.github.re4pr.kidslauncher.model.LauncherSettings
import io.github.re4pr.kidslauncher.model.RecentLaunchRecord
import io.github.re4pr.kidslauncher.model.SkinDefinition
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    private val _recents = MutableStateFlow(loadRecents())
    val recents: StateFlow<List<RecentLaunchRecord>> = _recents.asStateFlow()

    private val _customSkins = MutableStateFlow(loadCustomSkins())
    val customSkins: StateFlow<List<SkinDefinition>> = _customSkins.asStateFlow()

    fun loadBuiltInSkins(): List<SkinDefinition> {
        val assetFiles = listOf(
            "skins/frost_kingdom.json",
            "skins/rainbow_unicorn.json",
            "skins/royal_princess.json",
            "skins/ocean_mermaid.json",
        )
        return assetFiles.mapNotNull { path ->
            runCatching {
                val raw = appContext.assets.open(path).bufferedReader().use { it.readText() }
                json.decodeFromString<SkinDefinition>(raw).copy(isBuiltIn = true)
            }.getOrNull()
        }
    }

    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        val next = transform(_settings.value)
        prefs.edit().putString(KEY_SETTINGS, json.encodeToString(next)).apply()
        _settings.value = next
    }

    fun recordRecent(entry: RecentLaunchRecord) {
        val updated = buildList {
            add(entry)
            addAll(_recents.value.filterNot { it.id == entry.id })
        }.take(MAX_RECENTS)
        prefs.edit().putString(KEY_RECENTS, json.encodeToString(updated)).apply()
        _recents.value = updated
    }

    fun addOrUpdateCustomSkin(skinJson: String): Result<SkinDefinition> = runCatching {
        val parsed = json.decodeFromString<SkinDefinition>(skinJson).copy(isBuiltIn = false)
        require(parsed.id.isNotBlank() && parsed.name.isNotBlank()) { "Skin must have an id and name" }
        val next = buildList {
            addAll(_customSkins.value.filterNot { it.id == parsed.id })
            add(parsed)
        }
        prefs.edit().putString(KEY_CUSTOM_SKINS, json.encodeToString(next)).apply()
        _customSkins.value = next
        parsed
    }

    fun removeCustomSkin(skinId: String) {
        val next = _customSkins.value.filterNot { it.id == skinId }
        prefs.edit().putString(KEY_CUSTOM_SKINS, json.encodeToString(next)).apply()
        _customSkins.value = next
    }

    fun getUsedSecondsToday(today: LocalDate = LocalDate.now()): Int {
        val key = "$KEY_USAGE_PREFIX$today"
        return prefs.getInt(key, 0)
    }

    fun addUsedSecondsToday(deltaSeconds: Int, today: LocalDate = LocalDate.now()): Int {
        if (deltaSeconds <= 0) return getUsedSecondsToday(today)
        val key = "$KEY_USAGE_PREFIX$today"
        val updated = (prefs.getInt(key, 0) + deltaSeconds).coerceAtLeast(0)
        prefs.edit().putInt(key, updated).apply()
        return updated
    }

    fun resetTodayUsage(today: LocalDate = LocalDate.now()) {
        prefs.edit().putInt("$KEY_USAGE_PREFIX$today", 0).apply()
    }

    /**
     * Exports a shareable configuration JSON with all tokens, PIN hashes, and auth headers stripped.
     */
    fun exportSanitizedConfigJson(): String {
        val current = _settings.value
        val sanitized = current.copy(
            pinHashHex = "",
            pinSaltHex = "",
            homeAssistant = current.homeAssistant.copy(
                serverUrl = "",
                accessToken = "",
            ),
            plugins = current.plugins.map { plugin ->
                plugin.copy(
                    endpointUrlOrEntityId = "",
                    authHeaderValue = "",
                )
            },
        )
        return json.encodeToString(sanitized)
    }

    private fun loadSettings(): LauncherSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return LauncherSettings()
        return runCatching { json.decodeFromString<LauncherSettings>(raw) }.getOrDefault(LauncherSettings())
    }

    private fun loadRecents(): List<RecentLaunchRecord> {
        val raw = prefs.getString(KEY_RECENTS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<RecentLaunchRecord>>(raw) }.getOrDefault(emptyList())
    }

    private fun loadCustomSkins(): List<SkinDefinition> {
        val raw = prefs.getString(KEY_CUSTOM_SKINS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<SkinDefinition>>(raw) }.getOrDefault(emptyList())
    }

    companion object {
        private const val PREFS_NAME = "open_kids_launcher_prefs"
        private const val KEY_SETTINGS = "launcher_settings_v1"
        private const val KEY_RECENTS = "recent_launches_v1"
        private const val KEY_CUSTOM_SKINS = "custom_skins_v1"
        private const val KEY_USAGE_PREFIX = "usage_sec_"
        private const val MAX_RECENTS = 24
    }
}
