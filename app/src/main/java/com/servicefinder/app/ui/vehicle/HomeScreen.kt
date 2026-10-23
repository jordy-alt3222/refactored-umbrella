package com.servicefinder.app.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.servicefinder.app.R
import com.servicefinder.app.data.Selection
import com.servicefinder.app.domain.formatKm
import com.servicefinder.app.domain.planService
import com.servicefinder.app.domain.title
import com.servicefinder.app.ui.FusesHome
import com.servicefinder.app.ui.Navigator
import com.servicefinder.app.ui.PickMake
import com.servicefinder.app.ui.ProblemsHome
import com.servicefinder.app.ui.Service
import com.servicefinder.app.ui.Sources
import com.servicefinder.app.ui.Specs
import com.servicefinder.app.ui.components.AppIcon
import com.servicefinder.app.ui.components.Citation
import com.servicefinder.app.ui.components.InfoCard
import com.servicefinder.app.ui.components.LoadingState
import com.servicefinder.app.ui.components.MessageState
import com.servicefinder.app.ui.components.NotAvailable
import com.servicefinder.app.ui.components.TextList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(nav: Navigator) {
    val vm = vehicleViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val odometer by vm.odometerKm.collectAsStateWithLifecycle()
    val scroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("Service Finder") },
                actions = {
                    IconButton(onClick = { nav.go(Sources) }) {
                        AppIcon(R.drawable.ic_info, contentDescription = "About and sources")
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        when (val s = state) {
            VehicleState.Loading -> LoadingState(Modifier.padding(padding))
            VehicleState.None -> NoVehicle(recents, onPick = { nav.go(PickMake) }, onRecent = vm::select,
                modifier = Modifier.padding(padding))
            is VehicleState.Selected -> QuickMenu(s.current, odometer, nav, PaddingValues(
                top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp,
                start = 16.dp, end = 16.dp,
            ))
        }
    }
}

@Composable
private fun NoVehicle(recents: List<Selection>, onPick: () -> Unit, onRecent: (Selection) -> Unit, modifier: Modifier) {
    Column(modifier) {
        MessageState(
            icon = R.drawable.ic_car,
            title = "Choose a vehicle",
            body = "Pick the make, model, year and engine to see oil capacity, tyre pressures, torques, " +
                "service items, fuses and known problems.",
            actionLabel = "Choose vehicle",
            onAction = onPick,
            modifier = Modifier.weight(1f),
        )
        if (recents.isNotEmpty()) RecentRow(recents, onRecent, Modifier.padding(16.dp))
    }
}

@Composable
private fun RecentRow(recents: List<Selection>, onRecent: (Selection) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Recent", style = MaterialTheme.typography.labelLarge)
        recents.forEach { r ->
            AssistChip(
                onClick = { onRecent(r) },
                label = { Text(r.title()) },
                leadingIcon = { AppIcon(R.drawable.ic_history, contentDescription = null) },
            )
        }
    }
}

@Composable
private fun QuickMenu(current: CurrentVehicle, odometer: Int?, nav: Navigator, contentPadding: PaddingValues) {
    val v = current.vehicle
    val engine = current.engine
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 300.dp),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { VehicleHeader(current, onChange = { nav.go(PickMake) }) }

        if (v == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                InfoCard("Specifications not available", R.drawable.ic_info) {
                    Text(
                        "$PROBLEMS_ONLY_REASON Oil capacity, tyre pressures, torques, service schedule and fuses " +
                            "are shown as \"Not available\" rather than estimated.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            item { ProblemsTile(current, nav) }
            return@LazyVerticalGrid
        }

        item {
            InfoCard("Engine oil", R.drawable.ic_drop, onClick = { nav.go(Specs) }) {
                val rows = v.oilFor(engine).filter { it.litres != null }
                if (rows.isEmpty()) {
                    NotAvailable(v.gapReason("oil", fallback = "The source manual does not give an oil capacity for this vehicle."))
                } else {
                    rows.take(3).forEach { o ->
                        Text(
                            buildString {
                                append(o.litres!!.litres())
                                if (engine == null) append(" · ${v.engineName(o.engineId)}")
                            },
                            style = MaterialTheme.typography.titleLarge,
                        )
                        o.basis?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    if (rows.size > 3) Text("+${rows.size - 3} more", style = MaterialTheme.typography.bodySmall)
                    Citation(rows.first().ref)
                }
            }
        }

        item {
            InfoCard("Tyre pressures", R.drawable.ic_speed, onClick = { nav.go(Specs) }) {
                val rows = v.tyresFor(engine)
                if (rows.isEmpty()) {
                    NotAvailable(v.gapReason("tyre", "tire", fallback = "The source manual does not give tyre pressures for this vehicle."))
                } else {
                    val first = rows.first()
                    Text(first.valueText(), style = MaterialTheme.typography.titleLarge)
                    Text(
                        listOfNotNull(first.sizes.joinToString(", ").ifEmpty { null }, first.condition).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (rows.size > 1) Text("${rows.size - 1} more sizes or loads", style = MaterialTheme.typography.bodySmall)
                    Citation(first.ref)
                }
                v.tpms?.let { t ->
                    Text(t.label(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Citation(t.ref)
                } ?: Text("TPMS: not available", style = MaterialTheme.typography.bodyMedium)
            }
        }

        item {
            InfoCard("Wheel nut torque", R.drawable.ic_torque, onClick = { nav.go(Specs) }) {
                val rows = v.wheelTorques.filter { it.valueText() != null }
                if (rows.isEmpty()) {
                    NotAvailable(v.gapReason("wheel", "torque", fallback = "The source manual does not give a wheel nut torque."))
                } else {
                    rows.forEach { w ->
                        Text(w.valueText()!!, style = MaterialTheme.typography.titleLarge)
                        w.variant?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    Citation(rows.first().ref)
                }
            }
        }

        item {
            InfoCard("Brake caliper torque", R.drawable.ic_torque) {
                val rows = v.caliperTorque.filter { it.nm != null }
                if (rows.isEmpty()) {
                    NotAvailable(v.gapReason("caliper", fallback = CALIPER_REASON))
                } else {
                    rows.forEach { c -> Text("${c.axle} ${c.fastener}: ${c.nm} N·m", style = MaterialTheme.typography.titleMedium) }
                    Citation(rows.first().ref)
                }
            }
        }

        item {
            InfoCard("Service", R.drawable.ic_schedule, onClick = { nav.go(Service) }) {
                val schedule = v.schedulesFor(engine).firstOrNull()
                when {
                    schedule == null -> NotAvailable(
                        v.gapReason("service", "schedule", fallback = "The source manual has no maintenance schedule for this vehicle."),
                    )
                    odometer == null -> Text("Enter the odometer reading to see what is due.", style = MaterialTheme.typography.bodyMedium)
                    else -> planService(schedule, engine?.id, odometer)?.let { plan ->
                        Text("${formatKm(plan.serviceKm)} service", style = MaterialTheme.typography.titleLarge)
                        Text("${plan.dueAtService.size} items due · odometer ${formatKm(odometer)}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item { ProblemsTile(current, nav) }

        item {
            InfoCard("Fuses", R.drawable.ic_bolt, onClick = { nav.tab(FusesHome) }) {
                Text("Search by fuse number or circuit", style = MaterialTheme.typography.bodyMedium)
            }
        }

        if (v.gaps.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                InfoCard("Not in the catalogue for this vehicle", R.drawable.ic_warning) { TextList(v.gaps) }
            }
        }
    }
}

@Composable
private fun ProblemsTile(current: CurrentVehicle, nav: Navigator) {
    InfoCard("Common problems", R.drawable.ic_build, onClick = { nav.tab(ProblemsHome) }) {
        if (current.problemCount == 0) {
            Text("No manufacturer bulletins in the catalogue for this model and year.",
                style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("${current.problemCount}", style = MaterialTheme.typography.titleLarge)
            Text("From manufacturer bulletins filed with NHTSA", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VehicleHeader(current: CurrentVehicle, onChange: () -> Unit) {
    val v = current.vehicle
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(current.selection.title(), style = MaterialTheme.typography.headlineSmall)
                Text(
                    listOfNotNull(v?.series, current.engine?.name, v?.market).joinToString(" · ")
                        .ifEmpty { "Common problems only" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(onClick = onChange) {
                AppIcon(R.drawable.ic_swap, contentDescription = null)
                Text("Change", Modifier.padding(start = 8.dp))
            }
        }
        v?.summary?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}
