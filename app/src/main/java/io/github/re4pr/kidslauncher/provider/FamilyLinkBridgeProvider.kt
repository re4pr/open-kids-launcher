package io.github.re4pr.kidslauncher.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bridges Android OS parental supervision / Google Family Link signals into the launcher.
 *
 * Google Family Link enforces per-app pauses, bedtime, and content limits through Android's
 * native DevicePolicyManager (package suspension and UserManager restrictions) rather than
 * exposing an unauthenticated third-party write API. This provider listens for live OS
 * suspension/restriction broadcasts so paused apps update immediately in the kid UI, and gives
 * parents a direct shortcut into system Digital Wellbeing / Family Link settings.
 */
class FamilyLinkBridgeProvider(
    private val context: Context,
    private val onPackagesChanged: () -> Unit,
) : ParentalControlProvider {
    override val id: String = "family_link_bridge"
    override val displayName: String = "Google Family Link / Android Supervision"

    private var registered = false

    private val _directive = MutableStateFlow(
        ProviderDirective(
            providerId = id,
            providerName = displayName,
            active = true,
            statusConnected = true,
            statusDetail = detectSupervisionSummary(),
        )
    )
    override val directive: StateFlow<ProviderDirective> = _directive.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            refreshStatus()
            onPackagesChanged()
        }
    }

    override fun start() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGES_SUSPENDED)
            addAction(Intent.ACTION_PACKAGES_UNSUSPENDED)
            addAction(Intent.ACTION_APPLICATION_RESTRICTIONS_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        registered = true
        refreshStatus()
    }

    override fun stop() {
        if (!registered) return
        runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }

    fun refreshStatus(enabled: Boolean = true) {
        _directive.value = ProviderDirective(
            providerId = id,
            providerName = displayName,
            active = enabled,
            statusConnected = enabled,
            statusDetail = if (enabled) detectSupervisionSummary() else "Bridge disabled",
        )
    }

    fun openSystemParentalControls(): Boolean {
        val candidates = listOf(
            Intent("android.settings.SUPERVISION_SETTINGS"),
            Intent("com.google.android.gms.kids.settings.PARENTAL_CONTROLS"),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(intent); true }.getOrDefault(false)) {
                return true
            }
        }
        return false
    }

    private fun detectSupervisionSummary(): String {
        val hasFamilyLinkHelper = runCatching {
            context.packageManager.getPackageInfo("com.google.android.apps.kids.familylinkhelper", 0)
            true
        }.getOrDefault(false)

        val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
        val hasOsRestrictions = userManager?.userRestrictions?.isEmpty == false

        return when {
            hasFamilyLinkHelper -> "Family Link detected • OS app pause sync active"
            hasOsRestrictions -> "Android OS supervision restrictions active"
            else -> "Ready • Monitoring OS package suspension"
        }
    }
}
