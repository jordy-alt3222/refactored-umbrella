package com.servicefinder.app.ui.fuses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.servicefinder.app.R
import com.servicefinder.app.ServiceFinderApp
import com.servicefinder.app.data.Fuse
import com.servicefinder.app.domain.title
import com.servicefinder.app.ui.Navigator
import com.servicefinder.app.ui.PickMake
import com.servicefinder.app.ui.components.AppIcon
import com.servicefinder.app.ui.components.Citation
import com.servicefinder.app.ui.components.LoadingState
import com.servicefinder.app.ui.components.MessageState
import com.servicefinder.app.ui.vehicle.PROBLEMS_ONLY_REASON
import com.servicefinder.app.ui.vehicle.rememberApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface FuseState {
    data object Loading : FuseState
    data object NoVehicle : FuseState
    data class NoData(val title: String, val reason: String) : FuseState
    data class Data(val title: String, val note: String?, val fuses: List<Fuse>, val total: Int) : FuseState
}

@OptIn(ExperimentalCoroutinesApi::class)
class FusesViewModel(app: ServiceFinderApp) : ViewModel() {
    val query = MutableStateFlow("")
    val box = MutableStateFlow<String?>(null)

    val state: StateFlow<FuseState> = app.selection.selection.flatMapLatest { sel ->
        val vehicle = sel?.vehicleId?.let { app.catalog.vehicle(it) }
        when {
            sel == null -> flowOf(FuseState.NoVehicle)
            vehicle == null -> flowOf(FuseState.NoData(sel.title(), PROBLEMS_ONLY_REASON))
            else -> combine(
                query.flatMapLatest { app.catalog.fuses(vehicle.id, it) },
                app.catalog.fuseCount(vehicle.id),
            ) { list, total ->
                // Fuses that belong to a particular engine are hidden for the other engines.
                val forEngine = list.filter { sel.engineId == null || it.engineIds == null || sel.engineId in it.engineIds }
                if (total == 0) {
                    FuseState.NoData(sel.title(), vehicle.gaps.firstOrNull { it.contains("fuse", true) }
                        ?: "The source manual has no fuse table for this vehicle.")
                } else {
                    FuseState.Data(sel.title(), vehicle.fuseNote, forEngine, total)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FuseState.Loading)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FusesScreen(nav: Navigator) {
    val app = rememberApp()
    val vm = viewModel { FusesViewModel(app) }
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val box by vm.box.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Fuses") }) }) { padding ->
        when (val s = state) {
            FuseState.Loading -> LoadingState(Modifier.padding(padding))
            FuseState.NoVehicle -> MessageState(R.drawable.ic_bolt, "Choose a vehicle",
                "Fuse tables are listed for the selected vehicle.", Modifier.padding(padding),
                "Choose vehicle") { nav.go(PickMake) }
            is FuseState.NoData -> MessageState(R.drawable.ic_bolt, "Fuses not available for the ${s.title}", s.reason,
                Modifier.padding(padding))
            is FuseState.Data -> {
                val boxes = s.fuses.map { it.box }.distinct()
                val shown = s.fuses.filter { box == null || it.box == box }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
                ) {
                    item {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Text(s.title, style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(
                                value = query,
                                onValueChange = { vm.query.value = it },
                                label = { Text("Fuse number, circuit or name") },
                                leadingIcon = { AppIcon(R.drawable.ic_search, contentDescription = null) },
                                trailingIcon = {
                                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) {
                                        AppIcon(R.drawable.ic_close, contentDescription = "Clear search")
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            )
                        }
                    }
                    if (boxes.size > 1) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                item { FilterChip(selected = box == null, onClick = { vm.box.value = null }, label = { Text("All boxes") }) }
                                items(boxes) { b ->
                                    FilterChip(selected = box == b, onClick = { vm.box.value = if (box == b) null else b },
                                        label = { Text(b, maxLines = 1) })
                                }
                            }
                        }
                    }
                    item {
                        Text(
                            "${shown.size} of ${s.total} fuses. Colour dots (5–15 A blade fuses) are a visual aid only: " +
                                "always confirm the rating printed on the fuse.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    if (shown.isEmpty()) {
                        item { Text("No fuse matches \"$query\".", Modifier.padding(16.dp)) }
                    }
                    shown.groupBy { it.box }.forEach { (boxName, fuses) ->
                        item(key = "h-$boxName") {
                            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Text(boxName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Citation(fuses.first().ref)
                                }
                            }
                        }
                        items(fuses) { FuseRow(it) }
                    }
                    s.note?.let { item { Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall) } }
                }
            }
        }
    }
}

@Composable
private fun FuseRow(f: Fuse) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(f.number ?: "–", style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 56.dp))
        Column(Modifier.weight(1f)) {
            val name = f.name
            val circuit = if (f.unused) "Not used" else f.circuit
            if (name != null) Text(name, style = MaterialTheme.typography.bodyLarge)
            if (circuit != null) Text(circuit, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (name == null && circuit == null) Text("Not available", style = MaterialTheme.typography.bodyMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            bladeColour(f.amps)?.let { c ->
                Box(
                    Modifier.size(14.dp).background(c, CircleShape)
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .semantics { contentDescription = "" },
                )
            }
            Text(f.rating ?: "–", style = MaterialTheme.typography.labelLarge)
        }
    }
    HorizontalDivider(Modifier.padding(start = 16.dp))
}

/**
 * Blade-fuse colours (ISO 8820) for 5–15 A only. From 20 A up, cartridge (J-case) and
 * maxi fuses share ratings with blade fuses but use different colours, and the manuals
 * do not always say which type is fitted, so those ratings get no colour.
 */
private fun bladeColour(amps: Double?): Color? = when (amps) {
    5.0 -> Color(0xFFD2B48C)
    7.5 -> Color(0xFF8B5A2B)
    10.0 -> Color(0xFFD32F2F)
    15.0 -> Color(0xFF1E63D6)
    else -> null
}
