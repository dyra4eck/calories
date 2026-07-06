package com.dyra.calories

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
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
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var adapter: EntryAdapter

    private lateinit var dateText: TextView
    private lateinit var totalText: TextView
    private lateinit var goalText: TextView
    private lateinit var remainingText: TextView
    private lateinit var macrosText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyText: TextView
    private lateinit var nameInput: EditText
    private lateinit var kcalInput: EditText
    private lateinit var nextDayButton: ImageButton

    private var date: LocalDate = LocalDate.now()
    private val entries = mutableListOf<Entry>()

    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { onBarcodeScanned(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        store = Store(this)

        dateText = findViewById(R.id.dateText)
        totalText = findViewById(R.id.totalText)
        goalText = findViewById(R.id.goalText)
        remainingText = findViewById(R.id.remainingText)
        macrosText = findViewById(R.id.macrosText)
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
        findViewById<ImageButton>(R.id.productsButton).setOnClickListener { showProductPicker() }
        findViewById<ImageButton>(R.id.statsButton).setOnClickListener {
            startActivity(Intent(this, StatsActivity::class.java))
        }
        goalText.setOnClickListener { editGoal() }

        val addButton = findViewById<Button>(R.id.addButton)
        addButton.setOnClickListener { addQuickEntry() }
        addButton.setOnLongClickListener {
            showDetailedAddDialog()
            true
        }

        kcalInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addQuickEntry()
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

    /** Быстрое добавление из строки внизу: только название и калории. */
    private fun addQuickEntry() {
        val kcal = kcalInput.text.toString().trim().toIntOrNull()
        if (kcal == null || kcal <= 0) {
            Toast.makeText(this, R.string.enter_kcal, Toast.LENGTH_SHORT).show()
            return
        }
        val name = nameInput.text.toString().trim()
            .ifEmpty { getString(R.string.default_entry_name) }
        addEntry(Entry(name, kcal, LocalTime.now().format(timeFormat)))
        nameInput.text.clear()
        kcalInput.text.clear()
        nameInput.requestFocus()
    }

    private fun addEntry(entry: Entry) {
        entries.add(entry)
        store.save(date, entries)
        adapter.notifyItemInserted(entries.size - 1)
        refreshSummary()
    }

    /** Выбор продукта из базы; сверху — ручной ввод и сканер штрих-кода. */
    private fun showProductPicker() {
        val products = store.products().sortedBy { it.name.lowercase() }
        val labels = mutableListOf(
            getString(R.string.manual_entry_option),
            getString(R.string.scan_option)
        )
        products.mapTo(labels) { "${it.name} — ${fmt(it.kcal100)} ккал/100 г" }

        AlertDialog.Builder(this)
            .setTitle(R.string.pick_product)
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showDetailedAddDialog()
                    1 -> startScan()
                    else -> askGrams(products[which - 2])
                }
            }
            .setNeutralButton(R.string.manage_products) { _, _ ->
                startActivity(Intent(this, ProductsActivity::class.java))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startScan() {
        scanLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.ALL_CODE_TYPES)
                .setPrompt(getString(R.string.scan_prompt))
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }

    /**
     * Отсканирован код: если он уже привязан к продукту в базе — сразу
     * спрашиваем вес; иначе предлагаем создать продукт с этим кодом.
     */
    private fun onBarcodeScanned(code: String) {
        val products = store.products()
        val existing = products.find { it.barcode == code }
        if (existing != null) {
            askGrams(existing)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.barcode_unknown_title)
            .setMessage(getString(R.string.barcode_unknown_message, code))
            .setPositiveButton(R.string.add_product) { _, _ ->
                ProductDialog.show(this, R.string.add_product, null, code) { product ->
                    products.add(product)
                    store.saveProducts(products)
                    askGrams(product)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Спросить вес порции и добавить запись с пересчитанным КБЖУ. */
    private fun askGrams(product: Product) {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.grams_hint)
        }
        AlertDialog.Builder(this)
            .setTitle(product.name)
            .setMessage(
                getString(
                    R.string.product_info,
                    fmt(product.kcal100),
                    fmt(product.protein100),
                    fmt(product.fat100),
                    fmt(product.carbs100)
                )
            )
            .setView(input)
            .setPositiveButton(R.string.add_word) { _, _ ->
                val grams = parseNum(input.text.toString())
                if (grams == null || grams <= 0) {
                    Toast.makeText(this, R.string.enter_grams, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val factor = grams / 100.0
                addEntry(
                    Entry(
                        name = "${product.name} (${fmt(grams)} г)",
                        kcal = (product.kcal100 * factor).roundToInt(),
                        time = LocalTime.now().format(timeFormat),
                        protein = round1(product.protein100 * factor),
                        fat = round1(product.fat100 * factor),
                        carbs = round1(product.carbs100 * factor)
                    )
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Ручной ввод записи со всеми полями: ккал + БЖУ. */
    private fun showDetailedAddDialog() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_food, null)
        view.findViewById<TextView>(R.id.foodSubtitle).visibility = View.GONE

        AlertDialog.Builder(this)
            .setTitle(R.string.detailed_add)
            .setView(view)
            .setPositiveButton(R.string.add_word) { _, _ ->
                val kcal = parseNum(view.findViewById<EditText>(R.id.foodKcal).text.toString())
                if (kcal == null || kcal <= 0) {
                    Toast.makeText(this, R.string.enter_kcal, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val name = view.findViewById<EditText>(R.id.foodName).text.toString().trim()
                    .ifEmpty { getString(R.string.default_entry_name) }
                addEntry(
                    Entry(
                        name = name,
                        kcal = kcal.roundToInt(),
                        time = LocalTime.now().format(timeFormat),
                        protein = parseNum(view.findViewById<EditText>(R.id.foodProtein).text.toString()) ?: 0.0,
                        fat = parseNum(view.findViewById<EditText>(R.id.foodFat).text.toString()) ?: 0.0,
                        carbs = parseNum(view.findViewById<EditText>(R.id.foodCarbs).text.toString()) ?: 0.0
                    )
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
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

        macrosText.text = getString(
            R.string.macros_summary,
            fmt(round1(entries.sumOf { it.protein })),
            fmt(round1(entries.sumOf { it.fat })),
            fmt(round1(entries.sumOf { it.carbs }))
        )

        emptyText.visibility = if (entries.isEmpty()) TextView.VISIBLE else TextView.GONE
    }
}
