package com.servicefinder.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.servicefinder.app.R
import com.servicefinder.app.ServiceFinderApp
import com.servicefinder.app.data.ImportState
import com.servicefinder.app.ui.components.AppIcon
import com.servicefinder.app.ui.components.ErrorState
import com.servicefinder.app.ui.components.LoadingState
import com.servicefinder.app.ui.components.LocalSources
import com.servicefinder.app.ui.fuses.FusesScreen
import com.servicefinder.app.ui.problems.ProblemDetailScreen
import com.servicefinder.app.ui.problems.ProblemsScreen
import com.servicefinder.app.ui.sources.SourcesScreen
import com.servicefinder.app.ui.tests.ProcedureScreen
import com.servicefinder.app.ui.tests.TestsScreen
import com.servicefinder.app.ui.vehicle.HomeScreen
import com.servicefinder.app.ui.vehicle.PickEngineScreen
import com.servicefinder.app.ui.vehicle.PickMakeScreen
import com.servicefinder.app.ui.vehicle.PickModelScreen
import com.servicefinder.app.ui.vehicle.PickYearScreen
import com.servicefinder.app.ui.vehicle.ServiceScreen
import com.servicefinder.app.ui.vehicle.SpecsScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

// ---- Destinations -------------------------------------------------------------

@Serializable data object VehicleHome : NavKey
@Serializable data object FusesHome : NavKey
@Serializable data object ProblemsHome : NavKey
@Serializable data object TestsHome : NavKey
@Serializable data object PickMake : NavKey
@Serializable data class PickModel(val make: String) : NavKey
@Serializable data class PickYear(val optionKey: String) : NavKey
@Serializable data class PickEngine(val vehicleId: String, val year: Int) : NavKey
@Serializable data object Specs : NavKey
@Serializable data object Service : NavKey
@Serializable data class ProblemDetail(val id: String) : NavKey
@Serializable data class ProcedureDetail(val id: String) : NavKey
@Serializable data object Sources : NavKey

private enum class Tab(val key: NavKey, val label: String, @DrawableRes val icon: Int) {
    Vehicle(VehicleHome, "Vehicle", R.drawable.ic_car),
    Fuses(FusesHome, "Fuses", R.drawable.ic_bolt),
    Problems(ProblemsHome, "Problems", R.drawable.ic_build),
    Tests(TestsHome, "Tests", R.drawable.ic_check_circle),
}

/** Navigation actions shared by the screens. */
class Navigator(private val backStack: NavBackStack<NavKey>) {
    fun go(key: NavKey) { backStack.add(key) }
    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    /** Switches tab. Other tabs sit above the vehicle tab, so Back from them returns to it. */
    fun tab(key: NavKey) {
        backStack.clear()
        backStack.add(VehicleHome)
        if (key != VehicleHome) backStack.add(key)
    }

    /** Leaves the vehicle picker and returns to wherever it was opened from. */
    fun finishPicking() {
        while (backStack.size > 1 && backStack.last().isPickerStep()) backStack.removeAt(backStack.lastIndex)
    }

    private fun NavKey.isPickerStep() = this is PickMake || this is PickModel || this is PickYear || this is PickEngine
}

@Composable
fun AppRoot(app: ServiceFinderApp) {
    val importState by app.catalog.state.collectAsStateWithLifecycle()
    val sources by app.catalog.sources().collectAsStateWithLifecycle(initialValue = emptyMap())
    val scope = rememberCoroutineScope()

    when (val s = importState) {
        ImportState.Loading -> LoadingState(label = "Preparing the offline catalogue")
        is ImportState.Failed -> ErrorState(
            message = "The offline catalogue could not be loaded (${s.message}).",
            onRetry = { scope.launch { app.catalog.ensureImported() } },
        )
        ImportState.Ready -> CompositionLocalProvider(LocalSources provides sources) { Main() }
    }
}

@Composable
private fun Main() {
    val backStack = rememberNavBackStack(VehicleHome)
    val nav = Navigator(backStack)
    // The tab is the lowest top-level destination above the vehicle tab, if any.
    val currentTab = Tab.entries.lastOrNull { tab -> backStack.any { it == tab.key } } ?: Tab.Vehicle

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            Tab.entries.forEach { tab ->
                item(
                    selected = tab == currentTab,
                    onClick = { nav.tab(tab.key) },
                    icon = { AppIcon(tab.icon, contentDescription = null) },
                    label = { Text(tab.label) },
                )
            }
        },
    ) {
        NavDisplay(
            backStack = backStack,
            onBack = { nav.back() },
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<VehicleHome> { HomeScreen(nav) }
                entry<FusesHome> { FusesScreen(nav) }
                entry<ProblemsHome> { ProblemsScreen(nav) }
                entry<TestsHome> { TestsScreen(nav) }
                entry<PickMake> { PickMakeScreen(nav) }
                entry<PickModel> { PickModelScreen(nav, it.make) }
                entry<PickYear> { PickYearScreen(nav, it.optionKey) }
                entry<PickEngine> { PickEngineScreen(nav, it.vehicleId, it.year) }
                entry<Specs> { SpecsScreen(nav) }
                entry<Service> { ServiceScreen(nav) }
                entry<ProblemDetail> { ProblemDetailScreen(nav, it.id) }
                entry<ProcedureDetail> { ProcedureScreen(nav, it.id) }
                entry<Sources> { SourcesScreen(nav) }
            },
            modifier = Modifier,
        )
    }
}
