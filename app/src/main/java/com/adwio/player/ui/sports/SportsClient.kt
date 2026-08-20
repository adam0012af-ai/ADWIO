package com.adwio.player.ui.sports

import com.adwio.player.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SportsClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(35, TimeUnit.SECONDS)
        .build()

    fun loadMatches(dateFrom: String, dateTo: String): List<SportsMatch> {
        val token = BuildConfig.FOOTBALL_DATA_TOKEN.trim()
        if (token.isBlank()) return emptyList()

        val url = "https://api.football-data.org/v4/matches?dateFrom=$dateFrom&dateTo=$dateTo"
        val request = Request.Builder()
            .url(url)
            .header("X-Auth-Token", token)
            .header("Accept", "application/json")
            .header("User-Agent", "ADWIO-Player/4.3")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return@use emptyList()
                parse(body)
            }
        }.getOrDefault(emptyList())
    }

    private fun parse(raw: String): List<SportsMatch> {
        val root = JSONObject(raw)
        val arr = root.optJSONArray("matches") ?: return emptyList()
        val out = ArrayList<SportsMatch>(arr.length())

        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val home = m.optJSONObject("homeTeam")
            val away = m.optJSONObject("awayTeam")
            val competition = m.optJSONObject("competition")
            val score = m.optJSONObject("score")?.optJSONObject("fullTime")

            out += SportsMatch(
                id = m.optLong("id"),
                competition = competition?.optString("name").orEmpty().ifBlank { "Football" },
                homeTeam = home?.optString("name").orEmpty().ifBlank { "Home" },
                awayTeam = away?.optString("name").orEmpty().ifBlank { "Away" },
                homeCrest = home?.optString("crest")?.takeIf { it.isNotBlank() && it != "null" },
                awayCrest = away?.optString("crest")?.takeIf { it.isNotBlank() && it != "null" },
                utcDate = m.optString("utcDate"),
                status = m.optString("status"),
                homeScore = score?.takeIf { !it.isNull("home") }?.optInt("home"),
                awayScore = score?.takeIf { !it.isNull("away") }?.optInt("away")
            )
        }
        return out.sortedBy { it.utcDate }
    }
}
