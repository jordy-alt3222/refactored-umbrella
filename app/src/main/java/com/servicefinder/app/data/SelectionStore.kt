package com.servicefinder.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The vehicle the technician is working on. [vehicleId] is null for models that
 * only have common-problem data (no owner's-manual figures in the catalogue).
 */
@Serializable
data class Selection(
    val make: String,
    val model: String,
    val vehicleId: String? = null,
    val year: Int,
    val engineId: String? = null,
)

/** Remembers the selected vehicle, recent vehicles and the last odometer reading between sessions. */
class SelectionStore(private val store: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }

    val selection: Flow<Selection?> = store.data.map { prefs ->
        prefs[SELECTION]?.let { runCatching { json.decodeFromString(Selection.serializer(), it) }.getOrNull() }
    }

    val recents: Flow<List<Selection>> = store.data.map { prefs ->
        prefs[RECENTS]?.let { runCatching { json.decodeFromString(RECENT_LIST, it) }.getOrNull() }.orEmpty()
    }

    val odometerKm: Flow<Int?> = store.data.map { it[ODOMETER] }

    suspend fun select(selection: Selection) {
        store.edit { prefs ->
            val previous = prefs[RECENTS]
                ?.let { runCatching { json.decodeFromString(RECENT_LIST, it) }.getOrNull() }.orEmpty()
            val recents = (listOf(selection) + previous.filterNot { it == selection }).take(MAX_RECENTS)
            if (prefs[SELECTION]?.let { json.decodeFromString(Selection.serializer(), it) } != selection) {
                prefs.remove(ODOMETER)
            }
            prefs[SELECTION] = json.encodeToString(Selection.serializer(), selection)
            prefs[RECENTS] = json.encodeToString(RECENT_LIST, recents)
        }
    }

    suspend fun setOdometer(km: Int?) {
        store.edit { prefs -> if (km == null) prefs.remove(ODOMETER) else prefs[ODOMETER] = km }
    }

    private companion object {
        val SELECTION = stringPreferencesKey("selection")
        val RECENTS = stringPreferencesKey("recents")
        val ODOMETER = intPreferencesKey("odometer_km")
        val RECENT_LIST = ListSerializer(Selection.serializer())
        const val MAX_RECENTS = 5
    }
}
