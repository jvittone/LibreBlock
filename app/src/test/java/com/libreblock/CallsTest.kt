package com.libreblock

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallsTest {
    @Test
    fun ringingAndCallsAreDetected() {
        assertTrue(isCallAudioMode(AudioManager.MODE_RINGTONE))
        assertTrue(isCallAudioMode(AudioManager.MODE_IN_CALL)) // appel téléphonique
        assertTrue(isCallAudioMode(AudioManager.MODE_IN_COMMUNICATION)) // appel WhatsApp, Teams…
    }

    @Test
    fun musicAndVideosAreNotCalls() {
        assertFalse(isCallAudioMode(AudioManager.MODE_NORMAL))
    }
}
