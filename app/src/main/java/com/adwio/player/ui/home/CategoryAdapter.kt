package com.adwio.player.ui.home

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.adwio.player.data.model.CategoryModel
import com.adwio.player.databinding.ItemCategoryBinding

class CategoryAdapter(
    private val onClick: (CategoryModel) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.VH>() {

    private val items = mutableListOf<CategoryModel>()
    private var selectedId: String = ""

    fun submit(list: List<CategoryModel>) {
        items.clear()
        items.addAll(list)
        selectedId = ""
        notifyDataSetChanged()
    }

    inner class VH(private val b: ItemCategoryBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: CategoryModel) {
            b.categoryText.text = item.name
            b.root.isSelected = item.id == selectedId
            b.root.setOnClickListener {
                selectedId = item.id
                notifyDataSetChanged()
                onClick(item)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size
    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
}
