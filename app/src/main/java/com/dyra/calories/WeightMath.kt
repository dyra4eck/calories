package com.dyra.calories

import java.time.LocalDate

/** Тренд веса и оценка фактического расхода энергии. */
object WeightMath {

    /** Ккал в килограмме жировой ткани — для пересчёта дельты веса в калории. */
    private const val KCAL_PER_KG = 7700.0

    /** Экспоненциальное сглаживание: тренд без дневных скачков воды. */
    fun trend(values: List<Double>, alpha: Double = 0.3): List<Double> {
        if (values.isEmpty()) return emptyList()
        val result = ArrayList<Double>(values.size)
        var current = values.first()
        for (value in values) {
            current += alpha * (value - current)
            result.add(current)
        }
        return result
    }

    /**
     * Скорость изменения веса, кг/неделю: наклон линейной регрессии тренда
     * за последние windowDays. null — данных мало (< 2 точек или охват < 7 дней).
     */
    fun weeklyRate(points: List<Pair<LocalDate, Double>>, windowDays: Long = 28): Double? {
        if (points.size < 2) return null
        val trendValues = trend(points.map { it.second })
        val cutoff = LocalDate.now().minusDays(windowDays)
        val recent = points.indices
            .filter { !points[it].first.isBefore(cutoff) }
            .map { points[it].first.toEpochDay().toDouble() to trendValues[it] }
        if (recent.size < 2) return null
        val span = recent.last().first - recent.first().first
        if (span < 7) return null

        val meanX = recent.sumOf { it.first } / recent.size
        val meanY = recent.sumOf { it.second } / recent.size
        val denominator = recent.sumOf { (it.first - meanX) * (it.first - meanX) }
        if (denominator == 0.0) return null
        val slopePerDay = recent.sumOf { (it.first - meanX) * (it.second - meanY) } / denominator
        return slopePerDay * 7
    }

    data class RealTdee(
        /** Фактический суточный расход, ккал. */
        val tdee: Int,
        /** Средний рацион за период, ккал/день. */
        val avgIntake: Int,
        /** Скорость изменения веса за период, кг/неделю. */
        val ratePerWeek: Double,
        /** Дней с записанной едой в периоде. */
        val foodDays: Int
    )

    /**
     * Фактический расход по связке «съеденное + динамика веса» за последние
     * windowDays. null — мало данных: нужно ≥ 10 дней с едой и ≥ 2 взвешиваний
     * с охватом ≥ 10 дней внутри окна.
     */
    fun realTdee(store: Store, windowDays: Long = 21): RealTdee? {
        val today = LocalDate.now()
        val from = today.minusDays(windowDays - 1)

        var kcalSum = 0
        var foodDays = 0
        var day = from
        while (!day.isAfter(today)) {
            val kcal = store.entriesFor(day).sumOf { it.kcal }
            if (kcal > 0) {
                kcalSum += kcal
                foodDays++
            }
            day = day.plusDays(1)
        }
        if (foodDays < 10) return null
        val avgIntake = kcalSum / foodDays

        val weights = store.weights()
        val trendValues = trend(weights.map { it.second })
        val inWindow = weights.indices.filter { !weights[it].first.isBefore(from) }
        if (inWindow.size < 2) return null
        val first = inWindow.first()
        val last = inWindow.last()
        val spanDays = weights[last].first.toEpochDay() - weights[first].first.toEpochDay()
        if (spanDays < 10) return null

        val deltaPerDay = (trendValues[last] - trendValues[first]) / spanDays
        val tdee = avgIntake - deltaPerDay * KCAL_PER_KG
        return RealTdee(
            tdee = tdee.toInt(),
            avgIntake = avgIntake,
            ratePerWeek = round1(deltaPerDay * 7),
            foodDays = foodDays
        )
    }
}
