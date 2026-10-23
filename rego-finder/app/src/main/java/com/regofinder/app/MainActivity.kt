package com.regofinder.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.regofinder.app.ui.BrowserLauncher
import com.regofinder.app.ui.RegoFinderTheme
import com.regofinder.app.ui.RegoScreen

class MainActivity : ComponentActivity() {
    private val browser by lazy { BrowserLauncher(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Bound for the activity's whole life, not just while started, so coming back from a
        // finder doesn't pay for re-binding and re-warming the browser.
        browser.bind()
        setContent {
            RegoFinderTheme {
                RegoScreen(browser)
            }
        }
    }

    override fun onDestroy() {
        browser.unbind()
        super.onDestroy()
    }
}
