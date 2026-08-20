package com.adwio.player.ui.home

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.adwio.player.R
import com.adwio.player.data.FavoritesStore
import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.data.model.MediaType
import com.adwio.player.databinding.ItemMediaBinding
import com.squareup.picasso.Picasso

class MediaAdapter(
    private val favorites: FavoritesStore,
    private val onClick: (MediaItemModel) -> Unit,
    private val onFocus: ((MediaItemModel) -> Unit)? = null
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
            b.favoriteText.text = if (favorites.isFavorite(item.id, item.type)) "★" else "☆"

            if (!item.logoUrl.isNullOrBlank()) {
                Picasso.get().load(item.logoUrl).fit().centerInside().into(b.logoImage)
            } else {
                b.logoImage.setImageResource(R.drawable.ic_adwio)
            }

            b.root.setOnClickListener {
                val recycler = b.root.parent as? RecyclerView
                val isMainLiveList = recycler?.id == R.id.contentRecycler && item.type == MediaType.LIVE
                val isFullscreenOverlay = recycler?.id == R.id.overlayChannelRecycler

                if (isMainLiveList) {
                    onClick(item)
                    onClick(item)
                } else {
                    onClick(item)
                }

                if (isFullscreenOverlay) {
                    b.root.post {
                        b.root.rootView.findViewById<View?>(R.id.liveBrowseOverlay)?.visibility = View.VISIBLE
                        b.root.rootView.findViewById<View?>(R.id.playerControls)?.visibility = View.GONE
                    }
                }
            }

            b.root.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) onFocus?.invoke(item)
            }

            b.favoriteText.setOnClickListener {
                val fav = favorites.toggle(item.id, item.type)
                b.favoriteText.text = if (fav) "★" else "☆"
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemMediaBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
}
