package com.adwio.player.data.model

data class ServerHost(
    val id: String,
    val name: String,
    val baseUrl: String,
    val enabled: Boolean = true,
    val priority: Int = 0
)

data class Session(
    val username: String,
    val password: String,
    val server: ServerHost,
    val expiresAt: String? = null,
    val status: String? = null,
    val displayName: String = "",
    val createdAt: String? = null,
    val activeConnections: String? = null,
    val maxConnections: String? = null
)

data class CategoryModel(
    val id: String,
    val name: String
)

data class MediaItemModel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val categoryId: String? = null,
    val type: MediaType,
    val meta: String? = null,
    val addedAt: Long = 0L
)

data class ContentInfoModel(
    val plot: String = "",
    val genre: String = "",
    val releaseDate: String = "",
    val duration: String = "",
    val rating: String = "",
    val director: String = "",
    val cast: String = "",
    val backdropUrl: String? = null
)

data class EpgItemModel(
    val title: String,
    val start: String? = null,
    val end: String? = null,
    val description: String? = null
)

data class EpisodeModel(
    val id: String,
    val title: String,
    val season: Int,
    val episodeNumber: Int,
    val streamUrl: String,
    val extension: String = "mp4"
)

enum class MediaType { LIVE, MOVIE, SERIES }
