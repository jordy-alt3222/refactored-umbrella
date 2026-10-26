package com.serviceadvisor.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.serviceadvisor.app.R
import com.serviceadvisor.app.ServiceAdvisorApp
import com.serviceadvisor.app.data.DataState
import com.serviceadvisor.app.ui.components.AppIcon
import com.serviceadvisor.app.ui.components.LoadingState
import com.serviceadvisor.app.ui.components.MessageState
import com.serviceadvisor.app.ui.lookup.LookupScreen
import com.serviceadvisor.app.ui.lookup.MakeScreen
import com.serviceadvisor.app.ui.lookup.ModelScreen
import com.serviceadvisor.app.ui.procedure.ProcedureScreen
import com.serviceadvisor.app.ui.saved.SavedScreen
import com.serviceadvisor.app.ui.settings.SettingsScreen
import com.serviceadvisor.app.ui.sheet.ServiceSheetScreen
import kotlinx.serialization.Serializable

// ---- Destinations -------------------------------------------------------------

@Serializable data object LookupHome : NavKey
@Serializable data object SavedHome : NavKey
@Serializable data object SettingsHome : NavKey
@Serializable data class MakeModels(val make: String) : NavKey
@Serializable data class ModelVariants(val make: String, val model: String) : NavKey
@Serializable data class Sheet(val vehicleId: String) : NavKey
@Serializable data class Steps(val vehicleId: String, val procedureId: String) : NavKey

private enum class Tab(val key: NavKey, val label: String, @DrawableRes val icon: Int) {
    Lookup(LookupHome, "Lookup", R.drawable.ic_search),
    Saved(SavedHome, "Saved", R.drawable.ic_star),
    Settings(SettingsHome, "Settings", R.drawable.ic_settings),
}

/** Navigation actions shared by the screens. */
class Navigator(private val backStack: NavBackStack<NavKey>) {
    fun go(key: NavKey) { backStack.add(key) }
    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    /** Switches tab. Other tabs sit above Lookup, so Back from them returns to the search. */
    fun tab(key: NavKey) {
        backStack.clear()
        backStack.add(LookupHome)
        if (key != LookupHome) backStack.add(key)
    }
}

@Composable
fun AppRoot(app: ServiceAdvisorApp) {
    val vm: AdvisorViewModel = viewModel { AdvisorViewModel(app) }
    val state by vm.data.collectAsStateWithLifecycle()
    when (val s = state) {
        DataState.Loading -> LoadingState(label = "Loading service data")
        is DataState.Failed -> MessageState(
            icon = R.drawable.ic_warning,
            title = "Service data could not be loaded",
            message = s.message,
            actionLabel = "Use built-in sample",
            onAction = { vm.restoreSample() },
        )
        is DataState.Ready -> Main(vm)
    }
}

@Composable
private fun Main(vm: AdvisorViewModel) {
    val backStack = rememberNavBackStack(LookupHome)
    val nav = Navigator(backStack)
    // The tab is the highest top-level destination on the stack.
    val currentTab = Tab.entries.lastOrNull { tab -> backStack.any { it == tab.key } } ?: Tab.Lookup

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
                entry<LookupHome> { LookupScreen(vm, nav) }
                entry<SavedHome> { SavedScreen(vm, nav) }
                entry<SettingsHome> { SettingsScreen(vm) }
                entry<MakeModels> { MakeScreen(vm, nav, it.make) }
                entry<ModelVariants> { ModelScreen(vm, nav, it.make, it.model) }
                entry<Sheet> { ServiceSheetScreen(vm, nav, it.vehicleId) }
                entry<Steps> { ProcedureScreen(vm, nav, it.vehicleId, it.procedureId) }
            },
        )
    }
}
