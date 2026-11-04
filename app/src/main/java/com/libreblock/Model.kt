package com.libreblock

import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

// Modèle de l'appli : données immuables et logique pure, sans dépendance à Android, testées en JVM.

/**
 * Liste d'applis. allowOnly = false : bloque les applis cochées. allowOnly = true : bloque toutes les
 * applis lançables sauf celles cochées (mode « tout bloquer sauf »).
 */
data class AppList(val apps: Set<String> = emptySet(), val allowOnly: Boolean = false) {
    /** pkg doit être une appli lançable et non essentielle : le service filtre le reste. */
    fun blocks(pkg: String) = if (allowOnly) pkg !in apps else pkg in apps

    /** Vrai si `next` débloque quelque chose par rapport à cette liste : interdit en mode strict. */
    fun isLoosenedBy(next: AppList) = when {
        next.allowOnly != allowOnly -> true
        allowOnly -> !apps.containsAll(next.apps) // une appli de plus autorisée
        else -> !next.apps.containsAll(apps) // une appli de moins bloquée
    }

    /** Vrai si on peut cocher / décocher pkg alors que la liste est verrouillée. */
    fun canToggleWhenLocked(pkg: String) = if (allowOnly) pkg in apps else pkg !in apps

    fun toggle(pkg: String) = copy(apps = if (pkg in apps) apps - pkg else apps + pkg)
}

/**
 * Plage horaire récurrente. Bloque la liste principale (useMainList) ou sa liste personnalisée.
 * days : bit 0 = lundi … bit 6 = dimanche. startMin / endMin : minutes depuis minuit.
 * Si endMin <= startMin, la plage déborde sur le lendemain (endMin == startMin : 24 h).
 * workMin > 0 : session de travail, alternant workMin de blocage et breakMin de pause.
 * strict : tant que l'horaire est actif, il ne peut être ni modifié ni contourné.
 * pausedOnVacation : horaire « Travail », suspendu pendant les vacances.
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
    val list: AppList = AppList(),
    val useMainList: Boolean = false,
    val pausedOnVacation: Boolean = false,
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

    /**
     * Début et fin (epoch ms) de l'occurrence en cours, ou null. Calculés en heure locale : la nuit du
     * changement d'heure, un horaire 23:00 – 08:00 se termine bien à 08:00 (8 ou 10 heures réelles).
     */
    fun window(now: Long, zone: ZoneId): Pair<Long, Long>? {
        val since = activeSince(LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone)) ?: return null
        val end = since.plusMinutes(durationMin.toLong())
        return since.atZone(zone).toInstant().toEpochMilli() to end.atZone(zone).toInstant().toEpochMilli()
    }

    private companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

/** Période de vacances, du premier au dernier jour inclus : les horaires « Travail » sont suspendus. */
data class Vacation(val id: Long, val start: LocalDate, val end: LocalDate, val label: String = "") {
    operator fun contains(day: LocalDate) = !day.isBefore(start) && !day.isAfter(end)
}

/** Session lancée à la main (epoch ms). workMin = 0 : blocage continu. */
data class Session(val start: Long, val end: Long, val strict: Boolean, val workMin: Int, val breakMin: Int)

/** Réglages de la dernière session, repris par l'écran d'accueil et la tuile. */
data class SessionConfig(val minutes: Int = 25, val strict: Boolean = false, val workMin: Int = 0, val breakMin: Int = 5)

data class DayStats(val blocked: Int = 0, val focusSeconds: Long = 0, val perApp: Map<String, Int> = emptyMap())

/** Tout l'état persistant de l'appli. */
data class Settings(
    val mainList: AppList = AppList(),
    val schedules: List<Schedule> = emptyList(),
    val vacations: List<Vacation> = emptyList(),
    val session: Session? = null,
    val lastSession: SessionConfig = SessionConfig(),
    val onboardingDone: Boolean = false,
    /** Statistiques par jour, clé = date ISO (2026-11-04). */
    val stats: Map<String, DayStats> = emptyMap(),
)

// ---------------------------------------------------------------------------------------------
// Blocages en cours

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
    val list: AppList,
    /** Id de l'horaire, null pour la session manuelle. */
    val scheduleId: Long?,
    /** Bloque la liste principale. */
    val usesMainList: Boolean,
) {
    val isSession get() = scheduleId == null
}

internal fun activeState(
    start: Long, end: Long, workMin: Int, breakMin: Int, now: Long,
    strict: Boolean, list: AppList, scheduleId: Long?, usesMainList: Boolean,
): Active {
    if (workMin <= 0) return Active(end, true, end, strict, cycles = false, list, scheduleId, usesMainList)
    val work = workMin * 60_000L
    val cycle = work + breakMin * 60_000L
    val pos = (now - start).mod(cycle)
    val cycleStart = now - pos
    val working = pos < work
    val phaseEnd = minOf(end, cycleStart + if (working) work else cycle)
    return Active(end, working, phaseEnd, strict, cycles = true, list, scheduleId, usesMainList)
}

fun Settings.active(now: Long, zone: ZoneId): List<Active> {
    val list = mutableListOf<Active>()
    session?.takeIf { it.end > now }?.let {
        list += activeState(it.start, it.end, it.workMin, it.breakMin, now, it.strict, mainList, null, true)
    }
    for (s in schedules) {
        val (start, end) = s.window(now, zone) ?: continue
        // Un horaire de nuit commencé la veille des vacances va jusqu'à son terme.
        if (s.pausedOnVacation && vacationOn(Instant.ofEpochMilli(start).atZone(zone).toLocalDate()) != null) continue
        list += activeState(
            start, end, s.workMin, s.breakMin, now, s.strict,
            if (s.useMainList) mainList else s.list, s.id, s.useMainList,
        )
    }
    return list
}

fun Settings.vacationOn(day: LocalDate): Vacation? = vacations.firstOrNull { day in it }

/**
 * Des vacances ajoutées, déplacées ou supprimées ne doivent pas interrompre un horaire strict déjà en cours :
 * sinon ce serait un moyen de contourner le mode strict.
 */
fun vacationChangeAllowed(before: Settings, after: Settings, now: Long, zone: ZoneId): Boolean {
    val locked = before.active(now, zone).lockedScheduleIds()
    return after.active(now, zone).mapNotNull { it.scheduleId }.toSet().containsAll(locked)
}

/** Vacances à afficher : en cours ou à venir, de la plus proche à la plus lointaine. */
fun List<Vacation>.upcoming(today: LocalDate) = filter { !it.end.isBefore(today) }.sortedBy { it.start }

/** Un blocage strict est actif (pauses comprises) : LibreBlock ne peut pas être désactivé. */
fun List<Active>.locked() = any { it.strict }

/** Session ou horaire strict en cours sur la liste principale : elle ne peut que devenir plus stricte. */
fun List<Active>.mainListLocked() = any { it.strict && it.usesMainList }

/** Horaires stricts en cours : non modifiables, leur liste ne peut que devenir plus stricte. */
fun List<Active>.lockedScheduleIds() = filter { it.strict }.mapNotNull { it.scheduleId }.toSet()

fun List<Active>.blocks(pkg: String) = any { it.blocking && it.list.blocks(pkg) }

// ---------------------------------------------------------------------------------------------
// Garde-fou : jamais de verrouillage strict permanent

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

/** Refuse ce qui allongerait le verrouillage au-delà de la limite ; accepte tout ce qui le raccourcit. */
fun strictChangeAllowed(before: List<Schedule>, after: List<Schedule>) =
    strictSchedulesAllowed(after) || longestStrictStretch(after) <= longestStrictStretch(before)

fun List<Schedule>.withSchedule(s: Schedule) =
    if (any { it.id == s.id }) map { if (it.id == s.id) s else it } else this + s

// ---------------------------------------------------------------------------------------------
// Statistiques

private const val STATS_DAYS = 60L

fun Settings.recordBlock(pkg: String, day: LocalDate): Settings = updateDay(day) {
    it.copy(blocked = it.blocked + 1, perApp = it.perApp + (pkg to (it.perApp[pkg] ?: 0) + 1))
}

fun Settings.recordFocus(seconds: Long, day: LocalDate): Settings =
    updateDay(day) { it.copy(focusSeconds = it.focusSeconds + seconds) }

private fun Settings.updateDay(day: LocalDate, change: (DayStats) -> DayStats): Settings {
    val key = day.toString()
    val oldest = day.minusDays(STATS_DAYS).toString()
    val kept = stats.filterKeys { it > oldest } // les dates ISO se comparent comme du texte
    return copy(stats = kept + (key to change(kept[key] ?: DayStats())))
}

fun Map<String, DayStats>.day(day: LocalDate) = this[day.toString()] ?: DayStats()

/** Jours d'affilée avec au moins une minute de concentration, en comptant aujourd'hui s'il en a. */
fun Map<String, DayStats>.streak(today: LocalDate): Int {
    var day = if (day(today).focusSeconds >= 60) today else today.minusDays(1)
    var count = 0
    while (day(day).focusSeconds >= 60) {
        count++
        day = day.minusDays(1)
    }
    return count
}

/** Applis les plus souvent bloquées sur les `days` derniers jours, de la plus bloquée à la moins bloquée. */
fun Map<String, DayStats>.topBlocked(today: LocalDate, days: Int, limit: Int): List<Pair<String, Int>> {
    val totals = mutableMapOf<String, Int>()
    for (i in 0 until days) day(today.minusDays(i.toLong())).perApp.forEach { (pkg, n) ->
        totals[pkg] = (totals[pkg] ?: 0) + n
    }
    return totals.entries.sortedByDescending { it.value }.take(limit).map { it.key to it.value }
}

// ---------------------------------------------------------------------------------------------
// Sérialisation JSON (fichier DataStore)

private fun AppList.toJson() = JSONObject().put("apps", JSONArray(apps.sorted())).put("allowOnly", allowOnly)

private fun JSONObject?.toAppList(): AppList {
    if (this == null) return AppList()
    val arr = optJSONArray("apps") ?: JSONArray()
    return AppList(List(arr.length()) { arr.getString(it) }.toSet(), optBoolean("allowOnly", false))
}

fun Settings.toJson(): String = JSONObject()
    .put("version", 1)
    .put("mainList", mainList.toJson())
    .put("schedules", JSONArray(schedules.map {
        JSONObject()
            .put("id", it.id).put("days", it.days).put("start", it.startMin).put("end", it.endMin)
            .put("enabled", it.enabled).put("work", it.workMin).put("break", it.breakMin)
            .put("strict", it.strict).put("list", it.list.toJson()).put("main", it.useMainList)
            .put("vacation", it.pausedOnVacation)
    }))
    .put("vacations", JSONArray(vacations.map {
        JSONObject().put("id", it.id).put("start", it.start.toString()).put("end", it.end.toString()).put("label", it.label)
    }))
    .put("session", session?.let {
        JSONObject().put("start", it.start).put("end", it.end).put("strict", it.strict)
            .put("work", it.workMin).put("break", it.breakMin)
    } ?: JSONObject.NULL)
    .put("lastSession", lastSession.let {
        JSONObject().put("minutes", it.minutes).put("strict", it.strict).put("work", it.workMin).put("break", it.breakMin)
    })
    .put("onboardingDone", onboardingDone)
    .put("stats", JSONObject().apply {
        stats.forEach { (day, s) ->
            put(day, JSONObject().put("blocked", s.blocked).put("focus", s.focusSeconds).put("apps", JSONObject(s.perApp)))
        }
    })
    .toString()

fun settingsFromJson(text: String): Settings {
    val o = JSONObject(text)
    val schedules = o.optJSONArray("schedules") ?: JSONArray()
    val vacations = o.optJSONArray("vacations") ?: JSONArray()
    val stats = o.optJSONObject("stats") ?: JSONObject()
    return Settings(
        mainList = o.optJSONObject("mainList").toAppList(),
        schedules = List(schedules.length()) { i ->
            val s = schedules.getJSONObject(i)
            val work = s.optInt("work", 0)
            Schedule(
                s.getLong("id"), s.getInt("days"), s.getInt("start"), s.getInt("end"), s.optBoolean("enabled", true),
                work, s.optInt("break", 5), s.optBoolean("strict", false),
                s.optJSONObject("list").toAppList(), s.optBoolean("main", false),
                // Avant les vacances : les sessions de travail (avec pauses) sont considérées comme du travail.
                pausedOnVacation = s.optBoolean("vacation", work > 0),
            )
        },
        vacations = List(vacations.length()) { i ->
            val v = vacations.getJSONObject(i)
            Vacation(v.getLong("id"), LocalDate.parse(v.getString("start")), LocalDate.parse(v.getString("end")), v.optString("label"))
        },
        session = o.optJSONObject("session")?.let {
            Session(it.getLong("start"), it.getLong("end"), it.optBoolean("strict"), it.optInt("work"), it.optInt("break", 5))
        },
        lastSession = o.optJSONObject("lastSession")?.let {
            SessionConfig(it.optInt("minutes", 25), it.optBoolean("strict"), it.optInt("work"), it.optInt("break", 5))
        } ?: SessionConfig(),
        onboardingDone = o.optBoolean("onboardingDone", false),
        stats = stats.keys().asSequence().associateWith { day ->
            val d = stats.getJSONObject(day)
            val apps = d.optJSONObject("apps") ?: JSONObject()
            DayStats(d.optInt("blocked"), d.optLong("focus"), apps.keys().asSequence().associateWith { apps.getInt(it) })
        },
    )
}

/**
 * Réglages des versions précédentes (SharedPreferences), passés en Map pour être testables.
 * Avant les listes par horaire, un seul réglage « strict » valait pour tous les horaires.
 */
fun legacySettings(prefs: Map<String, Any?>): Settings {
    @Suppress("UNCHECKED_CAST")
    val mainApps = (prefs["blocked_apps"] as? Set<String>).orEmpty()
    val v2 = prefs["schedules_v2"] == true
    val legacyStrict = prefs["strict_schedules"] == true
    val arr = JSONArray(prefs["schedules"] as? String ?: "[]")
    val schedules = List(arr.length()) { i ->
        val s = arr.getJSONObject(i)
        val apps = s.optJSONArray("apps")
        Schedule(
            s.getLong("id"), s.getInt("days"), s.getInt("start"), s.getInt("end"), s.optBoolean("enabled", true),
            s.optInt("work", 0), s.optInt("break", 5),
            strict = if (v2) s.optBoolean("strict", false) else legacyStrict,
            list = AppList(if (v2 && apps != null) List(apps.length()) { apps.getString(it) }.toSet() else mainApps),
            useMainList = s.optBoolean("main", false),
            pausedOnVacation = s.optInt("work", 0) > 0,
        )
    }
    val end = prefs["session_end"] as? Long ?: 0L
    val work = prefs["session_work"] as? Int ?: 0
    val pause = prefs["session_break"] as? Int ?: 5
    val strict = prefs["session_strict"] == true
    return Settings(
        mainList = AppList(mainApps),
        schedules = schedules,
        session = if (end > 0) Session(prefs["session_start"] as? Long ?: 0L, end, strict, work, pause) else null,
        lastSession = SessionConfig(prefs["last_minutes"] as? Int ?: 25, strict, work, pause),
        onboardingDone = prefs["onboarding_done"] == true || mainApps.isNotEmpty(),
    )
}

// ---------------------------------------------------------------------------------------------
// Mise en forme

/** Noms des jours dans la langue du téléphone, du lundi au dimanche (même ordre que Schedule.days). */
fun dayNames(style: TextStyle, locale: Locale = Locale.getDefault()): List<String> =
    DayOfWeek.entries.map { it.getDisplayName(style, locale) }

// Format court de la langue du téléphone : « 09:00 » en français, « 9:00 AM » en anglais américain.
private fun clock() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(Locale.getDefault())

fun formatMinutes(m: Int): String = clock().format(LocalTime.of(m / 60, m % 60))

fun formatClock(epochMs: Long): String = clock().format(Instant.ofEpochMilli(epochMs).atZone(TrustedClock.zone))

/** « 12 nov. 2026 » en français, « Nov 12, 2026 » en anglais. */
fun formatDate(day: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(day)

fun formatDuration(minutes: Int) = if (minutes < 60) "$minutes min" else "%dh%02d".format(minutes / 60, minutes % 60)

fun formatCountdown(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
