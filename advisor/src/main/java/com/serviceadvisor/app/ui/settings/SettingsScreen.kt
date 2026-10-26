package com.serviceadvisor.app.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serviceadvisor.app.R
import com.serviceadvisor.app.data.DataSource
import com.serviceadvisor.app.data.TextSize
import com.serviceadvisor.app.data.ThemeMode
import com.serviceadvisor.app.ui.AdvisorViewModel
import com.serviceadvisor.app.ui.components.AppIcon
import com.serviceadvisor.app.ui.components.InfoCard
import com.serviceadvisor.app.ui.components.SectionHeading
import com.serviceadvisor.app.ui.components.UnverifiedBanner
import com.serviceadvisor.app.ui.observeDataset
import kotlinx.coroutines.launch

/** Settings tab: display options and the service data file (status, import, restore, template). */
@Composable
fun SettingsScreen(vm: AdvisorViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val dataset = vm.observeDataset() ?: return
    val source = vm.source
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<Pair<String, String>?>(null) }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val error = vm.import(uri)
            message = if (error == null) "Data imported" to "The service sheets now use the imported file."
            else "File not imported" to error
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(vm.sampleJson().encodeToByteArray()) }
            }.isSuccess
            message = if (ok) "Template saved" to "Edit the values, set \"verified\" to true for records you have checked, then import it."
            else "Template not saved" to "The file could not be written."
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)

        SectionHeading("Display")
        Text("Theme", style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = settings.theme == mode,
                    onClick = { vm.setTheme(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                ) { Text(mode.label) }
            }
        }
        Text("Text size", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TextSize.entries.forEachIndexed { i, size ->
                SegmentedButton(
                    selected = settings.textSize == size,
                    onClick = { vm.setTextSize(size) },
                    shape = SegmentedButtonDefaults.itemShape(i, TextSize.entries.size),
                ) { Text(size.label) }
            }
        }
        val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        SwitchRow(
            title = "Dynamic colour",
            detail = if (dynamicAvailable) "Match the colours of your wallpaper (Material You)." else "Needs Android 12 or later.",
            checked = settings.dynamicColor && dynamicAvailable,
            enabled = dynamicAvailable,
            onChange = vm::setDynamicColor,
        )
        SwitchRow(
            title = "Keep screen on during procedures",
            detail = "The step-by-step screen stays on while you work in the car.",
            checked = settings.keepScreenOn,
            onChange = vm::setKeepScreenOn,
        )

        SectionHeading("Service data")
        InfoCard {
            Text(dataset.info.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                when (source) {
                    DataSource.Imported -> "Imported workshop file"
                    else -> "Built into the app"
                },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${dataset.vehicles.size} vehicles · ${dataset.procedures.size} procedures" +
                    (dataset.info.updated?.let { " · updated $it" } ?: ""),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (dataset.info.verified) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(R.drawable.ic_check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Marked as checked" + (dataset.info.verifiedBy?.let { " by $it" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
                val unchecked = dataset.vehicles.count { !it.verified }
                if (unchecked > 0) {
                    Text(
                        "$unchecked vehicle records are not marked as checked and show a warning.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            dataset.info.notes?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        if (!dataset.info.verified) {
            UnverifiedBanner(
                "This data has not been checked against Ryco, Penrite, Valvoline or manufacturer information. " +
                    "Every service sheet shows a warning until a checked file is imported.",
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                AppIcon(R.drawable.ic_upload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import data file")
            }
            OutlinedButton(onClick = { exporter.launch("service-advisor-template.json") }) { Text("Save template") }
            if (source == DataSource.Imported) {
                OutlinedButton(onClick = { vm.restoreSample() }) {
                    AppIcon(R.drawable.ic_restore, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Use built-in sample")
                }
            }
        }
        Text(
            "A data file is JSON in the same format as the template. A record shows as checked only when the " +
                "file sets \"verified\": true in its info section and on that vehicle.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionHeading("About")
        Text(
            "Service Advisor 1.0. Brings oil capacity and grade, Ryco oil filter, service light reset and TPMS " +
                "reset into one screen. Not affiliated with Ryco, Penrite or Valvoline. Always follow the vehicle " +
                "maker's information.",
            style = MaterialTheme.typography.bodyLarge,
        )
    }

    message?.let { (title, text) ->
        AlertDialog(
            onDismissRequest = { message = null },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
            title = { Text(title) },
            text = { Text(text) },
        )
    }
}

@Composable
private fun SwitchRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
