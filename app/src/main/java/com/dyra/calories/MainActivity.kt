package com.dyra.calories

import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var adapter: EntryAdapter

    private lateinit var dateText: TextView
    private lateinit var totalText: TextView
    private lateinit var goalText: TextView
    private lateinit var remainingText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var nameInput: EditText
    private lateinit var kcalInput: EditText
    private lateinit var nextDayButton: ImageButton

    private var date: LocalDate = LocalDate.now()
    private val entries = mutableListOf<Entry>()

    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = Store(this)

        dateText = findViewById(R.id.dateText)
        totalText = findViewById(R.id.totalText)
        goalText = findViewById(R.id.goalText)
        remainingText = findViewById(R.id.remainingText)
        progressBar = findViewById(R.id.progressBar)
        emptyText = findViewById(R.id.emptyText)
        nameInput = findViewById(R.id.nameInput)
        kcalInput = findViewById(R.id.kcalInput)
        nextDayButton = findViewById(R.id.nextDayButton)

        val list = findViewById<RecyclerView>(R.id.entryList)
        adapter = EntryAdapter(entries) { position -> confirmDelete(position) }
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<ImageButton>(R.id.prevDayButton).setOnClickListener { shiftDate(-1) }
        nextDayButton.setOnClickListener { shiftDate(1) }
        findViewById<Button>(R.id.addButton).setOnClickListener { addEntry() }
        goalText.setOnClickListener { editGoal() }

        kcalInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addEntry()
                true
            } else {
                false
            }
        }

        loadDate()
    }

    private fun shiftDate(days: Long) {
        val newDate = date.plusDays(days)
        if (newDate.isAfter(LocalDate.now())) return
        date = newDate
        loadDate()
    }

    private fun loadDate() {
        entries.clear()
        entries.addAll(store.entriesFor(date))
        adapter.notifyDataSetChanged()
        dateText.text = if (date == LocalDate.now()) {
            getString(R.string.today)
        } else {
            date.format(dateFormat)
        }
        nextDayButton.isEnabled = date.isBefore(LocalDate.now())
        nextDayButton.alpha = if (nextDayButton.isEnabled) 1f else 0.3f
        refreshSummary()
    }

    private fun addEntry() {
        val kcal = kcalInput.text.toString().trim().toIntOrNull()
        if (kcal == null || kcal <= 0) {
            Toast.makeText(this, R.string.enter_kcal, Toast.LENGTH_SHORT).show()
            return
        }
        val name = nameInput.text.toString().trim()
            .ifEmpty { getString(R.string.default_entry_name) }

        entries.add(Entry(name, kcal, LocalTime.now().format(timeFormat)))
        store.save(date, entries)
        adapter.notifyItemInserted(entries.size - 1)
        nameInput.text.clear()
        kcalInput.text.clear()
        nameInput.requestFocus()
        refreshSummary()
    }

    private fun confirmDelete(position: Int) {
        if (position !in entries.indices) return
        val entry = entries[position]
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message, entry.name, entry.kcal))
            .setPositiveButton(R.string.delete) { _, _ ->
                entries.removeAt(position)
                store.save(date, entries)
                adapter.notifyItemRemoved(position)
                refreshSummary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun editGoal() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(store.goal.toString())
            setSelection(text.length)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.goal_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val goal = input.text.toString().trim().toIntOrNull()
                if (goal != null && goal > 0) {
                    store.goal = goal
                    refreshSummary()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshSummary() {
        val total = entries.sumOf { it.kcal }
        val goal = store.goal

        totalText.text = getString(R.string.kcal_value, total)
        goalText.text = getString(R.string.goal_value, goal)
        progressBar.max = goal
        progressBar.progress = total.coerceAtMost(goal)

        val remaining = goal - total
        remainingText.text = if (remaining >= 0) {
            getString(R.string.remaining, remaining)
        } else {
            getString(R.string.exceeded, -remaining)
        }

        emptyText.visibility = if (entries.isEmpty()) TextView.VISIBLE else TextView.GONE
    }
}
