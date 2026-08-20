package com.adwio.player.data

import java.net.URI
import java.net.URLDecoder

data class M3uXtreamCredentials(
    val baseUrl: String,
    val username: String,
    val password: String
)

object M3uXtreamResolver {

    /**
     * Recognises Xtream-generated M3U URLs such as:
     * /get.php?username=...&password=...&type=m3u_plus&output=ts
     *
     * Nothing is hard-coded for a provider. Credentials are read from the URL
     * supplied by the user and are then handled by the existing XtreamClient.
     */
    fun parse(rawUrl: String): M3uXtreamCredentials? {
        val value = rawUrl.trim().replace("&amp;", "&", ignoreCase = true)
        if (value.isBlank()) return null

        return runCatching {
            val uri = URI(value)
            if (uri.host.isNullOrBlank()) return@runCatching null

            val params = LinkedHashMap<String, String>()
            uri.rawQuery.orEmpty()
                .split('&')
                .filter { it.isNotBlank() }
                .forEach { part ->
                    val key = part.substringBefore('=').lowercase()
                    val raw = part.substringAfter('=', "")
                    params[key] = URLDecoder.decode(raw, Charsets.UTF_8.name())
                }

            val username = params["username"].orEmpty()
            val password = params["password"].orEmpty()
            if (username.isBlank() || password.isBlank()) return@runCatching null

            val scheme = uri.scheme?.takeIf { it.equals("http", true) || it.equals("https", true) }
                ?: "http"
            val port = if (uri.port >= 0) ":${uri.port}" else ""
            val baseUrl = "$scheme://${uri.host}$port"

            M3uXtreamCredentials(
                baseUrl = baseUrl.trimEnd('/'),
                username = username,
                password = password
            )
        }.getOrNull()
    }
}
