package com.adwio.player.data

import android.content.Context
import com.adwio.player.data.model.EpgItemModel

class EpgCache(context: Context) {
    private val prefs = context.getSharedPreferences("adwio_epg_cache", Context.MODE_PRIVATE)
    private val ttlMs = 5 * 60 * 1000L

    fun get(streamId: String): List<EpgItemModel>? {
        val savedAt = prefs.getLong("$streamId:time", 0L)
        if (System.currentTimeMillis() - savedAt > ttlMs) return null
        val count = prefs.getInt("$streamId:count", 0)
        if (count <= 0) return null
        return (0 until count).mapNotNull { i ->
            val title = prefs.getString("$streamId:$i:title", null) ?: return@mapNotNull null
            EpgItemModel(
                title = title,
                start = prefs.getString("$streamId:$i:start", null),
                end = prefs.getString("$streamId:$i:end", null),
                description = prefs.getString("$streamId:$i:desc", null)
            )
        }
    }

    fun put(streamId: String, items: List<EpgItemModel>) {
        val editor = prefs.edit()
            .putLong("$streamId:time", System.currentTimeMillis())
            .putInt("$streamId:count", items.size)
        items.take(4).forEachIndexed { i, item ->
            editor.putString("$streamId:$i:title", item.title)
            editor.putString("$streamId:$i:start", item.start)
            editor.putString("$streamId:$i:end", item.end)
            editor.putString("$streamId:$i:desc", item.description)
        }
        editor.apply()
    }
}
