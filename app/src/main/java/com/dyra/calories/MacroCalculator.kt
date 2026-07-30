package com.dyra.calories

import kotlin.math.roundToInt

/**
 * Расчёт дневных калорий и БЖУ по параметрам тела.
 *
 * Основной обмен (BMR) — формула Миффлина-Сан Жеора, суточный расход (TDEE) —
 * BMR × коэффициент активности. Белки и жиры задаются в граммах на кг веса
 * (спортивные рекомендации), углеводы добирают остаток калорий.
 */
object MacroCalculator {

    /** Коэффициенты активности: сидячий → очень высокая. */
    val ACTIVITY_FACTORS = doubleArrayOf(1.2, 1.375, 1.55, 1.725, 1.9)

    enum class Goal { GAIN, MAINTAIN, LOSE }

    data class Plan(
        val goal: Goal,
        val kcal: Int,
        val protein: Int,
        val fat: Int,
        val carbs: Int
    )

    fun bmr(male: Boolean, weightKg: Double, heightCm: Int, ageYears: Int): Double =
        10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears + if (male) 5.0 else -161.0

    fun tdee(bmr: Double, activityIndex: Int): Double =
        bmr * ACTIVITY_FACTORS[activityIndex.coerceIn(0, ACTIVITY_FACTORS.lastIndex)]

    fun plan(goal: Goal, tdee: Double, weightKg: Double): Plan {
        val kcal: Double
        val proteinPerKg: Double
        val fatPerKg: Double
        when (goal) {
            // Профицит ~12% и повышенный белок для роста мышц
            Goal.GAIN -> {
                kcal = tdee * 1.12
                proteinPerKg = 1.8
                fatPerKg = 1.0
            }
            Goal.MAINTAIN -> {
                kcal = tdee
                proteinPerKg = 1.6
                fatPerKg = 1.0
            }
            // Дефицит ~15%; белок выше, чтобы сохранить мышцы
            Goal.LOSE -> {
                kcal = tdee * 0.85
                proteinPerKg = 2.0
                fatPerKg = 0.8
            }
        }
        val protein = (proteinPerKg * weightKg).roundToInt()
        val fat = (fatPerKg * weightKg).roundToInt()
        val carbs = ((kcal - protein * 4 - fat * 9) / 4).roundToInt().coerceAtLeast(0)
        return Plan(goal, kcal.roundToInt(), protein, fat, carbs)
    }
}
