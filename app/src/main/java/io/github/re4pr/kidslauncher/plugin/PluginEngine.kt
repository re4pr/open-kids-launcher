package io.github.re4pr.kidslauncher.plugin

import io.github.re4pr.kidslauncher.model.AppCategory
import io.github.re4pr.kidslauncher.model.ContentPluginConfig
import io.github.re4pr.kidslauncher.model.PluginKind
import io.github.re4pr.kidslauncher.model.ShelfMediaItem
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Pluggable Book Service interface so external e-book/audiobook ecosystems (OPDS catalogs,
 * Kavita, Komga, Audiobookshelf, or custom JSON catalogs) can populate the Books menu
 * without modifying core UI code.
 */
interface BookServiceProvider {
    val providerId: String
    val providerTitle: String
    suspend fun fetchBooks(): List<ShelfMediaItem>
}

/**
 * Evaluates customizable [ContentPluginConfig] instances:
 * - Generic JSON HTTP feeds (e.g., Continue Watching shelves from Nuvio, Jellyfin, or custom APIs)
 * - Home Assistant entity attribute shelves (e.g., `sensor.continue_watching`)
 * - Pluggable book feeds (`BOOK_OPDS_OR_JSON`)
 */
class PluginEngine(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchAllPluginItems(
        configs: List<ContentPluginConfig>,
        haEntitySnapshot: Map<String, JsonObject>,
    ): List<ShelfMediaItem> = withContext(Dispatchers.IO) {
        configs.filter { it.enabled && it.endpointUrlOrEntityId.isNotBlank() }
            .flatMap { cfg ->
                runCatching {
                    when (cfg.kind) {
                        PluginKind.JSON_FEED, PluginKind.BOOK_OPDS_OR_JSON -> fetchJsonHttpFeed(cfg)
                        PluginKind.HOME_ASSISTANT_ENTITY -> extractFromHaEntity(cfg, haEntitySnapshot)
                    }
                }.getOrDefault(emptyList())
            }
    }

    private fun fetchJsonHttpFeed(cfg: ContentPluginConfig): List<ShelfMediaItem> {
        val reqBuilder = Request.Builder().url(cfg.endpointUrlOrEntityId.trim())
        if (cfg.authHeaderName.isNotBlank() && cfg.authHeaderValue.isNotBlank()) {
            reqBuilder.header(cfg.authHeaderName.trim(), cfg.authHeaderValue.trim())
        }
        httpClient.newCall(reqBuilder.build()).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val body = resp.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            return parseItemsFromJson(body, cfg)
        }
    }

    private fun extractFromHaEntity(
        cfg: ContentPluginConfig,
        haEntitySnapshot: Map<String, JsonObject>,
    ): List<ShelfMediaItem> {
        val entity = haEntitySnapshot[cfg.endpointUrlOrEntityId.trim()] ?: return emptyList()
        val attributes = entity["attributes"]?.jsonObject ?: return emptyList()
        val arrayElem = attributes[cfg.itemsJsonArrayKey.ifBlank { "items" }] as? JsonArray
            ?: return emptyList()
        return mapJsonArray(arrayElem, cfg)
    }

    fun parseItemsFromJson(rawJson: String, cfg: ContentPluginConfig): List<ShelfMediaItem> {
        val root: JsonElement = json.parseToJsonElement(rawJson)
        val array: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> {
                val key = cfg.itemsJsonArrayKey.trim()
                if (key.isNotEmpty() && root[key] is JsonArray) {
                    root[key] as JsonArray
                } else {
                    root.values.firstOrNull { it is JsonArray } as? JsonArray ?: return emptyList()
                }
            }
            else -> return emptyList()
        }
        return mapJsonArray(array, cfg)
    }

    private fun mapJsonArray(array: JsonArray, cfg: ContentPluginConfig): List<ShelfMediaItem> {
        val category = if (cfg.kind == PluginKind.BOOK_OPDS_OR_JSON) AppCategory.BOOKS else cfg.targetCategory
        return array.mapIndexedNotNull { idx, elem ->
            val obj = elem as? JsonObject ?: return@mapIndexedNotNull null
            val title = obj.stringField(cfg.titleField.ifBlank { "title" })
                .ifBlank { obj.stringField("name") }
            if (title.isBlank()) return@mapIndexedNotNull null

            val subtitle = obj.stringField(cfg.subtitleField.ifBlank { "subtitle" })
            val imageUrl = obj.stringField(cfg.imageUrlField.ifBlank { "imageUrl" })
                .ifBlank { obj.stringField("poster") }
            val launchUri = obj.stringField(cfg.launchUriField.ifBlank { "launchUri" })
                .ifBlank { obj.stringField("uri") }
            val pkg = obj.stringField("packageName").ifBlank { cfg.fallbackPackageName.trim() }
            val progress = obj[cfg.progressField.ifBlank { "progress" }]
                ?.jsonPrimitive?.floatOrNull?.coerceIn(0f, 1f) ?: 0f
            val itemId = obj.stringField("id").ifBlank { "${cfg.id}_$idx" }

            ShelfMediaItem(
                id = itemId,
                pluginId = cfg.id,
                title = title,
                subtitle = subtitle,
                category = category,
                imageUrl = imageUrl,
                progressFraction = progress,
                launchUri = launchUri,
                packageName = pkg,
                intentExtras = cfg.staticIntentExtras,
            )
        }
    }

    private fun JsonObject.stringField(key: String): String =
        runCatching { this[key]?.jsonPrimitive?.content.orEmpty().trim() }.getOrDefault("")
}
