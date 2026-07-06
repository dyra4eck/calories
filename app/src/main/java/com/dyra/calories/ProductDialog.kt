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
