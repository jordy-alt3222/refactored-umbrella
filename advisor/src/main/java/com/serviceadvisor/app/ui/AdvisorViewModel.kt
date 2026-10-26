package com.serviceadvisor.app.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.serviceadvisor.app.ServiceAdvisorApp
import com.serviceadvisor.app.data.DataState
import com.serviceadvisor.app.data.DataSource
import com.serviceadvisor.app.data.Dataset
import com.serviceadvisor.app.data.DatasetInfo
import com.serviceadvisor.app.data.Procedure
import com.serviceadvisor.app.data.Settings
import com.serviceadvisor.app.data.TextSize
import com.serviceadvisor.app.data.ThemeMode
import com.serviceadvisor.app.data.Vehicle
import com.serviceadvisor.app.data.isVerified
import com.serviceadvisor.app.domain.VehicleSearch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One activity-scoped ViewModel: the data set is small and every screen reads from it. */
class AdvisorViewModel(private val app: ServiceAdvisorApp) : ViewModel() {
    val data: StateFlow<DataState> = app.data.state

    val settings: StateFlow<Settings> =
        app.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val vehicles = data.map { (it as? DataState.Ready)?.dataset?.vehicles.orEmpty() }

    val recent: StateFlow<List<Vehicle>> = combine(vehicles, app.settings.recent) { all, ids ->
        val byId = all.associateBy { it.id }
        ids.mapNotNull(byId::get)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favourites: StateFlow<List<Vehicle>> = combine(vehicles, app.settings.favourites) { all, ids ->
        val byId = all.associateBy { it.id }
        ids.mapNotNull(byId::get)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val query = MutableStateFlow("")

    val results: StateFlow<List<Vehicle>> = combine(vehicles, query) { all, q -> VehicleSearch.search(all, q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val dataset: Dataset? get() = (data.value as? DataState.Ready)?.dataset

    val info: DatasetInfo? get() = dataset?.info
    val source: DataSource? get() = (data.value as? DataState.Ready)?.source
    fun isVerified(vehicle: Vehicle): Boolean = dataset?.isVerified(vehicle) == true

    fun vehicle(id: String): Vehicle? = dataset?.vehicles?.firstOrNull { it.id == id }
    fun procedure(id: String): Procedure? = dataset?.procedures?.firstOrNull { it.id == id }
    fun makes(): List<String> = dataset?.vehicles?.map { it.make }?.distinct()?.sorted().orEmpty()
    fun vehicles(make: String): List<Vehicle> = dataset?.vehicles?.filter { it.make == make }.orEmpty()

    fun opened(vehicle: Vehicle) = viewModelScope.launch { app.settings.addRecent(vehicle.id) }
    fun toggleFavourite(vehicle: Vehicle) = viewModelScope.launch { app.settings.toggleFavourite(vehicle.id) }
    fun clearRecent() = viewModelScope.launch { app.settings.clearRecent() }

    fun setTheme(v: ThemeMode) = viewModelScope.launch { app.settings.setTheme(v) }
    fun setDynamicColor(v: Boolean) = viewModelScope.launch { app.settings.setDynamicColor(v) }
    fun setTextSize(v: TextSize) = viewModelScope.launch { app.settings.setTextSize(v) }
    fun setKeepScreenOn(v: Boolean) = viewModelScope.launch { app.settings.setKeepScreenOn(v) }

    /** Returns null on success, otherwise why the file was rejected. */
    suspend fun import(uri: Uri): String? = app.data.import(uri)
    fun restoreSample() = viewModelScope.launch { app.data.restoreSample() }
    fun reload() = viewModelScope.launch { app.data.load() }
    fun sampleJson(): String = app.data.readSample()
}

/** The active dataset, observed so screens update after an import or restore. */
@Composable
fun AdvisorViewModel.observeDataset(): Dataset? {
    val state by data.collectAsStateWithLifecycle()
    return (state as? DataState.Ready)?.dataset
}
