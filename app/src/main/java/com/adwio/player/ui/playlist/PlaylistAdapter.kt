package com.adwio.player.ui.playlist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.adwio.player.data.model.PlaylistProfile
import com.adwio.player.databinding.ItemPlaylistBinding

class PlaylistAdapter(
    private val onOpen: (PlaylistProfile) -> Unit,
    private val onDelete: (PlaylistProfile) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.VH>() {

    private val items = mutableListOf<PlaylistProfile>()

    fun submit(list: List<PlaylistProfile>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemPlaylistBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: PlaylistProfile) {
            b.playlistNameText.text = item.name
            b.playlistServerText.text = item.serverName
            b.root.setOnClickListener { onOpen(item) }
            b.deletePlaylist.setOnClickListener { onDelete(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
}
