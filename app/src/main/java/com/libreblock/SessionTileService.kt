package com.libreblock

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Tuile des réglages rapides : démarre une session avec les derniers réglages, ou l'arrête
 * (sauf en mode strict). Sans appli dans la liste principale, ouvre LibreBlock.
 */
class SessionTileService : TileService() {

    private val repo get() = SettingsRepository.get(this)

    override fun onStartListening() = refresh()

    override fun onClick() {
        val settings = repo.settings.value
        val session = repo.evaluate().firstOrNull { it.isSession }
        when {
            session?.strict == true -> Unit // impossible d'arrêter une session stricte
            session != null -> repo.update { it.copy(session = null) }
            !settings.mainList.allowOnly && settings.mainList.apps.isEmpty() -> openApp()
            else -> repo.startSession(settings.lastSession)
        }
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val session = repo.evaluate().firstOrNull { it.isSession }
        tile.state = if (session != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                session == null -> getString(R.string.tile_start)
                session.strict -> getString(R.string.tile_strict_until, formatClock(session.end))
                else -> getString(R.string.tile_until, formatClock(session.end))
            }
        }
        tile.updateTile()
    }

    // L'ancienne version de startActivityAndCollapse n'est appelée qu'avant Android 14, où la nouvelle n'existe pas.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
