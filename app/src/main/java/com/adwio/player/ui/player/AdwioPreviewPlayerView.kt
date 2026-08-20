package com.adwio.player.ui.player

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.media3.ui.PlayerView
import com.adwio.player.data.model.MediaType

/**
 * Mini Live player used on the Library screen.
 * Tap it, press OK/Enter, or DPAD-center to open the currently playing channel fullscreen.
 */
class AdwioPreviewPlayerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : PlayerView(context, attrs, defStyleAttr) {

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { openFullscreen() }
    }

    override fun performClick(): Boolean {
        super.performClick()
        openFullscreen()
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER)
        ) {
            openFullscreen()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun openFullscreen() {
        if (PlaybackEngine.currentType != MediaType.LIVE) return
        val streamUrl = PlaybackEngine.currentUrl
        if (streamUrl.isBlank()) return

        context.startActivity(Intent(context, PlayerActivity::class.java).apply {
            putExtra("url", streamUrl)
            putExtra("title", PlaybackEngine.currentTitle)
            putExtra("id", PlaybackEngine.currentId)
            putExtra("type", MediaType.LIVE.name)
        })
    }
}
