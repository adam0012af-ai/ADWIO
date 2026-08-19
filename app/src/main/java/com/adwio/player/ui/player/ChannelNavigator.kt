package com.adwio.player.ui.player

import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.data.model.MediaType

object ChannelNavigator {
    private var channels: List<MediaItemModel> = emptyList()
    private var index: Int = -1

    fun setQueue(items: List<MediaItemModel>, currentId: String) {
        channels = items.filter { it.type == MediaType.LIVE && it.streamUrl.isNotBlank() }
        index = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    }

    fun current(): MediaItemModel? = channels.getOrNull(index)

    fun next(): MediaItemModel? {
        if (channels.isEmpty()) return null
        index = (index + 1) % channels.size
        return channels[index]
    }

    fun previous(): MediaItemModel? {
        if (channels.isEmpty()) return null
        index = if (index <= 0) channels.lastIndex else index - 1
        return channels[index]
    }

    fun hasQueue(): Boolean = channels.isNotEmpty()
}
