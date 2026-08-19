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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun load(url: String): List<MediaItemModel> {
        val request = Request.Builder().url(url).header("User-Agent", "ADWIO-Player/3.5").build()
        val body = client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return emptyList()
            r.body?.string().orEmpty()
        }
        return parse(body)
    }

    fun categories(items: List<MediaItemModel>, type: MediaType): List<CategoryModel> {
        val filtered = items.filter { it.type == type }
        val groups = filtered.mapNotNull { it.categoryId }.distinct()
        return listOf(CategoryModel("", "All")) + groups.map { CategoryModel(it, it) }
    }

    private fun parse(text: String): List<MediaItemModel> {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        val out = ArrayList<MediaItemModel>()
        var info: String? = null
        for (line in lines) {
            if (line.startsWith("#EXTINF", true)) {
                info = line
                continue
            }
            if (line.startsWith("#")) continue
            val meta = info ?: continue
            info = null
            val name = meta.substringAfterLast(',', "Untitled").trim().ifBlank { "Untitled" }
            val group = attr(meta, "group-title").ifBlank { "General" }
            val logo = attr(meta, "tvg-logo").ifBlank { null }
            val lower = (group + " " + name + " " + line).lowercase()
            val type = when {
                lower.contains("series") || lower.contains("مسلسل") || lower.contains("episode") -> MediaType.SERIES
                lower.contains("movie") || lower.contains("film") || lower.contains("vod") || lower.contains("افلام") || lower.contains("أفلام") || Regex("\\.(mp4|mkv|avi|mov)(\\?|$)").containsMatchIn(lower) -> MediaType.MOVIE
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
        return out
    }

    private fun attr(line: String, key: String): String =
        Regex("${Regex.escape(key)}=\\\"([^\\\"]*)\\\"", RegexOption.IGNORE_CASE)
            .find(line)?.groupValues?.getOrNull(1).orEmpty()

    private fun sha1(value: String): String = MessageDigest.getInstance("SHA-1")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
