package com.serviceadvisor.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

sealed interface DataState {
    data object Loading : DataState
    data class Ready(val dataset: Dataset, val source: DataSource) : DataState
    data class Failed(val message: String) : DataState
}

enum class DataSource { BuiltInSample, Imported }

/**
 * Holds the active dataset: the workshop's imported file if there is one, otherwise the bundled
 * sample. The whole dataset is small enough to keep in memory and search directly.
 */
class ServiceDataRepository(private val context: Context) {
    private val importedFile = File(context.filesDir, "imported-vehicles.json")
    private val _state = MutableStateFlow<DataState>(DataState.Loading)
    val state: StateFlow<DataState> = _state.asStateFlow()

    suspend fun load() {
        _state.value = withContext(Dispatchers.IO) {
            try {
                if (importedFile.isFile) {
                    DataState.Ready(DatasetParser.parse(importedFile.readText()), DataSource.Imported)
                } else {
                    DataState.Ready(DatasetParser.parse(readSample()), DataSource.BuiltInSample)
                }
            } catch (e: Exception) {
                DataState.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** Imports a data file chosen with the system picker. Returns null on success, else the reason. */
    suspend fun import(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                ?: return@withContext "The file could not be opened."
            val dataset = DatasetParser.parse(text)
            importedFile.writeText(text)
            _state.value = DataState.Ready(dataset, DataSource.Imported)
            null
        } catch (e: DatasetParser.InvalidDataset) {
            e.message
        } catch (e: Exception) {
            "The file could not be read (${e.message ?: e.javaClass.simpleName})."
        }
    }

    suspend fun restoreSample() {
        withContext(Dispatchers.IO) { importedFile.delete() }
        load()
    }

    /** The bundled sample, used as the template when a workshop builds its own file. */
    fun readSample(): String = context.assets.open("vehicles.json").use { it.readBytes().decodeToString() }
}
