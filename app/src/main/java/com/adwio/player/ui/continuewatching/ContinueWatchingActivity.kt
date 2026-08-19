package com.adwio.player.ui.continuewatching

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.adwio.player.data.PlaybackHistory
import com.adwio.player.data.WatchEntry
import com.adwio.player.databinding.ActivityContinueWatchingBinding
import com.adwio.player.databinding.ItemContinueWatchingBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.player.PlayerActivity

class ContinueWatchingActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityContinueWatchingBinding
    private lateinit var history: PlaybackHistory
    private val adapter = WatchAdapter(
        onPlay = { entry ->
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra("id", entry.id)
                putExtra("title", entry.title)
                putExtra("url", entry.url)
                putExtra("type", entry.type.name)
            })
        },
        onRemove = { entry ->
            history.remove(entry.id, entry.type)
            refresh()
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityContinueWatchingBinding.inflate(layoutInflater)
        setContentView(b.root)
        history = PlaybackHistory(this)
        b.list.layoutManager = LinearLayoutManager(this)
        b.list.adapter = adapter
        b.backButton.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val items = history.continueWatching()
        adapter.submit(items)
        b.emptyText.visibility = if (items.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        if (items.isNotEmpty()) b.list.post { b.list.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
    }
}

private class WatchAdapter(
    private val onPlay: (WatchEntry) -> Unit,
    private val onRemove: (WatchEntry) -> Unit
) : RecyclerView.Adapter<WatchAdapter.VH>() {
    private val items = mutableListOf<WatchEntry>()

    fun submit(newItems: List<WatchEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class VH(private val b: ItemContinueWatchingBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: WatchEntry) {
            b.titleText.text = item.title
            b.typeText.text = item.type.name
            val percent = (item.progress * 100).toInt()
            b.progressBar.progress = percent
            b.progressText.text = "$percent%"
            b.root.setOnClickListener { onPlay(item) }
            b.removeButton.setOnClickListener { onRemove(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemContinueWatchingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size
}
