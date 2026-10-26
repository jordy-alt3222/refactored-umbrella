package com.servicefinder.app.ui.tests

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.servicefinder.app.R
import com.servicefinder.app.data.Procedure
import com.servicefinder.app.data.ProcedureStep
import com.servicefinder.app.ui.Navigator
import com.servicefinder.app.ui.ProcedureDetail
import com.servicefinder.app.ui.components.AppIcon
import com.servicefinder.app.ui.components.Citation
import com.servicefinder.app.ui.components.LoadingState
import com.servicefinder.app.ui.components.SectionTitle
import com.servicefinder.app.ui.components.TextList
import com.servicefinder.app.ui.vehicle.rememberApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestsScreen(nav: Navigator) {
    val app = rememberApp()
    val proceduresFlow = remember(app) { app.catalog.procedures() }
    val procedures by proceduresFlow.collectAsStateWithLifecycle(initialValue = null)

    Scaffold(topBar = { TopAppBar(title = { Text("Electrical tests") }) }) { padding ->
        val list = procedures ?: return@Scaffold LoadingState(Modifier.padding(padding))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            item {
                Text(
                    "General step-by-step tests for 12 V systems. The figures are textbook guide values; " +
                        "use the manufacturer's specification where it is available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            list.groupBy { it.system }.forEach { (system, procs) ->
                item(key = "h-$system") { SectionTitle(system, Modifier.padding(horizontal = 16.dp)) }
                items(procs, key = { it.id }) { p ->
                    ListItem(
                        headlineContent = { Text(p.title) },
                        supportingContent = { p.summary?.let { Text(it, maxLines = 2) } },
                        trailingContent = { Text("${p.steps.size} steps", style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.clickable { nav.go(ProcedureDetail(p.id)) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * A procedure as a walkthrough: an overview (tools and safety), then one step at a
 * time with its expected result, then how to read the results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProcedureScreen(nav: Navigator, id: String) {
    val app = rememberApp()
    val procedure by produceState<Procedure?>(null, id) { value = app.catalog.procedure(id) }
    // Page 0 is the overview, 1..n the steps, n+1 the results.
    var page by rememberSaveable(id) { mutableIntStateOf(0) }
    var showAll by rememberSaveable(id) { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(procedure?.title ?: "Test", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = nav::back) { AppIcon(R.drawable.ic_arrow_back, contentDescription = "Back") }
                },
                actions = {
                    TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Step by step" else "All steps") }
                },
            )
        },
    ) { padding ->
        val p = procedure ?: return@Scaffold LoadingState(Modifier.padding(padding))
        val last = p.steps.size + 1
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (showAll) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 760.dp)) {
                    Overview(p)
                    SectionTitle("Steps")
                    p.steps.forEachIndexed { i, s -> StepCard(i + 1, p.steps.size, s, Modifier.padding(bottom = 8.dp)) }
                    Results(p)
                }
                return@Column
            }
            LinearProgressIndicator(progress = { page / last.toFloat() }, modifier = Modifier.fillMaxWidth())
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val dir = if (targetState > initialState) 1 else -1
                    slideInHorizontally { it * dir } togetherWith slideOutHorizontally { -it * dir }
                },
                modifier = Modifier.weight(1f),
                label = "step",
            ) { current ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 760.dp)) {
                    when (current) {
                        0 -> Overview(p)
                        last -> Results(p)
                        else -> StepCard(current, p.steps.size, p.steps[current - 1])
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = { page-- }, enabled = page > 0, modifier = Modifier.weight(1f)) { Text("Previous") }
                Button(
                    onClick = { if (page < last) page++ else nav.back() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when (page) {
                            0 -> "Start"
                            last -> "Done"
                            last - 1 -> "Results"
                            else -> "Next step"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Overview(p: Procedure) {
    Text(p.title, style = MaterialTheme.typography.headlineSmall)
    p.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
    p.appliesTo?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp))
    }
    if (p.safety.isNotEmpty()) {
        SectionTitle("Safety")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppIcon(R.drawable.ic_warning, contentDescription = null)
                TextList(p.safety)
            }
        }
    }
    if (p.tools.isNotEmpty()) {
        SectionTitle("Equipment")
        TextList(p.tools)
    }
    Spacer(Modifier.padding(4.dp))
    Citation(p.ref)
}

@Composable
private fun StepCard(number: Int, total: Int, step: ProcedureStep, modifier: Modifier = Modifier) {
    Column(modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text("Step $number of $total", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(step.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
        step.detail?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp)) }
        step.expected?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(R.drawable.ic_check, contentDescription = null)
                    Column {
                        Text("Expected", style = MaterialTheme.typography.labelLarge)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Results(p: Procedure) {
    SectionTitle("Reading the results")
    if (p.results.isEmpty()) {
        Text("Compare each reading with the expected result for its step.", style = MaterialTheme.typography.bodyMedium)
    } else {
        TextList(p.results)
    }
    Spacer(Modifier.padding(4.dp))
    Citation(p.ref)
}
