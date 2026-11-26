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
        val homeLaunchers = pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_HOME),
            0,
        ).mapNotNull { it.activityInfo?.packageName }.toSet()

        val vpnPackages = pm.queryIntentServices(
            Intent("android.net.VpnService"),
            0,
        ).mapNotNull { it.serviceInfo?.packageName }.toSet()

        val resolves = pm.queryIntentActivities(mainIntent, 0)
        return resolves
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == context.packageName) return@mapNotNull null
                val appInfo = runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
                val label = info.loadLabel(pm).toString().trim().ifBlank { pkg }
                val suspended = runCatching { pm.isPackageSuspended(pkg) }.getOrDefault(false)
                val category = suggestCategory(pkg, label, appInfo)
                val defaultAllowed = isDefaultKidVisibleCandidate(
                    packageName = pkg,
                    label = label,
                    appInfo = appInfo,
                    category = category,
                    homeLauncherPackages = homeLaunchers,
                    vpnPackages = vpnPackages,
                )
                InstalledAppEntry(
                    packageName = pkg,
                    label = label,
                    suggestedCategory = category,
                    isSuspendedByOs = suspended,
                    defaultAllowed = defaultAllowed,
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
                ApplicationInfo.CATEGORY_VIDEO -> return AppCategory.VIDEOS
                ApplicationInfo.CATEGORY_AUDIO -> return AppCategory.AUDIO
                ApplicationInfo.CATEGORY_GAME -> return AppCategory.GAMES
            }
            return when {
                VIDEO_KEYWORDS.any { combined.contains(it) } -> AppCategory.VIDEOS
                AUDIO_KEYWORDS.any { combined.contains(it) } -> AppCategory.AUDIO
                BOOK_KEYWORDS.any { combined.contains(it) } -> AppCategory.BOOKS
                LEARNING_KEYWORDS.any { combined.contains(it) } -> AppCategory.LEARNING
                else -> AppCategory.GAMES
            }
        }

        /**
         * Determines whether an installed app should appear automatically on first run before
         * a parent customizes per-app toggles. User-installed games, video players, and kid audio
         * apps default to visible, whereas OS settings, browsers, app stores, VPNs, alternative
         * launchers, and admin/background helpers remain hidden unless explicitly enabled.
         */
        fun isDefaultKidVisibleCandidate(
            packageName: String,
            label: String,
            appInfo: ApplicationInfo?,
            category: AppCategory,
            homeLauncherPackages: Set<String>,
            vpnPackages: Set<String>,
        ): Boolean {
            if (packageName in homeLauncherPackages || packageName in vpnPackages) return false

            val pkgLower = packageName.lowercase()
            val combined = "$pkgLower ${label.lowercase()}"
            if (ADMIN_OR_UTILITY_KEYWORDS.any { combined.contains(it) }) return false

            val flags = appInfo?.flags ?: 0
            val isPureSystemApp = (flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0
            if (isPureSystemApp) return false

            // Keep Books empty by default until parent explicitly enables a book app or plugin
            if (category == AppCategory.BOOKS) return false

            val isUpdatedSystemApp = (flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            if (isUpdatedSystemApp) {
                // Only auto-show updated system apps if they are explicitly categorized media/games
                return appInfo?.category in listOf(
                    ApplicationInfo.CATEGORY_GAME,
                    ApplicationInfo.CATEGORY_VIDEO,
                    ApplicationInfo.CATEGORY_AUDIO,
                )
            }

            return true
        }

        private val VIDEO_KEYWORDS = listOf(
            "netflix", "nuvio", "youtube", "disney", "plex", "jellyfin", "kodi", "cinema", "primevideo"
        )
        private val AUDIO_KEYWORDS = listOf(
            "yoto", "music", "spotify", "audiobook", "podcast", "radio", "songs"
        )
        private val BOOK_KEYWORDS = listOf(
            "kindle", "kobo", "reader", "epub", "komga", "kavita", "libby"
        )
        private val LEARNING_KEYWORDS = listOf(
            "khan", "duolingo", "school", "math", "abc", "learn", "study", "phonics", "numbers"
        )
        private val ADMIN_OR_UTILITY_KEYWORDS = listOf(
            "com.android.",
            "familylink",
            "kids.home",
            "kidsplat",
            "kidsinstaller",
            "homeassistant",
            "chrome",
            "browser",
            "vending",
            "photos",
            "quicksearchbox",
            "settings",
            "adb",
            "guard",
            "cache",
            "vpn",
            "tailscale",
            "wireguard",
            "zeropage",
            "contactkeys",
            "authenticator",
            "terminal",
            "filemanager",
        )
    }
}
