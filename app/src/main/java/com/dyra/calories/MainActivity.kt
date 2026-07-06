package com.dyra.calories

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
    private lateinit var searchInput: AutoCompleteTextView
    private lateinit var nextDayButton: ImageButton

    private var date: LocalDate = LocalDate.now()
    private val entries = mutableListOf<Entry>()
    private var searchProducts: List<Product> = emptyList()

    private val dateFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale("ru"))
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { onBarcodeScanned(it) }
    }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(store.exportJson().toByteArray())
                }
                Toast.makeText(this, R.string.export_done, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, R.string.export_error, Toast.LENGTH_SHORT).show()
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            try {
                val text = contentResolver.openInputStream(uri)?.use { stream ->
                    stream.readBytes().decodeToString()
                } ?: throw IllegalStateException()
                store.importJson(text)
                date = LocalDate.now()
                loadDate()
                refreshSearchAdapter()
                Toast.makeText(this, R.string.import_done, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, R.string.import_error, Toast.LENGTH_SHORT).show()
            }
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
        searchInput = findViewById(R.id.searchInput)
        nextDayButton = findViewById(R.id.nextDayButton)

        val list = findViewById<RecyclerView>(R.id.entryList)
        adapter = EntryAdapter(
            entries,
            onClick = { position -> showEditEntryDialog(position) },
            onLongClick = { position -> confirmDelete(position) }
        )
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<ImageButton>(R.id.prevDayButton).setOnClickListener { shiftDate(-1) }
        nextDayButton.setOnClickListener { shiftDate(1) }
        findViewById<ImageButton>(R.id.menuButton).setOnClickListener { showMenu() }
        findViewById<ImageButton>(R.id.productsButton).setOnClickListener { showProductPicker() }
        findViewById<ImageButton>(R.id.scanButton).setOnClickListener { startScan() }
        goalText.setOnClickListener { editGoals() }

        searchInput.threshold = 1
        searchInput.setOnItemClickListener { parent, _, position, _ ->
            val label = parent.getItemAtPosition(position) as String
            val product = searchProducts.find { productLabel(it) == label }
            searchInput.text.clear()
            product?.let { askGrams(it) }
        }
        searchInput.setOnClickListener {
            if (searchProducts.isEmpty()) {
                Toast.makeText(this, R.string.no_products, Toast.LENGTH_SHORT).show()
            }
        }

        loadDate()
    }

    override fun onResume() {
        super.onResume()
        // База могла измениться на экране продуктов
        refreshSearchAdapter()
    }

    private fun productLabel(product: Product) =
        "${product.name} — ${fmt(product.kcal100)} ккал/100 г"

    /** Продукты: чаще используемые — выше. */
    private fun sortedProducts(): List<Product> =
        store.products().sortedWith(
            compareByDescending<Product> { it.uses }.thenBy { it.name.lowercase() }
        )

    private fun refreshSearchAdapter() {
        searchProducts = sortedProducts()
        searchInput.setAdapter(
            ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                searchProducts.map { productLabel(it) }
            )
        )
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

    private fun addEntry(entry: Entry) {
        entries.add(entry)
        store.save(date, entries)
        adapter.notifyItemInserted(entries.size - 1)
        refreshSummary()
    }

    /** Меню приложения. */
    private fun showMenu() {
        val reminderLabel = if (store.reminderEnabled) {
            getString(
                R.string.reminder_menu_on,
                String.format(Locale.US, "%02d:%02d", store.reminderHour, store.reminderMinute)
            )
        } else {
            getString(R.string.reminder_menu_off)
        }
        val items = arrayOf(
            getString(R.string.stats_title),
            getString(R.string.products_title),
            reminderLabel,
            getString(R.string.export_data),
            getString(R.string.import_data)
        )
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> startActivity(Intent(this, StatsActivity::class.java))
                    1 -> startActivity(Intent(this, ProductsActivity::class.java))
                    2 -> showReminderDialog()
                    3 -> exportLauncher.launch("calories-backup-${LocalDate.now()}.json")
                    4 -> confirmImport()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------- Напоминание ----------

    private fun showReminderDialog() {
        if (!store.reminderEnabled) {
            pickReminderTime()
            return
        }
        val options = arrayOf(
            getString(R.string.reminder_change),
            getString(R.string.reminder_disable)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.reminder_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> pickReminderTime()
                    1 -> {
                        store.reminderEnabled = false
                        ReminderScheduler.cancel(this)
                        Toast.makeText(this, R.string.reminder_disabled, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickReminderTime() {
        requestNotificationPermissionIfNeeded()
        TimePickerDialog(
            this,
            { _, hour, minute ->
                store.reminderHour = hour
                store.reminderMinute = minute
                store.reminderEnabled = true
                ReminderScheduler.schedule(this)
                Toast.makeText(
                    this,
                    getString(
                        R.string.reminder_set,
                        String.format(Locale.US, "%02d:%02d", hour, minute)
                    ),
                    Toast.LENGTH_SHORT
                ).show()
            },
            store.reminderHour,
            store.reminderMinute,
            true
        ).show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    // ---------- Импорт/экспорт ----------

    private fun confirmImport() {
        AlertDialog.Builder(this)
            .setTitle(R.string.import_data)
            .setMessage(R.string.import_confirm)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                importLauncher.launch(
                    arrayOf("application/json", "text/plain", "application/octet-stream")
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------- Добавление еды ----------

    /** Выбор продукта из базы; сверху — ручной ввод и сканер штрих-кода. */
    private fun showProductPicker() {
        val products = sortedProducts()
        val labels = mutableListOf(
            getString(R.string.manual_entry_option),
            getString(R.string.scan_option)
        )
        products.mapTo(labels) { productLabel(it) }

        AlertDialog.Builder(this)
            .setTitle(R.string.pick_product)
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showEntryDialog(null)
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
                    refreshSearchAdapter()
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
                store.incrementUse(product)
                refreshSearchAdapter()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showEditEntryDialog(position: Int) {
        if (position !in entries.indices) return
        showEntryDialog(position)
    }

    /**
     * Диалог записи: position == null — новая (ручной ввод),
     * иначе редактирование существующей (время сохраняется).
     */
    private fun showEntryDialog(position: Int?) {
        val existing = position?.let { entries[it] }
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_food, null)
        view.findViewById<TextView>(R.id.foodSubtitle).visibility = View.GONE

        val nameInput = view.findViewById<EditText>(R.id.foodName)
        val kcalInput = view.findViewById<EditText>(R.id.foodKcal)
        val proteinInput = view.findViewById<EditText>(R.id.foodProtein)
        val fatInput = view.findViewById<EditText>(R.id.foodFat)
        val carbsInput = view.findViewById<EditText>(R.id.foodCarbs)

        if (existing != null) {
            nameInput.setText(existing.name)
            kcalInput.setText(existing.kcal.toString())
            proteinInput.setText(fmt(existing.protein))
            fatInput.setText(fmt(existing.fat))
            carbsInput.setText(fmt(existing.carbs))
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) R.string.detailed_add else R.string.edit_entry)
            .setView(view)
            .setPositiveButton(if (existing == null) R.string.add_word else R.string.save) { _, _ ->
                val kcal = parseNum(kcalInput.text.toString())
                if (kcal == null || kcal <= 0) {
                    Toast.makeText(this, R.string.enter_kcal, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val entry = Entry(
                    name = nameInput.text.toString().trim()
                        .ifEmpty { getString(R.string.default_entry_name) },
                    kcal = kcal.roundToInt(),
                    time = existing?.time ?: LocalTime.now().format(timeFormat),
                    protein = parseNum(proteinInput.text.toString()) ?: 0.0,
                    fat = parseNum(fatInput.text.toString()) ?: 0.0,
                    carbs = parseNum(carbsInput.text.toString()) ?: 0.0
                )
                if (position == null) {
                    addEntry(entry)
                } else {
                    entries[position] = entry
                    store.save(date, entries)
                    adapter.notifyItemChanged(position)
                    refreshSummary()
                }
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

    // ---------- Цели ----------

    /** Диалог целей: калории и (опционально) БЖУ. */
    private fun editGoals() {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_goals, null)
        val kcalInput = view.findViewById<EditText>(R.id.goalKcal)
        val proteinInput = view.findViewById<EditText>(R.id.goalProtein)
        val fatInput = view.findViewById<EditText>(R.id.goalFat)
        val carbsInput = view.findViewById<EditText>(R.id.goalCarbs)

        kcalInput.setText(store.goal.toString())
        if (store.goalProtein > 0) proteinInput.setText(store.goalProtein.toString())
        if (store.goalFat > 0) fatInput.setText(store.goalFat.toString())
        if (store.goalCarbs > 0) carbsInput.setText(store.goalCarbs.toString())

        AlertDialog.Builder(this)
            .setTitle(R.string.goal_title)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                val goal = kcalInput.text.toString().trim().toIntOrNull()
                if (goal == null || goal <= 0) {
                    Toast.makeText(this, R.string.enter_kcal, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                store.goal = goal
                store.goalProtein = proteinInput.text.toString().trim().toIntOrNull() ?: 0
                store.goalFat = fatInput.text.toString().trim().toIntOrNull() ?: 0
                store.goalCarbs = carbsInput.text.toString().trim().toIntOrNull() ?: 0
                refreshSummary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------- Сводка дня ----------

    private fun macroSummaryPart(labelRes: Int, value: Double, goal: Int): String {
        val label = getString(labelRes)
        return if (goal > 0) {
            getString(R.string.macro_with_goal, label, fmt(round1(value)), goal)
        } else {
            getString(R.string.macro_plain, label, fmt(round1(value)))
        }
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

        macrosText.text = listOf(
            macroSummaryPart(R.string.protein_label, entries.sumOf { it.protein }, store.goalProtein),
            macroSummaryPart(R.string.fat_label, entries.sumOf { it.fat }, store.goalFat),
            macroSummaryPart(R.string.carbs_label, entries.sumOf { it.carbs }, store.goalCarbs)
        ).joinToString(" • ")

        emptyText.visibility = if (entries.isEmpty()) TextView.VISIBLE else TextView.GONE

        WidgetProvider.updateAll(this)
    }
}
