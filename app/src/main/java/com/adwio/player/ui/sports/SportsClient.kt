package com.adwio.player.ui.sports

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class SportsClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    fun loadMatches(dateFrom: String, dateTo: String): List<SportsMatch> {
        val days = datesBetween(dateFrom, dateTo)
        val all = mutableListOf<SportsMatch>()
        days.forEach { day ->
            all += loadDay(day)
        }
        return all.distinctBy { it.id }.sortedBy { it.utcDate }
    }

    private fun loadDay(day: String): List<SportsMatch> {
        val url = "https://www.sofascore.com/api/v1/sport/football/scheduled-events/$day"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", "ADWIO-Player/4.4")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank()) return@use emptyList()
                parse(raw)
            }
        }.getOrDefault(emptyList())
    }

    private fun parse(raw: String): List<SportsMatch> {
        val root = JSONObject(raw)
        val arr = root.optJSONArray("events") ?: return emptyList()
        val out = ArrayList<SportsMatch>(arr.length())

        for (i in 0 until arr.length()) {
            val e = arr.optJSONObject(i) ?: continue
            val home = e.optJSONObject("homeTeam")
            val away = e.optJSONObject("awayTeam")
            val tournament = e.optJSONObject("tournament")
            val unique = tournament?.optJSONObject("uniqueTournament")
            val statusObj = e.optJSONObject("status")
            val homeScoreObj = e.optJSONObject("homeScore")
            val awayScoreObj = e.optJSONObject("awayScore")
            val startTimestamp = e.optLong("startTimestamp", 0L)

            if (startTimestamp <= 0L) continue

            val homeId = home?.optLong("id", 0L) ?: 0L
            val awayId = away?.optLong("id", 0L) ?: 0L

            out += SportsMatch(
                id = e.optLong("id"),
                competition = unique?.optString("name").orEmpty()
                    .ifBlank { tournament?.optString("name").orEmpty() }
                    .ifBlank { "Football" },
                homeTeam = home?.optString("name").orEmpty().ifBlank { "Home" },
                awayTeam = away?.optString("name").orEmpty().ifBlank { "Away" },
                homeCrest = if (homeId > 0) "https://api.sofascore.app/api/v1/team/$homeId/image" else null,
                awayCrest = if (awayId > 0) "https://api.sofascore.app/api/v1/team/$awayId/image" else null,
                utcDate = isoUtc(startTimestamp * 1000L),
                status = statusObj?.optString("type").orEmpty().uppercase(Locale.US),
                homeScore = homeScoreObj?.takeIf { it.has("current") }?.optInt("current"),
                awayScore = awayScoreObj?.takeIf { it.has("current") }?.optInt("current")
            )
        }
        return out
    }

    private fun isoUtc(ms: Long): String {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        return f.format(Date(ms))
    }

    private fun datesBetween(from: String, to: String): List<String> {
        val f = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val start = f.parse(from) ?: return listOf(from)
        val end = f.parse(to) ?: return listOf(from)
        val out = mutableListOf<String>()
        var t = start.time
        while (t <= end.time && out.size < 8) {
            out += f.format(Date(t))
            t += 86_400_000L
        }
        return out
    }
}
