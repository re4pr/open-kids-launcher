package io.github.re4pr.kidslauncher.guard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.re4pr.kidslauncher.MainActivity
import io.github.re4pr.kidslauncher.R
import io.github.re4pr.kidslauncher.data.AppCatalogService
import io.github.re4pr.kidslauncher.data.SettingsRepository
import io.github.re4pr.kidslauncher.provider.ScheduleEvaluator
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Lightweight foreground guard service that keeps the child inside Open Kids Launcher
 * and approved kid apps when Strict Kid Lock is enabled.
 */
class KidGuardService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var repository: SettingsRepository
    private lateinit var appCatalog: AppCatalogService

    @Volatile
    private var cachedAllowedPackages: Set<String> = emptySet()
    private var lastCatalogRefreshMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(applicationContext)
        appCatalog = AppCatalogService(applicationContext)
        ensureChannel()
        startForeground(NOTIF_ID, buildNotification())
        startGuardLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startGuardLoop() {
        serviceScope.launch {
            while (isActive) {
                delay(750L)
                val settings = repository.settings.value
                if (!settings.localRestrictions.strictLauncherLockEnabled) continue
                if (!KidGuardController.isInteractive(applicationContext)) continue
                if (KidGuardController.isAdminExitBypassActive()) continue
                if (!KidGuardController.hasUsageStatsPermission(applicationContext)) continue

                val nowMs = System.currentTimeMillis()
                if (nowMs - lastCatalogRefreshMs > 20_000L || cachedAllowedPackages.isEmpty()) {
                    cachedAllowedPackages = computeAllowedPackages(settings)
                    lastCatalogRefreshMs = nowMs
                }

                val topPkg = KidGuardController.queryTopForegroundPackage(applicationContext) ?: continue
                val usedMins = repository.getUsedSecondsToday() / 60
                val totalBudget = if (settings.localRestrictions.dailyScreenTimeBudgetMinutes > 0) {
                    settings.localRestrictions.dailyScreenTimeBudgetMinutes + settings.localRestrictions.bonusMinutesToday
                } else {
                    0
                }
                val activeMode = ScheduleEvaluator.evaluateLocalMode(
                    config = settings.localRestrictions,
                    now = LocalDateTime.now(),
                    nowEpochMs = nowMs,
                    usedMinutesToday = usedMins,
                    effectiveDailyBudgetMinutes = totalBudget,
                )

                val permitted = KidGuardController.isPackagePermittedInForeground(
                    selfPackage = packageName,
                    foregroundPackage = topPkg,
                    settings = settings,
                    activeMode = activeMode,
                    allowedKidPackages = cachedAllowedPackages,
                    nowMs = nowMs,
                )
                if (!permitted) {
                    val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                        putExtra(EXTRA_BLOCKED_SWITCH_NOTICE, true)
                    }
                    runCatching { startActivity(launchIntent) }
                }
            }
        }
    }

    private fun computeAllowedPackages(settings: io.github.re4pr.kidslauncher.model.LauncherSettings): Set<String> {
        val installed = appCatalog.queryInstalledLaunchableApps()
        return installed.mapNotNull { entry ->
            val rule = settings.appRules[entry.packageName]
            val isAllowed = rule?.allowed ?: entry.defaultAllowed
            if (isAllowed) entry.packageName else null
        }.toSet()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java) ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kids Launcher Guard",
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = "Keeps Open Kids Launcher active when Strict Kid Lock is enabled"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Open Kids Launcher")
            .setContentText("Kid Lock active")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openIntent)
            .build()
    }

    companion object {
        const val EXTRA_BLOCKED_SWITCH_NOTICE = "extra_blocked_switch_notice"
        private const val CHANNEL_ID = "kids_launcher_guard"
        private const val NOTIF_ID = 4107

        fun syncState(context: Context, enabled: Boolean) {
            val intent = Intent(context, KidGuardService::class.java)
            if (enabled && KidGuardController.hasUsageStatsPermission(context)) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            } else {
                runCatching { context.stopService(intent) }
            }
        }
    }
}
