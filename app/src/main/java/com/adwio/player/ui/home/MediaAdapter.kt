package com.adwio.player.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.adwio.player.data.FavoritesStore
import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.databinding.ItemMediaBinding
import com.squareup.picasso.Picasso

class MediaAdapter(
    private val favorites: FavoritesStore,
    private val onClick: (MediaItemModel) -> Unit
) : RecyclerView.Adapter<MediaAdapter.VH>() {

    private val items = mutableListOf<MediaItemModel>()

    fun submit(list: List<MediaItemModel>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemMediaBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: MediaItemModel) {
            b.nameText.text = item.name
            b.metaText.text = item.meta ?: item.type.name
            b.favoriteText.text = if (favorites.isFavorite(item.id)) "★" else "☆"
            if (!item.logoUrl.isNullOrBlank()) {
                Picasso.get().load(item.logoUrl).fit().centerInside().into(b.logoImage)
            } else {
                b.logoImage.setImageResource(com.adwio.player.R.drawable.ic_adwio)
            }
            b.root.setOnClickListener { onClick(item) }
            b.favoriteText.setOnClickListener {
                val fav = favorites.toggle(item.id)
                b.favoriteText.text = if (fav) "★" else "☆"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
}
