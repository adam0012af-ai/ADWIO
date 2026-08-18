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
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .callTimeout(18, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder().build()

    fun authenticate(username: String, password: String): Session? {
        for (server in ServerRepository.activeServers()) {
            val url = "${server.baseUrl.trimEnd('/')}/player_api.php?username=${enc(username)}&password=${enc(password)}"
            try {
                val body = get(url) ?: continue
                val auth = Regex("\"auth\"\\s*:\\s*\"?1\"?").containsMatchIn(body)
                if (auth) {
                    val expires = Regex("\"exp_date\"\\s*:\\s*\"?([^\",}]*)").find(body)?.groupValues?.getOrNull(1)
                    val status = Regex("\"status\"\\s*:\\s*\"([^\"]*)").find(body)?.groupValues?.getOrNull(1)
                    return Session(username, password, server, expires, status)
                }
            } catch (_: Exception) {}
        }
        return null
    }

    fun loadLive(session: Session) = loadStreams(session, "get_live_streams", MediaType.LIVE)

    fun loadMovies(session: Session) = loadStreams(session, "get_vod_streams", MediaType.MOVIE)

    fun loadSeries(session: Session): List<MediaItemModel> {
        val raw = get("${apiBase(session)}&action=get_series") ?: return emptyList()
        return parseRows(raw).mapNotNull { row ->
            val id = numberString(row["series_id"]) ?: return@mapNotNull null
            MediaItemModel(
                id = id,
                name = row["name"]?.toString() ?: "Series",
                streamUrl = "",
                logoUrl = row["cover"]?.toString(),
                categoryId = row["category_id"]?.toString(),
                type = MediaType.SERIES,
                meta = row["rating"]?.toString()
            )
        }
    }

    fun loadSeriesEpisodes(session: Session, seriesId: String): List<EpisodeModel> {
        val raw = get("${apiBase(session)}&action=get_series_info&series_id=${enc(seriesId)}") ?: return emptyList()
        val root = parseObject(raw)
        val episodesObj = root["episodes"] as? Map<*, *> ?: return emptyList()
        val result = mutableListOf<EpisodeModel>()

        episodesObj.forEach { (seasonKey, value) ->
            val season = seasonKey?.toString()?.toIntOrNull() ?: 0
            val episodeRows = value as? List<*> ?: return@forEach
            episodeRows.forEachIndexed { index, any ->
                val row = any as? Map<*, *> ?: return@forEachIndexed
                val id = numberString(row["id"]) ?: return@forEachIndexed
                val ext = row["container_extension"]?.toString()?.takeIf { it.isNotBlank() } ?: "mp4"
                val title = row["title"]?.toString()
                    ?: row["info"]?.let { info -> (info as? Map<*, *>)?.get("movie_image")?.toString() }
                    ?: "Episode ${index + 1}"
                val epNum = numberString(row["episode_num"])?.toIntOrNull() ?: (index + 1)
                val base = session.server.baseUrl.trimEnd('/')
                val streamUrl = "$base/series/${enc(session.username)}/${enc(session.password)}/$id.$ext"
                result += EpisodeModel(id, title, season, epNum, streamUrl, ext)
            }
        }
        return result.sortedWith(compareBy<EpisodeModel> { it.season }.thenBy { it.episodeNumber })
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
                categoryId = row["category_id"]?.toString(),
                type = type,
                meta = row["rating"]?.toString()
            )
        }
    }

    private fun apiBase(session: Session): String =
        "${session.server.baseUrl.trimEnd('/')}/player_api.php?username=${enc(session.username)}&password=${enc(session.password)}"

    private fun parseRows(raw: String): List<Map<String, Any?>> {
        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        val listType = Types.newParameterizedType(List::class.java, mapType)
        return try {
            moshi.adapter<List<Map<String, Any?>>>(listType).fromJson(raw).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseObject(raw: String): Map<String, Any?> {
        val mapType = Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
        return try {
            moshi.adapter<Map<String, Any?>>(mapType).fromJson(raw).orEmpty()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun numberString(v: Any?): String? = when (v) {
        is Number -> v.toLong().toString()
        null -> null
        else -> v.toString()
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ADWIO-Player/1.0")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            return response.body?.string()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
