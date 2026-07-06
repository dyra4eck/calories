package com.dyra.calories

data class Entry(
    val name: String,
    val kcal: Int,
    val time: String,
    val protein: Double = 0.0,
    val fat: Double = 0.0,
    val carbs: Double = 0.0
)
