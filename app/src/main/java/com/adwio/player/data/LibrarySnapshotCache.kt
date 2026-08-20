package com.adwio.player.data

import android.content.Context
import com.adwio.player.data.model.CategoryModel
import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.data.model.MediaType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class LibrarySnapshotCache(private val context: Context) {
    data class Snapshot(
        val categories: List<CategoryModel>,
        val items: List<MediaItemModel>,
        val savedAt: Long
    )

    private val freshForMs = TimeUnit.MINUTES.toMillis(30)

    fun load(source: String, type: MediaType): Snapshot? {
        val file = fileFor(source, type)
        if (!file.exists() || file.length() < 10L) return null
        return runCatching {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val savedAt = root.optLong("savedAt", 0L)
            val catsJson = root.optJSONArray("categories") ?: JSONArray()
            val itemsJson = root.optJSONArray("items") ?: JSONArray()

            val categories = buildList {
                for (i in 0 until catsJson.length()) {
                    val o = catsJson.optJSONObject(i) ?: continue
                    add(CategoryModel(o.optString("id"), o.optString("name")))
                }
            }

            val items = buildList {
                for (i in 0 until itemsJson.length()) {
                    val o = itemsJson.optJSONObject(i) ?: continue
                    val id = o.optString("id")
                    val name = o.optString("name")
                    val url = o.optString("streamUrl")
                    val itemType = runCatching {
                        MediaType.valueOf(o.optString("type", type.name))
                    }.getOrDefault(type)
                    if (id.isBlank() || name.isBlank()) continue
                    add(
                        MediaItemModel(
                            id = id,
                            name = name,
                            streamUrl = url,
                            logoUrl = o.optString("logoUrl").takeIf { it.isNotBlank() },
                            categoryId = o.optString("categoryId").takeIf { it.isNotBlank() },
                            type = itemType,
                            meta = o.optString("meta").takeIf { it.isNotBlank() },
                            addedAt = o.optLong("addedAt", 0L)
                        )
                    )
                }
            }

            if (items.isEmpty()) null else Snapshot(categories, items, savedAt)
        }.getOrNull()
    }

    fun isFresh(snapshot: Snapshot): Boolean =
        snapshot.savedAt > 0L && System.currentTimeMillis() - snapshot.savedAt < freshForMs

    fun save(
        source: String,
        type: MediaType,
        categories: List<CategoryModel>,
        items: List<MediaItemModel>
    ) {
        if (items.isEmpty()) return
        runCatching {
            val root = JSONObject()
            root.put("savedAt", System.currentTimeMillis())

            val cats = JSONArray()
            categories.forEach { c ->
                cats.put(JSONObject().put("id", c.id).put("name", c.name))
            }
            root.put("categories", cats)

            val rows = JSONArray()
            items.forEach { item ->
                rows.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("name", item.name)
                        .put("streamUrl", item.streamUrl)
                        .put("logoUrl", item.logoUrl ?: "")
                        .put("categoryId", item.categoryId ?: "")
                        .put("type", item.type.name)
                        .put("meta", item.meta ?: "")
                        .put("addedAt", item.addedAt)
                )
            }
            root.put("items", rows)

            val target = fileFor(source, type)
            val tmp = File(target.parentFile, "${target.name}.tmp")
            tmp.writeText(root.toString(), Charsets.UTF_8)
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }
    }

    fun clear() {
        File(context.cacheDir, "library_snapshots").deleteRecursively()
    }

    private fun fileFor(source: String, type: MediaType): File {
        val dir = File(context.cacheDir, "library_snapshots").apply { mkdirs() }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.trim().toByteArray())
            .take(10)
            .joinToString("") { "%02x".format(it) }
        return File(dir, "${digest}_${type.name.lowercase()}.json")
    }
}
