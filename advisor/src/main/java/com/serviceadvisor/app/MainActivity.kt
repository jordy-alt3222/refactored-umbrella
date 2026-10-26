package com.serviceadvisor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.serviceadvisor.app.data.DataState
import com.serviceadvisor.app.data.Settings
import com.serviceadvisor.app.ui.AppRoot
import com.serviceadvisor.app.ui.theme.ServiceAdvisorTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as ServiceAdvisorApp
        // Keep the splash up while the data file is parsed (a few milliseconds).
        installSplashScreen().setKeepOnScreenCondition { app.data.state.value is DataState.Loading }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
            ServiceAdvisorTheme(settings) {
                AppRoot(app)
            }
        }
    }
}
