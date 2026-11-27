package io.github.re4pr.kidslauncher.guard

import android.app.AppOpsManager
import android.app.role.RoleManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import io.github.re4pr.kidslauncher.model.AppCategory
import io.github.re4pr.kidslauncher.model.LauncherMode
import io.github.re4pr.kidslauncher.model.LauncherSettings

/**
 * Coordinates Strict Kid Lock mode so children cannot leave Open Kids Launcher
 * via Recents, Taskbar, or System Settings unless a parent unlocks the Admin Menu.
 */
object KidGuardController {
    @Volatile
    var adminExitBypassUntilEpochMs: Long = 0L
        private set

    @Volatile
    var lastBlockedNoticeEpochMs: Long = 0L

    private val essentialSystemPackages = setOf(
        "android",
        "com.android.systemui",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.google.android.gms.supervision",
        "com.samsung.android.biometrics.app.setting",
        "com.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.google.android.inputmethod.latin",
    )

    fun grantTemporaryAdminExit(durationMs: Long = 120_000L) {
        adminExitBypassUntilEpochMs = System.currentTimeMillis() + durationMs
    }

    fun clearAdminExitBypass() {
        adminExitBypassUntilEpochMs = 0L
    }

    fun isAdminExitBypassActive(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs < adminExitBypassUntilEpochMs

    fun isDefaultHomeApp(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleHeld(RoleManager.ROLE_HOME)) return true
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == context.packageName
    }

    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isInteractive(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return true
        return pm.isInteractive
    }

    /**
     * Queries the most recent foreground resumed activity package within the last [lookbackMs].
     */
    fun queryTopForegroundPackage(context: Context, lookbackMs: Long = 15_000L): String? {
        if (!hasUsageStatsPermission(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val start = end - lookbackMs
        val events = runCatching { usm.queryEvents(start, end) }.getOrNull() ?: return null
        val event = UsageEvents.Event()
        var latestPkg: String? = null
        var latestTs = 0L
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForeground = event.eventType == UsageEvents.Event.ACTIVITY_RESUMED ||
                @Suppress("DEPRECATION")
                (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND)
            if (isForeground && event.timeStamp >= latestTs && !event.packageName.isNullOrBlank()) {
                latestTs = event.timeStamp
                latestPkg = event.packageName
            }
        }
        return latestPkg
    }

    /**
     * Sums today's foreground usage time across user/kid apps via Android UsageStatsManager
     * (matching the same device-level counter used by Digital Wellbeing & Google Family Link).
     */
    fun queryTodayTotalScreenTimeMinutes(context: Context): Int? {
        if (!hasUsageStatsPermission(context)) return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val zone = java.time.ZoneId.systemDefault()
        val startOfDayMs = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val nowMs = System.currentTimeMillis()
        val stats = runCatching {
            usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDayMs, nowMs)
        }.getOrNull() ?: return null

        val excluded = essentialSystemPackages + setOf(
            context.packageName,
            "com.sec.android.app.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher3",
            "com.android.settings",
        )
        var totalMs = 0L
        stats.forEach { entry ->
            val pkg = entry.packageName ?: return@forEach
            if (pkg in excluded) return@forEach
            if (entry.lastTimeUsed >= startOfDayMs && entry.totalTimeInForeground > 0L) {
                totalMs += entry.totalTimeInForeground
            }
        }
        return (totalMs / 60_000L).toInt().coerceAtLeast(0)
    }

    /**
     * Pure policy decision: determines whether [foregroundPackage] is permitted on screen
     * under [settings], [activeMode], and [allowedKidPackages].
     */
    fun isPackagePermittedInForeground(
        selfPackage: String,
        foregroundPackage: String,
        settings: LauncherSettings,
        activeMode: LauncherMode,
        allowedKidPackages: Set<String>,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!settings.localRestrictions.strictLauncherLockEnabled) return true
        if (isAdminExitBypassActive(nowMs)) return true
        if (foregroundPackage == selfPackage) return true
        if (foregroundPackage in essentialSystemPackages) return true

        val rule = settings.appRules[foregroundPackage]
        return when (activeMode) {
            LauncherMode.LOCKED -> false
            LauncherMode.BEDTIME -> {
                settings.localRestrictions.allowBedtimeAudio &&
                    foregroundPackage in allowedKidPackages &&
                    (rule?.allowedInBedtime == true || rule?.category == AppCategory.AUDIO)
            }
            LauncherMode.TIDY_UP -> {
                foregroundPackage in allowedKidPackages && rule?.category == AppCategory.AUDIO
            }
            LauncherMode.SCHOOL_TIME -> {
                foregroundPackage in allowedKidPackages &&
                    (rule?.allowedInSchoolTime == true || rule?.category in setOf(AppCategory.BOOKS, AppCategory.LEARNING, AppCategory.AUDIO))
            }
            LauncherMode.FREE_PLAY -> foregroundPackage in allowedKidPackages
        }
    }
}
