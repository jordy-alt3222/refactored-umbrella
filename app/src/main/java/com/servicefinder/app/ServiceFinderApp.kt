package com.servicefinder.app

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.servicefinder.app.data.CatalogDatabase
import com.servicefinder.app.data.CatalogRepository
import com.servicefinder.app.data.SelectionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.selectionDataStore by preferencesDataStore(name = "selection")

/** Holds the app's single repository and preference store (manual dependency injection). */
class ServiceFinderApp : Application() {
    val appScope = CoroutineScope(SupervisorJob())

    val catalog: CatalogRepository by lazy {
        CatalogRepository(CatalogDatabase.create(this).dao()) {
            assets.open("catalog.json").use { it.readBytes() }
        }
    }

    val selection: SelectionStore by lazy { SelectionStore(selectionDataStore) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch { catalog.ensureImported() }
        // Read the preferences file while the catalogue check runs. DataStore keeps what it read,
        // so the quick menu has the selected vehicle as soon as the splash screen is dismissed.
        appScope.launch { runCatching { selection.selection.first() } }
    }
}
