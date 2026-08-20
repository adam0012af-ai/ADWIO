package com.adwio.player.ui.sports

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object MatchReminderScheduler {
    fun schedule(context: Context, match: SportsMatch, minutesBefore: Int = 15): Boolean {
        val kickoff = parseUtc(match.utcDate) ?: return false
        val triggerAt = kickoff - minutesBefore * 60_000L
        if (triggerAt <= System.currentTimeMillis()) return false

        val intent = Intent(context, MatchReminderReceiver::class.java).apply {
            putExtra("title", "${match.homeTeam} × ${match.awayTeam}")
            putExtra("body", "تبدأ المباراة بعد $minutesBefore دقيقة • ${match.competition}")
            putExtra("notification_id", (match.id % Int.MAX_VALUE).toInt())
        }
        val pending = PendingIntent.getBroadcast(
            context,
            (match.id % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        return true
    }

    private fun parseUtc(value: String): Long? = runCatching {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        f.parse(value)?.time
    }.getOrNull()
}
