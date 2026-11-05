package io.github.ambroal.climer.timer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.ambroal.climer.domain.interval.IntervalConfig
import io.github.ambroal.climer.domain.load.BlockRunContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Tutto ciò che serve per ricostruire il timer dopo che il sistema ha chiuso l'app. */
@Serializable
data class ActiveRun(
    val title: String,
    val config: IntervalConfig,
    val runState: TimerRunState,
    val savedTimerId: Long? = null,
    val workoutId: Long? = null,
    val blockLogIds: List<Long> = emptyList(),
    val context: BlockRunContext? = null,
    val bootCount: Int = 0,
    /** Blocco di origine, per salvare le modifiche fatte dal timer. */
    val blockId: Long? = null,
    /** Blocchi della seduta ancora da eseguire dopo questo, in ordine. */
    val queue: List<QueuedBlock> = emptyList(),
    /** Posizione del blocco nella seduta (1-based); 0 per un blocco o timer singolo. */
    val blockNumber: Int = 0,
    val blockCount: Int = 0,
    val mode: RunMode = RunMode.TIMER,
    /** Cronometro: avviso ogni N secondi, 0 = nessuno. */
    val alertEverySec: Int = 0,
    /** Cronometro: tempo trascorso a ogni giro. */
    val laps: List<Long> = emptyList()
)

@Serializable
enum class RunMode { TIMER, STOPWATCH }

@Serializable
data class QueuedBlock(val blockId: Long, val name: String)

interface ActiveRunStore {
    suspend fun save(run: ActiveRun?)

    suspend fun load(): ActiveRun?
}

private val ACTIVE_RUN_KEY = stringPreferencesKey("active_run_json")
private val json = Json { ignoreUnknownKeys = true }

class DataStoreActiveRunStore(private val store: DataStore<Preferences>) : ActiveRunStore {
    override suspend fun save(run: ActiveRun?) {
        store.edit { prefs ->
            if (run == null) {
                prefs.remove(ACTIVE_RUN_KEY)
            } else {
                prefs[ACTIVE_RUN_KEY] = json.encodeToString(ActiveRun.serializer(), run)
            }
        }
    }

    override suspend fun load(): ActiveRun? {
        val raw = store.data.first()[ACTIVE_RUN_KEY] ?: return null
        return runCatching { json.decodeFromString(ActiveRun.serializer(), raw) }.getOrNull()
    }
}
