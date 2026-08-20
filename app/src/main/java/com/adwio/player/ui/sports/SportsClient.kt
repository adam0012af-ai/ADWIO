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
        .callTimeout(28, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun loadMatches(dateFrom: String, dateTo: String): List<SportsMatch> {
        val days = datesBetween(dateFrom, dateTo)
        val result = mutableListOf<SportsMatch>()

        days.forEach { day ->
            val dayMatches =
                loadSofascore("https://api.sofascore.com/api/v1/sport/football/scheduled-events/$day")
                    .ifEmpty {
                        loadSofascore("https://www.sofascore.com/api/v1/sport/football/scheduled-events/$day")
                    }
                    .ifEmpty {
                        loadEspn(day.replace("-", ""))
                    }
            result += dayMatches
        }

        return result.distinctBy { it.id }.sortedBy { it.utcDate }
    }

    private fun request(url: String): String? = runCatching {
        val req = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("Accept-Language", "ar,en;q=0.8")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) ADWIO/5.0.3")
            .build()

        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body?.string()?.takeIf { it.isNotBlank() }
        }
    }.getOrNull()

    private fun loadSofascore(url: String): List<SportsMatch> {
        val raw = request(url) ?: return emptyList()
        return runCatching {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("events") ?: return@runCatching emptyList()
            val out = ArrayList<SportsMatch>(arr.length())

            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                val home = e.optJSONObject("homeTeam")
                val away = e.optJSONObject("awayTeam")
                val tournament = e.optJSONObject("tournament")
                val unique = tournament?.optJSONObject("uniqueTournament")
                val statusObj = e.optJSONObject("status")
                val homeScore = e.optJSONObject("homeScore")
                val awayScore = e.optJSONObject("awayScore")
                val start = e.optLong("startTimestamp", 0L)
                if (start <= 0L) continue

                val hid = home?.optLong("id", 0L) ?: 0L
                val aid = away?.optLong("id", 0L) ?: 0L

                val competitionName =
                    unique?.optString("name").orEmpty().trim()
                        .ifBlank { tournament?.optString("name").orEmpty().trim() }

                out += SportsMatch(
                    id = e.optLong("id"),
                    competition = competitionName,
                    homeTeam = home?.optString("name").orEmpty().ifBlank { "Home" },
                    awayTeam = away?.optString("name").orEmpty().ifBlank { "Away" },
                    homeCrest = if (hid > 0) "https://api.sofascore.com/api/v1/team/$hid/image" else null,
                    awayCrest = if (aid > 0) "https://api.sofascore.com/api/v1/team/$aid/image" else null,
                    utcDate = isoUtc(start * 1000L),
                    status = statusObj?.optString("type").orEmpty().uppercase(Locale.US),
                    homeScore = homeScore?.takeIf { it.has("current") }?.optInt("current"),
                    awayScore = awayScore?.takeIf { it.has("current") }?.optInt("current"),
                    broadcaster = null
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    private fun loadEspn(day: String): List<SportsMatch> {
        val raw = request(
            "https://site.api.espn.com/apis/site/v2/sports/soccer/all/scoreboard?dates=$day&limit=1000"
        ) ?: return emptyList()

        return runCatching {
            val root = JSONObject(raw)
            val arr = root.optJSONArray("events") ?: return@runCatching emptyList()
            val out = ArrayList<SportsMatch>(arr.length())

            for (i in 0 until arr.length()) {
                val event = arr.optJSONObject(i) ?: continue
                val comp = event.optJSONArray("competitions")?.optJSONObject(0) ?: continue
                val competitors = comp.optJSONArray("competitors") ?: continue

                var home: JSONObject? = null
                var away: JSONObject? = null
                for (j in 0 until competitors.length()) {
                    val c = competitors.optJSONObject(j) ?: continue
                    when (c.optString("homeAway")) {
                        "home" -> home = c
                        "away" -> away = c
                    }
                }
                if (home == null || away == null) continue

                val homeTeam = home!!.optJSONObject("team")
                val awayTeam = away!!.optJSONObject("team")
                val status = event.optJSONObject("status")?.optJSONObject("type")
                val league = event.optJSONObject("league")
                val date = event.optString("date")
                if (date.isBlank()) continue

                val competitionName =
                    league?.optString("name").orEmpty().trim()
                        .ifBlank { league?.optString("abbreviation").orEmpty().trim() }

                val broadcasts = comp.optJSONArray("broadcasts")
                val broadcaster = buildList {
                    if (broadcasts != null) {
                        for (j in 0 until broadcasts.length()) {
                            val names = broadcasts.optJSONObject(j)?.optJSONArray("names")
                            if (names != null) {
                                for (k in 0 until names.length()) {
                                    val n = names.optString(k).trim()
                                    if (n.isNotBlank()) add(n)
                                }
                            }
                        }
                    }
                }.distinct().joinToString(" • ").ifBlank { null }

                out += SportsMatch(
                    id = event.optString("id").hashCode().toLong().let { if (it < 0) -it else it },
                    competition = competitionName,
                    homeTeam = homeTeam?.optString("displayName").orEmpty().ifBlank { "Home" },
                    awayTeam = awayTeam?.optString("displayName").orEmpty().ifBlank { "Away" },
                    homeCrest = homeTeam?.optString("logo")?.takeIf { it.isNotBlank() },
                    awayCrest = awayTeam?.optString("logo")?.takeIf { it.isNotBlank() },
                    utcDate = normalizeIso(date),
                    status = when {
                        status?.optBoolean("completed") == true -> "FINISHED"
                        status?.optString("state") == "in" -> "LIVE"
                        else -> "SCHEDULED"
                    },
                    homeScore = home!!.optString("score").toIntOrNull(),
                    awayScore = away!!.optString("score").toIntOrNull(),
                    broadcaster = broadcaster
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    private fun normalizeIso(value: String): String {
        return runCatching {
            val candidates = listOf(
                "yyyy-MM-dd'T'HH:mm'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
            )
            for (pattern in candidates) {
                val f = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val d = runCatching { f.parse(value) }.getOrNull()
                if (d != null) return@runCatching isoUtc(d.time)
            }
            value
        }.getOrDefault(value)
    }

    private fun isoUtc(ms: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(ms))

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
