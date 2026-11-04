package com.libreblock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * Plage horaire récurrente. Bloque la liste principale (useMainList) ou sa liste personnalisée (apps).
 * days : bit 0 = lundi … bit 6 = dimanche. startMin / endMin : minutes depuis minuit.
 * Si endMin <= startMin, la plage déborde sur le lendemain (endMin == startMin : 24 h).
 * workMin > 0 : session de travail, alternant workMin de blocage et breakMin de pause.
 * strict : tant que l'horaire est actif, il ne peut être ni modifié ni contourné.
 */
data class Schedule(
    val id: Long,
    val days: Int,
    val startMin: Int,
    val endMin: Int,
    val enabled: Boolean = true,
    val workMin: Int = 0,
    val breakMin: Int = 5,
    val strict: Boolean = false,
    val apps: Set<String> = emptySet(),
    val useMainList: Boolean = false,
) {
    fun hasDay(day: Int) = (days and (1 shl day)) != 0

    val durationMin: Int
        get() = (endMin - startMin).mod(MINUTES_PER_DAY).let { if (it == 0) MINUTES_PER_DAY else it }

    /** Début de l'occurrence en cours, ou null si l'horaire n'est pas actif à t. */
    fun activeSince(t: LocalDateTime): LocalDateTime? {
        if (!enabled) return null
        val startedToday = t.hour * 60 + t.minute >= startMin
        val today = t.dayOfWeek.value - 1
        val day = if (startedToday) today else (today + 6) % 7
        if (!hasDay(day)) return null
        var start = t.toLocalDate().atStartOfDay().plusMinutes(startMin.toLong())
        if (!startedToday) start = start.minusDays(1)
        return if (Duration.between(start, t).toMinutes() < durationMin) start else null
    }

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/** Temps libre minimal, d'affilée, que les horaires stricts doivent laisser toutes les 24 h. */
const val MIN_FREE_MIN = 2 * 60

/** Durée maximale d'une période stricte continue. */
const val MAX_STRICT_MIN = 24 * 60 - MIN_FREE_MIN

private const val WEEK_MIN = 7 * 24 * 60

/**
 * Plus longue période, en minutes, pendant laquelle les horaires stricts actifs verrouillent l'appli
 * sur une semaine type. Les trous de moins de MIN_FREE_MIN comptent comme verrouillés : ils sont trop
 * courts pour modifier quoi que ce soit. Renvoie WEEK_MIN si le verrouillage ne s'arrête jamais.
 */
fun longestStrictStretch(schedules: List<Schedule>): Int {
    val locked = BooleanArray(WEEK_MIN)
    for (s in schedules) {
        if (!s.enabled || !s.strict) continue
        for (day in 0..6) {
            if (!s.hasDay(day)) continue
            val start = day * 24 * 60 + s.startMin
            for (m in 0 until s.durationMin) locked[(start + m) % WEEK_MIN] = true
        }
    }
    // La semaine boucle : on la parcourt à partir du début d'un moment libre.
    val origin = (0 until WEEK_MIN).firstOrNull { !locked[it] && locked[(it + WEEK_MIN - 1) % WEEK_MIN] }
        ?: return if (locked[0]) WEEK_MIN else 0
    val runs = mutableListOf<Pair<Boolean, Int>>() // (verrouillé, durée), alternés, en commençant par du libre
    for (i in 0 until WEEK_MIN) {
        val value = locked[(origin + i) % WEEK_MIN]
        if (runs.isNotEmpty() && runs.last().first == value) runs[runs.lastIndex] = value to runs.last().second + 1
        else runs += value to 1
    }
    // Comble les trous trop courts : chaque période verrouillée absorbe le trou qui la précède.
    var longest = 0
    var current = 0
    for ((isLocked, length) in runs) {
        current = when {
            isLocked -> current + length
            length < MIN_FREE_MIN -> current + length
            else -> 0
        }
        longest = maxOf(longest, current)
    }
    // Le premier trou, s'il est court, relie la dernière période à la première.
    val firstGap = runs.first().second
    if (firstGap < MIN_FREE_MIN && runs.size > 1) {
        val head = runs.drop(1).takeWhile { it.first || it.second < MIN_FREE_MIN }.sumOf { it.second }
        longest = maxOf(longest, current + firstGap + head)
    }
    return minOf(longest, WEEK_MIN)
}

/** Vrai si ces horaires laissent toujours assez de temps libre pour reprendre la main sur le téléphone. */
fun strictSchedulesAllowed(schedules: List<Schedule>) = longestStrictStretch(schedules) <= MAX_STRICT_MIN

/** Un blocage en cours : la session manuelle ou un horaire. */
data class Active(
    val end: Long,
    /** false pendant une pause. */
    val blocking: Boolean,
    /** Fin de la phase travail / pause en cours (= end en blocage continu). */
    val phaseEnd: Long,
    val strict: Boolean,
    /** Session de travail avec pauses. */
    val cycles: Boolean,
    val apps: Set<String>,
    /** Id de l'horaire, null pour la session manuelle. */
    val scheduleId: Long?,
    /** Bloque la liste principale. */
    val usesMainList: Boolean,
) {
    val isSession get() = scheduleId == null
}

internal fun activeState(
    start: Long, end: Long, workMin: Int, breakMin: Int, now: Long,
    strict: Boolean, apps: Set<String>, scheduleId: Long?, usesMainList: Boolean,
): Active {
    if (workMin <= 0) return Active(end, true, end, strict, cycles = false, apps, scheduleId, usesMainList)
    val work = workMin * 60_000L
    val cycle = work + breakMin * 60_000L
    val pos = (now - start).mod(cycle)
    val cycleStart = now - pos
    val working = pos < work
    val phaseEnd = minOf(end, cycleStart + if (working) work else cycle)
    return Active(end, working, phaseEnd, strict, cycles = true, apps, scheduleId, usesMainList)
}

/**
 * Toutes les règles, stockées dans des SharedPreferences.
 * L'instance de SharedPreferences est partagée dans le processus : le service voit
 * immédiatement les changements faits dans l'interface.
 */
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("libreblock", Context.MODE_PRIVATE)

    /** Liste principale : celle des sessions manuelles, et le point de départ des nouveaux horaires. */
    var blockedApps: Set<String>
        get() = prefs.getStringSet(KEY_APPS, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_APPS, value).apply()

    var schedules: List<Schedule>
        get() {
            val arr = JSONArray(prefs.getString(KEY_SCHEDULES, "[]"))
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val apps = o.optJSONArray("apps")
                Schedule(
                    o.getLong("id"), o.getInt("days"), o.getInt("start"), o.getInt("end"), o.getBoolean("enabled"),
                    o.optInt("work", 0), o.optInt("break", 5), o.optBoolean("strict", false),
                    if (apps == null) emptySet() else List(apps.length()) { apps.getString(it) }.toSet(),
                    o.optBoolean("main", false),
                )
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach {
                arr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("days", it.days)
                        .put("start", it.startMin)
                        .put("end", it.endMin)
                        .put("enabled", it.enabled)
                        .put("work", it.workMin)
                        .put("break", it.breakMin)
                        .put("strict", it.strict)
                        .put("apps", JSONArray(it.apps.toList()))
                        .put("main", it.useMainList)
                )
            }
            prefs.edit().putString(KEY_SCHEDULES, arr.toString()).apply()
        }

    /** Fin de la session manuelle en cours (epoch ms), 0 si aucune. */
    var sessionEnd: Long
        get() = prefs.getLong(KEY_SESSION_END, 0L)
        set(value) = prefs.edit().putLong(KEY_SESSION_END, value).apply()

    init {
        // Anciennes versions : une seule liste d'applis et un mode strict commun à tous les horaires.
        if (!prefs.contains(KEY_SCHEDULES_V2)) {
            val legacyStrict = prefs.getBoolean(KEY_STRICT_SCHEDULES, false)
            val apps = blockedApps
            schedules = schedules.map { it.copy(strict = legacyStrict, apps = apps) }
            prefs.edit().putBoolean(KEY_SCHEDULES_V2, true).remove(KEY_STRICT_SCHEDULES).apply()
        }
    }

    /** workMin = 0 : blocage continu ; sinon session de travail avec pauses. */
    fun startSession(minutes: Int, strict: Boolean, workMin: Int, breakMin: Int) {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putLong(KEY_SESSION_START, now)
            .putLong(KEY_SESSION_END, now + minutes * 60_000L)
            .putBoolean(KEY_SESSION_STRICT, strict)
            .putInt(KEY_SESSION_WORK, workMin)
            .putInt(KEY_SESSION_BREAK, breakMin)
            .apply()
    }

    fun active(now: Long): List<Active> {
        val list = mutableListOf<Active>()
        val mainList = blockedApps
        val end = sessionEnd
        if (end > now) {
            list += activeState(
                prefs.getLong(KEY_SESSION_START, 0L), end,
                prefs.getInt(KEY_SESSION_WORK, 0), prefs.getInt(KEY_SESSION_BREAK, 5), now,
                prefs.getBoolean(KEY_SESSION_STRICT, false), mainList, scheduleId = null, usesMainList = true,
            )
        }
        val zone = ZoneId.systemDefault()
        val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)
        for (s in schedules) {
            val start = s.activeSince(t)?.atZone(zone)?.toInstant()?.toEpochMilli() ?: continue
            list += activeState(
                start, start + s.durationMin * 60_000L, s.workMin, s.breakMin, now, s.strict,
                if (s.useMainList) mainList else s.apps, s.id, s.useMainList,
            )
        }
        return list
    }

    /** Un blocage strict est actif (pauses comprises) : LibreBlock ne peut pas être désactivé. */
    fun isLocked(now: Long) = active(now).any { it.strict }

    /** Session ou horaire strict en cours sur la liste principale : elle ne peut que s'allonger. */
    fun mainListLocked(now: Long) = active(now).any { it.strict && it.usesMainList }

    /** Horaires stricts en cours : non modifiables, leur liste ne peut que s'allonger. */
    fun lockedScheduleIds(now: Long) = active(now).filter { it.strict }.mapNotNull { it.scheduleId }.toSet()

    fun shouldBlock(pkg: String, now: Long) = active(now).any { it.blocking && pkg in it.apps }

    private companion object {
        const val KEY_APPS = "blocked_apps"
        const val KEY_SCHEDULES = "schedules"
        const val KEY_SCHEDULES_V2 = "schedules_v2"
        const val KEY_SESSION_START = "session_start"
        const val KEY_SESSION_END = "session_end"
        const val KEY_SESSION_STRICT = "session_strict"
        const val KEY_SESSION_WORK = "session_work"
        const val KEY_SESSION_BREAK = "session_break"
        const val KEY_STRICT_SCHEDULES = "strict_schedules"
    }
}

/** Noms des jours dans la langue du téléphone, du lundi au dimanche (même ordre que Schedule.days). */
fun dayNames(style: TextStyle): List<String> =
    DayOfWeek.entries.map { it.getDisplayName(style, Locale.getDefault()) }

// Format court de la langue du téléphone : « 09:00 » en français, « 9:00 AM » en anglais américain.
private fun clock() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())

fun formatMinutes(m: Int): String = clock().format(LocalTime.of(m / 60, m % 60))

fun formatClock(epochMs: Long): String = clock().format(Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()))

fun formatDuration(minutes: Int) = if (minutes < 60) "$minutes min" else "%dh%02d".format(minutes / 60, minutes % 60)

fun formatCountdown(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
