package com.libreblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ClockTest {
    private val hour = 3_600_000L
    private val paris = "Europe/Paris"
    private val start = ClockState().observe(wall = 1_000 * hour, elapsed = 10 * hour, boot = 1)

    @Test
    fun clockChangeIsNeutralizedWhileLocked() {
        val locked = start.withArmed(true, paris)
        // Une minute plus tard, l'utilisateur avance l'horloge de 3 h.
        val wall = 1_000 * hour + 60_000 + 3 * hour
        val after = locked.observe(wall, elapsed = 10 * hour + 60_000, boot = 1)
        assertEquals(1_000 * hour + 60_000, after.trustedNow(wall))
    }

    @Test
    fun clockChangeIsAcceptedWhenNotLocked() {
        val wall = 1_000 * hour + 3 * hour
        val after = start.observe(wall, elapsed = 10 * hour, boot = 1)
        assertEquals(wall, after.trustedNow(wall))
    }

    @Test
    fun smallCorrectionsAreIgnored() {
        val locked = start.withArmed(true, paris)
        val wall = 1_000 * hour + 2 * hour + 5_000 // 5 s d'avance après 2 h : correction automatique
        val after = locked.observe(wall, elapsed = 12 * hour, boot = 1)
        assertEquals(0, after.offset)
    }

    @Test
    fun unlockingReturnsToPhoneTime() {
        val locked = start.withArmed(true, paris)
        val changed = locked.observe(1_000 * hour + 3 * hour, elapsed = 10 * hour, boot = 1)
        val unlocked = changed.withArmed(false, paris)
        assertEquals(0, unlocked.offset)
        assertFalse(unlocked.armed)
    }

    @Test
    fun offsetSurvivesReboot() {
        val locked = start.withArmed(true, paris)
        val wall = 1_000 * hour + 3 * hour
        val changed = locked.observe(wall, elapsed = 10 * hour, boot = 1)
        // Redémarrage : le temps écoulé repart de zéro, la correction est conservée.
        val rebooted = changed.observe(wall + 60_000, elapsed = 30_000, boot = 2)
        assertEquals(1_000 * hour + 60_000, rebooted.trustedNow(wall + 60_000))
    }

    @Test
    fun timeZoneIsFrozenWhileLocked() {
        val locked = start.withArmed(true, paris)
        assertEquals(paris, locked.zoneId("Asia/Tokyo"))
        assertEquals("Asia/Tokyo", locked.withArmed(false, "Asia/Tokyo").zoneId("Asia/Tokyo"))
    }

    @Test
    fun daylightSavingTimeIsNotAClockChange() {
        // Nuit du passage à l'heure d'hiver (25 octobre 2026) : l'heure locale recule d'une heure,
        // mais l'horloge système (UTC) avance normalement.
        val zone = ZoneId.of(paris)
        val before = LocalDateTime.of(2026, 10, 25, 2, 30).atZone(zone).withEarlierOffsetAtOverlap()
        val wall0 = before.toInstant().toEpochMilli()
        val locked = ClockState().observe(wall0, elapsed = 5 * hour, boot = 1).withArmed(true, paris)
        val wall1 = wall0 + hour // une heure réelle plus tard, il est de nouveau 2:30 en heure locale
        val after = locked.observe(wall1, elapsed = 6 * hour, boot = 1)
        assertEquals(0, after.offset)
        assertEquals(wall1, after.trustedNow(wall1))
        assertEquals(paris, after.zoneId(paris))
    }

    private fun nightSchedule() =
        Schedule(id = 1, days = 0b1111111, startMin = 23 * 60, endMin = 8 * 60, strict = true)

    @Test
    fun overnightScheduleEndsAt8OnSpringForwardNight() {
        // Nuit du 27 au 28 mars 2027 : à 2:00 il est 3:00, la nuit ne dure que 8 heures réelles.
        val zone = ZoneId.of(paris)
        val at = LocalDateTime.of(2027, 3, 28, 5, 0).atZone(zone).toInstant().toEpochMilli()
        val (start, end) = nightSchedule().window(at, zone)!!
        assertEquals(LocalDateTime.of(2027, 3, 27, 23, 0), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(start), zone))
        assertEquals(LocalDateTime.of(2027, 3, 28, 8, 0), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(end), zone))
        assertEquals(8 * hour, end - start)
    }

    @Test
    fun overnightScheduleEndsAt8OnFallBackNight() {
        // Nuit du 24 au 25 octobre 2026 : à 3:00 il est 2:00, la nuit dure 10 heures réelles.
        val zone = ZoneId.of(paris)
        val at = LocalDateTime.of(2026, 10, 25, 6, 0).atZone(zone).toInstant().toEpochMilli()
        val (start, end) = nightSchedule().window(at, zone)!!
        assertEquals(LocalDateTime.of(2026, 10, 25, 8, 0), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(end), zone))
        assertEquals(10 * hour, end - start)
    }
}
