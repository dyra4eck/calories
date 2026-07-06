package com.dyra.calories

import android.os.Bundle
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Статистика за 7 или 30 дней: графики калорий и БЖУ, средние значения. */
class StatsActivity : AppCompatActivity() {

    private lateinit var store: Store

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stats)

        store = Store(this)

        findViewById<RadioGroup>(R.id.periodGroup).setOnCheckedChangeListener { _, checkedId ->
            load(if (checkedId == R.id.period30) 30 else 7)
        }
        load(7)
    }

    private fun load(daysCount: Int) {
        val today = LocalDate.now()
        val days = (daysCount - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val entriesByDay = days.map { store.entriesFor(it) }

        val labels = days.mapIndexed { i, day ->
            if (daysCount == 7) {
                day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru"))
            } else {
                if (i % 5 == 0 || i == days.lastIndex) day.dayOfMonth.toString() else ""
            }
        }

        val kcals = entriesByDay.map { list -> list.sumOf { it.kcal } }
        val protein = entriesByDay.map { list -> list.sumOf { it.protein } }
        val fat = entriesByDay.map { list -> list.sumOf { it.fat } }
        val carbs = entriesByDay.map { list -> list.sumOf { it.carbs } }

        findViewById<WeekChartView>(R.id.weekChart)
            .setData(labels, kcals, store.goal, days.size - 1)
        findViewById<MacrosChartView>(R.id.macrosChart)
            .setData(labels, protein, fat, carbs)

        val daysWithEntries = entriesByDay.count { it.isNotEmpty() }
        val totalKcal = kcals.sum()
        val divider = if (daysWithEntries > 0) daysWithEntries else 1

        findViewById<TextView>(R.id.statsSummary).text = getString(
            R.string.stats_summary,
            daysWithEntries,
            daysCount,
            totalKcal,
            (totalKcal.toDouble() / divider).roundToInt(),
            fmt(round1(protein.sum() / divider)),
            fmt(round1(fat.sum() / divider)),
            fmt(round1(carbs.sum() / divider))
        )
    }
}
