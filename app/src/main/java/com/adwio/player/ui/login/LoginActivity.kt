package com.adwio.player.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.InputType
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
    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.passwordEye.setOnClickListener {
            passwordVisible = !passwordVisible
            b.passwordInput.inputType = if (passwordVisible) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
            b.passwordInput.setSelection(b.passwordInput.text?.length ?: 0)
        }
        b.signInButton.setOnClickListener { login() }
        b.playlistNameInput.requestFocus()
    }

    private fun login() {
        val playlistName = b.playlistNameInput.text?.toString()?.trim().orEmpty()
        val username = b.usernameInput.text?.toString()?.trim().orEmpty()
        val password = b.passwordInput.text?.toString().orEmpty()

        when {
            playlistName.isBlank() -> b.errorText.text = getString(com.adwio.player.R.string.enter_playlist_name)
            username.isBlank() || password.isBlank() -> b.errorText.text = getString(com.adwio.player.R.string.enter_credentials)
            else -> authenticate(playlistName, username, password)
        }
    }

    private fun authenticate(playlistName: String, username: String, password: String) {
        setLoading(true)
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { api.authenticate(username, password) }
            setLoading(false)
            if (session == null) {
                b.errorText.text = getString(com.adwio.player.R.string.connection_failed)
                return@launch
            }
            SessionStore(this@LoginActivity).save(session)
            if (b.rememberMe.isChecked) PlaylistStore(this@LoginActivity).add(playlistName, session)
            startActivity(Intent(this@LoginActivity, HomeActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            ))
        }
    }

    private fun setLoading(v: Boolean) {
        b.progressBar.visibility = if (v) View.VISIBLE else View.GONE
        b.signInButton.isEnabled = !v
        b.passwordEye.isEnabled = !v
        b.errorText.text = if (v) getString(com.adwio.player.R.string.connecting) else ""
    }
}
