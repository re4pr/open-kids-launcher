package io.github.re4pr.kidslauncher

import io.github.re4pr.kidslauncher.data.AppCatalogService
import io.github.re4pr.kidslauncher.model.AppCategory
import io.github.re4pr.kidslauncher.model.ContentPluginConfig
import io.github.re4pr.kidslauncher.model.LauncherMode
import io.github.re4pr.kidslauncher.model.LauncherTab
import io.github.re4pr.kidslauncher.model.LocalRestrictionConfig
import io.github.re4pr.kidslauncher.model.TimeWindowRule
import io.github.re4pr.kidslauncher.plugin.PluginEngine
import io.github.re4pr.kidslauncher.provider.HomeAssistantControlProvider
import io.github.re4pr.kidslauncher.provider.PolicyResolver
import io.github.re4pr.kidslauncher.provider.ProviderDirective
import io.github.re4pr.kidslauncher.provider.ScheduleEvaluator
import io.github.re4pr.kidslauncher.security.PinSecurity
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherCoreUnitTest {

    @Test
    fun pinSecurity_hashesAndVerifiesInConstantTime() {
        val salt1 = PinSecurity.generateSaltHex()
        val salt2 = PinSecurity.generateSaltHex()
        assertNotEquals(salt1, salt2)

        val hash = PinSecurity.hashPasscode("2580", salt1)
        assertTrue(PinSecurity.verifyPasscode("2580", hash, salt1))
        assertFalse(PinSecurity.verifyPasscode("9999", hash, salt1))
        assertFalse(PinSecurity.verifyPasscode("", hash, salt1))
    }

    @Test
    fun scheduleEvaluator_handlesOvernightBedtimeAndWeekdaySchool() {
        val bedtime = TimeWindowRule(enabled = true, startHour = 20, startMinute = 0, endHour = 7, endMinute = 0)
        // Wednesday 21:15 -> inside bedtime
        assertTrue(ScheduleEvaluator.isWithinWindow(bedtime, LocalDateTime.of(2026, 11, 25, 21, 15)))
        // Thursday 06:30 -> inside overnight bedtime
        assertTrue(ScheduleEvaluator.isWithinWindow(bedtime, LocalDateTime.of(2026, 11, 26, 6, 30)))
        // Thursday 12:00 -> outside bedtime
        assertFalse(ScheduleEvaluator.isWithinWindow(bedtime, LocalDateTime.of(2026, 11, 26, 12, 0)))

        val school = TimeWindowRule(
            enabled = true,
            startHour = 8,
            startMinute = 30,
            endHour = 15,
            endMinute = 0,
            daysOfWeek = setOf(1, 2, 3, 4, 5),
        )
        // Thursday 10:00 -> inside school
        assertTrue(ScheduleEvaluator.isWithinWindow(school, LocalDateTime.of(2026, 11, 26, 10, 0)))
        // Saturday 10:00 -> weekend, outside school
        assertFalse(ScheduleEvaluator.isWithinWindow(school, LocalDateTime.of(2026, 11, 28, 10, 0)))
    }

    @Test
    fun scheduleEvaluator_enforcesTidyUpAndScreenTimeBudget() {
        val cfg = LocalRestrictionConfig(
            dailyScreenTimeBudgetMinutes = 60,
            bedtimeSchedule = TimeWindowRule(enabled = false),
            schoolSchedule = TimeWindowRule(enabled = false),
            tidyUpEndsAtEpochMs = 2_000_000L,
        )
        assertEquals(
            LauncherMode.TIDY_UP,
            ScheduleEvaluator.evaluateLocalMode(cfg, LocalDateTime.of(2026, 11, 26, 14, 0), 1_500_000L, 10, 60),
        )
        assertEquals(
            LauncherMode.LOCKED,
            ScheduleEvaluator.evaluateLocalMode(cfg, LocalDateTime.of(2026, 11, 26, 14, 0), 2_500_000L, 60, 60),
        )
        assertEquals(
            LauncherMode.FREE_PLAY,
            ScheduleEvaluator.evaluateLocalMode(cfg, LocalDateTime.of(2026, 11, 26, 14, 0), 2_500_000L, 35, 60),
        )
    }

    @Test
    fun policyResolver_prioritizesHomeAssistantAndFiltersAllowedTabs() {
        val local = ProviderDirective("local_schedule", "Parent Schedule", enforcedMode = LauncherMode.FREE_PLAY)
        val ha = ProviderDirective("home_assistant", "Home Assistant", enforcedMode = LauncherMode.TIDY_UP)
        val (mode, source) = PolicyResolver.resolveModePriority(listOf(local, ha))
        assertEquals(LauncherMode.TIDY_UP, mode)
        assertEquals("Home Assistant", source)

        val bedtimeAudioTabs = PolicyResolver.allowedTabsForMode(LauncherMode.BEDTIME, allowBedtimeAudio = true)
        assertTrue(LauncherTab.AUDIO in bedtimeAudioTabs)
        assertFalse(LauncherTab.GAMES in bedtimeAudioTabs)
        assertFalse(LauncherTab.VIDEOS in bedtimeAudioTabs)
    }

    @Test
    fun homeAssistantUrlAndModeParsing_worksForHttpAndHttps() {
        assertEquals(
            "ws://homeassistant.local:8123/api/websocket",
            HomeAssistantControlProvider.buildWebSocketUrl("http://homeassistant.local:8123/"),
        )
        assertEquals(
            "wss://ha.example.org/api/websocket",
            HomeAssistantControlProvider.buildWebSocketUrl("https://ha.example.org"),
        )
        assertEquals(LauncherMode.SCHOOL_TIME, HomeAssistantControlProvider.parseModeString("School Time"))
        assertEquals(LauncherMode.TIDY_UP, HomeAssistantControlProvider.parseModeString("tidy-up"))
        assertEquals(LauncherMode.BEDTIME, HomeAssistantControlProvider.parseModeString("sleep"))
    }

    @Test
    fun pluginEngine_parsesGenericContinueWatchingFeed() {
        val engine = PluginEngine()
        val cfg = ContentPluginConfig(
            id = "cw_test",
            name = "Continue Watching",
            targetCategory = AppCategory.VIDEOS,
            fallbackPackageName = "com.example.mediaplayer",
        )
        val payload = """
            {
              "items": [
                {
                  "id": "ep-101",
                  "title": "Ocean Explorers",
                  "subtitle": "S1 • E4",
                  "imageUrl": "https://cdn.example.org/poster.jpg",
                  "progress": 0.65,
                  "launchUri": "intent://detail/101#Intent;scheme=mediaplayer;end"
                }
              ]
            }
        """.trimIndent()
        val parsed = engine.parseItemsFromJson(payload, cfg)
        assertEquals(1, parsed.size)
        assertEquals("Ocean Explorers", parsed[0].title)
        assertEquals(0.65f, parsed[0].progressFraction, 0.001f)
        assertEquals("com.example.mediaplayer", parsed[0].packageName)
    }

    @Test
    fun appCatalog_autoShowsKidAppsAndHidesUtilities() {
        assertTrue(
            AppCatalogService.isDefaultKidVisibleCandidate(
                packageName = "com.tocaboca.tocahairsalon4",
                label = "Hair Salon 4",
                appInfo = null,
                category = AppCategory.GAMES,
                homeLauncherPackages = setOf("com.sec.android.app.launcher"),
                vpnPackages = setOf("com.example.vpn"),
            )
        )
        assertTrue(
            AppCatalogService.isDefaultKidVisibleCandidate(
                packageName = "com.netflix.mediaclient",
                label = "Netflix",
                appInfo = null,
                category = AppCategory.VIDEOS,
                homeLauncherPackages = emptySet(),
                vpnPackages = emptySet(),
            )
        )
        assertFalse(
            AppCatalogService.isDefaultKidVisibleCandidate(
                packageName = "com.example.vpn",
                label = "Safe VPN",
                appInfo = null,
                category = AppCategory.GAMES,
                homeLauncherPackages = emptySet(),
                vpnPackages = setOf("com.example.vpn"),
            )
        )
        assertFalse(
            AppCatalogService.isDefaultKidVisibleCandidate(
                packageName = "com.android.chrome",
                label = "Chrome",
                appInfo = null,
                category = AppCategory.GAMES,
                homeLauncherPackages = emptySet(),
                vpnPackages = emptySet(),
            )
        )
    }
}
