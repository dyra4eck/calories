package com.dyra.calories

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Замеры тела: обхваты по типам, график динамики (см). */
class MeasurementsActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var chart: WeightChartView
    private lateinit var currentText: TextView
    private lateinit var typeSpinner: Spinner

    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))

    private val type: Int
        get() = typeSpinner.selectedItemPosition

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_measurements)

        store = Store(this)
        chart = findViewById(R.id.measureChart)
        currentText = findViewById(R.id.measureCurrentText)
        typeSpinner = findViewById(R.id.measureTypeSpinner)

        typeSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.measure_types)
        )
        typeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) = refresh()
            override fun onNothingSelected(p: AdapterView<*>?) = Unit
        }

        findViewById<Button>(R.id.addMeasureButton).setOnClickListener {
            showMeasureDialog(LocalDate.now(), null)
        }
        findViewById<Button>(R.id.measureHistoryButton).setOnClickListener { showHistory() }

        refresh()
    }

    private fun refresh() {
        val points = store.measurements(type)
        chart.setData(points, 0.0)
        val last = points.lastOrNull()
        currentText.text = if (last != null) {
            getString(R.string.measure_current, fmt(last.second), last.first.format(dateFormat))
        } else {
            getString(R.string.measure_no_data)
        }
    }

    private fun showMeasureDialog(date: LocalDate, existing: Double?) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.measure_cm_hint)
            existing?.let { setText(fmt(it)) }
        }
        AlertDialog.Builder(this)
            .setTitle(resources.getStringArray(R.array.measure_types)[type])
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val value = parseNum(input.text.toString())
                if (value == null || value <= 0) {
                    Toast.makeText(this, R.string.measure_invalid, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                store.setMeasurement(type, date, round1(value))
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showHistory() {
        val points = store.measurements(type).reversed()
        if (points.isEmpty()) {
            Toast.makeText(this, R.string.measure_no_data, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = points
            .map { (date, value) -> "${date.format(dateFormat)} — ${fmt(value)} см" }
            .toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.weight_history)
            .setItems(labels) { _, which ->
                val (date, value) = points[which]
                AlertDialog.Builder(this)
                    .setTitle(labels[which])
                    .setItems(
                        arrayOf(getString(R.string.edit_entry), getString(R.string.delete))
                    ) { _, action ->
                        when (action) {
                            0 -> showMeasureDialog(date, value)
                            1 -> {
                                store.removeMeasurement(type, date)
                                refresh()
                            }
                        }
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
