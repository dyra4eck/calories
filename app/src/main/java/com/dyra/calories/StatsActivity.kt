package com.dyra.calories

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Статистика за последние 7 дней: график калорий и средние БЖУ. */
class StatsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        val store = Store(this)
        val today = LocalDate.now()
        val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
        val entriesByDay = days.map { store.entriesFor(it) }

        val labels = days.map {
            it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru"))
        }
        val kcals = entriesByDay.map { list -> list.sumOf { it.kcal } }

        findViewById<WeekChartView>(R.id.weekChart)
            .setData(labels, kcals, store.goal, days.size - 1)

        val daysWithEntries = entriesByDay.count { it.isNotEmpty() }
        val totalKcal = kcals.sum()
        val divider = if (daysWithEntries > 0) daysWithEntries else 1

        findViewById<TextView>(R.id.statsSummary).text = getString(
            R.string.stats_summary,
            daysWithEntries,
            totalKcal,
            (totalKcal.toDouble() / divider).roundToInt(),
            fmt(round1(entriesByDay.sumOf { list -> list.sumOf { it.protein } } / divider)),
            fmt(round1(entriesByDay.sumOf { list -> list.sumOf { it.fat } } / divider)),
            fmt(round1(entriesByDay.sumOf { list -> list.sumOf { it.carbs } } / divider))
        )
    }
}
