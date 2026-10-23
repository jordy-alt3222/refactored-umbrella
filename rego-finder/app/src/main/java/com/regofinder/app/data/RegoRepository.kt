package com.regofinder.app.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Rego lookups with a local cache in front of PlateAPI. A plate's vehicle rarely changes, so a
 * repeat search is answered from the cache without a network call or any quota; the screen shows
 * the result's age and offers a refresh. Recent lookups are kept in a small JSON file.
 */
class RegoRepository(
    private val api: PlateApi,
    private val historyFile: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private var loaded = false
    private val _history = MutableStateFlow<List<RegoRecord>>(emptyList())

    /** Most recent first. Empty until [load] has read the file. */
    val history: StateFlow<List<RegoRecord>> = _history.asStateFlow()

    suspend fun load() = withContext(io) {
        mutex.withLock {
            if (loaded) return@withLock
            loaded = true
            val saved = runCatching { json.decodeFromString<List<RegoRecord>>(historyFile.readText()) }
                .getOrDefault(emptyList())
            _history.update { current -> (current + saved).distinctBy { it.key }.take(MAX_HISTORY) }
        }
    }

    fun cached(plate: String, state: AuState): RegoRecord? =
        _history.value.firstOrNull { it.plate == plate && it.state == state }

    suspend fun lookup(plate: String, state: AuState, apiKey: String, refresh: Boolean): LookupOutcome {
        if (!refresh) {
            load()
            cached(plate, state)?.let { return LookupOutcome.Found(it, fromCache = true) }
        }
        if (apiKey.isBlank()) return LookupOutcome.Failed(LookupError.MissingKey)
        val outcome = withContext(io) { api.lookup(plate, state, apiKey) }
        if (outcome is LookupOutcome.Found) remember(outcome.record)
        return outcome
    }

    /** Moves an existing entry to the top, e.g. when it is reopened from the history list. */
    suspend fun remember(record: RegoRecord) {
        load()
        _history.update { list ->
            (listOf(record) + list.filterNot { it.key == record.key }).take(MAX_HISTORY)
        }
        persist()
    }

    suspend fun clearHistory() {
        load()
        _history.value = emptyList()
        persist()
    }

    fun preconnect() = api.preconnect()

    private suspend fun persist() = withContext(io) {
        mutex.withLock {
            // Read inside the lock so overlapping saves always finish with the newest list.
            val records = _history.value
            // Write then rename, so a crash mid-write never leaves a truncated file.
            val tmp = File(historyFile.parentFile, historyFile.name + ".tmp")
            tmp.writeText(json.encodeToString(records))
            if (!tmp.renameTo(historyFile)) {
                historyFile.delete()
                tmp.renameTo(historyFile)
            }
        }
    }

    private val RegoRecord.key get() = "${state.name}/$plate"

    companion object {
        const val MAX_HISTORY = 30
    }
}
