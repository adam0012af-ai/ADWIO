package com.adwio.player.ui.profile

import android.os.Bundle
import com.adwio.player.data.SessionStore
import com.adwio.player.databinding.ActivityProfileBinding
import com.adwio.player.ui.BaseFullscreenActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(b.root)

        val session = SessionStore(this).load()
        val isM3u = session?.server?.id?.startsWith("m3u", ignoreCase = true) == true

        b.clientNameText.text =
            session?.displayName?.takeIf { it.isNotBlank() } ?: "ADWIO User"

        b.usernameText.text =
            session?.username?.takeIf { it.isNotBlank() } ?: "M3U User"

        b.statusText.text =
            session?.status?.takeIf { it.isNotBlank() } ?: "Active"

        // m3u_xtream uses Xtream API internally but must remain M3U to the user.
        b.sourceTypeText.text = if (isM3u) "M3U" else "Xtream Codes"

        b.expiryText.text = formatEpoch(session?.expiresAt)
        b.createdText.text = formatEpoch(session?.createdAt)
        b.activeConnectionsText.text =
            session?.activeConnections?.takeIf { it.isNotBlank() } ?: "—"
        b.maxConnectionsText.text =
            session?.maxConnections?.takeIf { it.isNotBlank() } ?: "—"

        b.backButton.setOnClickListener { finish() }
    }

    private fun formatEpoch(raw: String?): String {
        if (raw.isNullOrBlank()) return "—"
        val seconds = raw.toLongOrNull() ?: return raw

        return runCatching {
            SimpleDateFormat(
                "dd MMM yyyy",
                Locale.getDefault()
            ).format(Date(seconds * 1000L))
        }.getOrDefault(raw)
    }
}
