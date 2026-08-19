package com.adwio.player.ui.playlist

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.adwio.player.data.PlaylistStore
import com.adwio.player.data.SessionStore
import com.adwio.player.databinding.ActivityUsersBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.home.HomeActivity
import com.adwio.player.ui.login.LoginActivity

class UsersActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityUsersBinding
    private lateinit var store: PlaylistStore
    private lateinit var adapter: PlaylistAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityUsersBinding.inflate(layoutInflater)
        setContentView(b.root)

        store = PlaylistStore(this)
        adapter = PlaylistAdapter(
            onOpen = { profile ->
                SessionStore(this).save(store.toSession(profile).copy(displayName = profile.name))
                store.put(profile)
                startActivity(Intent(this, HomeActivity::class.java).addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                ))
            },
            onManage = { profile ->
                AlertDialog.Builder(this)
                    .setTitle(profile.name)
                    .setItems(arrayOf("تعديل", "حذف")) { _, which ->
                        when (which) {
                            0 -> startActivity(
                                Intent(this, LoginActivity::class.java)
                                    .putExtra(LoginActivity.EXTRA_PROFILE_ID, profile.id)
                            )
                            1 -> AlertDialog.Builder(this)
                                .setTitle("حذف المستخدم؟")
                                .setMessage(profile.name)
                                .setPositiveButton("حذف") { _, _ ->
                                    store.remove(profile.id)
                                    refresh()
                                }
                                .setNegativeButton("إلغاء", null)
                                .show()
                        }
                    }.show()
            }
        )

        b.usersRecycler.layoutManager = LinearLayoutManager(this)
        b.usersRecycler.adapter = adapter
        b.backButton.setOnClickListener { finish() }
        b.addUserButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        adapter.submit(store.list())
    }
}
