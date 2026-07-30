package com.dyra.calories

/** Приём пищи по времени суток: до 11 завтрак, до 16 обед, до 21 ужин, иначе перекус. */
fun mealForTime(time: String): Int {
    val hour = time.substringBefore(':').toIntOrNull() ?: return Entry.MEAL_SNACK
    return when {
        hour < 11 -> Entry.MEAL_BREAKFAST
        hour < 16 -> Entry.MEAL_LUNCH
        hour < 21 -> Entry.MEAL_DINNER
        else -> Entry.MEAL_SNACK
    }
}

data class Entry(
    val name: String,
    val kcal: Int,
    val time: String,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0,
    /** Приём пищи: MEAL_BREAKFAST..MEAL_SNACK; по умолчанию — по времени. */
    val meal: Int = mealForTime(time)
) {
    companion object {
        const val MEAL_BREAKFAST = 0
        const val MEAL_LUNCH = 1
        const val MEAL_DINNER = 2
        const val MEAL_SNACK = 3
        const val MEAL_COUNT = 4
    }
}
