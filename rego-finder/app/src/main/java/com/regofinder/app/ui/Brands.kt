package com.regofinder.app.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession
import androidx.core.net.toUri

/**
 * The official product finders. Penrite, Valvoline and Ryco license their vehicle-to-product data
 * and keep their rego lookups behind reCAPTCHA, so the app opens their own pages rather than
 * scraping them: the recommendations are always the manufacturer's current ones.
 */
enum class Brand(val title: String, val covers: String, val url: String) {
    PENRITE(
        title = "Penrite",
        covers = "Recommended oils and fluids, by rego or by vehicle",
        url = "https://penriteoil.com.au/rego-search",
    ),
    VALVOLINE(
        title = "Valvoline",
        covers = "Lube Guide: recommended oils and fluids by vehicle",
        url = "https://www.valvolineglobal.com/en-au/lube-guide/",
    ),
    RYCO(
        title = "Ryco",
        covers = "Filters by rego, VIN, vehicle or part number",
        url = "https://rycofilters.com.au/search/-/-",
    ),
    ;

    companion object {
        fun rycoPartUrl(partNumber: String): String =
            "https://rycofilters.com.au/part/" + partNumber.trim().lowercase().filter { it.isLetterOrDigit() }
    }
}

/**
 * Opens pages in a Custom Tab. Binding warms up the browser process ahead of time, and
 * [prefetch] asks it to pre-connect to and pre-load the brand finders once a vehicle has been
 * found, so tapping a finder shows the page with little or no wait.
 */
class BrowserLauncher(private val context: Context) {
    private var session: CustomTabsSession? = null
    private var pending: List<String>? = null
    private var bound = false

    private val connection = object : CustomTabsServiceConnection() {
        override fun onCustomTabsServiceConnected(name: ComponentName, client: CustomTabsClient) {
            client.warmup(0)
            session = client.newSession(null)
            pending?.let(::prefetch)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            session = null
        }
    }

    fun bind() {
        if (bound) return
        val packageName = CustomTabsClient.getPackageName(context, null) ?: return
        bound = CustomTabsClient.bindCustomTabsService(context, packageName, connection)
    }

    fun unbind() {
        if (!bound) return
        context.unbindService(connection)
        bound = false
        session = null
    }

    /** The first URL is the most likely to be opened; the rest are only pre-connected. */
    fun prefetch(urls: List<String>) {
        val current = session
        if (current == null || urls.isEmpty()) {
            pending = urls
            return
        }
        pending = null
        val others = urls.drop(1).map { Bundle().apply { putParcelable(CustomTabsService.KEY_URL, it.toUri()) } }
        current.mayLaunchUrl(urls.first().toUri(), null, others)
    }

    fun open(activityContext: Context, url: String) {
        val intent = CustomTabsIntent.Builder(session).setShowTitle(true).build()
        try {
            intent.launchUrl(activityContext, url.toUri())
        } catch (_: ActivityNotFoundException) {
            try {
                activityContext.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
            } catch (_: ActivityNotFoundException) {
                // No browser at all: nothing sensible to do.
            }
        }
    }
}
