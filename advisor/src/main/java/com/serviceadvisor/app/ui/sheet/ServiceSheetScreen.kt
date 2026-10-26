package com.serviceadvisor.app.ui.sheet

import android.content.ClipData
import android.content.Intent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serviceadvisor.app.R
import com.serviceadvisor.app.data.Dataset
import com.serviceadvisor.app.data.Procedure
import com.serviceadvisor.app.data.Vehicle
import com.serviceadvisor.app.data.isVerified
import com.serviceadvisor.app.data.litres
import com.serviceadvisor.app.data.subtitle
import com.serviceadvisor.app.data.title
import com.serviceadvisor.app.ui.AdvisorViewModel
import com.serviceadvisor.app.ui.Navigator
import com.serviceadvisor.app.ui.Steps
import com.serviceadvisor.app.ui.components.AppIcon
import com.serviceadvisor.app.ui.components.BackTopBar
import com.serviceadvisor.app.ui.components.MessageState
import com.serviceadvisor.app.ui.components.UnverifiedBanner
import com.serviceadvisor.app.ui.components.UnverifiedTag
import com.serviceadvisor.app.ui.observeDataset
import kotlinx.coroutines.launch

/** Where to confirm values: the makers' own vehicle lookups. */
const val RYCO_LOOKUP = "https://www.ryco.com.au/"
const val PENRITE_LOOKUP = "https://penriteoil.com.au/"

/**
 * The service sheet: the four things an advisor needs for one vehicle. Phones show one column in
 * priority order (oil, filter, service reset, TPMS); tablets and landscape show two.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceSheetScreen(vm: AdvisorViewModel, nav: Navigator, vehicleId: String) {
    val dataset = vm.observeDataset() ?: return
    val vehicle = dataset.vehicles.firstOrNull { it.id == vehicleId }
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    if (vehicle == null) {
        Scaffold(topBar = { BackTopBar("Vehicle", onBack = nav::back) }) { padding ->
            MessageState(
                R.drawable.ic_warning,
                "Vehicle not found",
                "It is not in the current data file. It may have been removed by an import.",
                Modifier.padding(padding),
            )
        }
        return
    }
    LaunchedEffect(vehicle.id) { vm.opened(vehicle) }
    val verified = dataset.isVerified(vehicle)
    val service = dataset.procedures.firstOrNull { it.id == vehicle.serviceResetId }
    val tpms = vehicle.tpmsResetId?.let { id -> dataset.procedures.firstOrNull { it.id == id } }
    val isFavourite = favourites.any { it.id == vehicle.id }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            BackTopBar(vehicle.title, onBack = nav::back, subtitle = vehicle.subtitle, scrollBehavior = scrollBehavior) {
                IconButton(onClick = { vm.toggleFavourite(vehicle) }) {
                    AppIcon(
                        if (isFavourite) R.drawable.ic_star else R.drawable.ic_star_border,
                        contentDescription = if (isFavourite) "Remove from saved" else "Save vehicle",
                    )
                }
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Service sheet: ${vehicle.title}")
                        putExtra(Intent.EXTRA_TEXT, shareText(dataset, vehicle, service, tpms))
                    }
                    context.startActivity(Intent.createChooser(send, "Share service sheet"))
                }) { AppIcon(R.drawable.ic_share, contentDescription = "Share service sheet") }
            }
        },
    ) { padding ->
        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Adaptive(minSize = 340.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp,
            modifier = Modifier.padding(padding),
        ) {
            if (!verified) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    val uri = LocalUriHandler.current
                    UnverifiedBanner(
                        "Sample data. Confirm in Ryco, Penrite or Valvoline before working on the car.",
                    ) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { uri.openUri(RYCO_LOOKUP) }) { Text("Open Ryco") }
                            TextButton(onClick = { uri.openUri(PENRITE_LOOKUP) }) { Text("Open Penrite") }
                        }
                    }
                }
            } else {
                dataset.info.verifiedBy?.let { by ->
                    item(span = StaggeredGridItemSpan.FullLine) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Checked by $by") },
                            leadingIcon = { AppIcon(R.drawable.ic_check, contentDescription = null) },
                        )
                    }
                }
            }
            item { OilCard(vehicle, verified) }
            item { FilterCard(vehicle, verified) }
            item {
                ProcedureCard(
                    icon = R.drawable.ic_build,
                    label = "SERVICE LIGHT RESET",
                    procedure = service,
                    verified = verified,
                    onStart = { nav.go(Steps(vehicle.id, vehicle.serviceResetId)) },
                )
            }
            // TPMS is hidden entirely when the vehicle has none.
            if (vehicle.tpmsResetId != null) {
                item {
                    ProcedureCard(
                        icon = R.drawable.ic_tyre,
                        label = "TPMS RESET",
                        procedure = tpms,
                        verified = verified,
                        onStart = { nav.go(Steps(vehicle.id, vehicle.tpmsResetId)) },
                    )
                }
            }
            vehicle.notes?.let { notes ->
                item(span = StaggeredGridItemSpan.FullLine) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                        Row(Modifier.padding(16.dp)) {
                            AppIcon(R.drawable.ic_info, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Text(notes, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ValueCard(
    @DrawableRes icon: Int,
    label: String,
    verified: Boolean,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(icon, contentDescription = null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (!verified) UnverifiedTag()
            }
            content()
        }
    }
}

@Composable
private fun OilCard(vehicle: Vehicle, verified: Boolean) {
    val oil = vehicle.oil
    ValueCard(R.drawable.ic_drop, "ENGINE OIL", verified) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(oil.capacityLitres.litres(), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text(
                oil.capacityNote ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        HorizontalDivider()
        Text(oil.grade, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text(oil.spec, style = MaterialTheme.typography.titleLarge)
        oil.gradeNote?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilterCard(vehicle: Vehicle, verified: Boolean) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    ValueCard(R.drawable.ic_filter, "RYCO OIL FILTER", verified) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                vehicle.oilFilter.ryco,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.weight(1f),
            )
            FilledTonalButton(onClick = {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("Ryco filter", vehicle.oilFilter.ryco)))
                    copied = true
                }
            }) {
                AppIcon(if (copied) R.drawable.ic_check else R.drawable.ic_copy, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (copied) "Copied" else "Copy")
            }
        }
        Text(vehicle.oilFilter.type.label, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun ProcedureCard(
    @DrawableRes icon: Int,
    label: String,
    procedure: Procedure?,
    verified: Boolean,
    onStart: () -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ValueCard(icon, label, verified) {
        if (procedure == null) {
            Text("Procedure missing from the data file.", style = MaterialTheme.typography.bodyLarge)
            return@ValueCard
        }
        Text(procedure.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        procedure.tpmsType?.let { Text(it.label, style = MaterialTheme.typography.bodyLarge) }
        Text(
            "${procedure.steps.size} steps",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
            Button(onClick = onStart, modifier = Modifier.weight(1f)) {
                Text("Step by step", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                AppIcon(R.drawable.ic_arrow_forward, contentDescription = null)
            }
            OutlinedButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Hide" else "All steps")
            }
        }
        if (expanded) {
            procedure.before?.let {
                Text("Before you start: $it", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            procedure.steps.forEachIndexed { i, step ->
                Row(Modifier.padding(top = 4.dp)) {
                    Text("${i + 1}.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(32.dp))
                    Text(step, style = MaterialTheme.typography.bodyLarge)
                }
            }
            procedure.notes?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Plain-text sheet for sharing to a job card, chat or email. Carries the verification status. */
private fun shareText(dataset: Dataset, v: Vehicle, service: Procedure?, tpms: Procedure?): String = buildString {
    appendLine("${v.title} — ${v.subtitle}")
    if (!dataset.isVerified(v)) appendLine("⚠ UNVERIFIED SAMPLE DATA – confirm with Ryco / Penrite / Valvoline before use.")
    appendLine()
    appendLine("Engine oil: ${v.oil.capacityLitres.litres()}${v.oil.capacityNote?.let { " ($it)" } ?: ""}")
    appendLine("Oil grade: ${v.oil.grade} — ${v.oil.spec}")
    appendLine("Ryco oil filter: ${v.oilFilter.ryco} (${v.oilFilter.type.label})")
    for ((label, p) in listOf("Service light reset" to service, "TPMS reset" to tpms)) {
        if (p == null) continue
        appendLine()
        appendLine("$label — ${p.title}")
        p.before?.let { appendLine("Before: $it") }
        p.steps.forEachIndexed { i, s -> appendLine("${i + 1}. $s") }
        p.notes?.let { appendLine("Note: $it") }
    }
}
