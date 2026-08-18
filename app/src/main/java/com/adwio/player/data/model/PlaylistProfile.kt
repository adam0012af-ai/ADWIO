package com.adwio.player.data.model

data class PlaylistProfile(
    val id: String,
    val name: String,
    val username: String,
    val password: String,
    val serverId: String,
    val serverName: String,
    val serverUrl: String,
    val lastUsedAt: Long = System.currentTimeMillis()
)
