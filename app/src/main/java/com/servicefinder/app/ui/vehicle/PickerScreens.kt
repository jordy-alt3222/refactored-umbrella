package com.servicefinder.app.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.clickable
import com.servicefinder.app.R
import com.servicefinder.app.data.Vehicle
import com.servicefinder.app.domain.ModelOption
import com.servicefinder.app.domain.title
import com.servicefinder.app.domain.toSelection
import com.servicefinder.app.ui.Navigator
import com.servicefinder.app.ui.PickEngine
import com.servicefinder.app.ui.PickModel
import com.servicefinder.app.ui.PickYear
import com.servicefinder.app.ui.components.AppIcon
import com.servicefinder.app.ui.components.LoadingState
import com.servicefinder.app.ui.components.MessageState
import com.servicefinder.app.ui.components.SectionTitle

/** Makes the app concentrates on; other makes in the catalogue are listed after them. */
private val EUROPEAN = setOf(
    "Alfa Romeo", "Audi", "BMW", "Citroën", "DS", "Fiat", "Jaguar", "Land Rover", "Mercedes-Benz", "MINI",
    "Peugeot", "Porsche", "Renault", "SEAT", "Škoda", "Volkswagen", "Volvo",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickerScaffold(title: String, nav: Navigator, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = nav::back) { AppIcon(R.drawable.ic_arrow_back, contentDescription = "Back") }
                },
            )
        },
        content = content,
    )
}

@Composable
fun PickMakeScreen(nav: Navigator) {
    val vm = vehicleViewModel()
    val options by vm.options.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }

    PickerScaffold("Choose make", nav) { padding ->
        val all = options ?: return@PickerScaffold LoadingState(Modifier.padding(padding))
        val makes = all.map { it.make }.distinct().filter { it.contains(query.trim(), ignoreCase = true) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search makes") },
                    leadingIcon = { AppIcon(R.drawable.ic_search, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (recents.isNotEmpty() && query.isBlank()) {
                item { SectionTitle("Recent", Modifier.padding(horizontal = 16.dp)) }
                items(recents, key = { "recent-" + it.hashCode() }) { r ->
                    ListItem(
                        headlineContent = { Text(r.title()) },
                        leadingContent = { AppIcon(R.drawable.ic_history, contentDescription = null) },
                        modifier = Modifier.clickable { vm.select(r) { nav.finishPicking() } },
                    )
                }
            }
            listOf("European" to makes.filter { it in EUROPEAN }, "Other makes" to makes.filterNot { it in EUROPEAN })
                .filter { it.second.isNotEmpty() }
                .forEach { (group, list) ->
                    item { SectionTitle(group, Modifier.padding(horizontal = 16.dp)) }
                    items(list, key = { it }) { make ->
                        val count = all.count { it.make == make }
                        ListItem(
                            headlineContent = { Text(make) },
                            supportingContent = { Text(if (count == 1) "1 model" else "$count models") },
                            trailingContent = { AppIcon(R.drawable.ic_chevron_right, contentDescription = null) },
                            modifier = Modifier.clickable { nav.go(PickModel(make)) },
                        )
                    }
                }
            if (makes.isEmpty()) item { Text("No make matches \"$query\".", Modifier.padding(16.dp)) }
        }
    }
}

@Composable
fun PickModelScreen(nav: Navigator, make: String) {
    val vm = vehicleViewModel()
    val options by vm.options.collectAsStateWithLifecycle()

    PickerScaffold(make, nav) { padding ->
        val list = options?.filter { it.make == make } ?: return@PickerScaffold LoadingState(Modifier.padding(padding))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            items(list, key = { it.key }) { o ->
                ListItem(
                    headlineContent = { Text(listOfNotNull(o.model, o.series).joinToString(" · ")) },
                    supportingContent = {
                        Text(listOfNotNull(o.yearsLabel, o.market).joinToString(" · "))
                    },
                    trailingContent = {
                        SuggestionChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(if (o.vehicleId != null) "Manual data" else "Problems only") },
                        )
                    },
                    modifier = Modifier.clickable { onOptionChosen(vm, nav, o) },
                )
                HorizontalDivider()
            }
        }
    }
}

private fun onOptionChosen(vm: VehicleViewModel, nav: Navigator, o: ModelOption) {
    if (o.yearFrom == o.yearTo) yearChosen(vm, nav, o, o.yearFrom) else nav.go(PickYear(o.key))
}

private fun yearChosen(vm: VehicleViewModel, nav: Navigator, o: ModelOption, year: Int) {
    if (o.vehicleId != null && o.engineIds.size > 1) nav.go(PickEngine(o.vehicleId, year))
    else vm.select(o.toSelection(year, o.engineIds.singleOrNull())) { nav.finishPicking() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickYearScreen(nav: Navigator, optionKey: String) {
    val vm = vehicleViewModel()
    val options by vm.options.collectAsStateWithLifecycle()
    val option = options?.firstOrNull { it.key == optionKey }

    PickerScaffold(option?.let { "${it.make} ${it.model}" } ?: "Choose year", nav) { padding ->
        if (options == null) return@PickerScaffold LoadingState(Modifier.padding(padding))
        if (option == null) {
            return@PickerScaffold MessageState(R.drawable.ic_warning, "Model not found",
                "This model is no longer in the catalogue.", Modifier.padding(padding))
        }
        Column(Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Model year", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                option.years.reversed().forEach { y ->
                    FilterChip(selected = false, onClick = { yearChosen(vm, nav, option, y) }, label = { Text("$y") })
                }
            }
        }
    }
}

@Composable
fun PickEngineScreen(nav: Navigator, vehicleId: String, year: Int) {
    val vm = vehicleViewModel()
    val vehicle by produceState<Vehicle?>(null, vehicleId) { value = vm.vehicle(vehicleId) }

    fun choose(v: Vehicle, engineId: String?) {
        vm.select(
            ModelOption(v.make, v.model, v.series, v.market, v.yearFrom, v.yearTo, v.id).toSelection(year, engineId),
        ) { nav.finishPicking() }
    }

    PickerScaffold("Engine", nav) { padding ->
        val v = vehicle ?: return@PickerScaffold LoadingState(Modifier.padding(padding))
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Text("$year ${v.make} ${v.model}", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp))
            }
            items(v.engines, key = { it.id }) { e ->
                ListItem(
                    headlineContent = { Text(e.name) },
                    supportingContent = {
                        listOfNotNull(e.code, e.fuel?.replaceFirstChar { it.uppercase() }).joinToString(" · ")
                            .takeIf { it.isNotEmpty() }?.let { Text(it) }
                    },
                    modifier = Modifier.clickable { choose(v, e.id) },
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Not sure – show all engines") },
                    modifier = Modifier.clickable { choose(v, null) },
                )
            }
        }
    }
}
