package com.adwio.player.data

import android.content.Context
import com.adwio.player.data.model.MediaItemModel
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class M3uCache(private val context: Context) {
    private val client = M3uClient()
    private val maxAgeMs = TimeUnit.HOURS.toMillis(6)

    fun load(url: String, forceRefresh: Boolean = false): List<MediaItemModel> {
        val cache = cacheFile(url)
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < maxAgeMs
        if (!forceRefresh && fresh) {
            return runCatching { client.parseFile(cache) }.getOrDefault(emptyList())
        }
        val downloaded = runCatching { client.downloadTo(url, cache) }.getOrDefault(false)
        val parsed = when {
            downloaded && cache.exists() -> runCatching { client.parseFile(cache) }.getOrDefault(emptyList())
            cache.exists() -> runCatching { client.parseFile(cache) }.getOrDefault(emptyList())
            else -> emptyList()
        }
        return if (parsed.isNotEmpty()) parsed else runCatching { client.load(url) }.getOrDefault(emptyList())
    }

    fun clear() {
        File(context.cacheDir, "m3u").deleteRecursively()
    }

    private fun cacheFile(url: String): File {
        val dir = File(context.cacheDir, "m3u").apply { mkdirs() }
        val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.m3u")
    }
}
