package io.github.re4pr.kidslauncher.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import io.github.re4pr.kidslauncher.model.AppCategory
import io.github.re4pr.kidslauncher.model.InstalledAppEntry
import io.github.re4pr.kidslauncher.model.ShelfMediaItem

class AppCatalogService(private val context: Context) {
    private val pm: PackageManager = context.packageManager

    fun queryInstalledLaunchableApps(): List<InstalledAppEntry> {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolves = pm.queryIntentActivities(mainIntent, 0)
        return resolves
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                val label = info.loadLabel(pm)?.toString()?.trim().orEmpty().ifBlank { pkg }
                val suspended = runCatching { pm.isPackageSuspended(pkg) }.getOrDefault(false)
                InstalledAppEntry(
                    packageName = pkg,
                    label = label,
                    suggestedCategory = suggestCategory(pkg, label, appInfo),
                    isSuspendedByOs = suspended,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun loadAppIcon(packageName: String): Drawable? =
        runCatching { pm.getApplicationIcon(packageName) }.getOrNull()

    fun launchApp(packageName: String): Boolean = runCatching {
        val intent = pm.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return false
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    fun launchShelfItem(item: ShelfMediaItem): Boolean = runCatching {
        val intent = when {
            item.launchUri.startsWith("intent:", ignoreCase = true) ->
                Intent.parseUri(item.launchUri, Intent.URI_INTENT_SCHEME)
            item.launchUri.isNotBlank() ->
                Intent(Intent.ACTION_VIEW, Uri.parse(item.launchUri)).apply {
                    if (item.packageName.isNotBlank()) {
                        setPackage(item.packageName)
                    }
                }
            item.packageName.isNotBlank() ->
                pm.getLaunchIntentForPackage(item.packageName)
            else -> null
        } ?: return false

        item.intentExtras.forEach { (key, value) ->
            val intVal = value.toIntOrNull()
            if (intVal != null) {
                intent.putExtra(key, intVal)
            } else {
                intent.putExtra(key, value)
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    }.recoverCatching {
        if (item.packageName.isNotBlank()) launchApp(item.packageName) else false
    }.getOrDefault(false)

    companion object {
        fun suggestCategory(packageName: String, label: String, appInfo: ApplicationInfo?): AppCategory {
            val combined = "${packageName.lowercase()} ${label.lowercase()}"
            when (appInfo?.category) {
                ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
                ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.VIDEOS
                ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.AUDIO
            }
            return when {
                VIDEO_KEYWORDS.any { combined.contains(it) } -> AppCategory.VIDEOS
                AUDIO_KEYWORDS.any { combined.contains(it) } -> AppCategory.AUDIO
                BOOK_KEYWORDS.any { combined.contains(it) } -> AppCategory.BOOKS
                LEARNING_KEYWORDS.any { combined.contains(it) } -> AppCategory.LEARNING
                else -> AppCategory.GAMES
            }
        }

        private val VIDEO_KEYWORDS = listOf(
            "netflix", "nuvio", "youtube", "video", "disney", "plex", "jellyfin", "kodi", "cinema", "tv", "player"
        )
        private val AUDIO_KEYWORDS = listOf(
            "yoto", "music", "spotify", "audio", "podcast", "radio", "sound", "songs"
        )
        private val BOOK_KEYWORDS = listOf(
            "book", "kindle", "kobo", "reader", "epub", "library", "komga", "kavita", "story"
        )
        private val LEARNING_KEYWORDS = listOf(
            "khan", "duolingo", "school", "math", "abc", "learn", "study", "phonics"
        )
    }
}
