package com.adwio.player.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object M3uWarmup {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap.newKeySet<String>()

    fun start(context: Context, url: String) {
        if (!running.add(url)) return
        val app = context.applicationContext
        scope.launch {
            try {
                M3uCache(app).warm(url)
            } finally {
                running.remove(url)
            }
        }
    }

    fun isRunning(url: String): Boolean = running.contains(url)
}
