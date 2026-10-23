package com.servicefinder.app.ui.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.servicefinder.app.R
import com.servicefinder.app.ui.Navigator
import com.servicefinder.app.ui.components.AppIcon
import com.servicefinder.app.ui.components.Citation
import com.servicefinder.app.ui.components.LoadingState
import com.servicefinder.app.ui.components.MessageState
import com.servicefinder.app.ui.components.NotAvailable
import com.servicefinder.app.ui.components.SectionTitle

/** Every figure for the selected vehicle, each with its own citation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpecsScreen(nav: Navigator) {
    val vm = vehicleViewModel()
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Specifications") },
                navigationIcon = {
                    IconButton(onClick = nav::back) { AppIcon(R.drawable.ic_arrow_back, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val current = (state as? VehicleState.Selected)?.current
        val v = current?.vehicle
        when {
            state is VehicleState.Loading -> LoadingState(Modifier.padding(padding))
            v == null -> MessageState(R.drawable.ic_info, "No specifications", PROBLEMS_ONLY_REASON, Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp,
                    top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                horizontalAlignment = Alignment.Start,
            ) {
                item {
                    Text("${current.selection.year} ${v.make} ${v.model}" + (v.series?.let { " · $it" } ?: ""),
                        style = MaterialTheme.typography.titleMedium)
                    current.engine?.let { Text("Engine: ${it.name}", style = MaterialTheme.typography.bodyMedium) }
                }

                item { SectionTitle("Engine oil capacity") }
                val oil = v.oil
                if (oil.isEmpty()) item { NotAvailable(v.gapReason("oil", fallback = "Not given in the source manual.")) }
                items(oil) { o ->
                    SpecRow(
                        title = v.engineName(o.engineId),
                        value = o.litres?.litres(),
                        lines = listOfNotNull(o.basis, o.note),
                        highlight = o.engineId == current.engine?.id,
                    ) { Citation(o.ref) }
                }

                item { SectionTitle("Tyre pressures (cold)") }
                if (v.tyres.isEmpty()) item { NotAvailable(v.gapReason("tyre", "tire", fallback = "Not given in the source manual.")) }
                items(v.tyres) { t ->
                    SpecRow(
                        title = t.sizes.joinToString(", ").ifEmpty { if (t.spare) "Spare" else "All sizes" },
                        value = t.valueText(),
                        lines = listOfNotNull(t.condition, t.note, t.engineIds?.joinToString { v.engineName(it) }),
                    ) { Citation(t.ref) }
                }

                item { SectionTitle("Tyre pressure monitoring") }
                item {
                    val t = v.tpms
                    if (t == null) NotAvailable(v.gapReason("tpms", fallback = "Not stated in the source manual."))
                    else SpecRow(title = t.label(), value = null, lines = listOfNotNull(t.text)) { Citation(t.ref) }
                }

                item { SectionTitle("Wheel nut torque") }
                if (v.wheelTorques.isEmpty()) item { NotAvailable(v.gapReason("wheel", "torque", fallback = "Not given in the source manual.")) }
                items(v.wheelTorques) { w ->
                    SpecRow(title = w.variant ?: "Wheel nuts / bolts", value = w.valueText(),
                        lines = listOfNotNull(w.text, w.note)) { Citation(w.ref) }
                }

                item { SectionTitle("Brake caliper torque") }
                if (v.caliperTorque.isEmpty()) item { NotAvailable(v.gapReason("caliper", fallback = CALIPER_REASON)) }
                items(v.caliperTorque) { c ->
                    SpecRow(title = "${c.axle} – ${c.fastener}", value = c.nm?.let { "$it N·m" },
                        lines = listOfNotNull(c.variant, c.text, c.note)) { Citation(c.ref) }
                }
            }
        }
    }
}

@Composable
private fun SpecRow(
    title: String,
    value: String?,
    lines: List<String>,
    highlight: Boolean = false,
    citation: @Composable () -> Unit,
) {
    Column(Modifier.padding(vertical = 8.dp).widthIn(max = 720.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal)
        if (value != null) Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        citation()
    }
    HorizontalDivider()
}
