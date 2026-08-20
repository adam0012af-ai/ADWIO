package com.adwio.player.ui.player

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.media3.ui.PlayerView
import com.adwio.player.data.model.MediaType

class AdwioPreviewPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {

    private var lastOpenAt = 0L

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { openFullscreenOnce() }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
        ) {
            openFullscreenOnce()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun openFullscreenOnce() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastOpenAt < 700L) return
        lastOpenAt = now

        if (PlaybackEngine.currentType != MediaType.LIVE || PlaybackEngine.currentUrl.isBlank()) return
        context.startActivity(Intent(context, PlayerActivity::class.java).apply {
            putExtra("url", PlaybackEngine.currentUrl)
            putExtra("title", PlaybackEngine.currentTitle)
            putExtra("id", PlaybackEngine.currentId)
            putExtra("type", MediaType.LIVE.name)
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
    }
}
