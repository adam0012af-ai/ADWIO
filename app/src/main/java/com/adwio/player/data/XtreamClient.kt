package com.adwio.player.data

import com.adwio.player.data.model.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class XtreamClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val moshi = Moshi.Builder().build()

    fun authenticate(username: String, password: String, serverUrl: String? = null): Session? {
        val servers = if (!serverUrl.isNullOrBlank()) {
            val normalized = normalizeBaseUrl(serverUrl) ?: return null
            listOf(ServerHost("custom", hostLabel(normalized), normalized, true, 0))
        } else ServerRepository.activeServers()

        for (server in servers) {
            val url = "${server.baseUrl.trimEnd('/')}/player_api.php?username=${enc(username)}&password=${enc(password)}"
            try {
                val body = get(url) ?: continue
                val auth = Regex("\"auth\"\\s*:\\s*\"?1\"?").containsMatchIn(body)
                if (auth) {
                    fun field(key: String) =
                        Regex("\"$key\"\\s*:\\s*\"?([^\",}]*)").find(body)?.groupValues?.getOrNull(1)

                    return Session(
                        username = username,
                        password = password,
                        server = server,
                        expiresAt = field("exp_date"),
                        status = field("status"),
                        createdAt = field("created_at"),
                        activeConnections = field("active_cons"),
                        maxConnections = field("max_connections")
                    )
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun loadCategories(session: Session, type: MediaType): List<CategoryModel> {
        val action = when (type) {
            MediaType.LIVE -> "get_live_categories"
            MediaType.MOVIE -> "get_vod_categories"
            MediaType.SERIES -> "get_series_categories"
        }
        val raw = get("${apiBase(session)}&action=$action") ?: return listOf(CategoryModel("", "All"))
        val parsed = parseRows(raw).mapNotNull { row ->
            val id = numberString(row["category_id"]) ?: return@mapNotNull null
            CategoryModel(id, row["category_name"]?.toString()?.takeIf { it.isNotBlank() } ?: "Category")
        }
        return listOf(CategoryModel("", "All")) + parsed
    }

    fun loadLive(session: Session) = loadStreams(session, "get_live_streams", MediaType.LIVE)
    fun loadMovies(session: Session) = loadStreams(session, "get_vod_streams", MediaType.MOVIE)

    fun loadShortEpg(session: Session, streamId: String, limit: Int = 2): List<EpgItemModel> {
        val raw = get("${apiBase(session)}&action=get_short_epg&stream_id=${enc(streamId)}&limit=$limit")
            ?: return emptyList()
        val root = parseObject(raw)
        val rows = root["epg_listings"] as? List<*> ?: return emptyList()
        return rows.mapNotNull { any ->
            val row = any as? Map<*, *> ?: return@mapNotNull null
            val title = row["title"]?.toString()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            EpgItemModel(
                decodeMaybeBase64(title),
                row["start"]?.toString(),
                row["end"]?.toString(),
                row["description"]?.toString()?.takeIf { it.isNotBlank() }?.let(::decodeMaybeBase64)
            )
        }
    }

    fun loadSeries(session: Session): List<MediaItemModel> {
        val raw = get("${apiBase(session)}&action=get_series") ?: return emptyList()
        return parseRows(raw).mapNotNull { row ->
            val id = numberString(row["series_id"]) ?: return@mapNotNull null
            MediaItemModel(
                id = id,
                name = row["name"]?.toString() ?: "Series",
                streamUrl = "",
                logoUrl = row["cover"]?.toString()?.takeIf { it.isNotBlank() },
                categoryId = numberString(row["category_id"]),
                type = MediaType.SERIES,
                meta = row["rating"]?.toString(),
                addedAt = numberString(row["last_modified"])?.toLongOrNull()
                    ?: numberString(row["added"])?.toLongOrNull()
                    ?: 0L
            )
        }
    }

    fun loadMovieInfo(session: Session, movieId: String): ContentInfoModel {
        val raw = get("${apiBase(session)}&action=get_vod_info&vod_id=${enc(movieId)}")
            ?: return ContentInfoModel()
        val root = parseObject(raw)
        val info = (root["info"] as? Map<*, *>) ?: root
        return parseContentInfo(info)
    }

    fun loadSeriesInfo(session: Session, seriesId: String): ContentInfoModel {
        val raw = get("${apiBase(session)}&action=get_series_info&series_id=${enc(seriesId)}")
            ?: return ContentInfoModel()
        val root = parseObject(raw)
        val info = (root["info"] as? Map<*, *>) ?: root
        return parseContentInfo(info)
    }

    private fun parseContentInfo(info: Map<*, *>): ContentInfoModel {
        fun v(vararg keys: String): String =
            keys.firstNotNullOfOrNull { k ->
                info[k]?.toString()?.takeIf { it.isNotBlank() && it != "null" }
            }.orEmpty()

        val backdrop = (info["backdrop_path"] as? List<*>)?.firstOrNull()?.toString()
            ?: v("backdrop", "movie_image").takeIf { it.isNotBlank() }

        val rawTrailer = v(
            "youtube_trailer",
            "trailer",
            "trailer_url",
            "youtube_trailer_id",
            "youtube_id"
        )

        val trailer = normalizeTrailer(rawTrailer)

        return ContentInfoModel(
            plot = v("plot", "description"),
            genre = v("genre"),
            releaseDate = v("releaseDate", "releasedate", "release_date"),
            duration = v("duration", "duration_secs"),
            rating = v("rating", "rating_5based"),
            director = v("director"),
            cast = v("cast", "actors"),
            backdropUrl = backdrop,
            trailerUrl = trailer
        )
    }

    fun loadSeriesEpisodes(session: Session, seriesId: String): List<EpisodeModel> {
        val raw = get("${apiBase(session)}&action=get_series_info&series_id=${enc(seriesId)}")
            ?: return emptyList()
        val root = parseObject(raw)
        val episodesObj = root["episodes"] as? Map<*, *> ?: return emptyList()
        val result = mutableListOf<EpisodeModel>()

        episodesObj.forEach { (seasonKey, value) ->
            val season = seasonKey?.toString()?.toIntOrNull() ?: 0
            val rows = value as? List<*> ?: return@forEach
            rows.forEachIndexed { index, any ->
                val row = any as? Map<*, *> ?: return@forEachIndexed
                val id = numberString(row["id"]) ?: return@forEachIndexed
                val ext = row["container_extension"]?.toString()?.takeIf { it.isNotBlank() } ?: "mp4"
                val title = row["title"]?.toString()?.takeIf { it.isNotBlank() } ?: "Episode ${index + 1}"
                val epNum = numberString(row["episode_num"])?.toIntOrNull() ?: (index + 1)
                val base = session.server.baseUrl.trimEnd('/')
                val streamUrl = "$base/series/${enc(session.username)}/${enc(session.password)}/$id.$ext"
                result += EpisodeModel(id, title, season, epNum, streamUrl, ext)
            }
        }

        return result.sortedWith(
            compareBy<EpisodeModel> { it.season }
                .thenBy { it.episodeNumber }
                .thenBy { it.id }
        )
    }

    private fun loadStreams(session: Session, action: String, type: MediaType): List<MediaItemModel> {
        val raw = get("${apiBase(session)}&action=$action") ?: return emptyList()
        return parseRows(raw).mapNotNull { row ->
            val id = numberString(row["stream_id"]) ?: return@mapNotNull null
            val ext = row["container_extension"]?.toString()?.takeIf { it.isNotBlank() } ?: "mp4"
            val base = session.server.baseUrl.trimEnd('/')
            val url = when (type) {
                MediaType.LIVE -> "$base/live/${enc(session.username)}/${enc(session.password)}/$id.ts"
                MediaType.MOVIE -> "$base/movie/${enc(session.username)}/${enc(session.password)}/$id.$ext"
                MediaType.SERIES -> ""
            }

            MediaItemModel(
                id = id,
                name = row["name"]?.toString() ?: "Untitled",
                streamUrl = url,
                logoUrl = row["stream_icon"]?.toString()?.takeIf { it.isNotBlank() },
                categoryId = numberString(row["category_id"]),
                type = type,
                meta = row["rating"]?.toString(),
                addedAt = numberString(row["added"])?.toLongOrNull()
                    ?: numberString(row["last_modified"])?.toLongOrNull()
                    ?: 0L
            )
        }
    }

    private fun normalizeTrailer(raw: String): String? {
        val value = raw.trim()
        if (value.isBlank()) return null
        if (value.startsWith("http://", true) || value.startsWith("https://", true)) return value
        if (value.matches(Regex("[A-Za-z0-9_-]{6,20}"))) {
            return "https://www.youtube.com/watch?v=$value"
        }
        return null
    }

    private fun apiBase(session: Session): String =
        "${session.server.baseUrl.trimEnd('/')}/player_api.php?username=${enc(session.username)}&password=${enc(session.password)}"

    private fun parseRows(raw: String): List<Map<String, Any?>> {
        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val listType = Types.newParameterizedType(List::class.java, mapType)
        return try { moshi.adapter<List<Map<String, Any?>>>(listType).fromJson(raw).orEmpty() }
        catch (_: Exception) { emptyList() }
    }

    private fun parseObject(raw: String): Map<String, Any?> {
        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        return try { moshi.adapter<Map<String, Any?>>(mapType).fromJson(raw).orEmpty() }
        catch (_: Exception) { emptyMap() }
    }

    private fun numberString(v: Any?): String? = when (v) {
        is Number -> v.toLong().toString()
        null -> null
        else -> v.toString()
    }

    private fun decodeMaybeBase64(value: String): String = try {
        val decoded = android.util.Base64.decode(value, android.util.Base64.DEFAULT)
        val text = decoded.toString(Charsets.UTF_8).trim()
        if (text.isBlank() || text.any { it == '\uFFFD' }) value else text
    } catch (_: Exception) { value }

    private fun get(url: String): String? {
        repeat(2) { attempt ->
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "ADWIO-Player/5.0.1")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) return body
                    }
                }
            } catch (_: Exception) {
                if (attempt == 1) return null
            }
        }
        return null
    }

    private fun normalizeBaseUrl(raw: String): String? {
        var value = raw.trim()
        if (value.isBlank()) return null
        if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) value = "http://$value"
        return runCatching {
            val u = java.net.URI(value)
            if (u.host.isNullOrBlank()) null else value.substringBefore("/player_api.php").trimEnd('/')
        }.getOrNull()
    }

    private fun hostLabel(url: String) =
        runCatching { java.net.URI(url).host ?: "Server" }.getOrDefault("Server")

    private fun enc(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
}
