package com.adwio.player.data

import android.content.Context
import com.adwio.player.data.model.MediaItemModel
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class M3uCache(private val context: Context) {
    private val client = M3uClient()
    private val maxAgeMs = TimeUnit.HOURS.toMillis(6)

    fun loadFast(url: String, maxItems: Int = 600): List<MediaItemModel> {
        val cache = cacheFile(url)
        if (cache.exists()) {
            val cached = runCatching { client.parseFile(cache) }.getOrDefault(emptyList())
            if (cached.isNotEmpty()) return cached
        }
        return runCatching { client.loadPartial(url, maxItems) }.getOrDefault(emptyList())
    }

    fun warm(url: String): List<MediaItemModel> {
        val cache = cacheFile(url)
        val fresh = cache.exists() &&
            System.currentTimeMillis() - cache.lastModified() < maxAgeMs

        if (fresh) {
            return runCatching { client.parseFile(cache) }.getOrDefault(emptyList())
        }

        val downloaded = runCatching { client.downloadTo(url, cache) }.getOrDefault(false)
        return when {
            downloaded && cache.exists() ->
                runCatching { client.parseFile(cache) }.getOrDefault(emptyList())
            cache.exists() ->
                runCatching { client.parseFile(cache) }.getOrDefault(emptyList())
            else ->
                emptyList()
        }
    }

    fun load(url: String, forceRefresh: Boolean = false): List<MediaItemModel> {
        if (forceRefresh) return warm(url)

        val cache = cacheFile(url)
        val fresh = cache.exists() &&
            System.currentTimeMillis() - cache.lastModified() < maxAgeMs

        if (fresh) {
            val cached = runCatching { client.parseFile(cache) }.getOrDefault(emptyList())
            if (cached.isNotEmpty()) return cached
        }

        val fast = loadFast(url)
        return if (fast.isNotEmpty()) fast else warm(url)
    }

    fun hasCache(url: String): Boolean = cacheFile(url).exists()

    fun clear() {
        File(context.cacheDir, "m3u").deleteRecursively()
    }

    private fun cacheFile(url: String): File {
        val dir = File(context.cacheDir, "m3u").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it) }

        return File(dir, "$hash.m3u")
    }
}
