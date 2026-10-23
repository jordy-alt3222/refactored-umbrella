package com.servicefinder.app.ui.problems

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.servicefinder.app.R
import com.servicefinder.app.ServiceFinderApp
import com.servicefinder.app.data.Problem
import com.servicefinder.app.data.Selection
import com.servicefinder.app.domain.title
import com.servicefinder.app.ui.Navigator
import com.servicefinder.app.ui.ProblemDetail
import com.servicefinder.app.ui.components.AppIcon
import com.servicefinder.app.ui.components.Citation
import com.servicefinder.app.ui.components.LoadingState
import com.servicefinder.app.ui.components.MessageState
import com.servicefinder.app.ui.components.SectionTitle
import com.servicefinder.app.ui.components.TextList
import com.servicefinder.app.ui.vehicle.rememberApp
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ProblemList(val selection: Selection?, val allMakes: Boolean, val problems: List<Problem>)

@OptIn(ExperimentalCoroutinesApi::class)
class ProblemsViewModel(app: ServiceFinderApp) : ViewModel() {
    val query = MutableStateFlow("")
    /** Browse every make instead of the selected vehicle. */
    val allMakes = MutableStateFlow(false)

    val list: StateFlow<ProblemList?> = combine(app.selection.selection, query, allMakes) { sel, q, all ->
        Triple(sel, q, all || sel == null)
    }.flatMapLatest { (sel, q, all) ->
        val flow = if (all) app.catalog.problems("", q) else app.catalog.problemsFor(sel!!.make, sel.model, sel.year, q)
        flow.map { ProblemList(sel, all, it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProblemsScreen(nav: Navigator) {
    val app = rememberApp()
    val vm = viewModel { ProblemsViewModel(app) }
    val list by vm.list.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Common problems") }) }) { padding ->
        val l = list ?: return@Scaffold LoadingState(Modifier.padding(padding))
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { vm.query.value = it },
                        label = { Text("Symptom, system, engine or NHTSA ID") },
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        l.selection?.let { sel ->
                            FilterChip(selected = !l.allMakes, onClick = { vm.allMakes.value = false }, label = { Text(sel.title()) })
                        }
                        FilterChip(selected = l.allMakes, onClick = { vm.allMakes.value = true }, label = { Text("All makes") })
                    }
                    Text(
                        "Restated from manufacturer bulletins filed with the US NHTSA. Check that the bulletin applies " +
                            "to the vehicle (market, engine, build date) before acting on it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            if (l.problems.isEmpty()) {
                item {
                    MessageState(
                        R.drawable.ic_build,
                        "No problems listed",
                        if (query.isNotEmpty()) "Nothing matches \"$query\"."
                        else "The catalogue has no manufacturer bulletins for this model and year.",
                    )
                }
            }
            items(l.problems, key = { it.id }) { p ->
                ListItem(
                    overlineContent = { Text(listOf(p.make, p.system).joinToString(" · ")) },
                    headlineContent = { Text(p.title) },
                    supportingContent = {
                        Text(
                            (if (p.engines.isNotEmpty()) p.engines.joinToString(", ") + " · " else "") +
                                p.applies.joinToString(", ") { "${it.model} ${it.yearFrom}–${it.yearTo}" },
                            maxLines = 2,
                        )
                    },
                    modifier = Modifier.clickable { nav.go(ProblemDetail(p.id)) },
                )
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProblemDetailScreen(nav: Navigator, id: String) {
    val app = rememberApp()
    val problem by produceState<Problem?>(null, id) { value = app.catalog.problem(id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(problem?.system ?: "Problem") },
                navigationIcon = {
                    IconButton(onClick = nav::back) { AppIcon(R.drawable.ic_arrow_back, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val p = problem ?: return@Scaffold LoadingState(Modifier.padding(padding))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 16.dp)
                .padding(bottom = 24.dp).widthIn(max = 760.dp),
        ) {
            Text(p.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                p.make + " " + p.applies.joinToString(", ") { "${it.model} ${it.yearFrom}–${it.yearTo}" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (p.engines.isNotEmpty()) {
                SectionTitle("Engines")
                Text(p.engines.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
            }
            SectionTitle("Symptoms")
            TextList(p.symptoms)
            SectionTitle("Diagnosis")
            TextList(p.diagnosis, numbered = true)
            p.remedy?.let {
                SectionTitle("Remedy")
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            p.note?.let {
                SectionTitle("Note")
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            SectionTitle("Bulletin summaries (NHTSA, verbatim)")
            p.bulletins.forEach { b ->
                OutlinedCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("NHTSA ID ${b.id}", style = MaterialTheme.typography.labelLarge)
                        Text(b.summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Citation(p.ref)
        }
    }
}
