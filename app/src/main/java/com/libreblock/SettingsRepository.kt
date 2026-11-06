package com.libreblock

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.LocalDate

/**
 * Source unique des réglages, partagée par l'interface, le service d'accessibilité et la tuile
 * (même processus). Les réglages sont stockés dans un fichier DataStore ; l'état courant est gardé en
 * mémoire dans un StateFlow, lisible sans attendre par le service à chaque changement d'appli.
 */
class SettingsRepository private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val store: DataStore<Settings> = DataStoreFactory.create(
        serializer = SettingsSerializer,
        migrations = listOf(LegacyPrefsMigration(context)),
        scope = scope,
        produceFile = { context.dataStoreFile("settings.json") },
    )

    // Lecture initiale bloquante : un petit fichier, lu une fois au démarrage du processus.
    private val state = MutableStateFlow(runBlocking { store.data.first() })
    val settings: StateFlow<Settings> = state.asStateFlow()

    // L'état en mémoire est la référence ; on n'écrit sur disque que la dernière version.
    private val pending = Channel<Settings>(Channel.CONFLATED)

    init {
        scope.launch { for (s in pending) store.updateData { s } }
    }

    fun update(transform: (Settings) -> Settings) {
        state.update(transform)
        pending.trySend(state.value)
    }

    /** Heure de référence, protégée contre les changements d'heure pendant le mode strict. */
    fun now() = TrustedClock.now(context)

    fun today(now: Long = now()): LocalDate = Instant.ofEpochMilli(now).atZone(TrustedClock.zone(context)).toLocalDate()

    fun startSession(config: SessionConfig) {
        val now = now()
        update {
            it.copy(
                session = Session(now, now + config.minutes * 60_000L, config.strict, config.workMin, config.breakMin),
                lastSession = config,
            )
        }
    }

    /**
     * Blocages en cours ; arme la protection de l'heure tant qu'un blocage strict est actif, qu'une limite
     * quotidienne est atteinte ou qu'une hausse de limite attend le lendemain.
     */
    fun evaluate(now: Long = now()): List<Active> {
        val settings = state.value
        val actives = settings.active(now, TrustedClock.zone(context))
        TrustedClock.setArmed(context, actives.locked() || settings.limitsArmed(today(now)))
        return actives
    }

    fun setLimit(pkg: String, minutes: Int?) = update { it.withLimit(pkg, minutes, today()) }

    companion object {
        @Volatile
        private var instance: SettingsRepository? = null

        fun get(context: Context): SettingsRepository = instance ?: synchronized(this) {
            instance ?: SettingsRepository(context.applicationContext).also { instance = it }
        }
    }
}

private object SettingsSerializer : Serializer<Settings> {
    override val defaultValue = Settings()

    override suspend fun readFrom(input: InputStream): Settings = try {
        settingsFromJson(input.readBytes().decodeToString())
    } catch (e: JSONException) {
        throw CorruptionException("Réglages illisibles", e)
    }

    override suspend fun writeTo(t: Settings, output: OutputStream) = output.write(t.toJson().encodeToByteArray())
}

/** Reprend les réglages des versions qui utilisaient des SharedPreferences, puis les efface. */
private class LegacyPrefsMigration(context: Context) : DataMigration<Settings> {
    private val prefs = context.getSharedPreferences("libreblock", Context.MODE_PRIVATE)

    override suspend fun shouldMigrate(currentData: Settings) = prefs.all.isNotEmpty()

    override suspend fun migrate(currentData: Settings) = legacySettings(prefs.all)

    override suspend fun cleanUp() {
        prefs.edit().clear().apply()
    }
}
