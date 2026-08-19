package com.adwio.player.data

import android.content.Context
import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.data.model.MediaType

class RecentChannelsStore(context: Context) {
    private val prefs = context.getSharedPreferences("adwio_recent_channels", Context.MODE_PRIVATE)

    fun add(item: MediaItemModel) {
        if (item.type != MediaType.LIVE) return
        val encoded = encode(item)
        val current = list().filterNot { it.id == item.id }.toMutableList()
        current.add(0, item)
        prefs.edit().putString("items", current.take(30).joinToString("") { encode(it) }).apply()
    }

    fun list(): List<MediaItemModel> = prefs.getString("items", "").orEmpty()
        .split("")
        .filter { it.isNotBlank() }
        .mapNotNull(::decode)

    fun clear() = prefs.edit().remove("items").apply()

    private fun encode(item: MediaItemModel): String = listOf(
        item.id, item.name, item.streamUrl, item.logoUrl.orEmpty(), item.categoryId.orEmpty(), item.meta.orEmpty()
    ).joinToString("") { it.replace("", "").replace("", "") }

    private fun decode(raw: String): MediaItemModel? {
        val x = raw.split("")
        if (x.size < 6) return null
        return MediaItemModel(
            id = x[0], name = x[1], streamUrl = x[2],
            logoUrl = x[3].takeIf { it.isNotBlank() },
            categoryId = x[4].takeIf { it.isNotBlank() },
            type = MediaType.LIVE,
            meta = x[5].takeIf { it.isNotBlank() }
        )
    }
}
