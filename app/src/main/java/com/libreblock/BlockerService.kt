package com.libreblock

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Surveille l'appli au premier plan et affiche BlockActivity quand elle est bloquée.
 * En mode strict, empêche aussi d'ouvrir les pages des Paramètres qui permettraient
 * de désactiver ce service ou de désinstaller LibreBlock.
 * Affiche une notification permanente pendant les blocages et prévient des pauses.
 */
class BlockerService : AccessibilityService() {

    private lateinit var repo: SettingsRepository
    private val audio by lazy { getSystemService(AudioManager::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private var foreground: String? = null
    private var lastBlock = 0L
    private var lastGuard = 0L
    private var lastTick = 0L
    private var lastWorking: Boolean? = null
    private var lastStatus: String? = null
    private var essentials = emptySet<String>()
    private var launchable = emptySet<String>()

    // Un horaire ou une phase de travail peut commencer pendant qu'on est déjà dans une
    // appli bloquée : aucun événement n'arrive alors, d'où cette vérification périodique.
    private val tick = object : Runnable {
        override fun run() {
            essentials = essentialPackages(this@BlockerService)
            launchable = launchableApps(this@BlockerService).map { it.packageName }.toSet()
            val now = repo.now()
            val actives = repo.evaluate(now)
            recordFocus(actives, now)
            notifyPhaseChange(actives)
            updateStatus(actives, now)
            foreground?.let { check(it) }
            handler.postDelayed(this, CHECK_INTERVAL_MS)
        }
    }

    // Réagit tout de suite à un changement d'heure ou de fuseau, sans attendre la prochaine vérification.
    private val timeChanged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(tick)
            handler.post(tick)
        }
    }

    override fun onServiceConnected() {
        repo = SettingsRepository.get(this)
        getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_PHASES, getString(R.string.channel_breaks), NotificationManager.IMPORTANCE_HIGH),
                NotificationChannel(CHANNEL_STATUS, getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW),
            )
        )
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, timeChanged, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        handler.post(tick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        unregisterReceiver(timeChanged)
        NotificationManagerCompat.from(this).cancel(NOTIFICATION_STATUS)
        super.onDestroy()
    }

    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::repo.isInitialized) return
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
        // Seules les applis du lanceur sont bloquables : jamais le clavier, l'interface système, les appels…
        if (pkg == packageName || pkg in essentials || pkg !in launchable) return
        val t = SystemClock.elapsedRealtime()
        // Pendant une sonnerie ou un appel, même dans une appli bloquée (WhatsApp…), on laisse décrocher et parler.
        // La vérification périodique bloquera l'appli une fois l'appel terminé.
        if (t - lastBlock < 1000 || isCallAudioMode(audio.mode)) return
        val now = repo.now()
        if (!repo.evaluate(now).blocks(pkg)) return
        lastBlock = t
        repo.update { it.recordBlock(pkg, repo.today(now)) }
        pauseMedia()
        performGlobalAction(GLOBAL_ACTION_HOME)
        startActivity(
            Intent(this, BlockActivity::class.java)
                .putExtra(BlockActivity.EXTRA_PACKAGE, pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    /**
     * Revenir à l'accueil ne coupe pas le son : YouTube, Spotify… continuent en arrière-plan.
     * Prendre le focus audio les met en pause, comme lors d'un appel ; on le rend une seconde plus tard,
     * et une perte définitive du focus ne les fait pas reprendre.
     */
    private fun pauseMedia() {
        if (isCallAudioMode(audio.mode)) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .build()
        if (audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            handler.postDelayed({ audio.abandonAudioFocusRequest(request) }, 1000)
        }
    }

    /** Mode strict : quitte les écrans Paramètres / désinstallation qui mentionnent LibreBlock. */
    private fun guard(pkg: String) {
        if (pkg !in PROTECTED_PACKAGES) return
        val t = SystemClock.elapsedRealtime()
        if (t - lastGuard < 500 || !repo.evaluate().locked()) return
        val root = rootInActiveWindow ?: return
        if (root.findAccessibilityNodeInfosByText(getString(R.string.app_name)).isNullOrEmpty()) return
        lastGuard = t
        performGlobalAction(GLOBAL_ACTION_BACK)
        performGlobalAction(GLOBAL_ACTION_HOME)
        Toast.makeText(this, R.string.strict_toast, Toast.LENGTH_SHORT).show()
    }

    /** Ajoute aux statistiques le temps passé depuis la dernière vérification, s'il y avait un blocage. */
    private fun recordFocus(actives: List<Active>, now: Long) {
        val t = SystemClock.elapsedRealtime()
        val seconds = if (lastTick == 0L) 0 else ((t - lastTick) / 1000).coerceAtMost(60)
        lastTick = t
        if (seconds > 0 && actives.any { it.blocking }) repo.update { it.recordFocus(seconds, repo.today(now)) }
    }

    /** Prévient au début de chaque pause et à la reprise du travail. */
    private fun notifyPhaseChange(actives: List<Active>) {
        val cycle = actives.filter { it.cycles }.maxByOrNull { it.end }
        val working = cycle?.blocking
        if (cycle != null && lastWorking != null && working != lastWorking) {
            val text = when {
                !cycle.blocking -> getString(R.string.notif_break, formatClock(cycle.phaseEnd))
                cycle.phaseEnd < cycle.end -> getString(R.string.notif_work_next, formatClock(cycle.phaseEnd))
                else -> getString(R.string.notif_work_end, formatClock(cycle.end))
            }
            notify(NOTIFICATION_PHASE, notification(CHANNEL_PHASES, getString(R.string.app_name), text).setAutoCancel(true))
        }
        lastWorking = working
    }

    /** Notification permanente tant qu'une session ou un horaire est en cours, avec un compte à rebours. */
    private fun updateStatus(actives: List<Active>, now: Long) {
        val a = actives.maxWithOrNull(compareBy<Active>({ it.isSession }, { it.blocking }, { it.end }))
        if (a == null) {
            if (lastStatus != null) {
                NotificationManagerCompat.from(this).cancel(NOTIFICATION_STATUS)
                lastStatus = null
                refreshTile()
            }
            return
        }
        val title = getString(
            when {
                a.isSession && a.cycles -> R.string.session_work
                a.isSession -> R.string.session_focus
                a.strict -> R.string.status_schedule_strict
                else -> R.string.status_schedule_flexible
            }
        )
        val text = when {
            !a.blocking -> getString(R.string.status_break_until, formatClock(a.phaseEnd))
            a.cycles && a.phaseEnd < a.end -> getString(R.string.status_work_until, formatClock(a.phaseEnd))
            else -> getString(R.string.status_blocked_until, formatClock(a.end))
        }
        val key = "$title|$text|${a.phaseEnd}"
        if (key == lastStatus) return
        lastStatus = key
        // Le chronomètre de la notification compte avec l'horloge du téléphone, éventuellement décalée.
        val phaseEndOnPhoneClock = a.phaseEnd + (System.currentTimeMillis() - now)
        notify(
            NOTIFICATION_STATUS,
            notification(CHANNEL_STATUS, title, text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setWhen(phaseEndOnPhoneClock)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true),
        )
        refreshTile()
    }

    private fun notification(channel: String, title: String, text: String): NotificationCompat.Builder {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
    }

    @SuppressLint("MissingPermission")
    private fun notify(id: Int, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(this).notify(id, builder.build())
    }

    private fun refreshTile() =
        TileService.requestListeningState(this, ComponentName(this, SessionTileService::class.java))

    private fun currentKeyboard(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')

    companion object {
        private const val CHECK_INTERVAL_MS = 10_000L
        private const val CHANNEL_PHASES = "phases"
        private const val CHANNEL_STATUS = "status"
        private const val NOTIFICATION_PHASE = 1
        private const val NOTIFICATION_STATUS = 2
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
