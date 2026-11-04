package com.libreblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class VacationTest {
    private val zone = ZoneId.of("Europe/Paris")
    private val everyDay = 0b1111111

    /** 4 novembre 2026, un mercredi. */
    private fun at(day: Int, h: Int) = LocalDateTime.of(2026, 11, day, h, 0).atZone(zone).toInstant().toEpochMilli()

    private val work = Schedule(1, everyDay, 9 * 60, 12 * 60, strict = true, pausedOnVacation = true)
    private val night = Schedule(2, everyDay, 23 * 60, 8 * 60, strict = true)
    private val holidays = Vacation(10, LocalDate.of(2026, 11, 5), LocalDate.of(2026, 11, 8))

    private fun Settings.runningIds(now: Long) = active(now, zone).mapNotNull { it.scheduleId }.toSet()

    @Test
    fun workSchedulesStopDuringVacationsOthersKeepRunning() {
        val s = Settings(schedules = listOf(work, night), vacations = listOf(holidays))
        assertEquals(setOf(1L), s.runningIds(at(4, 10))) // la veille : on travaille
        assertEquals(emptySet<Long>(), s.runningIds(at(6, 10))) // en vacances : plus de travail
        assertEquals(setOf(2L), s.runningIds(at(6, 23))) // mais la nuit continue
        assertEquals(setOf(1L), s.runningIds(at(9, 10))) // retour au travail
    }

    @Test
    fun overnightWorkStartedBeforeVacationsGoesToItsEnd() {
        val lateShift = Schedule(3, everyDay, 22 * 60, 6 * 60, pausedOnVacation = true)
        val s = Settings(schedules = listOf(lateShift), vacations = listOf(holidays))
        assertEquals(setOf(3L), s.runningIds(at(5, 2))) // commencé le 4 au soir, avant les vacances
        assertEquals(emptySet<Long>(), s.runningIds(at(5, 23))) // celui du 5 au soir n'a pas lieu
    }

    @Test
    fun vacationsCannotStopARunningStrictSchedule() {
        val before = Settings(schedules = listOf(work))
        val startingToday = before.copy(vacations = listOf(Vacation(11, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 8))))
        assertFalse(vacationChangeAllowed(before, startingToday, at(4, 10), zone)) // pendant le blocage strict
        assertTrue(vacationChangeAllowed(before, startingToday, at(4, 14), zone)) // une fois le blocage fini
        assertTrue(vacationChangeAllowed(before, before.copy(vacations = listOf(holidays)), at(4, 10), zone)) // à partir de demain
    }

    @Test
    fun vacationsCanStopAFlexibleSchedule() {
        val before = Settings(schedules = listOf(work.copy(strict = false)))
        val today = before.copy(vacations = listOf(Vacation(11, LocalDate.of(2026, 11, 4), LocalDate.of(2026, 11, 4))))
        assertTrue(vacationChangeAllowed(before, today, at(4, 10), zone))
    }

    @Test
    fun pastVacationsAreHidden() {
        val past = Vacation(1, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3))
        val later = Vacation(2, LocalDate.of(2026, 12, 20), LocalDate.of(2027, 1, 3))
        assertEquals(listOf(holidays, later), listOf(later, past, holidays).upcoming(LocalDate.of(2026, 11, 4)))
    }

    @Test
    fun workSessionsCountAsWorkInOlderSettings() {
        val json = """{"schedules":[
            {"id":1,"days":31,"start":540,"end":720,"work":25},
            {"id":2,"days":127,"start":1380,"end":480,"work":0}]}"""
        val (pomodoro, sleep) = settingsFromJson(json).schedules
        assertTrue(pomodoro.pausedOnVacation)
        assertFalse(sleep.pausedOnVacation)
    }
}
