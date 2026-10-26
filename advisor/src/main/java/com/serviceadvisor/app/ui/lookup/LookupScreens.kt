package com.serviceadvisor.app.ui.lookup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serviceadvisor.app.R
import com.serviceadvisor.app.data.Vehicle
import com.serviceadvisor.app.data.years
import com.serviceadvisor.app.ui.AdvisorViewModel
import com.serviceadvisor.app.ui.MakeModels
import com.serviceadvisor.app.ui.ModelVariants
import com.serviceadvisor.app.ui.Navigator
import com.serviceadvisor.app.ui.SavedHome
import com.serviceadvisor.app.ui.SettingsHome
import com.serviceadvisor.app.ui.Sheet
import com.serviceadvisor.app.ui.components.AppIcon
import com.serviceadvisor.app.ui.components.BackTopBar
import com.serviceadvisor.app.ui.components.MessageState
import com.serviceadvisor.app.ui.components.SectionHeading
import com.serviceadvisor.app.ui.components.UnverifiedBanner
import com.serviceadvisor.app.ui.components.VehicleCard
import com.serviceadvisor.app.ui.observeDataset

/** Lookup tab: one search box for everything, with browse-by-make and recent vehicles beneath it. */
@Composable
fun LookupScreen(vm: AdvisorViewModel, nav: Navigator) {
    val dataset = vm.observeDataset() ?: return
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val makes = dataset.vehicles.groupBy { it.make }.toSortedMap()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        fullWidth {
            Text(
                "Find a vehicle",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        fullWidth {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.titleLarge,
                placeholder = { Text("Model, engine, year or Ryco no.", maxLines = 1) },
                leadingIcon = { AppIcon(R.drawable.ic_search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { vm.query.value = "" }) {
                            AppIcon(R.drawable.ic_close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    // A single match opens straight away: the fastest path at the counter.
                    results.singleOrNull()?.let { nav.go(Sheet(it.id)) }
                }),
            )
        }
        if (!dataset.info.verified) {
            fullWidth {
                UnverifiedBanner(
                    "This app is running on built-in sample data. Values are for demonstration and may be " +
                        "wrong. Confirm in the Ryco, Penrite or Valvoline lookup before use.",
                ) {
                    TextButton(onClick = { nav.tab(SettingsHome) }) { Text("Load workshop data") }
                }
            }
        }

        if (query.isNotBlank()) {
            fullWidth { SectionHeading(if (results.size == 1) "1 match" else "${results.size} matches") }
            if (results.isEmpty()) {
                fullWidth {
                    MessageState(
                        icon = R.drawable.ic_search,
                        title = "No vehicle found",
                        message = "Try fewer words, just the model (\"hilux\"), an engine code (\"1gd\") or a year.",
                    )
                }
            }
            items(results, key = { it.id }, span = { GridItemSpan(maxLineSpan) }) { v ->
                VehicleCard(v, onClick = { nav.go(Sheet(v.id)) })
            }
        } else {
            if (recent.isNotEmpty()) {
                fullWidth {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeading("Recent", Modifier.weight(1f))
                        TextButton(onClick = { nav.tab(SavedHome) }) { Text("See all") }
                    }
                }
                items(recent.take(3), key = { "recent-" + it.id }, span = { GridItemSpan(maxLineSpan) }) { v ->
                    VehicleCard(v, onClick = { nav.go(Sheet(v.id)) })
                }
            }
            fullWidth { SectionHeading("Browse by make") }
            items(makes.keys.toList(), key = { "make-$it" }) { make ->
                val models = makes.getValue(make).map { it.model }.distinct()
                Card(
                    onClick = { nav.go(MakeModels(make)) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                ) {
                    Column(Modifier.padding(16.dp).heightIn(min = 64.dp), verticalArrangement = Arrangement.Center) {
                        Text(make, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (models.size == 1) "1 model" else "${models.size} models",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

/** Models for one make. A model with a single variant opens its service sheet directly. */
@Composable
fun MakeScreen(vm: AdvisorViewModel, nav: Navigator, make: String) {
    val dataset = vm.observeDataset() ?: return
    val models = dataset.vehicles.filter { it.make == make }.groupBy { it.model }.toSortedMap()
    Scaffold(topBar = { BackTopBar(make, onBack = nav::back) }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            if (models.isEmpty()) item { MessageState(R.drawable.ic_car, "No models", "This make is not in the data file.") }
            items(models.keys.toList(), key = { it }) { model ->
                val variants = models.getValue(model)
                Card(onClick = {
                    nav.go(variants.singleOrNull()?.let { Sheet(it.id) } ?: ModelVariants(make, model))
                }) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(model, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                variantSummary(variants),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AppIcon(R.drawable.ic_chevron_right, contentDescription = null)
                    }
                }
            }
        }
    }
}

private fun variantSummary(variants: List<Vehicle>): String {
    val from = variants.minOf { it.yearFrom }
    val to = if (variants.any { it.yearTo == null }) null else variants.maxOf { it.yearTo!! }
    val count = if (variants.size == 1) variants.single().engine else "${variants.size} engines"
    return "$count · $from–${to ?: "on"}"
}

/** Engine and series variants of one model. */
@Composable
fun ModelScreen(vm: AdvisorViewModel, nav: Navigator, make: String, model: String) {
    val dataset = vm.observeDataset() ?: return
    val variants = dataset.vehicles.filter { it.make == make && it.model == model }
        .sortedWith(compareBy({ -it.yearFrom }, { it.engine }))
    Scaffold(topBar = { BackTopBar("$make $model", onBack = nav::back, subtitle = "Choose the engine") }) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding),
        ) {
            items(variants, key = { it.id }) { v ->
                Card(onClick = { nav.go(Sheet(v.id)) }) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(v.engine, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(v.engineCode, v.series, v.years).joinToString(" · "),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        AppIcon(R.drawable.ic_chevron_right, contentDescription = null)
                    }
                }
            }
        }
    }
}
