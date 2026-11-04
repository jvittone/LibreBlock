package com.libreblock

import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager

/**
 * Applis qu'on ne peut jamais bloquer : les écrans d'accueil (les bloquer ferait tourner l'écran de
 * blocage en boucle), l'appli Téléphone par défaut (appels d'urgence) et LibreBlock lui-même.
 */
fun essentialPackages(context: Context): Set<String> {
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    @Suppress("DEPRECATION")
    val launchers = context.packageManager.queryIntentActivities(home, 0).map { it.activityInfo.packageName }
    val dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    return (launchers + listOfNotNull(dialer, context.packageName)).toSet()
}
