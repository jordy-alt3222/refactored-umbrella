package com.serviceadvisor.app.ui.saved

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serviceadvisor.app.R
import com.serviceadvisor.app.ui.AdvisorViewModel
import com.serviceadvisor.app.ui.Navigator
import com.serviceadvisor.app.ui.Sheet
import com.serviceadvisor.app.ui.components.MessageState
import com.serviceadvisor.app.ui.components.SectionHeading
import com.serviceadvisor.app.ui.components.VehicleCard

/** Saved tab: starred vehicles (the workshop's regulars) and the last vehicles looked up. */
@Composable
fun SavedScreen(vm: AdvisorViewModel, nav: Navigator) {
    val favourites by vm.favourites.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Saved vehicles", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
        item { SectionHeading("Starred") }
        if (favourites.isEmpty()) {
            item {
                MessageState(
                    R.drawable.ic_star_border,
                    "No starred vehicles",
                    "Tap the star on a service sheet to keep the vehicles you see most often here.",
                )
            }
        }
        items(favourites, key = { "fav-" + it.id }) { v -> VehicleCard(v, onClick = { nav.go(Sheet(v.id)) }) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionHeading("Recent", Modifier.weight(1f))
                if (recent.isNotEmpty()) TextButton(onClick = { vm.clearRecent() }) { Text("Clear") }
            }
        }
        if (recent.isEmpty()) {
            item { MessageState(R.drawable.ic_history, "Nothing yet", "Vehicles you open appear here.") }
        }
        items(recent, key = { "recent-" + it.id }) { v -> VehicleCard(v, onClick = { nav.go(Sheet(v.id)) }) }
    }
}
