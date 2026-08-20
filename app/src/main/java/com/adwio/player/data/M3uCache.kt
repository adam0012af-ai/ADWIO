package com.adwio.player.data

import android.content.Context
import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.data.model.MediaType
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class M3uCache(private val context: Context) {

    private val client = M3uClient()
    private val maxAgeMs = TimeUnit.HOURS.toMillis(6)

    fun loadFast(url: String, maxItems: Int = 1200): List<MediaItemModel> {
        val cache = cacheFile(url)

        val cached = readValidCache(cache)
        if (cached.isNotEmpty()) return cached

        return runCatching {
            client.loadPartial(url, maxItems)
        }.getOrDefault(emptyList())
    }

    /**
     * Reliable section loader.
     *
     * Order:
     * 1) Valid local full cache.
     * 2) Full remote scan for requested type.
     * 3) Download full M3U to cache and filter locally.
     * 4) Direct full-list load and local filter.
     *
     * This prevents an M3U account from being shown as Active while
     * Live/Movies/Series are all empty because one network path failed.
     */
    fun loadForType(
        url: String,
        type: MediaType,
        maxItems: Int = Int.MAX_VALUE
    ): List<MediaItemModel> {

        val cache = cacheFile(url)

        val cached = readValidCache(cache)
        if (cached.isNotEmpty()) {
            val typed = cached.filter { it.type == type }
            if (typed.isNotEmpty()) {
                return if (maxItems == Int.MAX_VALUE) typed else typed.take(maxItems)
            }
        }

        val directType = runCatching {
            client.loadForType(url, type, maxItems)
        }.getOrDefault(emptyList())

        if (directType.isNotEmpty()) return directType

        // Force a fresh full download even when an old/corrupt cache exists.
        val downloaded = runCatching {
            client.downloadTo(url, cache)
        }.getOrDefault(false)

        if (downloaded) {
            val full = readValidCache(cache)
            val typed = full.filter { it.type == type }
            if (typed.isNotEmpty()) {
                return if (maxItems == Int.MAX_VALUE) typed else typed.take(maxItems)
            }
        }

        val fullDirect = runCatching {
            client.load(url)
        }.getOrDefault(emptyList())

        if (fullDirect.isNotEmpty()) {
            val typed = fullDirect.filter { it.type == type }
            if (typed.isNotEmpty()) {
                return if (maxItems == Int.MAX_VALUE) typed else typed.take(maxItems)
            }
        }

        return emptyList()
    }

    fun warm(url: String): List<MediaItemModel> {
        val cache = cacheFile(url)

        val fresh =
            cache.exists() &&
            System.currentTimeMillis() - cache.lastModified() < maxAgeMs

        if (fresh) {
            val cached = readValidCache(cache)
            if (cached.isNotEmpty()) return cached

            // A fresh timestamp does not mean the cache is valid.
            cache.delete()
        }

        val downloaded = runCatching {
            client.downloadTo(url, cache)
        }.getOrDefault(false)

        if (downloaded) {
            val parsed = readValidCache(cache)
            if (parsed.isNotEmpty()) return parsed
            cache.delete()
        }

        // If disk caching fails, do not make the account unusable.
        return runCatching {
            client.load(url)
        }.getOrDefault(emptyList())
    }

    fun load(
        url: String,
        forceRefresh: Boolean = false
    ): List<MediaItemModel> {

        if (forceRefresh) {
            cacheFile(url).delete()
            return warm(url)
        }

        val cache = cacheFile(url)
        val fresh =
            cache.exists() &&
            System.currentTimeMillis() - cache.lastModified() < maxAgeMs

        if (fresh) {
            val cached = readValidCache(cache)
            if (cached.isNotEmpty()) return cached
            cache.delete()
        }

        val fast = loadFast(url)
        if (fast.isNotEmpty()) return fast

        return warm(url)
    }

    fun hasCache(url: String): Boolean =
        readValidCache(cacheFile(url)).isNotEmpty()

    fun clear() {
        File(context.cacheDir, "m3u").deleteRecursively()
    }

    private fun readValidCache(file: File): List<MediaItemModel> {
        if (!file.exists() || file.length() < 16L) return emptyList()

        val parsed = runCatching {
            client.parseFile(file)
        }.getOrDefault(emptyList())

        if (parsed.isEmpty()) {
            file.delete()
        }

        return parsed
    }

    private fun cacheFile(url: String): File {
        val dir = File(context.cacheDir, "m3u").apply { mkdirs() }

        val normalized = url.trim()
            .replace("&amp;", "&", ignoreCase = true)

        val hash = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray())
            .take(12)
            .joinToString("") { "%02x".format(it) }

        return File(dir, "$hash.m3u")
    }
}
