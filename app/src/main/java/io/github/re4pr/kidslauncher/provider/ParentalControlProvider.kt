package io.github.re4pr.kidslauncher.provider

import io.github.re4pr.kidslauncher.model.LauncherMode
import io.github.re4pr.kidslauncher.model.LauncherTab
import kotlinx.coroutines.flow.StateFlow

/**
 * Snapshot emitted by any [ParentalControlProvider] implementation.
 *
 * Contributors can add new family control integrations (e.g., NextDNS, MQTT, Bark,
 * custom REST MDM) by implementing [ParentalControlProvider] and registering it with
 * [PolicyCoordinator].
 */
data class ProviderDirective(
    val providerId: String,
    val providerName: String,
    val active: Boolean = true,
    val enforcedMode: LauncherMode? = null,
    val dailyBudgetOverrideMinutes: Int? = null,
    val usedMinutesOverride: Int? = null,
    val remoteTidyUpEndsAtEpochMs: Long? = null,
    val suspendedPackages: Set<String> = emptySet(),
    val statusConnected: Boolean = false,
    val statusDetail: String = "",
)

interface ParentalControlProvider {
    val id: String
    val displayName: String
    val directive: StateFlow<ProviderDirective>

    fun start() {}
    fun stop() {}
    fun notifyActivity(eventType: String, payload: Map<String, String> = emptyMap()) {}
}

/**
 * Pure policy resolution logic combining local schedule directives, remote providers
 * (such as Home Assistant), and OS-level bridges (such as Google Family Link).
 */
object PolicyResolver {
    fun resolveModePriority(directives: List<ProviderDirective>): Pair<LauncherMode, String> {
        val active = directives.filter { it.active && it.enforcedMode != null }
        if (active.isEmpty()) return LauncherMode.FREE_PLAY to "Free Play"

        // Remote Home Assistant explicit modes take priority when active, otherwise
        // select the most restrictive mode across active providers.
        val haDirective = active.firstOrNull { it.providerId == "home_assistant" && it.enforcedMode != null }
        if (haDirective?.enforcedMode != null) {
            return haDirective.enforcedMode to haDirective.providerName
        }

        val winner = active.maxBy { severity(it.enforcedMode ?: LauncherMode.FREE_PLAY) }
        return (winner.enforcedMode ?: LauncherMode.FREE_PLAY) to winner.providerName
    }

    fun allowedTabsForMode(mode: LauncherMode, allowBedtimeAudio: Boolean): Set<LauncherTab> =
        when (mode) {
            LauncherMode.FREE_PLAY -> setOf(
                LauncherTab.HOME,
                LauncherTab.GAMES,
                LauncherTab.VIDEOS,
                LauncherTab.BOOKS,
                LauncherTab.AUDIO,
            )
            LauncherMode.SCHOOL_TIME -> setOf(
                LauncherTab.HOME,
                LauncherTab.BOOKS,
                LauncherTab.AUDIO,
            )
            LauncherMode.TIDY_UP -> setOf(LauncherTab.HOME)
            LauncherMode.BEDTIME -> if (allowBedtimeAudio) {
                setOf(LauncherTab.HOME, LauncherTab.AUDIO, LauncherTab.BOOKS)
            } else {
                setOf(LauncherTab.HOME)
            }
            LauncherMode.LOCKED -> setOf(LauncherTab.HOME)
        }

    private fun severity(mode: LauncherMode): Int = when (mode) {
        LauncherMode.FREE_PLAY -> 0
        LauncherMode.SCHOOL_TIME -> 1
        LauncherMode.TIDY_UP -> 2
        LauncherMode.BEDTIME -> 3
        LauncherMode.LOCKED -> 4
    }
}
