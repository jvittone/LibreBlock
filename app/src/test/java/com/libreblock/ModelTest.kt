package com.libreblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ModelTest {
    private val yt = "com.google.android.youtube"
    private val maps = "com.google.android.apps.maps"

    @Test
    fun blockListBlocksOnlyCheckedApps() {
        val list = AppList(setOf(yt))
        assertTrue(list.blocks(yt))
        assertFalse(list.blocks(maps))
    }

    @Test
    fun allowListBlocksEverythingElse() {
        val list = AppList(setOf(maps), allowOnly = true)
        assertTrue(list.blocks(yt))
        assertFalse(list.blocks(maps))
        assertTrue(AppList(allowOnly = true).blocks(maps)) // liste vide : tout est bloqué
    }

    @Test
    fun strictModeOnlyAllowsStricterLists() {
        val block = AppList(setOf(yt))
        assertFalse(block.isLoosenedBy(block.toggle(maps))) // bloquer une appli de plus
        assertTrue(block.isLoosenedBy(block.toggle(yt))) // débloquer YouTube
        assertTrue(block.isLoosenedBy(block.copy(allowOnly = true))) // changer de mode

        val allow = AppList(setOf(maps), allowOnly = true)
        assertFalse(allow.isLoosenedBy(allow.toggle(maps))) // retirer une appli autorisée
        assertTrue(allow.isLoosenedBy(allow.toggle(yt))) // autoriser YouTube

        assertTrue(block.canToggleWhenLocked(maps))
        assertFalse(block.canToggleWhenLocked(yt))
        assertTrue(allow.canToggleWhenLocked(maps))
        assertFalse(allow.canToggleWhenLocked(yt))
    }

    @Test
    fun sessionUsesMainList() {
        val settings = Settings(mainList = AppList(setOf(maps), allowOnly = true), session = Session(0, 60_000, false, 0, 5))
        val actives = settings.active(30_000, ZoneId.of("Europe/Paris"))
        assertTrue(actives.blocks(yt))
        assertFalse(actives.blocks(maps))
        assertTrue(settings.active(60_000, ZoneId.of("Europe/Paris")).isEmpty())
    }

    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun statsCountBlocksAndFocus() {
        val s = Settings()
            .recordBlock(yt, today).recordBlock(yt, today).recordBlock(maps, today)
            .recordFocus(90, today).recordFocus(30, today)
        val day = s.stats.day(today)
        assertEquals(3, day.blocked)
        assertEquals(120, day.focusSeconds)
        assertEquals(listOf(yt to 2, maps to 1), s.stats.topBlocked(today, 7, 5))
    }

    @Test
    fun oldStatsAreDropped() {
        val s = Settings().recordBlock(yt, today.minusDays(90)).recordBlock(yt, today)
        assertEquals(setOf(today.toString()), s.stats.keys)
    }

    @Test
    fun streakCountsConsecutiveFocusDays() {
        var s = Settings()
        for (i in 1..3) s = s.recordFocus(600, today.minusDays(i.toLong()))
        // Pas encore de concentration aujourd'hui : la série d'hier compte toujours.
        assertEquals(3, s.stats.streak(today))
        assertEquals(4, s.recordFocus(600, today).stats.streak(today))
        // Un jour sans concentration casse la série.
        assertEquals(0, s.stats.streak(today.plusDays(2)))
    }

    @Test
    fun settingsSurviveJsonRoundTrip() {
        val settings = Settings(
            mainList = AppList(setOf(yt, maps), allowOnly = true),
            schedules = listOf(
                Schedule(7, 0b0011111, 9 * 60, 12 * 60, workMin = 25, strict = true, list = AppList(setOf(yt)), useMainList = false),
            ),
            session = Session(1_000, 2_000, true, 25, 5),
            lastSession = SessionConfig(50, true, 25, 5),
            onboardingDone = true,
        ).recordBlock(yt, today).recordFocus(300, today)
        assertEquals(settings, settingsFromJson(settings.toJson()))
    }

    @Test
    fun legacyPreferencesAreMigrated() {
        val prefs = mapOf(
            "blocked_apps" to setOf(yt),
            "schedules_v2" to true,
            "schedules" to """[{"id":1,"days":127,"start":1380,"end":480,"enabled":true,"work":0,"break":5,
                "strict":true,"apps":["$maps"],"main":false}]""",
            "session_end" to 0L,
            "last_minutes" to 40,
        )
        val s = legacySettings(prefs)
        assertEquals(AppList(setOf(yt)), s.mainList)
        assertEquals(AppList(setOf(maps)), s.schedules.single().list)
        assertTrue(s.schedules.single().strict)
        assertNull(s.session)
        assertEquals(40, s.lastSession.minutes)
        assertTrue(s.onboardingDone) // il avait déjà des applis : pas d'écran de premier lancement
    }

    @Test
    fun veryOldPreferencesUseSharedStrictSetting() {
        val prefs = mapOf(
            "blocked_apps" to setOf(yt),
            "strict_schedules" to true,
            "schedules" to """[{"id":1,"days":31,"start":540,"end":720,"enabled":true}]""",
        )
        val schedule = legacySettings(prefs).schedules.single()
        assertTrue(schedule.strict)
        assertEquals(AppList(setOf(yt)), schedule.list)
    }
}
