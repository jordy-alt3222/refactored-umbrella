package com.serviceadvisor.app.ui.procedure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serviceadvisor.app.R
import com.serviceadvisor.app.data.Procedure
import com.serviceadvisor.app.data.isVerified
import com.serviceadvisor.app.data.title
import com.serviceadvisor.app.ui.AdvisorViewModel
import com.serviceadvisor.app.ui.Navigator
import com.serviceadvisor.app.ui.components.AppIcon
import com.serviceadvisor.app.ui.components.BackTopBar
import com.serviceadvisor.app.ui.components.MessageState
import com.serviceadvisor.app.ui.components.UnverifiedTag
import com.serviceadvisor.app.ui.observeDataset
import kotlinx.coroutines.launch

/** One page per step. A "before you start" page comes first when the procedure has one. */
private sealed interface Page {
    data class Before(val text: String) : Page
    data class Step(val number: Int, val text: String) : Page
}

private fun Procedure.pages(): List<Page> =
    listOfNotNull(before?.let(Page::Before)) + steps.mapIndexed { i, s -> Page.Step(i + 1, s) }

/**
 * Step-by-step procedure: one large step per page, swipe or use the big Back / Next buttons.
 * The screen stays on while it is open (Settings can turn that off), so it can be followed
 * from the driver's seat without touching the phone.
 */
@Composable
fun ProcedureScreen(vm: AdvisorViewModel, nav: Navigator, vehicleId: String, procedureId: String) {
    val dataset = vm.observeDataset() ?: return
    val settings by vm.settings.collectAsStateWithLifecycle()
    val vehicle = dataset.vehicles.firstOrNull { it.id == vehicleId }
    val procedure = dataset.procedures.firstOrNull { it.id == procedureId }
    if (vehicle == null || procedure == null) {
        Scaffold(topBar = { BackTopBar("Procedure", onBack = nav::back) }) { padding ->
            MessageState(R.drawable.ic_warning, "Procedure not found", "It is not in the current data file.", Modifier.padding(padding))
        }
        return
    }

    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    val pages = procedure.pages()
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    var showAll by rememberSaveable { mutableStateOf(false) }
    val verified = dataset.isVerified(vehicle)
    val kind = if (procedure.tpmsType != null) "TPMS reset" else "Service light reset"

    Scaffold(
        topBar = {
            BackTopBar(kind, onBack = nav::back, subtitle = vehicle.title) {
                IconButton(onClick = { showAll = !showAll }) {
                    AppIcon(
                        if (showAll) R.drawable.ic_arrow_forward else R.drawable.ic_list,
                        contentDescription = if (showAll) "One step at a time" else "Show all steps",
                    )
                }
            }
        },
        bottomBar = {
            if (!showAll) {
                val last = pager.currentPage == pages.lastIndex
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } },
                            enabled = pager.currentPage > 0,
                            modifier = Modifier.weight(1f).height(64.dp),
                        ) {
                            AppIcon(R.drawable.ic_arrow_back, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Back", style = MaterialTheme.typography.titleLarge)
                        }
                        Button(
                            onClick = {
                                if (last) nav.back() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                            },
                            modifier = Modifier.weight(1f).height(64.dp),
                        ) {
                            Text(if (last) "Done" else "Next", style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.width(8.dp))
                            AppIcon(if (last) R.drawable.ic_check else R.drawable.ic_arrow_forward, contentDescription = null)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        procedure.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (!verified) UnverifiedTag()
                }
                if (!showAll) {
                    LinearProgressIndicator(
                        progress = { (pager.currentPage + 1f) / pages.size },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
            }
            if (showAll) AllSteps(procedure) else HorizontalPager(pager, Modifier.weight(1f)) { index ->
                StepPage(pages[index], procedure, isLast = index == pages.lastIndex)
            }
        }
    }
}

@Composable
private fun StepPage(page: Page, procedure: Procedure, isLast: Boolean) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (page) {
            is Page.Before -> {
                Text(
                    "BEFORE YOU START",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(page.text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
            is Page.Step -> {
                Text(
                    "STEP ${page.number} OF ${procedure.steps.size}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(page.text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            }
        }
        if (isLast) procedure.notes?.let { NoteCard(it) }
    }
}

@Composable
private fun AllSteps(procedure: Procedure) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        procedure.before?.let { item { NoteCard("Before you start: $it") } }
        itemsIndexed(procedure.steps) { i, step ->
            Row {
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(40.dp),
                )
                Text(step, style = MaterialTheme.typography.titleLarge)
            }
        }
        procedure.notes?.let { item { NoteCard(it) } }
    }
}

@Composable
private fun NoteCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(Modifier.padding(16.dp)) {
            AppIcon(R.drawable.ic_info, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}
