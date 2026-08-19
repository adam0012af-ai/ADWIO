package com.adwio.player.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.adwio.player.data.M3uClient
import com.adwio.player.data.PlaylistStore
import com.adwio.player.data.SessionStore
import com.adwio.player.data.XtreamClient
import com.adwio.player.data.model.ServerHost
import com.adwio.player.data.model.Session
import com.adwio.player.databinding.ActivityLoginBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.home.HomeActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginActivity : BaseFullscreenActivity() {
    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
    }
    private lateinit var b: ActivityLoginBinding
    private val api = XtreamClient()
    private val m3u = M3uClient()
    private var passwordVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.sourceTypeGroup.setOnCheckedChangeListener { _, checked ->
            val isM3u = checked == b.m3uRadio.id
            b.xtreamFields.visibility = if (isM3u) View.GONE else View.VISIBLE
            b.m3uFields.visibility = if (isM3u) View.VISIBLE else View.GONE
            b.errorText.text = ""
        }
        b.passwordEye.setOnClickListener {
            passwordVisible = !passwordVisible
            b.passwordInput.inputType = if (passwordVisible) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            b.passwordInput.setSelection(b.passwordInput.text?.length ?: 0)
        }
        val editId = intent.getStringExtra(EXTRA_PROFILE_ID)
        if (!editId.isNullOrBlank()) {
            PlaylistStore(this).find(editId)?.let { profile ->
                b.playlistNameInput.setText(profile.name)
                if (profile.serverId == "m3u") {
                    b.m3uRadio.isChecked = true
                    b.m3uUrlInput.setText(profile.serverUrl)
                } else {
                    b.xtreamRadio.isChecked = true
                    b.serverUrlInput.setText(profile.serverUrl)
                    b.usernameInput.setText(profile.username)
                    b.passwordInput.setText(profile.password)
                }
                b.signInButton.text = "حفظ"
            }
        }

        b.signInButton.setOnClickListener { connect() }
        b.playlistNameInput.requestFocus()
    }

    private fun connect() {
        val name = b.playlistNameInput.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) { b.errorText.text = "Enter playlist name"; return }
        if (b.m3uRadio.isChecked) connectM3u(name) else connectXtream(name)
    }

    private fun connectXtream(name: String) {
        val server = b.serverUrlInput.text?.toString()?.trim().orEmpty()
        val username = b.usernameInput.text?.toString()?.trim().orEmpty()
        val password = b.passwordInput.text?.toString().orEmpty()
        if (server.isBlank() || username.isBlank() || password.isBlank()) { b.errorText.text = "Enter server URL, username and password"; return }
        setLoading(true)
        lifecycleScope.launch {
            val session = withContext(Dispatchers.IO) { api.authenticate(username, password, server) }
            setLoading(false)
            if (session == null) { b.errorText.text = "Unable to connect. Check playlist details."; return@launch }
            saveAndOpen(name, session)
        }
    }

    private fun connectM3u(name: String) {
        val url = b.m3uUrlInput.text?.toString()?.trim().orEmpty()
        if (url.isBlank()) { b.errorText.text = "Enter M3U URL"; return }
        setLoading(true)
        lifecycleScope.launch {
            val available = withContext(Dispatchers.IO) {
                runCatching {
                    if (!m3u.probe(url)) false
                    else m3u.load(url).isNotEmpty()
                }.getOrDefault(false)
            }
            setLoading(false)
            if (!available) {
                b.errorText.text = "لم يتم العثور على محتوى صالح في القائمة"
                return@launch
            }
            val host = runCatching { java.net.URI(url).host ?: "M3U" }.getOrDefault("M3U")
            getSharedPreferences("adwio_m3u", MODE_PRIVATE).edit()
                .putString("active_url", url)
                .putString("active_epg", b.epgUrlInput.text?.toString()?.trim().orEmpty())
                .apply()
            saveAndOpen(name, Session("", "", ServerHost("m3u", host, url), null, "Active"))
        }
    }

    private fun saveAndOpen(name: String, session: Session) {
        val namedSession = session.copy(displayName = name)
        SessionStore(this).save(namedSession)

        if (b.rememberMe.isChecked) {
            val store = PlaylistStore(this)
            val editId = intent.getStringExtra(EXTRA_PROFILE_ID)
            if (!editId.isNullOrBlank()) {
                val old = store.find(editId)
                if (old != null) {
                    store.put(old.copy(
                        name = name,
                        username = namedSession.username,
                        password = namedSession.password,
                        serverId = namedSession.server.id,
                        serverName = namedSession.server.name,
                        serverUrl = namedSession.server.baseUrl
                    ))
                } else {
                    store.add(name, namedSession)
                }
            } else {
                store.add(name, namedSession)
            }
        }

        startActivity(Intent(this, HomeActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        ))
    }

    private fun setLoading(v: Boolean) {
        b.progressBar.visibility = if (v) View.VISIBLE else View.GONE
        b.signInButton.isEnabled = !v
        b.passwordEye.isEnabled = !v
        b.errorText.text = if (v) "Connecting…" else ""
    }
}
