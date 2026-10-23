package com.regofinder.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(val apiKey: String, val state: AuState)

private val Context.dataStore by preferencesDataStore(name = "settings")

/** The PlateAPI key and the last state searched (most workshops only ever use one). */
class SettingsStore(private val context: Context, private val defaultApiKey: String) {
    private val apiKey = stringPreferencesKey("plateapi_key")
    private val state = stringPreferencesKey("state")

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            apiKey = prefs[apiKey]?.takeIf { it.isNotBlank() } ?: defaultApiKey,
            state = prefs[state]?.let { name -> AuState.entries.firstOrNull { it.name == name } } ?: AuState.NSW,
        )
    }

    suspend fun setApiKey(key: String) = context.dataStore.edit { it[apiKey] = key.trim() }

    suspend fun setState(value: AuState) = context.dataStore.edit { it[state] = value.name }
}
