package com.adwio.player.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.adwio.player.R
import com.adwio.player.data.FavoritesStore
import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.databinding.ItemPosterBinding
import com.squareup.picasso.Picasso

class PosterAdapter(
    private val favorites: FavoritesStore,
    private val onClick: (MediaItemModel) -> Unit
) : RecyclerView.Adapter<PosterAdapter.VH>() {

    private val items = mutableListOf<MediaItemModel>()

    fun submit(list: List<MediaItemModel>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class VH(private val b: ItemPosterBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: MediaItemModel) {
            b.posterTitle.text = item.name
            b.posterMeta.text = item.meta.orEmpty()
            b.favoriteText.text = if (favorites.isFavorite(item.id, item.type)) "★" else "☆"

            if (!item.logoUrl.isNullOrBlank()) {
                Picasso.get()
                    .load(item.logoUrl)
                    .placeholder(R.drawable.ic_adwio)
                    .error(R.drawable.ic_adwio)
                    .fit()
                    .centerInside()
                    .into(b.posterImage)
            } else {
                b.posterImage.setImageResource(R.drawable.ic_adwio)
            }

            b.root.setOnClickListener { onClick(item) }
            b.favoriteText.setOnClickListener {
                val fav = favorites.toggle(item.id, item.type)
                b.favoriteText.text = if (fav) "★" else "☆"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemPosterBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
}
