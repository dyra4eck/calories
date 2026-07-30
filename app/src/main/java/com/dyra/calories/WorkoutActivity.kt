package com.dyra.calories

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Журнал тренировок: упражнения и подходы (вес×повторы) по дням,
 * история упражнения с графиком 1ПМ (формула Эпли).
 * День с тренировкой автоматически помечается «тренировочным» для целей КБЖУ.
 */
class WorkoutActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var dateText: TextView
    private lateinit var nextDayButton: ImageButton
    private lateinit var emptyText: TextView
    private lateinit var nameInput: AutoCompleteTextView

    private var date: LocalDate = LocalDate.now()
    private val exercises = mutableListOf<WorkoutExercise>()
    private lateinit var adapter: ExerciseAdapter

    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
    private val shortDateFormat = DateTimeFormatter.ofPattern("d.MM", Locale("ru"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workout)

        store = Store(this)
        dateText = findViewById(R.id.workoutDateText)
        nextDayButton = findViewById(R.id.workoutNextDay)
        emptyText = findViewById(R.id.workoutEmptyText)
        nameInput = findViewById(R.id.exerciseNameInput)

        val list = findViewById<RecyclerView>(R.id.exerciseList)
        adapter = ExerciseAdapter()
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<ImageButton>(R.id.workoutPrevDay).setOnClickListener { shiftDate(-1) }
        nextDayButton.setOnClickListener { shiftDate(1) }
        findViewById<Button>(R.id.addExerciseButton).setOnClickListener { addExercise() }

        refreshNameSuggestions()
        loadDate()
    }

    private fun refreshNameSuggestions() {
        nameInput.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, store.exerciseNames())
        )
    }

    private fun shiftDate(days: Long) {
        val newDate = date.plusDays(days)
        if (newDate.isAfter(LocalDate.now())) return
        date = newDate
        loadDate()
    }

    private fun loadDate() {
        exercises.clear()
        exercises.addAll(store.workoutFor(date))
        adapter.notifyDataSetChanged()
        dateText.text = if (date == LocalDate.now()) getString(R.string.today) else date.format(dateFormat)
        nextDayButton.isEnabled = date.isBefore(LocalDate.now())
        nextDayButton.alpha = if (nextDayButton.isEnabled) 1f else 0.3f
        refreshEmpty()
    }

    private fun refreshEmpty() {
        emptyText.visibility = if (exercises.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun persist() {
        store.saveWorkout(date, exercises)
        // Тренировка в этот день → включаем тренировочный профиль целей
        if (exercises.isNotEmpty()) store.setTrainingDay(date, true)
        refreshEmpty()
    }

    private fun addExercise() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, R.string.exercise_name_required, Toast.LENGTH_SHORT).show()
            return
        }
        nameInput.text.clear()
        val existing = exercises.indexOfFirst { it.name.equals(name, ignoreCase = true) }
        if (existing >= 0) {
            askSet(existing)
            return
        }
        exercises.add(WorkoutExercise(name))
        persist()
        adapter.notifyDataSetChanged()
        refreshNameSuggestions()
        askSet(exercises.size - 1)
    }

    /** Последний выполненный подход упражнения — сегодня или в прошлый раз. */
    private fun lastSetOf(name: String, position: Int): WorkoutSet? =
        exercises[position].sets.lastOrNull()
            ?: store.exerciseHistory(name).lastOrNull()?.second?.lastOrNull()

    private fun askSet(position: Int) {
        if (position !in exercises.indices) return
        val exercise = exercises[position]
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(48, 16, 48, 0)
        }
        val last = lastSetOf(exercise.name, position)
        val weightInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.set_weight_hint)
            last?.let { setText(fmt(it.weight)) }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val repsInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.set_reps_hint)
            last?.let { setText(it.reps.toString()) }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        container.addView(weightInput)
        container.addView(repsInput)

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_set_title, exercise.name))
            .setView(container)
            .setPositiveButton(R.string.add_word) { _, _ ->
                val weight = parseNum(weightInput.text.toString()) ?: 0.0
                val reps = repsInput.text.toString().trim().toIntOrNull()
                if (weight < 0 || reps == null || reps <= 0) {
                    Toast.makeText(this, R.string.set_invalid, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                exercise.sets.add(WorkoutSet(round1(weight), reps))
                persist()
                adapter.notifyItemChanged(position)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showActions(position: Int) {
        if (position !in exercises.indices) return
        val exercise = exercises[position]
        AlertDialog.Builder(this)
            .setTitle(exercise.name)
            .setItems(
                arrayOf(
                    getString(R.string.exercise_history),
                    getString(R.string.remove_last_set),
                    getString(R.string.remove_exercise)
                )
            ) { _, which ->
                when (which) {
                    0 -> showHistory(exercise.name)
                    1 -> {
                        if (exercise.sets.isNotEmpty()) {
                            exercise.sets.removeAt(exercise.sets.size - 1)
                            persist()
                            adapter.notifyItemChanged(position)
                        }
                    }
                    2 -> {
                        exercises.removeAt(position)
                        persist()
                        adapter.notifyDataSetChanged()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** История упражнения: график лучшего 1ПМ по датам и последние тренировки. */
    private fun showHistory(name: String) {
        val history = store.exerciseHistory(name)
        if (history.isEmpty()) {
            Toast.makeText(this, R.string.exercise_no_history, Toast.LENGTH_SHORT).show()
            return
        }
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_exercise_history, null)

        val oneRmPoints = history.map { (day, sets) ->
            day to round1(sets.maxOf { oneRepMax(it.weight, it.reps) })
        }
        view.findViewById<WeightChartView>(R.id.historyChart).setData(oneRmPoints, 0.0)

        val best = oneRmPoints.maxOf { it.second }
        val lines = history.takeLast(8).reversed().joinToString("\n") { (day, sets) ->
            day.format(shortDateFormat) + ":  " + sets.joinToString("  ") { setLabel(it) }
        }
        view.findViewById<TextView>(R.id.historyText).text =
            getString(R.string.exercise_best_1rm, best.roundToInt()) + "\n\n" + lines

        AlertDialog.Builder(this)
            .setTitle(name)
            .setView(view)
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private inner class ExerciseAdapter : RecyclerView.Adapter<ExerciseAdapter.Holder>() {

        inner class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.exerciseName)
            val sets: TextView = view.findViewById(R.id.exerciseSets)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_exercise, parent, false)
            )

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val exercise = exercises[position]
            holder.name.text = exercise.name
            holder.sets.text = if (exercise.sets.isEmpty()) {
                getString(R.string.no_sets_hint)
            } else {
                exercise.sets.joinToString("   ") { setLabel(it) }
            }
            holder.itemView.setOnClickListener { askSet(holder.bindingAdapterPosition) }
            holder.itemView.setOnLongClickListener {
                showActions(holder.bindingAdapterPosition)
                true
            }
        }

        override fun getItemCount() = exercises.size
    }
}
