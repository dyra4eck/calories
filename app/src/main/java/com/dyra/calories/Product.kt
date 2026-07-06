package com.dyra.calories

import java.util.Locale
import kotlin.math.roundToInt

/** Продукт из базы: значения указаны на 100 грамм. */
data class Product(
    val name: String,
    val kcal100: Double,
    val protein100: Double,
    val fat100: Double,
    val carbs100: Double,
    val barcode: String? = null
)

/** Округление до одного знака после запятой. */
fun round1(value: Double): Double = (value * 10).roundToInt() / 10.0

/** «12» вместо «12.0», иначе один знак после запятой. */
fun fmt(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString()
    else String.format(Locale.US, "%.1f", value)

/** Число из поля ввода: понимает и точку, и запятую. */
fun parseNum(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
