package com.ronglin.nameveil

import android.app.Application
import com.ronglin.nameveil.core.DEFAULT_WORKERS
import com.ronglin.nameveil.core.MAX_WORKERS
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class NameVeilApplication : Application() {
    val storage by lazy { AndroidStorageGateway(this) }
    private val prefs by lazy { getSharedPreferences("run_options", MODE_PRIVATE) }
    val controller by lazy {
        val saved = RunSettings(
            threadMode = ThreadMode.entries.find { it.name == prefs.getString("threads", null) } ?: ThreadMode.Auto,
            manualWorkers = prefs.getInt("workers", DEFAULT_WORKERS).coerceIn(1, MAX_WORKERS),
            includeSubdirectories = prefs.getBoolean("recursive", true),
            preserveExtension = prefs.getBoolean("keepExtension", false),
        )
        AppController(storage, initialSettings = saved) { settings ->
            prefs.edit().putString("threads", settings.threadMode.name).putInt("workers", settings.manualWorkers)
                .putBoolean("recursive", settings.includeSubdirectories).putBoolean("keepExtension", settings.preserveExtension)
                .apply()
        }
    }

    val executionScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
