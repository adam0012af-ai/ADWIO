package com.adwio.player

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.adwio.player.data.model.MediaType
import com.adwio.player.ui.crash.CrashActivity
import com.adwio.player.ui.library.LibraryActivity
import com.adwio.player.ui.player.PlaybackEngine
import com.adwio.player.ui.player.PlaybackService
import com.adwio.player.ui.player.PlayerActivity
import kotlin.math.max
import kotlin.system.exitProcess

class AdwioApplication : Application() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var resumedActivities = 0

    private val stopWhenBackground = Runnable {
        if (resumedActivities != 0) return@Runnable

        // HOME / PiP is not an app exit for LIVE.
        // Keep the same ExoPlayer session alive so audio/video continue and
        // LibraryActivity can re-attach it when the user returns.
        val activeLive =
            PlaybackEngine.player != null &&
            PlaybackEngine.currentType == MediaType.LIVE &&
            PlaybackEngine.currentUrl.isNotBlank()

        if (activeLive) {
            PlaybackEngine.player?.let { player ->
                player.playWhenReady = true
                player.play()
            }
            return@Runnable
        }

        stopPlaybackCompletely()
    }

    override fun onCreate() {
        super.onCreate()

        // Non-Live playback is stopped when the app leaves the foreground.
        // Active LIVE playback is intentionally preserved for Android PiP/Home
        // and is stopped explicitly when the user actually exits Live.
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                resumedActivities++
                mainHandler.removeCallbacks(stopWhenBackground)

                val keepCurrentPlayback = when (activity) {
                    is PlayerActivity -> true
                    is LibraryActivity -> {
                        val section = activity.intent.getStringExtra(LibraryActivity.EXTRA_TYPE)
                        PlaybackEngine.currentType == MediaType.LIVE &&
                            section.equals(MediaType.LIVE.name, ignoreCase = true)
                    }
                    else -> false
                }

                if (PlaybackEngine.player != null && !keepCurrentPlayback) {
                    stopPlaybackCompletely()
                }
            }

            override fun onActivityPaused(activity: Activity) {
                resumedActivities = max(0, resumedActivities - 1)
                mainHandler.removeCallbacks(stopWhenBackground)
                mainHandler.postDelayed(stopWhenBackground, 450L)
            }

            override fun onActivityCreated(activity: Activity, state: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val report = throwable.stackTraceToString()
                getSharedPreferences("adwio_crash", MODE_PRIVATE)
                    .edit()
                    .putString("last_crash", report)
                    .commit()

                startActivity(Intent(this, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("crash", report)
                })
                Thread.sleep(300)
            } catch (_: Throwable) {
            }

            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                android.os.Process.killProcess(android.os.Process.myPid())
                exitProcess(10)
            }
        }
    }

    private fun stopPlaybackCompletely() {
        PlaybackEngine.stopAndRelease()
        runCatching { stopService(Intent(this, PlaybackService::class.java)) }
    }
}
