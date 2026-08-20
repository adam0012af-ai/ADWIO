package com.adwio.player.ui.player

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.adwio.player.R

/**
 * Transparent right-side target in fullscreen Live.
 * Tapping / pressing OK on this area closes the channel browser overlay.
 */
class LiveOverlayDismissView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    init {
        isClickable = true
        isFocusable = true
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setOnClickListener { dismissOverlay() }
    }

    override fun performClick(): Boolean {
        super.performClick()
        dismissOverlay()
        return true
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN &&
            event.keyCode in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER)
        ) {
            dismissOverlay()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun dismissOverlay() {
        rootView.findViewById<View?>(R.id.liveBrowseOverlay)?.visibility = GONE
        rootView.findViewById<View?>(R.id.playerControls)?.visibility = VISIBLE
    }
}
