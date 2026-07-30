package com.dyra.calories

/** Подход: вес снаряда (кг) × повторы. */
data class WorkoutSet(val weight: Double, val reps: Int)

/** Упражнение в тренировке с списком подходов. */
data class WorkoutExercise(
    val name: String,
    val sets: MutableList<WorkoutSet> = mutableListOf()
)

/** Расчётный одноповторный максимум по формуле Эпли. */
fun oneRepMax(weight: Double, reps: Int): Double =
    if (reps <= 1) weight else weight * (1 + reps / 30.0)

/** Короткая запись подхода: «80×8». */
fun setLabel(set: WorkoutSet): String = "${fmt(set.weight)}×${set.reps}"
