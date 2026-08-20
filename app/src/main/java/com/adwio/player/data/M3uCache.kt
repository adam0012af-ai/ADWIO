package com.adwio.player.data

import android.content.Context
import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.data.model.MediaType
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class M3uCache(private val context: Context) {

    private val client = M3uClient()
    private val maxAgeMs = TimeUnit.HOURS.toMillis(6)

    companion object {
        /*
         * One in-memory parsed playlist per URL.
         * More importantly, one lock per URL prevents Login warmup + Library
         * from opening multiple simultaneous provider requests.
         */
        private val memory = ConcurrentHashMap<String, List<MediaItemModel>>()
        private val locks = ConcurrentHashMap<String, Any>()
    }

    fun loadFast(url: String, maxItems: Int = 1200): List<MediaItemModel> {
        val key = key(url)
        memory[key]?.takeIf { it.isNotEmpty() }?.let { return it }

        val cache = cacheFile(url)
        val cached = readValidCache(cache)
        if (cached.isNotEmpty()) {
            memory[key] = cached
            return cached
        }

        return runCatching { client.loadPartial(url, maxItems) }
            .getOrDefault(emptyList())
    }

    fun loadForType(
        url: String,
        type: MediaType,
        maxItems: Int = Int.MAX_VALUE
    ): List<MediaItemModel> {
        val full = fullPlaylist(url)
        if (full.isEmpty()) return emptyList()

        val typed = full.filter { it.type == type }
        return if (maxItems == Int.MAX_VALUE) typed else typed.take(maxItems)
    }

    /**
     * Single-flight full playlist load.
     *
     * This fixes providers that return empty/failed results when ADWIO opens
     * a background warmup request and a Library request at the same time.
     */
    private fun fullPlaylist(url: String): List<MediaItemModel> {
        val normalized = normalize(url)
        if (normalized.isBlank()) return emptyList()

        val k = key(normalized)
        memory[k]?.takeIf { it.isNotEmpty() }?.let { return it }

        val lock = locks.getOrPut(k) { Any() }

        synchronized(lock) {
            memory[k]?.takeIf { it.isNotEmpty() }?.let { return it }

            val cache = cacheFile(normalized)
            val fresh = cache.exists() &&
                System.currentTimeMillis() - cache.lastModified() < maxAgeMs

            if (fresh) {
                val cached = readValidCache(cache)
                if (cached.isNotEmpty()) {
                    memory[k] = cached
                    return cached
                }
                cache.delete()
            }

            // First choice: one full download to disk, then parse locally.
            val downloaded = runCatching {
                client.downloadTo(normalized, cache)
            }.getOrDefault(false)

            if (downloaded) {
                val parsed = readValidCache(cache)
                if (parsed.isNotEmpty()) {
                    memory[k] = parsed
                    return parsed
                }
                cache.delete()
            }

            // Fallback: one full direct network parse.
            val direct = runCatching {
                client.load(normalized)
            }.getOrDefault(emptyList())

            if (direct.isNotEmpty()) {
                memory[k] = direct
                return direct
            }

            // Final fallback: partial list at least keeps Live usable.
            val partial = runCatching {
                client.loadPartial(normalized, 1800)
            }.getOrDefault(emptyList())

            if (partial.isNotEmpty()) memory[k] = partial
            return partial
        }
    }

    fun warm(url: String): List<MediaItemModel> = fullPlaylist(url)

    fun load(url: String, forceRefresh: Boolean = false): List<MediaItemModel> {
        if (forceRefresh) {
            invalidate(url)
        }
        return fullPlaylist(url)
    }

    fun hasCache(url: String): Boolean {
        val k = key(url)
        if (memory[k]?.isNotEmpty() == true) return true
        return readValidCache(cacheFile(url)).isNotEmpty()
    }

    fun clear() {
        memory.clear()
        File(context.cacheDir, "m3u").deleteRecursively()
    }

    private fun invalidate(url: String) {
        memory.remove(key(url))
        cacheFile(url).delete()
    }

    private fun readValidCache(file: File): List<MediaItemModel> {
        if (!file.exists() || file.length() < 16L) return emptyList()

        val parsed = runCatching {
            client.parseFile(file)
        }.getOrDefault(emptyList())

        if (parsed.isEmpty()) file.delete()
        return parsed
    }

    private fun normalize(url: String): String =
        url.trim().replace("&amp;", "&", ignoreCase = true)

    private fun key(url: String): String {
        val normalized = normalize(url)
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it) }
    }

    private fun cacheFile(url: String): File {
        val dir = File(context.cacheDir, "m3u").apply { mkdirs() }
        return File(dir, "${key(url)}.m3u")
    }
}
