package com.libreblock

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Surveille l'appli au premier plan et affiche BlockActivity quand elle est bloquée.
 * En mode strict, empêche aussi d'ouvrir les pages des Paramètres qui permettraient
 * de désactiver ce service ou de désinstaller LibreBlock.
 */
class BlockerService : AccessibilityService() {

    private lateinit var store: Store
    private val handler = Handler(Looper.getMainLooper())
    private var foreground: String? = null
    private var lastBlock = 0L
    private var lastGuard = 0L
    private var lastWorking: Boolean? = null

    // Un horaire ou une phase de travail peut commencer pendant qu'on est déjà dans une
    // appli bloquée : aucun événement n'arrive alors, d'où cette vérification périodique.
    private val tick = object : Runnable {
        override fun run() {
            notifyPhaseChange()
            foreground?.let { check(it) }
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        store = Store(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_PHASES, "Pauses des sessions de travail", NotificationManager.IMPORTANCE_HIGH)
        )
        handler.post(tick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::store.isInitialized) return
        val pkg = event.packageName?.toString() ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (pkg != SYSTEM_UI && pkg != currentKeyboard()) foreground = pkg
                check(pkg)
                guard(pkg)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> guard(pkg)
        }
    }

    private fun check(pkg: String) {
        if (pkg == packageName) return
        val now = System.currentTimeMillis()
        if (!store.shouldBlock(pkg, now) || now - lastBlock < 1000) return
        lastBlock = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockActivity::class.java)
                .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    /** Mode strict : quitte les écrans Paramètres / désinstallation qui mentionnent LibreBlock. */
    private fun guard(pkg: String) {
        if (pkg !in PROTECTED_PACKAGES) return
        val now = System.currentTimeMillis()
        if (now - lastGuard < 500 || !store.isLocked(now)) return
        val root = rootInActiveWindow ?: return
        if (root.findAccessibilityNodeInfosByText(getString(R.string.app_name)).isNullOrEmpty()) return
        lastGuard = now
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(this, "Mode strict actif", Toast.LENGTH_SHORT).show()
    }

    /** Prévient au début de chaque pause et à la reprise du travail. */
    private fun notifyPhaseChange() {
        val now = System.currentTimeMillis()
        val cycle = store.active(now).filter { it.cycles }.maxByOrNull { it.end }
        val working = cycle?.blocking
        if (cycle != null && lastWorking != null && working != lastWorking) {
            val text = when {
                !cycle.blocking -> "Pause ! Reprise à ${formatClock(cycle.phaseEnd)}"
                cycle.phaseEnd < cycle.end -> "Au travail ! Prochaine pause à ${formatClock(cycle.phaseEnd)}"
                else -> "Au travail ! Fin de la session à ${formatClock(cycle.end)}"
            }
            postNotification(text)
        }
        lastWorking = working
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_PHASES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(NOTIFICATION_PHASE, notification)
    }

    private fun currentKeyboard(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')

    companion object {
        private const val CHECK_INTERVAL_MS = 10_000L
        private const val CHANNEL_PHASES = "phases"
        private const val NOTIFICATION_PHASE = 1
        private const val SYSTEM_UI = "com.android.systemui"
        private val PROTECTED_PACKAGES = setOf(
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.miui.securitycenter",
        )

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val me = ComponentName(context, BlockerService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}
