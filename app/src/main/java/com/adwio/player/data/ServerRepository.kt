package com.adwio.player.data

import com.adwio.player.data.model.ServerHost

object ServerRepository {
    fun activeServers(): List<ServerHost> = listOf(
        ServerHost("srv_1", "Server 1", "http://ervs.info", true, 10),
        ServerHost("srv_2", "Server 2", "http://zikoo.top:80", true, 20)
    ).filter { it.enabled }.sortedBy { it.priority }
}
