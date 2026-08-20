package com.adwio.player.ui.sports

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.adwio.player.BuildConfig
import com.adwio.player.databinding.ActivitySportsBinding
import com.adwio.player.ui.BaseFullscreenActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SportsActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivitySportsBinding
    private val api = SportsClient()
    private lateinit var adapter: SportsAdapter
    private var allMatches = emptyList<SportsMatch>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySportsBinding.inflate(layoutInflater)
        setContentView(b.root)

        adapter = SportsAdapter(SportsFavorites(this), ::remind)
        b.matchesRecycler.layoutManager = LinearLayoutManager(this)
        b.matchesRecycler.adapter = adapter

        b.backButton.setOnClickListener { finish() }
        b.todayButton.setOnClickListener { showDay(0) }
        b.tomorrowButton.setOnClickListener { showDay(1) }
        b.allButton.setOnClickListener { adapter.submit(allMatches) }
        b.refreshButton.setOnClickListener { load() }

        b.todayButton.requestFocus()
        load()
    }

    private fun load() {
        if (BuildConfig.FOOTBALL_DATA_TOKEN.isBlank()) {
            b.statusText.visibility = View.VISIBLE
            b.statusText.text = "يلزم إضافة FOOTBALL_DATA_TOKEN إلى GitHub Secrets"
            adapter.submit(emptyList())
            return
        }

        b.statusText.visibility = View.VISIBLE
        b.statusText.text = "جاري تحديث مواعيد المباريات…"
        b.refreshButton.isEnabled = false

        val from = dateOffset(0)
        val to = dateOffset(7)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { api.loadMatches(from, to) }
            allMatches = result
            b.refreshButton.isEnabled = true
            b.statusText.text = if (result.isEmpty()) "لا توجد مباريات متاحة أو تعذر التحديث" else ""
            b.statusText.visibility = if (result.isEmpty()) View.VISIBLE else View.GONE
            showDay(0)
        }
    }

    private fun showDay(offset: Int) {
        val wanted = dateOffset(offset)
        adapter.submit(allMatches.filter { it.utcDate.startsWith(wanted) })
    }

    private fun remind(match: SportsMatch) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 440)
        }

        if (MatchReminderScheduler.schedule(this, match, 15)) {
            Toast.makeText(this, "سيتم تنبيهك قبل المباراة بـ 15 دقيقة", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "موعد المباراة قريب أو انتهى", Toast.LENGTH_SHORT).show()
        }
    }

    private fun dateOffset(days: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, days)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }
}
