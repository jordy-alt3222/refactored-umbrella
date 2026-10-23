package com.regofinder.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.regofinder.app.R
import com.regofinder.app.data.AuState
import com.regofinder.app.data.RegoRecord
import com.regofinder.app.data.Vehicle

private const val PLATEAPI_SIGNUP = "https://plateapi.com.au/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegoScreen(browser: BrowserLauncher, viewModel: RegoViewModel = viewModel(factory = RegoViewModel.Factory)) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showKeyDialog by rememberSaveable { mutableStateOf(false) }

    // Once a vehicle is on screen the next tap is almost always a finder, so pre-load them now.
    LaunchedEffect(ui.record) {
        if (ui.record != null) browser.prefetch(Brand.entries.map { it.url })
    }

    val openBrand: (String) -> Unit = { url ->
        ui.record?.let { copyRego(context, it.plate) }
        browser.open(context, url)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rego Finder") },
                actions = {
                    IconButton(onClick = { showKeyDialog = true }) {
                        Icon(painterResource(R.drawable.ic_key), contentDescription = "PlateAPI key")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "search") {
                SearchCard(
                    ui = ui,
                    onPlateChange = viewModel::onPlateChange,
                    onStateChange = viewModel::onStateChange,
                    onSearch = { viewModel.search() },
                )
            }
            ui.error?.let { error ->
                item(key = "error") {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            ui.record?.let { record ->
                item(key = "result") {
                    ResultCard(
                        record = record,
                        selected = ui.selected,
                        fromCache = ui.fromCache,
                        loading = ui.loading,
                        onSelect = viewModel::onSelectVehicle,
                        onRefresh = { viewModel.search(refresh = true) },
                    )
                }
            }
            item(key = "brands-header") {
                SectionHeader(
                    "Oils and filters",
                    ui.record?.let { "Opening a finder copies ${it.plate} so you can paste it into its rego search." },
                )
            }
            items(Brand.entries, key = { it.name }) { brand ->
                BrandCard(brand, onOpen = { openBrand(brand.url) })
            }
            item(key = "ryco-part") {
                RycoPartRow(onOpen = { part -> browser.open(context, Brand.rycoPartUrl(part)) })
            }
            if (ui.history.isNotEmpty()) {
                item(key = "history-header") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionHeader("Recent", null, Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearHistory) { Text("Clear") }
                    }
                }
                items(ui.history, key = { "h/${it.state}/${it.plate}" }) { record ->
                    HistoryRow(record, onClick = { viewModel.open(record) })
                }
            }
            item(key = "footer") {
                Text(
                    "Vehicle details from PlateAPI. Oil and filter recommendations open on the official " +
                        "Penrite, Valvoline and Ryco sites, so they are always the manufacturers' current ones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showKeyDialog) {
        ApiKeyDialog(
            current = ui.apiKey,
            onGetKey = { browser.open(context, PLATEAPI_SIGNUP) },
            onSave = { viewModel.saveApiKey(it); showKeyDialog = false },
            onDismiss = { showKeyDialog = false },
        )
    }
}

@Composable
private fun SearchCard(
    ui: UiState,
    onPlateChange: (String) -> Unit,
    onStateChange: (AuState) -> Unit,
    onSearch: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = ui.plate,
                onValueChange = onPlateChange,
                label = { Text("Rego") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AuState.entries.forEach { state ->
                    FilterChip(
                        selected = state == ui.state,
                        onClick = { onStateChange(state) },
                        label = { Text(state.name) },
                    )
                }
            }
            Button(onClick = onSearch, enabled = !ui.loading, modifier = Modifier.fillMaxWidth()) {
                if (ui.loading) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Search")
            }
        }
    }
}

@Composable
private fun ResultCard(
    record: RegoRecord,
    selected: Int,
    fromCache: Boolean,
    loading: Boolean,
    onSelect: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    val vehicle = record.vehicles.getOrElse(selected) { record.vehicles.first() }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${record.plate} · ${record.state.name}",
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefresh, enabled = !loading) {
                    Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Look up again")
                }
            }
            Text(title(vehicle), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Detail("Years", vehicle.yearRange)
            Detail("Body", vehicle.body)
            Detail("Engine", vehicle.engine)
            vehicle.detailedDescription?.let { Detail("Details", it) }
            Text(
                (if (fromCache) "Saved result, looked up " else "Looked up ") +
                    DateUtils.getRelativeTimeSpanString(record.fetchedAt),
                style = MaterialTheme.typography.bodySmall,
            )
            if (record.vehicles.size > 1) {
                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                Text(
                    "PlateAPI matched ${record.vehicles.size} variants. Pick the one that fits:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                record.vehicles.forEachIndexed { index, v ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(index) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = index == selected, onClick = { onSelect(index) })
                        Text(v.description ?: title(v), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun title(v: Vehicle): String =
    listOfNotNull(v.make, v.model).joinToString(" ").ifBlank { v.description ?: "Unknown vehicle" }

@Composable
private fun Detail(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BrandCard(brand: Brand, onOpen: () -> Unit) {
    OutlinedCard(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(brand.title, fontWeight = FontWeight.SemiBold) },
            supportingContent = { Text(brand.covers) },
            trailingContent = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
        )
    }
}

@Composable
private fun RycoPartRow(onOpen: (String) -> Unit) {
    var part by rememberSaveable { mutableStateOf("") }
    val submit = { if (part.isNotBlank()) onOpen(part) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = part,
            onValueChange = { part = it.uppercase().take(20) },
            label = { Text("Ryco part number, e.g. Z411") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = submit, enabled = part.isNotBlank()) {
            Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = "Open Ryco part")
        }
    }
}

@Composable
private fun HistoryRow(record: RegoRecord, onClick: () -> Unit) {
    val first = record.vehicles.first()
    ListItem(
        headlineContent = { Text("${record.plate} · ${record.state.name}", fontFamily = FontFamily.Monospace) },
        supportingContent = { Text(first.description ?: title(first)) },
        leadingContent = { Icon(painterResource(R.drawable.ic_history), contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ApiKeyDialog(current: String, onGetKey: () -> Unit, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var key by rememberSaveable { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PlateAPI key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Rego lookups use PlateAPI, a licensed Australian plate-to-vehicle service. " +
                        "The free plan covers 20 lookups a month; repeat searches are answered from this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it.trim() },
                    label = { Text("API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = onGetKey) { Text("Get a key at plateapi.com.au") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun copyRego(context: Context, plate: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Rego", plate))
    // Android 13+ shows its own confirmation when the clipboard changes.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, "$plate copied", Toast.LENGTH_SHORT).show()
    }
}
