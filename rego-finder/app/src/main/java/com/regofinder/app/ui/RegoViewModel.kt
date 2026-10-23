package com.regofinder.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.regofinder.app.RegoFinderApp
import com.regofinder.app.data.AuState
import com.regofinder.app.data.LookupError
import com.regofinder.app.data.LookupOutcome
import com.regofinder.app.data.Plate
import com.regofinder.app.data.RegoRecord
import com.regofinder.app.data.RegoRepository
import com.regofinder.app.data.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val plate: String = "",
    val state: AuState = AuState.NSW,
    val apiKey: String = "",
    val settingsLoaded: Boolean = false,
    val loading: Boolean = false,
    val record: RegoRecord? = null,
    val fromCache: Boolean = false,
    val selected: Int = 0,
    val error: String? = null,
    val history: List<RegoRecord> = emptyList(),
)

class RegoViewModel(
    private val repositoryProvider: () -> RegoRepository,
    private val settings: SettingsStore,
) : ViewModel() {
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var repository: RegoRepository? = null
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            settings.settings.collect { s ->
                _ui.update { it.copy(apiKey = s.apiKey, state = if (it.settingsLoaded) it.state else s.state, settingsLoaded = true) }
            }
        }
        viewModelScope.launch {
            val repo = repository()
            // Warm the connection while the user is still typing the plate.
            withContext(Dispatchers.IO) { repo.preconnect() }
            repo.load()
            repo.history.collect { list -> _ui.update { it.copy(history = list) } }
        }
    }

    fun onPlateChange(value: String) {
        val cleaned = value.uppercase().filter { it.isLetterOrDigit() || it == ' ' || it == '-' }
        _ui.update { it.copy(plate = cleaned.take(Plate.MAX_LENGTH + 2), error = null) }
    }

    fun onStateChange(value: AuState) {
        _ui.update { it.copy(state = value, error = null) }
        viewModelScope.launch { settings.setState(value) }
    }

    fun onSelectVehicle(index: Int) = _ui.update { it.copy(selected = index) }

    fun search(refresh: Boolean = false) {
        val current = _ui.value
        val plate = Plate.normalize(current.plate)
        if (!Plate.isValid(plate)) {
            _ui.update { it.copy(error = "Enter a rego of up to ${Plate.MAX_LENGTH} letters and numbers.") }
            return
        }
        val state = current.state
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            val outcome = repository().lookup(plate, state, _ui.value.apiKey, refresh)
            _ui.update {
                when (outcome) {
                    is LookupOutcome.Found -> it.copy(
                        loading = false,
                        record = outcome.record,
                        fromCache = outcome.fromCache,
                        selected = 0,
                    )
                    is LookupOutcome.Failed -> it.copy(loading = false, error = message(outcome.error, plate, state))
                }
            }
        }
    }

    fun open(record: RegoRecord) {
        searchJob?.cancel()
        _ui.update {
            it.copy(plate = record.plate, state = record.state, record = record, fromCache = true, selected = 0, loading = false, error = null)
        }
        viewModelScope.launch { repository().remember(record) }
    }

    fun clearHistory() {
        viewModelScope.launch { repository().clearHistory() }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch { settings.setApiKey(key) }
        _ui.update { it.copy(error = null) }
    }

    private suspend fun repository(): RegoRepository =
        repository ?: withContext(Dispatchers.IO) { repositoryProvider() }.also { repository = it }

    private fun message(error: LookupError, plate: String, state: AuState): String = when (error) {
        LookupError.MissingKey -> "Add your PlateAPI key (key icon, top right) to search by rego."
        LookupError.InvalidKey -> "PlateAPI rejected the key. Check it under the key icon."
        LookupError.NotFound -> "No vehicle found for $plate in ${state.name}. Check the plate and state."
        is LookupError.RateLimited -> "PlateAPI limit reached" + retry(error.retryAfterSeconds)
        is LookupError.Unavailable -> "PlateAPI couldn't complete the lookup" + retry(error.retryAfterSeconds)
        is LookupError.Rejected -> error.message
        is LookupError.Network -> "No connection to PlateAPI (${error.message})."
    }

    private fun retry(seconds: Long?): String = when {
        seconds == null -> ". Try again shortly."
        seconds < 120 -> ". Try again in $seconds s."
        else -> ". Try again in ${seconds / 60} min."
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as RegoFinderApp
                RegoViewModel({ app.repository }, app.settings)
            }
        }
    }
}
