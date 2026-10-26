package com.serviceadvisor.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode(val label: String) { System("System"), Light("Light"), Dark("Dark") }

/** Text scale on top of the system font size. Large is the default: the app is read at arm's length. */
enum class TextSize(val label: String, val scale: Float) {
    Normal("Normal", 1.0f),
    Large("Large", 1.15f),
    ExtraLarge("Extra large", 1.3f),
}

data class Settings(
    val theme: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val textSize: TextSize = TextSize.Large,
    val keepScreenOn: Boolean = true,
)

/** Settings plus recently viewed and favourite vehicles, all stored in one Preferences DataStore. */
class SettingsStore(private val context: Context) {
    private object Keys {
        val theme = stringPreferencesKey("theme")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val textSize = stringPreferencesKey("text_size")
        val keepScreenOn = booleanPreferencesKey("keep_screen_on")
        val recent = stringPreferencesKey("recent")
        val favourites = stringPreferencesKey("favourites")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
            dynamicColor = p[Keys.dynamicColor] ?: true,
            textSize = p[Keys.textSize]?.let { runCatching { TextSize.valueOf(it) }.getOrNull() } ?: TextSize.Large,
            keepScreenOn = p[Keys.keepScreenOn] ?: true,
        )
    }

    /** Most recent first. */
    val recent: Flow<List<String>> = context.dataStore.data.map { it.ids(Keys.recent) }

    /** In the order they were starred. */
    val favourites: Flow<List<String>> = context.dataStore.data.map { it.ids(Keys.favourites) }

    suspend fun setTheme(value: ThemeMode) = context.dataStore.edit { it[Keys.theme] = value.name }
    suspend fun setDynamicColor(value: Boolean) = context.dataStore.edit { it[Keys.dynamicColor] = value }
    suspend fun setTextSize(value: TextSize) = context.dataStore.edit { it[Keys.textSize] = value.name }
    suspend fun setKeepScreenOn(value: Boolean) = context.dataStore.edit { it[Keys.keepScreenOn] = value }

    suspend fun addRecent(id: String) = context.dataStore.edit {
        it[Keys.recent] = (listOf(id) + it.ids(Keys.recent).filter { r -> r != id }).take(MAX_RECENT).joinToString(SEP)
    }

    suspend fun clearRecent() = context.dataStore.edit { it.remove(Keys.recent) }

    suspend fun toggleFavourite(id: String) = context.dataStore.edit {
        val current = it.ids(Keys.favourites)
        it[Keys.favourites] = (if (id in current) current - id else current + id).joinToString(SEP)
    }

    private fun Preferences.ids(key: Preferences.Key<String>): List<String> =
        this[key]?.split(SEP)?.filter(String::isNotEmpty).orEmpty()

    private companion object {
        const val SEP = "\n"
        const val MAX_RECENT = 15
    }
}
