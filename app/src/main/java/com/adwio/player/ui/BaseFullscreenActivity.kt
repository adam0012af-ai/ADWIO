package com.adwio.player.ui

import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.adwio.player.data.LibrarySnapshotCache
import com.adwio.player.data.M3uCache
import com.adwio.player.data.M3uWarmup
import com.adwio.player.data.RemoteContentClient
import com.adwio.player.data.SessionStore
import com.adwio.player.data.TelemetryClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

abstract class BaseFullscreenActivity : AppCompatActivity() {
    private var heartbeatJob: Job? = null
    private var contentSyncJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        runCatching { applyFullscreen() }
    }

    override fun onStart() {
        super.onStart()
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        startHeartbeat()
        startManagedContentSync()
    }

    override fun onStop() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        contentSyncJob?.cancel()
        contentSyncJob = null
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            runCatching { applyFullscreen() }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        val session = SessionStore(this).load() ?: return
        heartbeatJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                TelemetryClient(this@BaseFullscreenActivity).heartbeat(session)
                delay(60_000L)
            }
        }
    }

    protected open fun onManagedContentChanged() = Unit

    private fun startManagedContentSync() {
        contentSyncJob?.cancel()
        contentSyncJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching {
                    val bootstrap = RemoteContentClient().bootstrap()
                    if (!bootstrap.configured ||
                        !bootstrap.enabled ||
                        bootstrap.authEnabled ||
                        bootstrap.playlistUrl.isBlank()
                    ) return@runCatching

                    val store = SessionStore(this@BaseFullscreenActivity)
                    val current = store.load() ?: return@runCatching
                    val currentUrl = current.server.baseUrl.trim()
                    val nextUrl = bootstrap.playlistUrl.trim()

                    if (currentUrl != nextUrl) {
                        val updated = current.copy(
                            server = current.server.copy(
                                id = "m3u",
                                name = "ADWIO",
                                baseUrl = nextUrl
                            ),
                            username = "",
                            password = "",
                            status = "Active",
                            displayName = "ADWIO"
                        )
                        store.save(updated)

                        M3uCache(this@BaseFullscreenActivity).clear()
                        LibrarySnapshotCache(this@BaseFullscreenActivity).clear()
                        M3uWarmup.start(this@BaseFullscreenActivity, nextUrl)

                        launch(Dispatchers.Main) {
                            onManagedContentChanged()
                        }
                    }
                }
                delay(5_000L)
            }
        }
    }

    private fun applyFullscreen() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val attrs = window.attributes
            attrs.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = attrs
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
    }
}
