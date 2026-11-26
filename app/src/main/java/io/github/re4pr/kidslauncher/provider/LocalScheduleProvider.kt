package io.github.re4pr.kidslauncher.provider

import io.github.re4pr.kidslauncher.model.LauncherMode
import io.github.re4pr.kidslauncher.model.LocalRestrictionConfig
import io.github.re4pr.kidslauncher.model.TimeWindowRule
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ScheduleEvaluator {
    fun isWithinWindow(rule: TimeWindowRule, now: LocalDateTime): Boolean {
        if (!rule.enabled) return false
        val start = LocalTime.of(rule.startHour.coerceIn(0, 23), rule.startMinute.coerceIn(0, 59))
        val end = LocalTime.of(rule.endHour.coerceIn(0, 23), rule.endMinute.coerceIn(0, 59))
        val time = now.toLocalTime()

        return if (start <= end) {
            // Same-day window (e.g. School Time 08:30 - 15:00)
            now.dayOfWeek.value in rule.daysOfWeek && !time.isBefore(start) && time.isBefore(end)
        } else {
            // Overnight window (e.g. Bedtime 20:00 - 07:00)
            val inEveningPart = now.dayOfWeek.value in rule.daysOfWeek && !time.isBefore(start)
            val prevDay = now.minusDays(1).dayOfWeek.value
            val inMorningPart = prevDay in rule.daysOfWeek && time.isBefore(end)
            inEveningPart || inMorningPart
        }
    }

    fun evaluateLocalMode(
        config: LocalRestrictionConfig,
        now: LocalDateTime,
        nowEpochMs: Long,
        usedMinutesToday: Int,
        effectiveDailyBudgetMinutes: Int,
    ): LauncherMode {
        config.manualModeOverride?.let { return it }

        if (config.tidyUpEndsAtEpochMs > nowEpochMs) {
            return LauncherMode.TIDY_UP
        }

        if (isWithinWindow(config.bedtimeSchedule, now)) {
            return LauncherMode.BEDTIME
        }

        if (effectiveDailyBudgetMinutes > 0 && usedMinutesToday >= effectiveDailyBudgetMinutes) {
            return LauncherMode.LOCKED
        }

        if (isWithinWindow(config.schoolSchedule, now)) {
            return LauncherMode.SCHOOL_TIME
        }

        return LauncherMode.FREE_PLAY
    }
}

class LocalScheduleProvider : ParentalControlProvider {
    override val id: String = "local_schedule"
    override val displayName: String = "Parent Schedule"

    private val _directive = MutableStateFlow(
        ProviderDirective(
            providerId = id,
            providerName = displayName,
            active = true,
            enforcedMode = LauncherMode.FREE_PLAY,
            statusConnected = true,
            statusDetail = "On-device schedule active",
        )
    )
    override val directive: StateFlow<ProviderDirective> = _directive.asStateFlow()

    fun evaluate(
        config: LocalRestrictionConfig,
        usedMinutesToday: Int,
        budgetMinutes: Int,
        now: LocalDateTime = LocalDateTime.now(),
        nowEpochMs: Long = System.currentTimeMillis(),
    ) {
        val mode = ScheduleEvaluator.evaluateLocalMode(
            config = config,
            now = now,
            nowEpochMs = nowEpochMs,
            usedMinutesToday = usedMinutesToday,
            effectiveDailyBudgetMinutes = budgetMinutes,
        )
        _directive.value = ProviderDirective(
            providerId = id,
            providerName = if (config.manualModeOverride != null) "Parent Override" else displayName,
            active = true,
            enforcedMode = mode,
            statusConnected = true,
            statusDetail = "Mode: ${mode.title}",
        )
    }
}
