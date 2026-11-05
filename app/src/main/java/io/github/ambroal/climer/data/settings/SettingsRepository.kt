package io.github.ambroal.climer.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)
}

private val SETTINGS_KEY = stringPreferencesKey("settings_json")
private val json = Json { ignoreUnknownKeys = true }

/** Un'unica chiave JSON: aggiungere un'impostazione non richiede migrazioni (i default coprono i campi mancanti). */
class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {
    override val settings: Flow<AppSettings> = store.data
        .map { prefs -> prefs[SETTINGS_KEY]?.let { decode(it) } ?: AppSettings() }
        .distinctUntilChanged()

    override suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { prefs ->
            val current = prefs[SETTINGS_KEY]?.let { decode(it) } ?: AppSettings()
            prefs[SETTINGS_KEY] = json.encodeToString(AppSettings.serializer(), transform(current))
        }
    }

    private fun decode(raw: String): AppSettings? = runCatching { json.decodeFromString(AppSettings.serializer(), raw) }.getOrNull()
}
