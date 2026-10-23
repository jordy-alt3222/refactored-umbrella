package com.servicefinder.app.ui.sources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.servicefinder.app.R
import com.servicefinder.app.data.Source
import com.servicefinder.app.ui.Navigator
import com.servicefinder.app.ui.components.AppIcon
import com.servicefinder.app.ui.components.LocalSources
import com.servicefinder.app.ui.components.SectionTitle
import com.servicefinder.app.ui.components.SourceDialog
import com.servicefinder.app.ui.components.TextList

private val KIND_LABELS = mapOf(
    "owners_manual" to "Owner's manual",
    "service_manual" to "Service manual",
    "textbook" to "Textbook",
    "bulletin_db" to "Bulletin database",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(nav: Navigator) {
    val sources = LocalSources.current.values.sortedWith(compareBy({ it.kind != "textbook" }, { it.title }))
    var open by remember { mutableStateOf<Source?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About and sources") },
                navigationIcon = {
                    IconButton(onClick = nav::back) { AppIcon(R.drawable.ic_arrow_back, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            item {
                SectionTitle("How the data is kept", Modifier.padding(horizontal = 16.dp))
                TextList(
                    listOf(
                        "Every figure comes from the source shown under it, with the page it is on.",
                        "Values are never estimated. When a source does not give a figure, the app shows " +
                            "\"Not available\" and the reason.",
                        "Owner's manual figures are for the market the manual was written for. Check the vehicle's " +
                            "own handbook or labels if the market differs.",
                        "Everything is stored on the device and works offline.",
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                SectionTitle("Sources (${sources.size})", Modifier.padding(horizontal = 16.dp))
            }
            items(sources, key = { it.id }) { s ->
                ListItem(
                    overlineContent = { Text(KIND_LABELS[s.kind] ?: s.kind) },
                    headlineContent = { Text(s.title) },
                    supportingContent = {
                        Text(listOfNotNull(s.publisher, s.market, s.pages?.let { "$it pages" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall)
                    },
                    modifier = Modifier.clickable { open = s },
                )
                HorizontalDivider()
            }
        }
    }
    open?.let { SourceDialog(it, null, onDismiss = { open = null }) }
}
