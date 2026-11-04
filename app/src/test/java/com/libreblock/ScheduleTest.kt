package com.libreblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class ScheduleTest {
    private val monday = 0b0000001
    private val weekdays = 0b0011111

    /** 2 novembre 2026 = un lundi. */
    private fun mon(h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 2, h, m)
    private fun tue(h: Int, m: Int = 0) = mon(h, m).plusDays(1)
    private fun sun(h: Int, m: Int = 0) = mon(h, m).minusDays(1)

    private fun schedule(days: Int, start: Int, end: Int, enabled: Boolean = true) =
        Schedule(id = 1, days = days, startMin = start * 60, endMin = end * 60, enabled = enabled)

    @Test
    fun referenceDateIsMonday() {
        assertEquals(DayOfWeek.MONDAY, mon(0).dayOfWeek)
    }

    @Test
    fun daytimeSchedule() {
        val s = schedule(weekdays, 9, 17)
        assertNull(s.activeSince(mon(8, 59)))
        assertEquals(mon(9), s.activeSince(mon(9)))
        assertEquals(mon(9), s.activeSince(mon(16, 59)))
        assertNull(s.activeSince(mon(17)))
        assertNull(s.activeSince(sun(10)))
    }

    @Test
    fun overnightScheduleBelongsToItsStartDay() {
        val s = schedule(monday, 23, 8)
        assertNull(s.activeSince(mon(7)))          // nuit de dimanche à lundi : dimanche non coché
        assertEquals(mon(23), s.activeSince(mon(23, 30)))
        assertEquals(mon(23), s.activeSince(tue(7, 59)))
        assertNull(s.activeSince(tue(8)))
        assertNull(s.activeSince(tue(23, 30)))     // mardi non coché
        assertEquals(9 * 60, s.durationMin)
    }

    @Test
    fun sameStartAndEndMeansWholeDay() {
        val s = schedule(monday, 6, 6)
        assertEquals(24 * 60, s.durationMin)
        assertEquals(mon(6), s.activeSince(mon(6)))
        assertEquals(mon(6), s.activeSince(tue(5, 59)))
        assertNull(s.activeSince(tue(6)))
    }

    @Test
    fun disabledScheduleNeverActive() {
        assertNull(schedule(weekdays, 9, 17, enabled = false).activeSince(mon(10)))
    }

    private val min = 60_000L

    private fun state(now: Long, end: Long = 120 * min, work: Int = 30, pause: Int = 5) =
        activeState(0L, end, work, pause, now, strict = false, apps = emptySet(), scheduleId = 1L, usesMainList = false)

    @Test
    fun continuousBlockingHasNoPhases() {
        val a = state(10 * min, work = 0)
        assertTrue(a.blocking)
        assertFalse(a.cycles)
        assertEquals(120 * min, a.phaseEnd)
    }

    @Test
    fun workAndBreakAlternate() {
        state(10 * min).let { assertTrue(it.blocking); assertEquals(30 * min, it.phaseEnd) }
        state(30 * min).let { assertFalse(it.blocking); assertEquals(35 * min, it.phaseEnd) }
        state(35 * min).let { assertTrue(it.blocking); assertEquals(65 * min, it.phaseEnd) }
        state(66 * min).let { assertFalse(it.blocking); assertEquals(70 * min, it.phaseEnd) }
    }

    @Test
    fun lastPhaseStopsAtSessionEnd() {
        // Cycles de 35 min : le 4e travail commence à 105 min et la session finit à 120.
        val a = state(110 * min)
        assertTrue(a.blocking)
        assertEquals(120 * min, a.phaseEnd)
    }
}
