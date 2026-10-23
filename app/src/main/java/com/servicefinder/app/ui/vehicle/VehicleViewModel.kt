package com.servicefinder.app.ui.vehicle

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.servicefinder.app.ServiceFinderApp
import com.servicefinder.app.data.Engine
import com.servicefinder.app.data.Selection
import com.servicefinder.app.data.Vehicle
import com.servicefinder.app.domain.ModelOption
import com.servicefinder.app.domain.modelOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The selected vehicle, resolved against the catalogue. */
data class CurrentVehicle(
    val selection: Selection,
    /** Null when the model only has common-problem data. */
    val vehicle: Vehicle?,
    val engine: Engine?,
    val problemCount: Int,
)

sealed interface VehicleState {
    data object Loading : VehicleState
    data object None : VehicleState
    data class Selected(val current: CurrentVehicle) : VehicleState
}

@OptIn(ExperimentalCoroutinesApi::class)
class VehicleViewModel(private val app: ServiceFinderApp) : ViewModel() {
    private val catalog = app.catalog
    private val store = app.selection

    val state: StateFlow<VehicleState> = store.selection.flatMapLatest { sel ->
        if (sel == null) {
            flowOf(VehicleState.None)
        } else {
            catalog.problemsFor(sel.make, sel.model, sel.year).map { problems ->
                val vehicle = sel.vehicleId?.let { catalog.vehicle(it) }
                VehicleState.Selected(
                    CurrentVehicle(sel, vehicle, vehicle?.engines?.firstOrNull { it.id == sel.engineId }, problems.size),
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleState.Loading)

    val recents: StateFlow<List<Selection>> =
        store.recents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val odometerKm: StateFlow<Int?> =
        store.odometerKm.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Every selectable model, or null while loading. */
    val options: StateFlow<List<ModelOption>?> =
        combine(catalog.vehicles(), catalog.problemModels()) { v, p -> modelOptions(v, p) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun select(selection: Selection, then: () -> Unit = {}) {
        viewModelScope.launch {
            store.select(selection)
            then()
        }
    }

    fun setOdometer(km: Int?) {
        viewModelScope.launch { store.setOdometer(km) }
    }

    suspend fun vehicle(id: String): Vehicle? = catalog.vehicle(id)
}

@Composable
fun rememberApp(): ServiceFinderApp = LocalContext.current.applicationContext as ServiceFinderApp

@Composable
fun vehicleViewModel(): VehicleViewModel {
    val app = rememberApp()
    return viewModel { VehicleViewModel(app) }
}
