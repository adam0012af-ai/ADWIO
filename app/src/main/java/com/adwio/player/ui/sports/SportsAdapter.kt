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
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    inner class VH(
        val root: LinearLayout,
        val competition: TextView,
        val homeLogo: ImageView,
        val home: TextView,
        val scoreTime: TextView,
        val away: TextView,
        val awayLogo: ImageView,
        val fav: TextView,
        val bell: TextView
    ) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val c = parent.context
        fun text(size: Float) = TextView(c).apply {
            setTextColor(ContextCompat.getColor(c, R.color.adwio_text))
            textSize = size
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
        }

        val root = LinearLayout(c).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(c,10), dp(c,5), dp(c,8), dp(c,5))
            background = ContextCompat.getDrawable(c, R.drawable.bg_focus)
            isFocusable = true
            isClickable = true
        }
        val competition = text(8f).apply {
            setTextColor(ContextCompat.getColor(c, R.color.adwio_muted))
        }
        val homeLogo = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        val home = text(10f).apply { typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        val scoreTime = text(10f).apply { gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD }
        val away = text(10f).apply { typeface = Typeface.DEFAULT_BOLD }
        val awayLogo = ImageView(c).apply { scaleType = ImageView.ScaleType.CENTER_INSIDE }
        val fav = text(16f).apply { gravity = Gravity.CENTER }
        val bell = text(15f).apply { gravity = Gravity.CENTER; text = "🔔" }

        root.addView(competition, LinearLayout.LayoutParams(dp(c,120), ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(homeLogo, LinearLayout.LayoutParams(dp(c,28), dp(c,28)))
        root.addView(home, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        root.addView(scoreTime, LinearLayout.LayoutParams(dp(c,90), ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(away, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        root.addView(awayLogo, LinearLayout.LayoutParams(dp(c,28), dp(c,28)))
        root.addView(fav, LinearLayout.LayoutParams(dp(c,34), ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(bell, LinearLayout.LayoutParams(dp(c,38), ViewGroup.LayoutParams.MATCH_PARENT))

        return VH(root, competition, homeLogo, home, scoreTime, away, awayLogo, fav, bell)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val m = items[position]
        h.competition.text = m.competition
        h.home.text = m.homeTeam
        h.away.text = m.awayTeam
        h.scoreTime.text = when {
            m.status == "FINISHED" && m.homeScore != null && m.awayScore != null -> "${m.homeScore}  -  ${m.awayScore}"
            m.status in setOf("LIVE","IN_PLAY","PAUSED") -> "مباشر"
            else -> localTime(m.utcDate)
        }
        h.fav.text = if (favorites.isFavorite(m.id)) "★" else "☆"

        h.homeLogo.setImageResource(R.drawable.ic_adwio)
        h.awayLogo.setImageResource(R.drawable.ic_adwio)
        m.homeCrest?.let { Picasso.get().load(it).fit().centerInside().into(h.homeLogo) }
        m.awayCrest?.let { Picasso.get().load(it).fit().centerInside().into(h.awayLogo) }

        h.fav.setOnClickListener {
            h.fav.text = if (favorites.toggle(m.id)) "★" else "☆"
        }
        h.bell.setOnClickListener { onReminder(m) }
    }

    override fun getItemCount(): Int = items.size

    private fun localTime(utc: String): String = runCatching {
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = input.parse(utc) ?: return@runCatching "--:--"
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    }.getOrDefault("--:--")

    private fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()
}
