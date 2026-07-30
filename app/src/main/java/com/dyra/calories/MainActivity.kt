package com.dyra.calories

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.HapticFeedbackConstants
import android.widget.HorizontalScrollView
import android.widget.Spinner
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.ScanContract
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var adapter: EntryAdapter

    private lateinit var dateText: TextView
    private lateinit var goalText: TextView
    private lateinit var remainingText: TextView
    private lateinit var kcalRing: MacroRingView
    private lateinit var proteinRing: MacroRingView
    private lateinit var fatRing: MacroRingView
    private lateinit var carbsRing: MacroRingView
    private lateinit var emptyText: TextView
    private lateinit var searchInput: AutoCompleteTextView
    private lateinit var nextDayButton: ImageButton
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var proteinPerKgText: TextView
    private lateinit var waterText: TextView
    private lateinit var chipScroll: HorizontalScrollView
    private lateinit var chipGroup: ChipGroup
    private lateinit var entryList: RecyclerView

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
        goalText = findViewById(R.id.goalText)
        remainingText = findViewById(R.id.remainingText)
        kcalRing = findViewById(R.id.kcalRing)
        proteinRing = findViewById(R.id.proteinRing)
        fatRing = findViewById(R.id.fatRing)
        carbsRing = findViewById(R.id.carbsRing)
        emptyText = findViewById(R.id.emptyText)
        searchInput = findViewById(R.id.searchInput)
        nextDayButton = findViewById(R.id.nextDayButton)
        drawerLayout = findViewById(R.id.drawerLayout)
        navView = findViewById(R.id.navView)
        proteinPerKgText = findViewById(R.id.proteinPerKgText)
        waterText = findViewById(R.id.waterText)
        chipScroll = findViewById(R.id.chipScroll)
        chipGroup = findViewById(R.id.chipGroup)

        proteinRing.setRingColor(ContextCompat.getColor(this, R.color.macro_protein))
        fatRing.setRingColor(ContextCompat.getColor(this, R.color.macro_fat))
        carbsRing.setRingColor(ContextCompat.getColor(this, R.color.macro_carbs))

        setUpDrawer()

        entryList = findViewById(R.id.entryList)
        adapter = EntryAdapter(
            entries,
            onClick = { position -> showEditEntryDialog(position) },
            onLongClick = { position -> confirmDelete(position) }
        )
        entryList.layoutManager = LinearLayoutManager(this)
        entryList.adapter = adapter
        setUpSwipes()

        findViewById<View>(R.id.waterAddButton).setOnClickListener { addWater(250) }
        waterText.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            showWaterDialog()
            true
        }

        findViewById<ImageButton>(R.id.prevDayButton).setOnClickListener { shiftDate(-1) }
        nextDayButton.setOnClickListener { shiftDate(1) }
        findViewById<ImageButton>(R.id.menuButton).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
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
        maybeCheckForUpdate()
    }

    override fun onResume() {
        super.onResume()
        // База могла измениться на экране продуктов, цели — на экране веса
        refreshSearchAdapter()
        refreshSummary()
        refreshReminderMenuItem()
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
        refreshChips()
    }

    /** Чипсы топ-5 частых продуктов — добавление в один тап. */
    private fun refreshChips() {
        val top = searchProducts.filter { it.uses > 0 }.take(5)
        chipGroup.removeAllViews()
        chipScroll.visibility = if (top.isEmpty()) View.GONE else View.VISIBLE
        for (product in top) {
            chipGroup.addView(
                Chip(this).apply {
                    text = product.name
                    isCheckable = false
                    setOnClickListener { askGrams(product) }
                }
            )
        }
    }

    private fun shiftDate(days: Long) {
        val newDate = date.plusDays(days)
        if (newDate.isAfter(LocalDate.now())) return
        date = newDate
        loadDate()
    }

    /** Свайпы по записям: влево — удалить (с отменой), вправо — изменить. */
    private fun setUpSwipes() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int = if (viewHolder is EntryAdapter.Holder) {
                super.getMovementFlags(recyclerView, viewHolder)
            } else {
                0
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ) = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val index = adapter.entryIndexAt(viewHolder.bindingAdapterPosition)
                if (index == null) {
                    adapter.rebuild()
                    return
                }
                if (direction == ItemTouchHelper.LEFT) {
                    deleteEntryWithUndo(index)
                } else {
                    // Возвращаем строку на место и открываем редактирование
                    adapter.rebuild()
                    showEntryDialog(index)
                }
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(entryList)
    }

    private fun deleteEntryWithUndo(index: Int) {
        if (index !in entries.indices) return
        val removed = entries.removeAt(index)
        store.save(date, entries)
        adapter.rebuild()
        refreshSummary()
        Snackbar.make(entryList, getString(R.string.entry_deleted, removed.name), Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) {
                entries.add(index.coerceAtMost(entries.size), removed)
                store.save(date, entries)
                adapter.rebuild()
                refreshSummary()
            }
            .show()
    }

    // ---------- Вода ----------

    private fun addWater(ml: Int) {
        store.setWater(date, store.waterFor(date) + ml)
        refreshWater()
    }

    private fun refreshWater() {
        val ml = store.waterFor(date)
        waterText.text = getString(
            R.string.water_line,
            fmt(round1(ml / 1000.0)),
            fmt(round1(store.waterGoal / 1000.0))
        ) + if (ml >= store.waterGoal && store.waterGoal > 0) " ✓" else ""
    }

    private fun showWaterDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = getString(R.string.water_goal_hint)
            setText(store.waterGoal.toString())
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.water_title)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                input.text.toString().trim().toIntOrNull()?.takeIf { it > 0 }?.let {
                    store.waterGoal = it
                }
                refreshWater()
            }
            .setNeutralButton(R.string.water_reset) { _, _ ->
                store.setWater(date, 0)
                refreshWater()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun loadDate() {
        entries.clear()
        entries.addAll(store.entriesFor(date))
        adapter.rebuild()
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
        adapter.rebuild()
        refreshSummary()
    }

    /** Боковое меню приложения. */
    private fun setUpDrawer() {
        navView.getHeaderView(0).findViewById<TextView>(R.id.navVersion).text =
            getString(R.string.version_label, currentVersion())
        navView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawers()
            when (item.itemId) {
                R.id.menu_stats -> startActivity(Intent(this, StatsActivity::class.java))
                R.id.menu_weight -> startActivity(Intent(this, WeightActivity::class.java))
                R.id.menu_products -> startActivity(Intent(this, ProductsActivity::class.java))
                R.id.menu_templates -> showTemplatesDialog()
                R.id.menu_reminder -> showReminderDialog()
                R.id.menu_export ->
                    exportLauncher.launch("calories-backup-${LocalDate.now()}.json")
                R.id.menu_import -> confirmImport()
                R.id.menu_updates -> checkForUpdateManually()
            }
            true
        }
    }

    // ---------- Шаблоны дня ----------

    private fun showTemplatesDialog() {
        val names = store.templateNames()
        val items = mutableListOf(
            getString(R.string.copy_yesterday),
            getString(R.string.save_as_template)
        )
        names.mapTo(items) { "📋 $it" }
        AlertDialog.Builder(this)
            .setTitle(R.string.templates_title)
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which == 0 -> copyYesterday()
                    which == 1 -> askTemplateName()
                    else -> showTemplateActions(names[which - 2])
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun appendEntries(added: List<Entry>) {
        entries.addAll(added)
        store.save(date, entries)
        adapter.rebuild()
        refreshSummary()
        Toast.makeText(
            this,
            getString(R.string.entries_added, added.size),
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun copyYesterday() {
        val yesterday = store.entriesFor(date.minusDays(1))
        if (yesterday.isEmpty()) {
            Toast.makeText(this, R.string.yesterday_empty, Toast.LENGTH_SHORT).show()
            return
        }
        appendEntries(yesterday)
    }

    private fun askTemplateName() {
        if (entries.isEmpty()) {
            Toast.makeText(this, R.string.template_day_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val input = EditText(this).apply { hint = getString(R.string.template_name_hint) }
        AlertDialog.Builder(this)
            .setTitle(R.string.save_as_template)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    Toast.makeText(this, R.string.name_required, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                store.saveTemplate(name, entries)
                Toast.makeText(this, R.string.template_saved, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showTemplateActions(name: String) {
        AlertDialog.Builder(this)
            .setTitle(name)
            .setItems(
                arrayOf(getString(R.string.template_apply), getString(R.string.delete))
            ) { _, which ->
                when (which) {
                    0 -> appendEntries(store.templateEntries(name))
                    1 -> store.deleteTemplate(name)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Пункт «Напоминание» показывает текущее состояние. */
    private fun refreshReminderMenuItem() {
        val label = if (store.reminderEnabled) {
            getString(
                R.string.reminder_menu_on,
                String.format(Locale.US, "%02d:%02d", store.reminderHour, store.reminderMinute)
            )
        } else {
            getString(R.string.reminder_menu_off)
        }
        navView.menu.findItem(R.id.menu_reminder).title = "⏰ $label"
    }

    // ---------- Обновления ----------

    private fun currentVersion(): String =
        packageManager.getPackageInfo(packageName, 0).versionName ?: "0"

    /** Тихая проверка обновлений при запуске, не чаще раза в сутки. */
    private fun maybeCheckForUpdate() {
        val now = System.currentTimeMillis()
        if (now - store.updateCheckedAt < 24 * 60 * 60 * 1000L) return
        store.updateCheckedAt = now
        UpdateChecker.check { release ->
            if (isFinishing || release == null) return@check
            if (UpdateChecker.isNewer(release.version, currentVersion())) {
                showUpdateDialog(release)
            }
        }
    }

    private fun checkForUpdateManually() {
        Toast.makeText(this, R.string.update_checking, Toast.LENGTH_SHORT).show()
        UpdateChecker.check { release ->
            if (isFinishing) return@check
            when {
                release == null ->
                    Toast.makeText(this, R.string.update_error, Toast.LENGTH_SHORT).show()
                UpdateChecker.isNewer(release.version, currentVersion()) ->
                    showUpdateDialog(release)
                else ->
                    Toast.makeText(this, R.string.update_none, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUpdateDialog(release: UpdateChecker.Release) {
        val message = release.notes.take(1500).ifEmpty {
            getString(R.string.update_available_message, currentVersion(), release.version)
        }
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available_title, release.version))
            .setMessage(message)
            .setPositiveButton(R.string.update_download) { _, _ ->
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.downloadUrl)))
            }
            .setNegativeButton(R.string.update_later, null)
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
                        refreshReminderMenuItem()
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
                refreshReminderMenuItem()
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

    /** Выбор продукта из базы; сверху — ручной ввод, сканер и общая база. */
    private fun showProductPicker() {
        val products = sortedProducts()
        val labels = mutableListOf(
            getString(R.string.manual_entry_option),
            getString(R.string.scan_option),
            getString(R.string.online_search_option)
        )
        products.mapTo(labels) { productLabel(it) }

        AlertDialog.Builder(this)
            .setTitle(R.string.pick_product)
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    0 -> showEntryDialog(null)
                    1 -> startScan()
                    2 -> OnlineSearchDialog.show(this) { product -> saveNewProduct(product) }
                    else -> askGrams(products[which - 3])
                }
            }
            .setNeutralButton(R.string.manage_products) { _, _ ->
                startActivity(Intent(this, ProductsActivity::class.java))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startScan() {
        scanLauncher.launch(ScanActivity.options())
    }

    /** Сохранить новый продукт в свою базу и сразу спросить вес порции. */
    private fun saveNewProduct(product: Product) {
        val products = store.products()
        products.add(product)
        store.saveProducts(products)
        refreshSearchAdapter()
        askGrams(product)
    }

    /**
     * Отсканирован код: если он уже привязан к продукту в базе — сразу
     * спрашиваем вес; иначе ищем его в общей базе продуктов, а при неудаче
     * предлагаем создать продукт вручную.
     */
    private fun onBarcodeScanned(code: String) {
        val existing = store.products().find { it.barcode == code }
        if (existing != null) {
            askGrams(existing)
            return
        }
        val progress = showProgressDialog(this, R.string.online_lookup_progress)
        FoodFacts.byBarcode(code) { found ->
            if (isFinishing || !progress.isShowing) return@byBarcode
            progress.dismiss()
            if (found != null) {
                Toast.makeText(this, R.string.online_found, Toast.LENGTH_SHORT).show()
                ProductDialog.show(this, R.string.add_product, found, code) { product ->
                    saveNewProduct(product)
                }
            } else {
                askCreateProduct(code)
            }
        }
    }

    private fun askCreateProduct(code: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.barcode_unknown_title)
            .setMessage(getString(R.string.barcode_unknown_message, code))
            .setPositiveButton(R.string.add_product) { _, _ ->
                ProductDialog.show(this, R.string.add_product, null, code) { product ->
                    saveNewProduct(product)
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

        val mealSpinner = view.findViewById<Spinner>(R.id.foodMeal)
        mealSpinner.visibility = View.VISIBLE
        mealSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            resources.getStringArray(R.array.meals)
        )
        mealSpinner.setSelection(
            existing?.meal ?: mealForTime(LocalTime.now().format(timeFormat))
        )

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
                    carbs = parseNum(carbsInput.text.toString()) ?: 0.0,
                    meal = mealSpinner.selectedItemPosition
                )
                if (position == null) {
                    addEntry(entry)
                } else {
                    entries[position] = entry
                    store.save(date, entries)
                    adapter.rebuild()
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
                adapter.rebuild()
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

    private fun updateMacroRing(ring: MacroRingView, value: Double, goalGrams: Int) {
        val sub = if (goalGrams > 0) {
            getString(R.string.ring_slash, goalGrams)
        } else {
            getString(R.string.ring_grams)
        }
        ring.setData(value, goalGrams.toDouble(), fmt(round1(value)), sub)
    }

    private fun refreshSummary() {
        val total = entries.sumOf { it.kcal }
        val goal = store.goal

        kcalRing.setData(
            total.toDouble(),
            goal.toDouble(),
            total.toString(),
            getString(R.string.ring_of, goal)
        )
        goalText.text = getString(R.string.goal_value, goal)

        val remaining = goal - total
        remainingText.text = if (remaining >= 0) {
            getString(R.string.remaining, remaining)
        } else {
            getString(R.string.exceeded, -remaining)
        }

        updateMacroRing(proteinRing, entries.sumOf { it.protein }, store.goalProtein)
        updateMacroRing(fatRing, entries.sumOf { it.fat }, store.goalFat)
        updateMacroRing(carbsRing, entries.sumOf { it.carbs }, store.goalCarbs)

        refreshProteinPerKg()
        refreshWater()

        emptyText.visibility = if (entries.isEmpty()) TextView.VISIBLE else TextView.GONE

        WidgetProvider.updateAll(this)
    }

    /** «Белок: 1,4 г/кг • цель 2 г/кг» — ключевая метрика на массе. */
    private fun refreshProteinPerKg() {
        val weight = store.currentWeight()
        if (weight == null || weight <= 0) {
            proteinPerKgText.visibility = View.GONE
            return
        }
        proteinPerKgText.visibility = View.VISIBLE
        val perKg = round1(entries.sumOf { it.protein } / weight)
        proteinPerKgText.text = getString(
            R.string.protein_per_kg_line,
            fmt(perKg),
            fmt(round1(store.proteinPerKg))
        )
    }
}
