package com.adwio.player.ui.sports

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.adwio.player.R
import com.squareup.picasso.Picasso
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class SportsAdapter(
    private val favorites: SportsFavorites,
    private val onReminder: (SportsMatch) -> Unit
) : RecyclerView.Adapter<SportsAdapter.VH>() {

    private val items = mutableListOf<SportsMatch>()

    fun submit(newItems: List<SportsMatch>) {
        items.clear()
        items.addAll(newItems.sortedBy { it.utcDate })
        notifyDataSetChanged()
    }

    inner class VH(
        val root: LinearLayout,
        val competition: TextView,
        val homeLogo: ImageView,
        val home: TextView,
        val time: TextView,
        val state: TextView,
        val away: TextView,
        val awayLogo: ImageView,
        val channel: TextView,
        val fav: TextView,
        val bell: TextView
    ) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val c = parent.context

        fun tv(size: Float, color: Int = R.color.adwio_text) = TextView(c).apply {
            setTextColor(ContextCompat.getColor(c, color))
            textSize = size
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            gravity = Gravity.CENTER_VERTICAL
        }

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(c, 10), dp(c, 6), dp(c, 8), dp(c, 6))
            background = ContextCompat.getDrawable(c, R.drawable.bg_focus)
            isFocusable = true
            isClickable = true
        }

        val competition = tv(8.5f, R.color.adwio_muted)
        val homeLogo = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        val home = tv(10.5f).apply {
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }

        val center = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val time = tv(14f).apply {
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val state = tv(7.5f, R.color.adwio_muted).apply { gravity = Gravity.CENTER }

        center.addView(
            time,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 24))
        )
        center.addView(
            state,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 16))
        )

        val away = tv(10.5f).apply { typeface = Typeface.DEFAULT_BOLD }
        val awayLogo = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        val channel = tv(8f, R.color.adwio_muted)
        val fav = tv(15f).apply { gravity = Gravity.CENTER }
        val bell = tv(14f).apply {
            gravity = Gravity.CENTER
            text = "🔔"
        }

        root.addView(competition, LinearLayout.LayoutParams(dp(c, 125), ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(homeLogo, LinearLayout.LayoutParams(dp(c, 30), dp(c, 30)))
        root.addView(home, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        root.addView(center, LinearLayout.LayoutParams(dp(c, 104), ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(away, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        root.addView(awayLogo, LinearLayout.LayoutParams(dp(c, 30), dp(c, 30)))
        root.addView(channel, LinearLayout.LayoutParams(dp(c, 150), ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(fav, LinearLayout.LayoutParams(dp(c, 34), ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(bell, LinearLayout.LayoutParams(dp(c, 38), ViewGroup.LayoutParams.MATCH_PARENT))

        return VH(root, competition, homeLogo, home, time, state, away, awayLogo, channel, fav, bell)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val m = items[position]
        val isArabic = Locale.getDefault().language.equals("ar", ignoreCase = true)

        h.competition.text =
            m.competition.takeIf { it.isNotBlank() }
                ?: if (isArabic) "كرة القدم" else "Football"

        h.home.text = m.homeTeam
        h.away.text = m.awayTeam

        when {
            m.status == "FINISHED" && m.homeScore != null && m.awayScore != null -> {
                h.time.text = "${m.homeScore} - ${m.awayScore}"
                h.state.text = if (isArabic) "انتهت" else "Finished"
            }

            m.status in setOf("LIVE", "IN_PLAY", "PAUSED") -> {
                h.time.text = if (isArabic) "مباشر" else "LIVE"
                h.state.text =
                    if (m.homeScore != null && m.awayScore != null) {
                        "${m.homeScore} - ${m.awayScore}"
                    } else {
                        if (isArabic) "جارية الآن" else "In progress"
                    }
            }

            else -> {
                h.time.text = localTime(m.utcDate)
                h.state.text = if (isArabic) "لم تبدأ" else "Not started"
            }
        }

        h.channel.text =
            if (m.broadcaster.isNullOrBlank()) {
                if (isArabic) "القناة غير معلنة" else "Channel not announced"
            } else {
                "📺 ${m.broadcaster}"
            }

        h.fav.text = if (favorites.isFavorite(m.id)) "★" else "☆"

        h.homeLogo.setImageResource(R.drawable.ic_adwio)
        h.awayLogo.setImageResource(R.drawable.ic_adwio)

        m.homeCrest?.let {
            Picasso.get().load(it).fit().centerInside().into(h.homeLogo)
        }
        m.awayCrest?.let {
            Picasso.get().load(it).fit().centerInside().into(h.awayLogo)
        }

        h.fav.setOnClickListener {
            h.fav.text = if (favorites.toggle(m.id)) "★" else "☆"
        }
        h.bell.setOnClickListener { onReminder(m) }
    }

    override fun getItemCount(): Int = items.size

    private fun localTime(utc: String): String {
        return runCatching {
            val input = SimpleDateFormat(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                Locale.US
            ).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

            val date = input.parse(utc) ?: return@runCatching "--:--"
            SimpleDateFormat("hh:mm a", Locale.getDefault()).format(date)
        }.getOrDefault("--:--")
    }

    private fun dp(c: Context, v: Int): Int =
        (v * c.resources.displayMetrics.density).toInt()
}
