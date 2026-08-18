package com.adwio.player.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.adwio.player.data.PlaylistStore
import com.adwio.player.data.SessionStore
import com.adwio.player.data.XtreamClient
import com.adwio.player.databinding.ActivityLoginBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.home.HomeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityLoginBinding
    private val api = XtreamClient()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.signInButton.setOnClickListener { login() }
        b.usernameInput.requestFocus()
    }

    private fun login() {
        val username = b.usernameInput.text?.toString()?.trim().orEmpty()
        val password = b.passwordInput.text?.toString().orEmpty()
        val playlistName = b.playlistNameInput.text?.toString()?.trim().orEmpty()

        if (username.isBlank() || password.isBlank()) {
            b.errorText.text = "Enter username and password"
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { api.authenticate(username, password) }
            setLoading(false)

            if (session == null) {
                b.errorText.text = "Account not found on available servers"
            } else {
                SessionStore(this@LoginActivity).save(session)
                PlaylistStore(this@LoginActivity).add(playlistName, session)

                startActivity(
                    Intent(this@LoginActivity, HomeActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    )
                )
            }
        }
    }

    private fun setLoading(v: Boolean) {
        b.progressBar.visibility = if (v) View.VISIBLE else View.GONE
        b.signInButton.isEnabled = !v
        b.errorText.text = if (v) "Searching available servers…" else ""
    }
}
