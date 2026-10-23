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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.servicefinder.app.R
import com.servicefinder.app.domain.PlannedItem
import com.servicefinder.app.domain.actionLabel
import com.servicefinder.app.domain.formatKm
import com.servicefinder.app.domain.planService
import com.servicefinder.app.ui.Navigator
import com.servicefinder.app.ui.components.AppIcon
import com.servicefinder.app.ui.components.Citation
import com.servicefinder.app.ui.components.LoadingState
import com.servicefinder.app.ui.components.MessageState
import com.servicefinder.app.ui.components.SectionTitle

/** Works out the items due at the next scheduled service from the odometer reading. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ServiceScreen(nav: Navigator) {
    val vm = vehicleViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val saved by vm.odometerKm.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var scheduleIndex by rememberSaveable { mutableIntStateOf(0) }
    val focus = LocalFocusManager.current

    LaunchedEffect(saved) { if (input.isEmpty() && saved != null) input = saved.toString() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Service due") },
                navigationIcon = {
                    IconButton(onClick = nav::back) { AppIcon(R.drawable.ic_arrow_back, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val current = (state as? VehicleState.Selected)?.current
        val v = current?.vehicle
        val schedules = v?.schedulesFor(current.engine).orEmpty()
        when {
            state is VehicleState.Loading -> return@Scaffold LoadingState(Modifier.padding(padding))
            v == null || schedules.isEmpty() -> return@Scaffold MessageState(
                R.drawable.ic_schedule, "No maintenance schedule",
                v.gapReason("service", "schedule", fallback = "The source manual has no maintenance schedule for this vehicle."),
                Modifier.padding(padding),
            )
        }
        val schedule = schedules[scheduleIndex.coerceIn(schedules.indices)]
        val km = input.filter(Char::isDigit).toIntOrNull()
        val plan = km?.let { planService(schedule, current!!.engine?.id, it) }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                OutlinedTextField(
                    value = input,
                    onValueChange = { new ->
                        input = new.filter(Char::isDigit).take(7)
                        vm.setOdometer(input.toIntOrNull())
                    },
                    label = { Text("Odometer (km)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            if (schedules.size > 1) {
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        schedules.forEachIndexed { i, s ->
                            FilterChip(selected = i == scheduleIndex, onClick = { scheduleIndex = i }, label = { Text(s.title) })
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(top = 8.dp)) {
                    Text(schedule.title, style = MaterialTheme.typography.titleSmall)
                    schedule.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        "Time limits also apply (\"whichever comes first\"); check the months shown for each item.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Citation(schedule.ref)
                }
            }
            if (plan == null) {
                item {
                    Text("Enter the odometer reading to see which items are due.",
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp))
                }
            } else {
                item { SectionTitle("Due at the ${formatKm(plan.serviceKm)} service (${plan.dueAtService.size})") }
                items(plan.dueAtService) { PlannedRow(it, showKm = false) }
                if (plan.later.isNotEmpty()) {
                    item { SectionTitle("Later") }
                    items(plan.later) { PlannedRow(it, showKm = true) }
                }
                if (plan.other.isNotEmpty()) {
                    item { SectionTitle("Time-based and other items") }
                    items(plan.other) { PlannedRow(it, showKm = false) }
                }
            }
            if (schedule.severe.isNotEmpty()) {
                item { SectionTitle("Severe operating conditions") }
                items(schedule.severe) { s ->
                    Column(Modifier.padding(vertical = 6.dp)) {
                        Text(listOfNotNull(s.name, s.qualifier).joinToString(" – "), style = MaterialTheme.typography.bodyLarge)
                        Text("${actionLabel(s.action)} ${s.interval}".trim(), style = MaterialTheme.typography.bodyMedium)
                    }
                    HorizontalDivider()
                }
                schedule.severeRef?.let { item { Citation(it) } }
            }
        }
    }
}

@Composable
private fun PlannedRow(item: PlannedItem, showKm: Boolean) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(
            listOfNotNull(
                actionLabel(item.action).takeIf { it.isNotEmpty() && !showKm },
                item.name,
            ).joinToString(": "),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
        item.qualifier?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        if (showKm && item.dueKm != null) {
            Text("${actionLabel(item.action)} at ${formatKm(item.dueKm)}".trim(),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Text(item.interval, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}
