package com.libreblock

import android.media.AudioManager

/**
 * Vrai si le mode audio d'Android indique une sonnerie ou un appel en cours : appel téléphonique, ou appel
 * passé par une appli (WhatsApp, Messenger, Teams…). Lire ce mode ne demande aucune autorisation.
 */
fun isCallAudioMode(mode: Int) = when (mode) {
    AudioManager.MODE_RINGTONE,
    AudioManager.MODE_IN_CALL,
    AudioManager.MODE_IN_COMMUNICATION,
    AudioManager.MODE_CALL_SCREENING,
    AudioManager.MODE_CALL_REDIRECT,
    AudioManager.MODE_COMMUNICATION_REDIRECT -> true
    else -> false
}
