package com.regofinder.app

import android.app.Application
import com.regofinder.app.data.PlateApi
import com.regofinder.app.data.RegoRepository
import com.regofinder.app.data.SettingsStore
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

class RegoFinderApp : Application() {
    // Built on first use (off the main thread, from the ViewModel) so app start does no extra work.
    val repository: RegoRepository by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
        RegoRepository(PlateApi(client), File(filesDir, "history.json"))
    }

    val settings: SettingsStore by lazy { SettingsStore(this, BuildConfig.DEFAULT_PLATEAPI_KEY) }
}
