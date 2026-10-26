package com.serviceadvisor.app

import android.app.Application
import com.serviceadvisor.app.data.ServiceDataRepository
import com.serviceadvisor.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Owns the app's two singletons. Small enough not to need a DI framework. */
class ServiceAdvisorApp : Application() {
    val appScope = CoroutineScope(SupervisorJob())
    lateinit var data: ServiceDataRepository private set
    lateinit var settings: SettingsStore private set

    override fun onCreate() {
        super.onCreate()
        data = ServiceDataRepository(this)
        settings = SettingsStore(this)
        appScope.launch { data.load() }
    }
}
