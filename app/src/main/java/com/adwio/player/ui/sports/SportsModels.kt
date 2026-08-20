package com.adwio.player.ui.sports

data class SportsMatch(
    val id: Long,
    val competition: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeCrest: String?,
    val awayCrest: String?,
    val utcDate: String,
    val status: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val broadcaster: String? = null
)
