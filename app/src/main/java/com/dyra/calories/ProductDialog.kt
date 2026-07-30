package com.dyra.calories

import android.app.Activity
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/** Общий диалог создания/редактирования продукта (КБЖУ на 100 г). */
object ProductDialog {

    fun show(
        activity: Activity,
        titleRes: Int,
        initial: Product?,
        barcode: String? = null,
        onSave: (Product) -> Unit
    ) {
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_food, null)

        val effectiveBarcode = barcode ?: initial?.barcode
        view.findViewById<TextView>(R.id.foodSubtitle).text = if (effectiveBarcode != null) {
            activity.getString(R.string.per_100g_with_barcode, effectiveBarcode)
        } else {
            activity.getString(R.string.per_100g)
        }

        val nameInput = view.findViewById<EditText>(R.id.foodName)
        val kcalInput = view.findViewById<EditText>(R.id.foodKcal)
        val proteinInput = view.findViewById<EditText>(R.id.foodProtein)
        val fatInput = view.findViewById<EditText>(R.id.foodFat)
        val carbsInput = view.findViewById<EditText>(R.id.foodCarbs)

        if (initial != null) {
            nameInput.setText(initial.name)
            kcalInput.setText(fmt(initial.kcal100))
            proteinInput.setText(fmt(initial.protein100))
            fatInput.setText(fmt(initial.fat100))
            carbsInput.setText(fmt(initial.carbs100))
        }

        AlertDialog.Builder(activity)
            .setTitle(titleRes)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = nameInput.text.toString().trim()
                val kcal = parseNum(kcalInput.text.toString())
                if (name.isEmpty()) {
                    Toast.makeText(activity, R.string.name_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (kcal == null || kcal < 0) {
                    Toast.makeText(activity, R.string.enter_kcal, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                onSave(
                    Product(
                        name = name,
                        kcal100 = kcal,
                        protein100 = parseNum(proteinInput.text.toString()) ?: 0.0,
                        fat100 = parseNum(fatInput.text.toString()) ?: 0.0,
                        carbs100 = parseNum(carbsInput.text.toString()) ?: 0.0,
                        barcode = effectiveBarcode
                    )
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

/** Неблокирующий диалог «идёт загрузка…»; отмена — кнопкой «назад». */
fun showProgressDialog(activity: Activity, textRes: Int): AlertDialog {
    val view = LayoutInflater.from(activity).inflate(R.layout.dialog_progress, null)
    view.findViewById<TextView>(R.id.progressText).setText(textRes)
    return AlertDialog.Builder(activity)
        .setView(view)
        .setCancelable(true)
        .show()
}

/** Диалоги поиска продукта в общей базе (Open Food Facts). */
object OnlineSearchDialog {

    fun show(activity: Activity, onPicked: (Product) -> Unit) {
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.online_search_hint)
        }
        AlertDialog.Builder(activity)
            .setTitle(R.string.online_search_title)
            .setView(input)
            .setPositiveButton(R.string.search_word) { _, _ ->
                val query = input.text.toString().trim()
                if (query.isNotEmpty()) runSearch(activity, query, onPicked)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun runSearch(activity: Activity, query: String, onPicked: (Product) -> Unit) {
        val progress = showProgressDialog(activity, R.string.online_search_progress)
        FoodFacts.search(query) { results ->
            if (activity.isFinishing || !progress.isShowing) return@search
            progress.dismiss()
            when {
                results == null ->
                    Toast.makeText(activity, R.string.online_error, Toast.LENGTH_SHORT).show()
                results.isEmpty() ->
                    Toast.makeText(activity, R.string.online_search_empty, Toast.LENGTH_LONG).show()
                else -> showResults(activity, results, onPicked)
            }
        }
    }

    private fun showResults(activity: Activity, results: List<Product>, onPicked: (Product) -> Unit) {
        val labels = results
            .map { "${it.name} — ${fmt(it.kcal100)} ккал/100 г" }
            .toTypedArray()
        AlertDialog.Builder(activity)
            .setTitle(R.string.online_search_title)
            .setItems(labels) { _, which ->
                // Даём проверить и поправить КБЖУ перед сохранением в свою базу
                ProductDialog.show(activity, R.string.add_product, results[which]) { product ->
                    onPicked(product)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
