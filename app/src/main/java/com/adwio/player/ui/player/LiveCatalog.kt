package com.adwio.player.ui.player

import com.adwio.player.data.model.CategoryModel
import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.data.model.MediaType

object LiveCatalog {
    private var categories: List<CategoryModel> = emptyList()
    private var channels: List<MediaItemModel> = emptyList()
    var selectedCategoryId: String = ""
        private set

    fun setData(cats: List<CategoryModel>, items: List<MediaItemModel>, selected: String = "") {
        categories = cats
        channels = items.filter { it.type == MediaType.LIVE && it.streamUrl.isNotBlank() }
        selectedCategoryId = selected
    }

    fun selectCategory(id: String) {
        selectedCategoryId = id
    }

    fun categories(): List<CategoryModel> = categories

    fun channelsFor(categoryId: String = selectedCategoryId): List<MediaItemModel> =
        if (categoryId.isBlank()) channels else channels.filter { it.categoryId == categoryId }
}
