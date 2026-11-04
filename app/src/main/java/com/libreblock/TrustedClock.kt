package com.libreblock

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import java.time.ZoneId
import kotlin.math.abs

/**
 * Heure de confiance, pour qu'on ne puisse pas sortir d'un blocage strict en changeant l'heure ou le
 * fuseau horaire du téléphone.
 *
 * Pendant un verrouillage strict, tout saut de l'horloge système est neutralisé : on suit le temps
 * écoulé depuis le démarrage du téléphone, que l'utilisateur ne peut pas modifier. Le fuseau horaire
 * reste celui du début du verrouillage.
 *
 * Le passage heure d'été / heure d'hiver n'est jamais pris pour une manipulation : l'horloge système
 * compte en UTC et ne saute pas, et le fuseau ne change pas (Europe/Paris reste Europe/Paris, seul son
 * décalage varie). Les horaires suivent donc l'heure locale comme prévu.
 *
 * Hors verrouillage, on fait confiance au téléphone : l'utilisateur peut de toute façon modifier ou
 * désactiver ses horaires, changer l'heure ne lui donne rien de plus.
 */
internal data class ClockState(
    /** Millisecondes à retirer de l'heure système pour obtenir l'heure de confiance. */
    val offset: Long = 0,
    val anchorWall: Long = 0,
    val anchorElapsed: Long = 0,
    /** Compteur de démarrages : le temps écoulé repart de zéro à chaque redémarrage. */
    val boot: Int = -1,
    val armed: Boolean = false,
    /** Fuseau figé au début du verrouillage. */
    val zone: String = "",
)

/** Écart au-delà duquel on considère que l'heure a été changée à la main. */
internal const val CLOCK_JUMP_MS = 60_000L
private const val REANCHOR_MS = 60 * 60_000L

/** Prend en compte l'heure système actuelle et neutralise un éventuel saut. */
internal fun ClockState.observe(wall: Long, elapsed: Long, boot: Int): ClockState {
    if (boot != this.boot) return copy(anchorWall = wall, anchorElapsed = elapsed, boot = boot)
    val drift = (wall - anchorWall) - (elapsed - anchorElapsed)
    return when {
        abs(drift) > CLOCK_JUMP_MS ->
            copy(offset = if (armed) offset + drift else 0L, anchorWall = wall, anchorElapsed = elapsed)
        // Absorbe régulièrement les petites corrections automatiques (quelques secondes par jour).
        elapsed - anchorElapsed > REANCHOR_MS -> copy(anchorWall = wall, anchorElapsed = elapsed)
        else -> this
    }
}

internal fun ClockState.trustedNow(wall: Long) = wall - offset

/** Active ou lève la protection ; à la levée, on revient à l'heure et au fuseau du téléphone. */
internal fun ClockState.withArmed(armed: Boolean, systemZone: String): ClockState = when {
    armed && !this.armed -> copy(armed = true, zone = systemZone)
    !armed && (this.armed || offset != 0L) -> copy(armed = false, offset = 0, zone = systemZone)
    else -> this
}

internal fun ClockState.zoneId(systemZone: String) = if (armed && zone.isNotEmpty()) zone else systemZone

object TrustedClock {
    @Volatile
    private var displayZone: ZoneId = ZoneId.systemDefault()

    /** Fuseau à utiliser pour afficher les heures (le dernier fuseau de confiance connu). */
    val zone: ZoneId get() = displayZone

    @Synchronized
    fun now(context: Context): Long {
        val wall = System.currentTimeMillis()
        val state = load(context).observe(wall, SystemClock.elapsedRealtime(), bootCount(context))
        save(context, state)
        return state.trustedNow(wall)
    }

    @Synchronized
    fun zone(context: Context): ZoneId {
        val zone = ZoneId.of(load(context).zoneId(ZoneId.systemDefault().id))
        displayZone = zone
        return zone
    }

    /** Appelé après chaque évaluation des blocages : protège l'heure tant qu'un blocage strict est actif. */
    @Synchronized
    fun setArmed(context: Context, armed: Boolean) {
        save(context, load(context).withArmed(armed, ZoneId.systemDefault().id))
    }

    private fun bootCount(context: Context) =
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)

    private var cached: ClockState? = null

    private fun load(context: Context): ClockState = cached ?: prefs(context).run {
        ClockState(
            offset = getLong("offset", 0),
            anchorWall = getLong("anchor_wall", 0),
            anchorElapsed = getLong("anchor_elapsed", 0),
            boot = getInt("boot", -1),
            armed = getBoolean("armed", false),
            zone = getString("zone", "") ?: "",
        )
    }.also { cached = it }

    private fun save(context: Context, state: ClockState) {
        if (state == cached) return
        cached = state
        prefs(context).edit()
            .putLong("offset", state.offset)
            .putLong("anchor_wall", state.anchorWall)
            .putLong("anchor_elapsed", state.anchorElapsed)
            .putInt("boot", state.boot)
            .putBoolean("armed", state.armed)
            .putString("zone", state.zone)
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("trusted_clock", Context.MODE_PRIVATE)
}
