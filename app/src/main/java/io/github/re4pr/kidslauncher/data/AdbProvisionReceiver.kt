package io.github.re4pr.kidslauncher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Shell/MDM-only provisioning receiver protected by `android.permission.WRITE_SECURE_SETTINGS`
 * (signature|privileged|development — cannot be invoked by normal third-party apps).
 *
 * Allows homelab parents to provision Home Assistant connection settings via ADB without
 * typing long access tokens on a tablet keyboard.
 */
class AdbProvisionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val repo = SettingsRepository(context.applicationContext)
        repo.updateSettings { current ->
            val ha = current.homeAssistant
            val updatedHa = ha.copy(
                enabled = if (intent.hasExtra("ha_enabled")) intent.getBooleanExtra("ha_enabled", ha.enabled) else ha.enabled,
                serverUrl = intent.getStringExtra("ha_url") ?: ha.serverUrl,
                accessToken = intent.getStringExtra("ha_token") ?: ha.accessToken,
                modeEntityId = intent.getStringExtra("mode_entity") ?: ha.modeEntityId,
                lockSwitchEntityId = intent.getStringExtra("lock_entity") ?: ha.lockSwitchEntityId,
                tidyUpSwitchEntityId = intent.getStringExtra("tidy_switch_entity") ?: ha.tidyUpSwitchEntityId,
                tidyUpTimerEntityId = intent.getStringExtra("tidy_timer_entity") ?: ha.tidyUpTimerEntityId,
                dailyBudgetEntityId = intent.getStringExtra("daily_budget_entity") ?: ha.dailyBudgetEntityId,
                usedMinutesEntityId = intent.getStringExtra("used_minutes_entity") ?: ha.usedMinutesEntityId,
                deviceIdentifier = intent.getStringExtra("device_id") ?: ha.deviceIdentifier,
            )
            current.copy(homeAssistant = updatedHa)
        }
    }
}
