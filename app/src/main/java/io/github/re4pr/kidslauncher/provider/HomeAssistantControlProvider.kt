package io.github.re4pr.kidslauncher.provider

import io.github.re4pr.kidslauncher.model.HomeAssistantConfig
import io.github.re4pr.kidslauncher.model.LauncherMode
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Native Home Assistant provider using WebSocket live state subscriptions + REST event reporting.
 * Zero URLs, entity IDs, or tokens are hardcoded; everything is supplied via [HomeAssistantConfig].
 */
class HomeAssistantControlProvider(
    private val httpClient: OkHttpClient = defaultOkHttpClient(),
) : ParentalControlProvider {
    override val id: String = "home_assistant"
    override val displayName: String = "Home Assistant"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private val msgCounter = AtomicInteger(1)

    private var config: HomeAssistantConfig = HomeAssistantConfig()
    private var webSocket: WebSocket? = null
    private var reconnectJob: Job? = null

    private val entityStates = mutableMapOf<String, JsonObject>()
    private val _allStatesFlow = MutableStateFlow<Map<String, JsonObject>>(emptyMap())
    val entityStatesFlow: StateFlow<Map<String, JsonObject>> = _allStatesFlow.asStateFlow()

    private val _directive = MutableStateFlow(
        ProviderDirective(
            providerId = id,
            providerName = displayName,
            active = false,
            statusConnected = false,
            statusDetail = "Disabled",
        )
    )
    override val directive: StateFlow<ProviderDirective> = _directive.asStateFlow()

    fun applyConfig(newConfig: HomeAssistantConfig) {
        val changed = newConfig != config
        config = newConfig
        if (!changed) return
        stop()
        if (newConfig.enabled && newConfig.serverUrl.isNotBlank() && newConfig.accessToken.isNotBlank()) {
            start()
        } else {
            _directive.value = ProviderDirective(
                providerId = id,
                providerName = displayName,
                active = false,
                statusConnected = false,
                statusDetail = if (newConfig.enabled) "Missing URL or Access Token" else "Disabled",
            )
        }
    }

    override fun start() {
        val current = config
        if (!current.enabled || current.serverUrl.isBlank() || current.accessToken.isBlank()) return
        reconnectJob?.cancel()
        connectWebSocket(current)
    }

    override fun stop() {
        reconnectJob?.cancel()
        webSocket?.close(1000, "Stopped")
        webSocket = null
    }

    private fun connectWebSocket(cfg: HomeAssistantConfig) {
        val wsUrl = buildWebSocketUrl(cfg.serverUrl)
        _directive.value = _directive.value.copy(
            active = true,
            statusConnected = false,
            statusDetail = "Connecting to Home Assistant...",
        )
        val req = Request.Builder().url(wsUrl).build()
        webSocket = httpClient.newWebSocket(req, object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(webSocket, cfg, text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _directive.value = _directive.value.copy(
                    statusConnected = false,
                    statusDetail = "Disconnected (${t.message ?: "network error"})",
                )
                scheduleReconnect(cfg)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (code != 1000) {
                    scheduleReconnect(cfg)
                }
            }
        })
    }

    private fun scheduleReconnect(cfg: HomeAssistantConfig) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(8_000)
            if (config == cfg && cfg.enabled) {
                connectWebSocket(cfg)
            }
        }
    }

    private fun handleMessage(ws: WebSocket, cfg: HomeAssistantConfig, raw: String) {
        val obj = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return
        when (obj["type"]?.jsonPrimitive?.content) {
            "auth_required" -> {
                val authPayload = buildJsonObject {
                    put("type", "auth")
                    put("access_token", cfg.accessToken.trim())
                }
                ws.send(authPayload.toString())
            }
            "auth_ok" -> {
                _directive.value = _directive.value.copy(
                    active = true,
                    statusConnected = true,
                    statusDetail = "Connected (WebSocket live)",
                )
                val getStatesCmd = buildJsonObject {
                    put("id", msgCounter.getAndIncrement())
                    put("type", "get_states")
                }
                val subEventsCmd = buildJsonObject {
                    put("id", msgCounter.getAndIncrement())
                    put("type", "subscribe_events")
                    put("event_type", "state_changed")
                }
                ws.send(getStatesCmd.toString())
                ws.send(subEventsCmd.toString())
            }
            "auth_invalid" -> {
                _directive.value = _directive.value.copy(
                    active = false,
                    statusConnected = false,
                    statusDetail = "Authentication failed — check Long-Lived Access Token",
                )
                ws.close(1000, "Auth invalid")
            }
            "result" -> {
                val resultElem = obj["result"]
                if (resultElem is JsonArray) {
                    synchronized(entityStates) {
                        resultElem.forEach { item ->
                            val stateObj = item as? JsonObject ?: return@forEach
                            val entityId = stateObj["entity_id"]?.jsonPrimitive?.content ?: return@forEach
                            entityStates[entityId] = stateObj
                        }
                        _allStatesFlow.value = entityStates.toMap()
                    }
                    recomputeDirective(cfg)
                }
            }
            "event" -> {
                val data = obj["event"]?.jsonObject?.get("data")?.jsonObject ?: return
                val entityId = data["entity_id"]?.jsonPrimitive?.content ?: return
                val newState = data["new_state"] as? JsonObject ?: return
                synchronized(entityStates) {
                    entityStates[entityId] = newState
                    _allStatesFlow.value = entityStates.toMap()
                }
                recomputeDirective(cfg)
            }
        }
    }

    private fun recomputeDirective(cfg: HomeAssistantConfig) {
        val snapshot = synchronized(entityStates) { entityStates.toMap() }

        val lockState = cfg.lockSwitchEntityId.trim().takeIf { it.isNotEmpty() }
            ?.let { snapshot[it]?.get("state")?.jsonPrimitive?.content }
        val tidyState = cfg.tidyUpSwitchEntityId.trim().takeIf { it.isNotEmpty() }
            ?.let { snapshot[it]?.get("state")?.jsonPrimitive?.content }
        val modeState = cfg.modeEntityId.trim().takeIf { it.isNotEmpty() }
            ?.let { snapshot[it]?.get("state")?.jsonPrimitive?.content }
        val budgetMinutes = cfg.dailyBudgetEntityId.trim().takeIf { it.isNotEmpty() }
            ?.let { snapshot[it]?.get("state")?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt() }

        val resolvedMode: LauncherMode? = when {
            isTruthySwitch(lockState) -> LauncherMode.LOCKED
            isTruthySwitch(tidyState) -> LauncherMode.TIDY_UP
            modeState != null -> parseModeString(modeState)
            else -> null
        }

        _directive.value = _directive.value.copy(
            active = cfg.enabled,
            enforcedMode = resolvedMode,
            dailyBudgetOverrideMinutes = budgetMinutes,
            statusConnected = true,
            statusDetail = if (resolvedMode != null) {
                "Connected • HA Mode: ${resolvedMode.title}"
            } else {
                "Connected • Listening for state changes"
            },
        )
    }

    override fun notifyActivity(eventType: String, payload: Map<String, String>) {
        val cfg = config
        if (!cfg.enabled || !cfg.publishEventsToHa || cfg.serverUrl.isBlank() || cfg.accessToken.isBlank()) return
        scope.launch {
            runCatching {
                val base = cfg.serverUrl.trim().trimEnd('/')
                val url = "$base/api/events/kids_launcher_event"
                val bodyJson = buildJsonObject {
                    put("device_id", cfg.deviceIdentifier.ifBlank { "kids_tablet" })
                    put("event_type", eventType)
                    payload.forEach { (k, v) -> put(k, v) }
                }.toString()
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer ${cfg.accessToken.trim()}")
                    .header("Content-Type", "application/json")
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()
                httpClient.newCall(req).execute().close()
            }
        }
    }

    companion object {
        fun buildWebSocketUrl(serverUrl: String): String {
            val clean = serverUrl.trim().trimEnd('/')
            val wsBase = when {
                clean.startsWith("https://", ignoreCase = true) ->
                    "wss://" + clean.removePrefix("https://").removePrefix("HTTPS://")
                clean.startsWith("http://", ignoreCase = true) ->
                    "ws://" + clean.removePrefix("http://").removePrefix("HTTP://")
                clean.startsWith("wss://", ignoreCase = true) || clean.startsWith("ws://", ignoreCase = true) ->
                    clean
                else -> "ws://$clean"
            }
            return "$wsBase/api/websocket"
        }

        fun parseModeString(raw: String): LauncherMode? {
            val norm = raw.trim().lowercase().replace('-', '_').replace(' ', '_')
            return when {
                norm in setOf("free_play", "play", "playtime", "normal", "unlocked", "home") ->
                    LauncherMode.FREE_PLAY
                norm in setOf("school", "school_time", "study", "homework", "learning") ->
                    LauncherMode.SCHOOL_TIME
                norm in setOf("tidy_up", "tidy", "clean_up", "cleanup", "chores") ->
                    LauncherMode.TIDY_UP
                norm in setOf("bedtime", "sleep", "night", "goodnight") ->
                    LauncherMode.BEDTIME
                norm in setOf("locked", "lock", "paused", "break", "screen_off") ->
                    LauncherMode.LOCKED
                else -> null
            }
        }

        private fun isTruthySwitch(state: String?): Boolean =
            state?.trim()?.lowercase() in setOf("on", "true", "locked", "active", "1")

        private fun defaultOkHttpClient(): OkHttpClient =
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
    }
}
