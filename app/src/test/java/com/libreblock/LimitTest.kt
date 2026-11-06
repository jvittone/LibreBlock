package com.libreblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LimitTest {
    private val today = LocalDate.of(2026, 11, 6)
    private val tomorrow = today.plusDays(1)
    private val youtube = "com.google.android.youtube"

    private val thirty = Settings().withLimit(youtube, 30, today)

    @Test
    fun newLimitAppliesRightAway() {
        assertEquals(30, thirty.limitOn(youtube, today))
    }

    @Test
    fun lowerLimitAppliesRightAway() {
        assertEquals(AppLimit(15), thirty.withLimit(youtube, 15, today).limits[youtube])
    }

    @Test
    fun higherLimitWaitsUntilTomorrow() {
        val s = thirty.withLimit(youtube, 60, today)
        assertEquals(30, s.limitOn(youtube, today))
        assertEquals(60, s.limitOn(youtube, tomorrow))
        assertTrue(s.limits.getValue(youtube).pendingOn(today))
    }

    @Test
    fun removedLimitStaysUntilTomorrow() {
        val s = thirty.withLimit(youtube, null, today)
        assertEquals(30, s.limitOn(youtube, today))
        assertNull(s.limitOn(youtube, tomorrow))
        // Le lendemain, toute modification fait le ménage.
        assertFalse(youtube in s.withLimit("other", 10, tomorrow).limits)
    }

    @Test
    fun loweringCancelsAPendingRaise() {
        val s = thirty.withLimit(youtube, 60, today).withLimit(youtube, 20, today)
        assertEquals(AppLimit(20), s.limits[youtube])
    }

    @Test
    fun limitIsReachedOnceTheTimeIsUsed() {
        var s = thirty.recordUsage(mapOf(youtube to 29 * 60L), today)
        assertEquals(60L, s.remainingSeconds(youtube, today))
        assertFalse(s.limitReached(youtube, today))
        assertFalse(s.limitsArmed(today))

        s = s.recordUsage(mapOf(youtube to 90L), today)
        assertTrue(s.limitReached(youtube, today))
        assertTrue(s.limitsArmed(today)) // changer l'heure ne doit pas débloquer
        assertFalse(s.limitReached(youtube, tomorrow)) // nouveau jour, compteur à zéro
        assertNull(s.remainingSeconds("other", today))
    }

    @Test
    fun pendingRaiseArmsTheClock() {
        assertTrue(thirty.withLimit(youtube, 60, today).limitsArmed(today))
    }

    @Test
    fun limitsAndUsageSurviveJsonRoundTrip() {
        val s = thirty.withLimit(youtube, 60, today).withLimit("other", 10, today)
            .recordUsage(mapOf(youtube to 125L), today)
        assertEquals(s, settingsFromJson(s.toJson()))
    }
}
