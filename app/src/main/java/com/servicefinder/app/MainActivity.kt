package com.servicefinder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.servicefinder.app.data.ImportState
import com.servicefinder.app.ui.AppRoot
import com.servicefinder.app.ui.theme.ServiceFinderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as ServiceFinderApp
        // Keep the splash screen up while the catalogue is first imported (usually well under a second).
        installSplashScreen().setKeepOnScreenCondition { app.catalog.state.value is ImportState.Loading }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ServiceFinderTheme {
                AppRoot(app)
            }
        }
    }
}
