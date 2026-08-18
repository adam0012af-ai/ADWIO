package com.adwio.player

import android.app.Application
import android.content.Intent
import com.adwio.player.ui.crash.CrashActivity
import kotlin.system.exitProcess

class AdwioApplication : Application() {
    override fun onCreate() {
        super.onCreate()

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
}
