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
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /*
     * Important:
     * Do not use a short global callTimeout for full M3U playlists.
     * Large provider playlists can be several MB and may take longer than
     * the previous 120/150 second total deadline, which caused an empty list.
     */
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private fun normalizedUrl(url: String): String =
        url.trim()
            .replace("&amp;", "&", ignoreCase = true)

    private fun request(url: String): Request =
        Request.Builder()
            .url(normalizedUrl(url))
            .header("User-Agent", "ADWIO-Player/4.2")
            .header("Accept", "*/*")
            .build()

    fun probe(url: String): Boolean {
        val req = Request.Builder()
            .url(normalizedUrl(url))
            .header("User-Agent", "ADWIO-Player/4.2")
            .header("Accept", "*/*")
            .build()

        return runCatching {
            fastClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val source = response.body?.source() ?: return@use false
                var checked = 0
                while (checked++ < 250) {
                    val line = source.readUtf8Line()?.trim() ?: break
                    if (line.startsWith("#EXTM3U", true) ||
                        line.startsWith("#EXTINF", true)
                    ) return@use true
                }
                false
            }
        }.getOrDefault(false)
    }

    fun loadPartial(url: String, maxItems: Int = 1200): List<MediaItemModel> {
        return runCatching {
            fastClient.newCall(request(url)).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val source = response.body?.source() ?: return@use emptyList()
                val out = ArrayList<MediaItemModel>(maxItems.coerceAtMost(2000))
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

    /**
     * Reliable type loader for large M3U lists.
     *
     * The older code could return an empty section when a huge M3U exceeded
     * the global request timeout. This version scans to EOF without a short
     * total call deadline and then has a second full-load fallback.
     */
    fun loadForType(
        url: String,
        type: MediaType,
        maxItems: Int = Int.MAX_VALUE
    ): List<MediaItemModel> {

        val direct = runCatching {
            client.newCall(request(url)).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()

                val source = response.body?.source() ?: return@use emptyList()
                val out = ArrayList<MediaItemModel>(4096)
                var info: String? = null

                while (true) {
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

                    val item = parseEntry(meta, line) ?: continue
                    if (item.type == type) {
                        out += item
                        if (maxItems != Int.MAX_VALUE && out.size >= maxItems) break
                    }
                }

                out
            }
        }.getOrDefault(emptyList())

        if (direct.isNotEmpty()) return direct

        // Second independent path: load the whole list then filter locally.
        val all = runCatching { load(url) }.getOrDefault(emptyList())
        if (all.isNotEmpty()) {
            val filtered = all.filter { it.type == type }
            if (filtered.isNotEmpty()) {
                return if (maxItems == Int.MAX_VALUE) filtered else filtered.take(maxItems)
            }
        }

        // Last fallback keeps Live usable even on slow or unusual providers.
        if (type == MediaType.LIVE) {
            return loadPartial(url, 1800).filter { it.type == MediaType.LIVE }
        }

        return emptyList()
    }

    fun downloadTo(url: String, destination: File): Boolean {
        val temp = File(destination.parentFile, destination.name + ".tmp")

        return runCatching {
            client.newCall(request(url)).execute().use { response ->
                if (!response.isSuccessful) return@use false

                val source = response.body?.source() ?: return@use false
                temp.sink().buffer().use { sink -> sink.writeAll(source) }

                // Reject HTML/error pages and tiny invalid responses.
                if (temp.length() < 16L) {
                    temp.delete()
                    return@use false
                }

                val validHeader = runCatching {
                    temp.bufferedReader().use { reader ->
                        var checked = 0
                        var valid = false
                        while (checked++ < 80) {
                            val line = reader.readLine()?.trim() ?: break
                            if (line.startsWith("#EXTM3U", true) ||
                                line.startsWith("#EXTINF", true)
                            ) {
                                valid = true
                                break
                            }
                        }
                        valid
                    }
                }.getOrDefault(false)

                if (!validHeader) {
                    temp.delete()
                    return@use false
                }

                destination.parentFile?.mkdirs()
                if (destination.exists()) destination.delete()
                temp.renameTo(destination)
            }
        }.getOrElse {
            temp.delete()
            false
        }
    }

    fun load(url: String): List<MediaItemModel> {
        return runCatching {
            client.newCall(request(url)).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val source = response.body?.source() ?: return@use emptyList()
                parseLines(generateSequence { source.readUtf8Line() })
            }
        }.getOrDefault(emptyList())
    }

    fun parseFile(file: File): List<MediaItemModel> =
        runCatching {
            file.bufferedReader().useLines { parseLines(it) }
        }.getOrDefault(emptyList())

    fun categories(
        items: List<MediaItemModel>,
        type: MediaType
    ): List<CategoryModel> {

        val groups = LinkedHashSet<String>()

        items.asSequence()
            .filter { it.type == type }
            .forEach {
                it.categoryId
                    ?.takeIf(String::isNotBlank)
                    ?.let(groups::add)
            }

        return buildList {
            add(CategoryModel("", "ALL"))
            groups.forEach { add(CategoryModel(it, it)) }
        }
    }

    private fun parseLines(lines: Sequence<String>): List<MediaItemModel> {
        val out = ArrayList<MediaItemModel>(4096)
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

    private fun parseEntry(meta: String, streamLine: String): MediaItemModel? {
        val url = streamLine.trim()
        if (url.isBlank()) return null

        val name = meta.substringAfterLast(',', "Untitled")
            .trim()
            .ifBlank { "Untitled" }

        val group = attr(meta, "group-title")
            .ifBlank { "General" }

        val logo = attr(meta, "tvg-logo")
            .ifBlank { null }

        val declaredType = listOf(
            attr(meta, "tvg-type"),
            attr(meta, "type"),
            attr(meta, "content-type"),
            attr(meta, "media-type")
        ).joinToString(" ")

        val lower = "$declaredType $group $name $url".lowercase()

        val looksSeries =
            "/series/" in lower ||
            " series " in " $lower " ||
            "series:" in lower ||
            "tv series" in lower ||
            "tv shows" in lower ||
            "episode" in lower ||
            "episodes" in lower ||
            "season " in lower ||
            "serial" in lower ||
            "مسلسل" in lower ||
            "مسلسلات" in lower ||
            Regex("""\bs\d{1,2}\s*e\d{1,3}\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(lower) ||
            Regex("""\b\d{1,2}x\d{1,3}\b""")
                .containsMatchIn(lower)

        val looksMovie =
            "/movie/" in lower ||
            " movie " in " $lower " ||
            " movies " in " $lower " ||
            " film " in " $lower " ||
            " films " in " $lower " ||
            " cinema " in " $lower " ||
            " vod " in " $lower " ||
            "video on demand" in lower ||
            "افلام" in lower ||
            "أفلام" in lower ||
            "فيلم" in lower ||
            "سينما" in lower ||
            Regex(
                """\.(mp4|mkv|avi|mov|m4v|wmv|flv|webm|mpg|mpeg)(?:\?|$)""",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(url)

        val type = when {
            looksSeries -> MediaType.SERIES
            looksMovie -> MediaType.MOVIE
            else -> MediaType.LIVE
        }

        return MediaItemModel(
            id = sha1("$url|$name").take(16),
            name = name,
            streamUrl = url,
            logoUrl = logo,
            categoryId = group,
            type = type,
            meta = group
        )
    }

    private fun attr(line: String, key: String): String =
        Regex(
            """${Regex.escape(key)}\s*=\s*"([^"]*)"""",
            RegexOption.IGNORE_CASE
        ).find(line)?.groupValues?.getOrNull(1).orEmpty()

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
