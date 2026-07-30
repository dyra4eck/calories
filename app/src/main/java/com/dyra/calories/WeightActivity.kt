package com.dyra.calories

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Экран веса и параметров тела: запись текущего веса, целевой вес,
 * плавный график динамики и расчёт дневных КБЖУ по формуле
 * Миффлина-Сан Жеора (набор массы / поддержание / похудение).
 */
class WeightActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var chart: WeightChartView
    private lateinit var currentText: TextView
    private lateinit var targetText: TextView
    private lateinit var trendText: TextView
    private lateinit var profileText: TextView
    private lateinit var tdeeText: TextView
    private lateinit var tdeeApplyButton: Button

    private val historyDateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weight)

        store = Store(this)
        chart = findViewById(R.id.weightChart)
        currentText = findViewById(R.id.weightCurrentText)
        targetText = findViewById(R.id.weightTargetText)
        trendText = findViewById(R.id.weightTrendText)
        profileText = findViewById(R.id.profileSummaryText)
        tdeeText = findViewById(R.id.tdeeText)
        tdeeApplyButton = findViewById(R.id.tdeeApplyButton)

        findViewById<Button>(R.id.addWeightButton).setOnClickListener { showWeightDialog(LocalDate.now(), null) }
        findViewById<Button>(R.id.targetWeightButton).setOnClickListener { showTargetDialog() }
        findViewById<Button>(R.id.weightHistoryButton).setOnClickListener { showHistory() }
        findViewById<Button>(R.id.profileButton).setOnClickListener { showProfileDialog() }
        findViewById<Button>(R.id.calcMacrosButton).setOnClickListener { showCalculation() }
        findViewById<Button>(R.id.measurementsButton).setOnClickListener {
            startActivity(Intent(this, MeasurementsActivity::class.java))
        }
        findViewById<Button>(R.id.photosButton).setOnClickListener {
            startActivity(Intent(this, PhotoActivity::class.java))
        }

        refresh()
    }

    private fun refresh() {
        val weights = store.weights()
        val current = weights.lastOrNull()?.second
        val target = store.targetWeight

        currentText.text = if (current != null) {
            getString(R.string.weight_current_line, fmt(current))
        } else {
            getString(R.string.weight_no_data)
        }

        targetText.text = when {
            target <= 0 -> getString(R.string.weight_target_none)
            current == null -> getString(R.string.weight_target_line, fmt(target))
            else -> {
                val diff = round1(current - target)
                val progress = when {
                    abs(diff) < 0.05 -> getString(R.string.weight_reached)
                    diff > 0 -> getString(R.string.weight_to_lose, fmt(diff))
                    else -> getString(R.string.weight_to_gain, fmt(-diff))
                }
                getString(R.string.weight_target_line, fmt(target)) + " • " + progress
            }
        }

        chart.setData(weights, target)
        refreshTrend(weights, current, target)
        refreshTdee()

        profileText.text = if (store.profileAge > 0 && store.profileHeight > 0) {
            getString(
                R.string.profile_summary,
                getString(if (store.profileMale) R.string.sex_male else R.string.sex_female),
                store.profileAge,
                store.profileHeight,
                resources.getStringArray(R.array.activity_levels_short)[store.profileActivity]
            )
        } else {
            getString(R.string.profile_not_set)
        }
    }

    /** «Тренд: −0,4 кг/нед • цель ≈ 15 сентября». */
    private fun refreshTrend(
        weights: List<Pair<LocalDate, Double>>,
        current: Double?,
        target: Double
    ) {
        val rate = WeightMath.weeklyRate(weights)
        if (rate == null) {
            trendText.visibility = View.GONE
            return
        }
        trendText.visibility = View.VISIBLE
        val rateText = getString(
            R.string.trend_rate,
            (if (rate > 0) "+" else "") + fmt(round1(rate))
        )
        var forecast = ""
        if (target > 0 && current != null && kotlin.math.abs(rate) >= 0.05) {
            val weeks = (target - current) / rate
            if (weeks > 0 && weeks < 104) {
                val eta = LocalDate.now().plusDays((weeks * 7).toLong())
                forecast = " • " + getString(R.string.trend_forecast, eta.format(historyDateFormat))
            }
        }
        trendText.text = rateText + forecast
    }

    /** Фактический расход энергии за последние 3 недели. */
    private fun refreshTdee() {
        val real = WeightMath.realTdee(store)
        if (real == null) {
            tdeeText.text = getString(R.string.tdee_not_enough)
            tdeeApplyButton.visibility = View.GONE
            return
        }
        tdeeText.text = getString(
            R.string.tdee_line,
            real.tdee,
            real.avgIntake,
            (if (real.ratePerWeek > 0) "+" else "") + fmt(real.ratePerWeek),
            real.foodDays
        )
        tdeeApplyButton.visibility = View.VISIBLE
        tdeeApplyButton.setOnClickListener {
            val weight = store.currentWeight() ?: return@setOnClickListener
            showPlansDialog(real.tdee.toDouble(), weight, getString(R.string.tdee_plans_title))
        }
    }

    // ---------- Вес ----------

    /** Диалог записи веса; existing != null — редактирование записи за дату. */
    private fun showWeightDialog(date: LocalDate, existing: Double?) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.weight_kg_hint)
            existing?.let { setText(fmt(it)) }
        }
        val title = if (date == LocalDate.now()) {
            getString(R.string.add_weight)
        } else {
            date.format(historyDateFormat)
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val kg = parseNum(input.text.toString())
                if (kg == null || kg <= 0) {
                    Toast.makeText(this, R.string.enter_weight, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                store.setWeight(date, round1(kg))
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showTargetDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            hint = getString(R.string.weight_kg_hint)
            if (store.targetWeight > 0) setText(fmt(store.targetWeight))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.set_target_weight)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                store.targetWeight = parseNum(input.text.toString())?.takeIf { it > 0 } ?: 0.0
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** История записей (новые сверху): нажатие — изменить или удалить. */
    private fun showHistory() {
        val weights = store.weights().reversed()
        if (weights.isEmpty()) {
            Toast.makeText(this, R.string.weight_history_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = weights
            .map { (date, kg) -> "${date.format(historyDateFormat)} — ${fmt(kg)} кг" }
            .toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.weight_history)
            .setItems(labels) { _, which ->
                val (date, kg) = weights[which]
                AlertDialog.Builder(this)
                    .setTitle(labels[which])
                    .setItems(
                        arrayOf(getString(R.string.edit_entry), getString(R.string.delete))
                    ) { _, action ->
                        when (action) {
                            0 -> showWeightDialog(date, kg)
                            1 -> {
                                store.removeWeight(date)
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

    // ---------- Параметры тела и расчёт КБЖУ ----------

    private fun showProfileDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_profile, null)
        val maleButton = view.findViewById<RadioButton>(R.id.sexMale)
        val femaleButton = view.findViewById<RadioButton>(R.id.sexFemale)
        val ageInput = view.findViewById<EditText>(R.id.ageInput)
        val heightInput = view.findViewById<EditText>(R.id.heightInput)
        val proteinInput = view.findViewById<EditText>(R.id.proteinPerKgInput)
        val activitySpinner = view.findViewById<Spinner>(R.id.activitySpinner)

        activitySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.activity_levels)
        )

        if (store.profileMale) maleButton.isChecked = true else femaleButton.isChecked = true
        if (store.profileAge > 0) ageInput.setText(store.profileAge.toString())
        if (store.profileHeight > 0) heightInput.setText(store.profileHeight.toString())
        proteinInput.setText(fmt(round1(store.proteinPerKg)))
        activitySpinner.setSelection(store.profileActivity)

        AlertDialog.Builder(this)
            .setTitle(R.string.profile_title)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val age = ageInput.text.toString().trim().toIntOrNull()
                val height = heightInput.text.toString().trim().toIntOrNull()
                if (age == null || age !in 10..120 || height == null || height !in 100..250) {
                    Toast.makeText(this, R.string.profile_invalid, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                store.profileMale = maleButton.isChecked
                store.profileAge = age
                store.profileHeight = height
                store.profileActivity = activitySpinner.selectedItemPosition
                parseNum(proteinInput.text.toString())?.takeIf { it in 0.5..4.0 }?.let {
                    store.proteinPerKg = it
                }
                refresh()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCalculation() {
        if (store.profileAge <= 0 || store.profileHeight <= 0) {
            Toast.makeText(this, R.string.calc_need_profile, Toast.LENGTH_SHORT).show()
            showProfileDialog()
            return
        }
        val weight = store.currentWeight()
        if (weight == null) {
            Toast.makeText(this, R.string.calc_need_weight, Toast.LENGTH_SHORT).show()
            showWeightDialog(LocalDate.now(), null)
            return
        }

        val bmr = MacroCalculator.bmr(store.profileMale, weight, store.profileHeight, store.profileAge)
        val tdee = MacroCalculator.tdee(bmr, store.profileActivity)
        showPlansDialog(
            tdee,
            weight,
            getString(R.string.calc_title) + "\n" +
                getString(R.string.calc_subtitle, bmr.roundToInt(), tdee.roundToInt())
        )
    }

    /** Три плана (набор/поддержание/похудение) от заданного суточного расхода. */
    private fun showPlansDialog(tdee: Double, weight: Double, title: String) {
        val perKg = store.proteinPerKg
        val plans = listOf(
            MacroCalculator.plan(MacroCalculator.Goal.GAIN, tdee, weight, perKg),
            MacroCalculator.plan(MacroCalculator.Goal.MAINTAIN, tdee, weight, perKg),
            MacroCalculator.plan(MacroCalculator.Goal.LOSE, tdee, weight, perKg)
        )
        val titles = listOf(
            getString(R.string.goal_gain),
            getString(R.string.goal_maintain),
            getString(R.string.goal_lose)
        )
        val labels = plans.mapIndexed { i, plan ->
            getString(R.string.calc_line, titles[i], plan.kcal, plan.protein, plan.fat, plan.carbs)
        }.toTypedArray()

        // setMessage вместе со setItems не работает — сводка идёт в заголовок
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(labels) { _, which -> confirmApplyGoals(plans[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Выбор, каким дням назначить рассчитанные цели. */
    private fun confirmApplyGoals(plan: MacroCalculator.Plan) {
        AlertDialog.Builder(this)
            .setTitle(R.string.apply_goals_title)
            .setMessage(
                getString(R.string.apply_goals_message, plan.kcal, plan.protein, plan.fat, plan.carbs)
            )
            .setPositiveButton(R.string.apply_normal_days) { _, _ ->
                store.goal = plan.kcal
                store.goalProtein = plan.protein
                store.goalFat = plan.fat
                store.goalCarbs = plan.carbs
                Toast.makeText(this, R.string.goals_applied, Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton(R.string.apply_train_days) { _, _ ->
                store.goalTrain = plan.kcal
                store.goalTrainProtein = plan.protein
                store.goalTrainFat = plan.fat
                store.goalTrainCarbs = plan.carbs
                Toast.makeText(this, R.string.goals_applied, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
