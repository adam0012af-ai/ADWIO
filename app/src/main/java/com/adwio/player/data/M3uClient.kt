package com.adwio.player.data

import com.adwio.player.data.model.CategoryModel
import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.data.model.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.buffer
import okio.sink
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class M3uClient {
    private val fastClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun probe(url: String): Boolean {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ADWIO-Player/4.1")
            .header("Range", "bytes=0-65535")
            .build()

        return runCatching {
            fastClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val source = response.body?.source() ?: return@use false
                var checked = 0
                while (checked++ < 80) {
                    val line = source.readUtf8Line()?.trim() ?: break
                    if (line.startsWith("#EXTM3U", true) || line.startsWith("#EXTINF", true)) {
                        return@use true
                    }
                }
                false
            }
        }.getOrDefault(false)
    }

    fun loadPartial(url: String, maxItems: Int = 600): List<MediaItemModel> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "ADWIO-Player/4.1")
            .build()

        return runCatching {
            fastClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val source = response.body?.source() ?: return@use emptyList()
                val out = ArrayList<MediaItemModel>(maxItems.coerceAtMost(800))
                var info: String? = null

                while (out.size < maxItems) {
                    val raw = source.readUtf8Line() ?: break
                    val line = raw.trim()
                    if (line.isEmpty()) continue
                    if (line.startsWith("#EXTINF", true)) {
                        info = line
                        continue
                    }
                    if (line.startsWith("#")) continue

                    val meta = info ?: continue
                    info = null
                    parseEntry(meta, line)?.let(out::add)
                }
                out
            }
        }.getOrDefault(emptyList())
    }

    fun downloadTo(url: String, destination: File): Boolean {
        val request = Request.Builder().url(url).header("User-Agent", "ADWIO-Player/4.1").build()
        val temp = File(destination.parentFile, destination.name + ".tmp")
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val source = response.body?.source() ?: return@use false
                temp.sink().buffer().use { sink -> sink.writeAll(source) }
                if (temp.length() < 8L) return@use false
                if (destination.exists()) destination.delete()
                temp.renameTo(destination)
            }
        }.getOrElse {
            temp.delete()
            false
        }
    }

    fun load(url: String): List<MediaItemModel> {
        val request = Request.Builder().url(url).header("User-Agent", "ADWIO-Player/4.1").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val source = response.body?.source() ?: return emptyList()
            parseLines(generateSequence { source.readUtf8Line() })
        }
    }

    fun parseFile(file: File): List<MediaItemModel> =
        file.bufferedReader().useLines { parseLines(it) }

    fun categories(items: List<MediaItemModel>, type: MediaType): List<CategoryModel> {
        val groups = LinkedHashSet<String>()
        items.asSequence()
            .filter { it.type == type }
            .forEach { it.categoryId?.takeIf(String::isNotBlank)?.let(groups::add) }

        return buildList {
            add(CategoryModel("", "ALL"))
            groups.forEach { add(CategoryModel(it, it)) }
        }
    }

    private fun parseLines(lines: Sequence<String>): List<MediaItemModel> {
        val out = ArrayList<MediaItemModel>(2048)
        var info: String? = null

        lines.forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach
            if (line.startsWith("#EXTINF", true)) {
                info = line
                return@forEach
            }
            if (line.startsWith("#")) return@forEach

            val meta = info ?: return@forEach
            info = null
            parseEntry(meta, line)?.let(out::add)
        }
        return out
    }

    private fun parseEntry(meta: String, line: String): MediaItemModel? {
        val name = meta.substringAfterLast(',', "Untitled").trim().ifBlank { "Untitled" }
        val group = attr(meta, "group-title").ifBlank { "General" }
        val logo = attr(meta, "tvg-logo").ifBlank { null }
        val lower = "$group $name $line".lowercase()

        val type = when {
            "/series/" in lower ||
                "series" in lower ||
                "مسلسل" in lower ||
                "مسلسلات" in lower ||
                "episode" in lower ||
                Regex("s\\d{1,2}e\\d{1,3}").containsMatchIn(lower) -> MediaType.SERIES

            "/movie/" in lower ||
                "movie" in lower ||
                "movies" in lower ||
                "film" in lower ||
                "vod" in lower ||
                "افلام" in lower ||
                "أفلام" in lower ||
                "فيلم" in lower ||
                Regex("\\.(mp4|mkv|avi|mov|m4v)(\\?|$)").containsMatchIn(lower) -> MediaType.MOVIE

            else -> MediaType.LIVE
        }

        return MediaItemModel(
            id = sha1("$line|$name").take(16),
            name = name,
            streamUrl = line,
            logoUrl = logo,
            categoryId = group,
            type = type,
            meta = group
        )
    }

    private fun attr(line: String, key: String): String =
        Regex("${Regex.escape(key)}=\\\"([^\\\"]*)\\\"", RegexOption.IGNORE_CASE)
            .find(line)?.groupValues?.getOrNull(1).orEmpty()

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
