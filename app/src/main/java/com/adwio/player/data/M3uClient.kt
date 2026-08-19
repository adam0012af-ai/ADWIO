package com.adwio.player.data

import com.adwio.player.data.model.CategoryModel
import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.data.model.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class M3uClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun probe(url: String): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ADWIO-Player/3.5.1")
            .header("Accept", "*/*")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false

                val source = response.body?.source() ?: return@use false
                var checked = 0
                var sawHeader = false
                var sawEntry = false

                while (checked < 80) {
                    val raw = source.readUtf8Line() ?: break
                    val line = raw.trim()
                    checked++

                    if (line.startsWith("#EXTM3U", ignoreCase = true)) {
                        sawHeader = true
                    } else if (line.startsWith("#EXTINF", ignoreCase = true)) {
                        sawEntry = true
                    } else if (
                        line.isNotBlank() &&
                        !line.startsWith("#") &&
                        (line.startsWith("http://", true) || line.startsWith("https://", true))
                    ) {
                        sawEntry = true
                    }

                    if (sawHeader && sawEntry) break
                }

                sawHeader || sawEntry
            }
        }.getOrDefault(false)
    }

    fun load(url: String): List<MediaItemModel> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ADWIO-Player/3.5.1")
            .header("Accept", "*/*")
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()

            val source = response.body?.source() ?: return emptyList()
            val out = ArrayList<MediaItemModel>(2048)
            var info: String? = null

            while (!source.exhausted()) {
                val raw = source.readUtf8Line() ?: break
                val line = raw.trim()
                if (line.isEmpty()) continue

                if (line.startsWith("#EXTINF", ignoreCase = true)) {
                    info = line
                    continue
                }

                if (line.startsWith("#")) continue

                val meta = info ?: continue
                info = null

                val name = meta.substringAfterLast(',', "Untitled")
                    .trim()
                    .ifBlank { "Untitled" }

                val group = attr(meta, "group-title")
                    .ifBlank { "General" }

                val logo = attr(meta, "tvg-logo").ifBlank { null }
                val lower = "$group $name $line".lowercase()

                val type = when {
                    lower.contains("series") ||
                        lower.contains("مسلسل") ||
                        lower.contains("episode") -> MediaType.SERIES

                    lower.contains("movie") ||
                        lower.contains("film") ||
                        lower.contains("vod") ||
                        lower.contains("افلام") ||
                        lower.contains("أفلام") ||
                        Regex("\\.(mp4|mkv|avi|mov)(\\?|$)").containsMatchIn(lower) -> MediaType.MOVIE

                    else -> MediaType.LIVE
                }

                out += MediaItemModel(
                    id = sha1("$line|$name").take(16),
                    name = name,
                    streamUrl = line,
                    logoUrl = logo,
                    categoryId = group,
                    type = type,
                    meta = group
                )
            }

            out
        }
    }

    fun categories(
        items: List<MediaItemModel>,
        type: MediaType
    ): List<CategoryModel> {
        val filtered = items.filter { it.type == type }
        val groups = LinkedHashSet<String>()

        filtered.forEach { item ->
            item.categoryId
                ?.takeIf { it.isNotBlank() }
                ?.let(groups::add)
        }

        return buildList {
            add(CategoryModel("", "All"))
            groups.forEach { add(CategoryModel(it, it)) }
        }
    }

    private fun attr(line: String, key: String): String =
        Regex(
            "${Regex.escape(key)}=\\\"([^\\\"]*)\\\"",
            RegexOption.IGNORE_CASE
        ).find(line)?.groupValues?.getOrNull(1).orEmpty()

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
