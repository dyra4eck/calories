package com.dyra.calories

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Хранилище на SharedPreferences: записи и продукты лежат в виде JSON. */
class Store(context: Context) {

    private val prefs = context.getSharedPreferences("calories", Context.MODE_PRIVATE)

    var goal: Int
        get() = prefs.getInt("goal", 2000)
        set(value) = prefs.edit().putInt("goal", value).apply()

    /** Цели по БЖУ в граммах; 0 — цель не задана. */
    var goalProtein: Int
        get() = prefs.getInt("goal_protein", 0)
        set(value) = prefs.edit().putInt("goal_protein", value).apply()

    var goalFat: Int
        get() = prefs.getInt("goal_fat", 0)
        set(value) = prefs.edit().putInt("goal_fat", value).apply()

    var goalCarbs: Int
        get() = prefs.getInt("goal_carbs", 0)
        set(value) = prefs.edit().putInt("goal_carbs", value).apply()

    // Цели тренировочного дня; kcal == 0 — не заданы, действуют обычные
    var goalTrain: Int
        get() = prefs.getInt("goal_train", 0)
        set(value) = prefs.edit().putInt("goal_train", value).apply()

    var goalTrainProtein: Int
        get() = prefs.getInt("goal_train_protein", 0)
        set(value) = prefs.edit().putInt("goal_train_protein", value).apply()

    var goalTrainFat: Int
        get() = prefs.getInt("goal_train_fat", 0)
        set(value) = prefs.edit().putInt("goal_train_fat", value).apply()

    var goalTrainCarbs: Int
        get() = prefs.getInt("goal_train_carbs", 0)
        set(value) = prefs.edit().putInt("goal_train_carbs", value).apply()

    fun isTrainingDay(date: LocalDate): Boolean {
        val raw = prefs.getString("train_days", null) ?: return false
        return JSONObject(raw).optBoolean(date.toString(), false)
    }

    fun setTrainingDay(date: LocalDate, on: Boolean) {
        val obj = JSONObject(prefs.getString("train_days", null) ?: "{}")
        if (on) obj.put(date.toString(), true) else obj.remove(date.toString())
        prefs.edit().putString("train_days", obj.toString()).apply()
    }

    data class Goals(val kcal: Int, val protein: Int, val fat: Int, val carbs: Int)

    /** Цели на конкретный день: в тренировочный — свой профиль, если задан. */
    fun goalsFor(date: LocalDate): Goals =
        if (isTrainingDay(date) && goalTrain > 0) {
            Goals(goalTrain, goalTrainProtein, goalTrainFat, goalTrainCarbs)
        } else {
            Goals(goal, goalProtein, goalFat, goalCarbs)
        }

    var reminderEnabled: Boolean
        get() = prefs.getBoolean("reminder_on", false)
        set(value) = prefs.edit().putBoolean("reminder_on", value).apply()

    var reminderHour: Int
        get() = prefs.getInt("reminder_hour", 20)
        set(value) = prefs.edit().putInt("reminder_hour", value).apply()

    var reminderMinute: Int
        get() = prefs.getInt("reminder_minute", 0)
        set(value) = prefs.edit().putInt("reminder_minute", value).apply()

    /** Когда в последний раз тихо проверяли обновления (мс с эпохи). */
    var updateCheckedAt: Long
        get() = prefs.getLong("update_checked_at", 0L)
        set(value) = prefs.edit().putLong("update_checked_at", value).apply()

    // ---------- Вес и параметры тела ----------

    /** Целевой вес в кг; 0 — не задан. */
    var targetWeight: Double
        get() = prefs.getFloat("target_weight", 0f).toDouble()
        set(value) = prefs.edit().putFloat("target_weight", value.toFloat()).apply()

    var profileMale: Boolean
        get() = prefs.getBoolean("profile_male", true)
        set(value) = prefs.edit().putBoolean("profile_male", value).apply()

    /** Возраст в годах; 0 — профиль не заполнен. */
    var profileAge: Int
        get() = prefs.getInt("profile_age", 0)
        set(value) = prefs.edit().putInt("profile_age", value).apply()

    /** Рост в см; 0 — профиль не заполнен. */
    var profileHeight: Int
        get() = prefs.getInt("profile_height", 0)
        set(value) = prefs.edit().putInt("profile_height", value).apply()

    /** Индекс уровня активности (см. MacroCalculator.ACTIVITY_FACTORS). */
    var profileActivity: Int
        get() = prefs.getInt("profile_activity", 2)
        set(value) = prefs.edit().putInt("profile_activity", value).apply()

    /** Записи веса по датам, отсортированы от старых к новым. */
    fun weights(): List<Pair<LocalDate, Double>> {
        val raw = prefs.getString("weights", null) ?: return emptyList()
        val obj = JSONObject(raw)
        val list = mutableListOf<Pair<LocalDate, Double>>()
        for (key in obj.keys()) {
            try {
                list.add(LocalDate.parse(key) to obj.getDouble(key))
            } catch (e: Exception) {
                // Битую запись пропускаем
            }
        }
        return list.sortedBy { it.first }
    }

    /** Последняя запись веса — текущий вес; null, если записей нет. */
    fun currentWeight(): Double? = weights().lastOrNull()?.second

    fun setWeight(date: LocalDate, kg: Double) {
        val obj = JSONObject(prefs.getString("weights", null) ?: "{}")
        obj.put(date.toString(), kg)
        prefs.edit().putString("weights", obj.toString()).apply()
    }

    fun removeWeight(date: LocalDate) {
        val obj = JSONObject(prefs.getString("weights", null) ?: "{}")
        obj.remove(date.toString())
        prefs.edit().putString("weights", obj.toString()).apply()
    }

    // ---------- Замеры тела (обхваты, см) ----------

    /** Замеры одного типа (индекс из массива measure_types), от старых к новым. */
    fun measurements(type: Int): List<Pair<LocalDate, Double>> {
        val root = JSONObject(prefs.getString("measurements", null) ?: return emptyList())
        val obj = root.optJSONObject(type.toString()) ?: return emptyList()
        val list = mutableListOf<Pair<LocalDate, Double>>()
        for (key in obj.keys()) {
            try {
                list.add(LocalDate.parse(key) to obj.getDouble(key))
            } catch (e: Exception) {
                // Битую запись пропускаем
            }
        }
        return list.sortedBy { it.first }
    }

    fun setMeasurement(type: Int, date: LocalDate, value: Double) {
        val root = JSONObject(prefs.getString("measurements", null) ?: "{}")
        val obj = root.optJSONObject(type.toString()) ?: JSONObject()
        obj.put(date.toString(), value)
        root.put(type.toString(), obj)
        prefs.edit().putString("measurements", root.toString()).apply()
    }

    fun removeMeasurement(type: Int, date: LocalDate) {
        val root = JSONObject(prefs.getString("measurements", null) ?: return)
        root.optJSONObject(type.toString())?.remove(date.toString())
        prefs.edit().putString("measurements", root.toString()).apply()
    }

    private fun entriesFromJson(array: JSONArray): MutableList<Entry> {
        val list = mutableListOf<Entry>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val time = obj.optString("time")
            list.add(
                Entry(
                    name = obj.optString("name"),
                    kcal = obj.optInt("kcal"),
                    time = time,
                    protein = obj.optDouble("protein", 0.0),
                    fat = obj.optDouble("fat", 0.0),
                    carbs = obj.optDouble("carbs", 0.0),
                    // У старых записей приёма пищи нет — определяем по времени
                    meal = if (obj.has("meal")) obj.getInt("meal") else mealForTime(time)
                )
            )
        }
        return list
    }

    private fun entriesToJson(entries: List<Entry>): JSONArray {
        val array = JSONArray()
        for (entry in entries) {
            array.put(
                JSONObject()
                    .put("name", entry.name)
                    .put("kcal", entry.kcal)
                    .put("time", entry.time)
                    .put("protein", entry.protein)
                    .put("fat", entry.fat)
                    .put("carbs", entry.carbs)
                    .put("meal", entry.meal)
            )
        }
        return array
    }

    fun entriesFor(date: LocalDate): MutableList<Entry> {
        val raw = prefs.getString(key(date), null) ?: return mutableListOf()
        return entriesFromJson(JSONArray(raw))
    }

    fun save(date: LocalDate, entries: List<Entry>) {
        if (entries.isEmpty()) {
            prefs.edit().remove(key(date)).apply()
            return
        }
        prefs.edit().putString(key(date), entriesToJson(entries).toString()).apply()
    }

    // ---------- Вода ----------

    /** Дневная норма воды в мл. */
    var waterGoal: Int
        get() = prefs.getInt("water_goal", 2000)
        set(value) = prefs.edit().putInt("water_goal", value).apply()

    fun waterFor(date: LocalDate): Int = prefs.getInt("water_$date", 0)

    fun setWater(date: LocalDate, ml: Int) {
        if (ml <= 0) {
            prefs.edit().remove("water_$date").apply()
        } else {
            prefs.edit().putInt("water_$date", ml).apply()
        }
    }

    /** Норма белка в г/кг веса — для индикатора и калькулятора КБЖУ. */
    var proteinPerKg: Double
        get() = prefs.getFloat("protein_per_kg", 1.8f).toDouble()
        set(value) = prefs.edit().putFloat("protein_per_kg", value.toFloat()).apply()

    // ---------- Шаблоны дня ----------

    fun templateNames(): List<String> {
        val obj = JSONObject(prefs.getString("templates", null) ?: return emptyList())
        return obj.keys().asSequence().toList().sorted()
    }

    fun saveTemplate(name: String, entries: List<Entry>) {
        val obj = JSONObject(prefs.getString("templates", null) ?: "{}")
        obj.put(name, entriesToJson(entries))
        prefs.edit().putString("templates", obj.toString()).apply()
    }

    fun templateEntries(name: String): List<Entry> {
        val obj = JSONObject(prefs.getString("templates", null) ?: return emptyList())
        val array = obj.optJSONArray(name) ?: return emptyList()
        return entriesFromJson(array)
    }

    fun deleteTemplate(name: String) {
        val obj = JSONObject(prefs.getString("templates", null) ?: return)
        obj.remove(name)
        prefs.edit().putString("templates", obj.toString()).apply()
    }

    fun products(): MutableList<Product> {
        val raw = prefs.getString("products", null) ?: return mutableListOf()
        val array = JSONArray(raw)
        val list = mutableListOf<Product>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                Product(
                    name = obj.optString("name"),
                    kcal100 = obj.optDouble("kcal", 0.0),
                    protein100 = obj.optDouble("protein", 0.0),
                    fat100 = obj.optDouble("fat", 0.0),
                    carbs100 = obj.optDouble("carbs", 0.0),
                    barcode = obj.optString("barcode").ifEmpty { null },
                    uses = obj.optInt("uses", 0)
                )
            )
        }
        return list
    }

    fun saveProducts(products: List<Product>) {
        val array = JSONArray()
        for (product in products) {
            val obj = JSONObject()
                .put("name", product.name)
                .put("kcal", product.kcal100)
                .put("protein", product.protein100)
                .put("fat", product.fat100)
                .put("carbs", product.carbs100)
                .put("uses", product.uses)
            product.barcode?.let { obj.put("barcode", it) }
            array.put(obj)
        }
        prefs.edit().putString("products", array.toString()).apply()
    }

    /** Отметить использование продукта (для сортировки по частоте). */
    fun incrementUse(product: Product) {
        val list = products()
        val index = list.indexOfFirst { it.name == product.name && it.barcode == product.barcode }
        if (index >= 0) {
            list[index] = list[index].copy(uses = list[index].uses + 1)
            saveProducts(list)
        }
    }

    /** Полный дамп данных (цели, продукты, записи по дням) в JSON. */
    fun exportJson(): String {
        val days = JSONObject()
        val water = JSONObject()
        for ((key, value) in prefs.all) {
            if (key.startsWith("entries_") && value is String) {
                days.put(key.removePrefix("entries_"), JSONArray(value))
            }
            if (key.startsWith("water_") && key != "water_goal" && value is Int) {
                water.put(key.removePrefix("water_"), value)
            }
        }
        return JSONObject()
            .put("waterGoal", waterGoal)
            .put("proteinPerKg", proteinPerKg)
            .put("water", water)
            .put("templates", JSONObject(prefs.getString("templates", "{}")))
            .put("goalTrain", goalTrain)
            .put("goalTrainProtein", goalTrainProtein)
            .put("goalTrainFat", goalTrainFat)
            .put("goalTrainCarbs", goalTrainCarbs)
            .put("trainDays", JSONObject(prefs.getString("train_days", "{}")))
            .put("goal", goal)
            .put("goalProtein", goalProtein)
            .put("goalFat", goalFat)
            .put("goalCarbs", goalCarbs)
            .put("targetWeight", targetWeight)
            .put(
                "profile",
                JSONObject()
                    .put("male", profileMale)
                    .put("age", profileAge)
                    .put("height", profileHeight)
                    .put("activity", profileActivity)
            )
            .put("weights", JSONObject(prefs.getString("weights", "{}")))
            .put("measurements", JSONObject(prefs.getString("measurements", "{}")))
            .put("products", JSONArray(prefs.getString("products", "[]")))
            .put("days", days)
            .toString(2)
    }

    /** Восстановление из дампа: полностью заменяет текущие данные. */
    fun importJson(text: String) {
        val root = JSONObject(text)
        val goalValue = root.optInt("goal", 2000)
        val productsValue = root.optJSONArray("products")
        val daysValue = root.optJSONObject("days")

        val reminderOn = reminderEnabled
        val reminderH = reminderHour
        val reminderM = reminderMinute

        val editor = prefs.edit().clear()
        editor.putInt("goal", goalValue)
        editor.putInt("goal_protein", root.optInt("goalProtein", 0))
        editor.putInt("goal_fat", root.optInt("goalFat", 0))
        editor.putInt("goal_carbs", root.optInt("goalCarbs", 0))
        editor.putBoolean("reminder_on", reminderOn)
        editor.putInt("reminder_hour", reminderH)
        editor.putInt("reminder_minute", reminderM)
        editor.putFloat("target_weight", root.optDouble("targetWeight", 0.0).toFloat())
        root.optJSONObject("profile")?.let { profile ->
            editor.putBoolean("profile_male", profile.optBoolean("male", true))
            editor.putInt("profile_age", profile.optInt("age", 0))
            editor.putInt("profile_height", profile.optInt("height", 0))
            editor.putInt("profile_activity", profile.optInt("activity", 2))
        }
        root.optJSONObject("weights")?.let { editor.putString("weights", it.toString()) }
        root.optJSONObject("measurements")?.let { editor.putString("measurements", it.toString()) }
        editor.putInt("water_goal", root.optInt("waterGoal", 2000))
        editor.putFloat("protein_per_kg", root.optDouble("proteinPerKg", 1.8).toFloat())
        root.optJSONObject("water")?.let { water ->
            for (key in water.keys()) {
                editor.putInt("water_$key", water.getInt(key))
            }
        }
        root.optJSONObject("templates")?.let { editor.putString("templates", it.toString()) }
        editor.putInt("goal_train", root.optInt("goalTrain", 0))
        editor.putInt("goal_train_protein", root.optInt("goalTrainProtein", 0))
        editor.putInt("goal_train_fat", root.optInt("goalTrainFat", 0))
        editor.putInt("goal_train_carbs", root.optInt("goalTrainCarbs", 0))
        root.optJSONObject("trainDays")?.let { editor.putString("train_days", it.toString()) }
        productsValue?.let { editor.putString("products", it.toString()) }
        daysValue?.let { days ->
            for (key in days.keys()) {
                editor.putString("entries_$key", days.getJSONArray(key).toString())
            }
        }
        editor.apply()
    }

    /**
     * Дневник в CSV (разделитель «;», как ждёт русский Excel):
     * дата, КБЖУ за день, вода, вес, тренировочный день.
     */
    fun exportCsv(): String {
        val dates = sortedSetOf<LocalDate>()
        for (key in prefs.all.keys) {
            val dateString = when {
                key.startsWith("entries_") -> key.removePrefix("entries_")
                key.startsWith("water_") && key != "water_goal" -> key.removePrefix("water_")
                else -> null
            }
            dateString?.let {
                try {
                    dates.add(LocalDate.parse(it))
                } catch (e: Exception) {
                    // Не дата — пропускаем
                }
            }
        }
        dates.addAll(weights().map { it.first })

        val weightByDate = weights().toMap()
        val sb = StringBuilder("Дата;Ккал;Белки;Жиры;Углеводы;Вода (мл);Вес (кг);Тренировка\n")
        for (date in dates) {
            val entries = entriesFor(date)
            sb.append(date).append(';')
                .append(entries.sumOf { it.kcal }).append(';')
                .append(fmt(round1(entries.sumOf { it.protein }))).append(';')
                .append(fmt(round1(entries.sumOf { it.fat }))).append(';')
                .append(fmt(round1(entries.sumOf { it.carbs }))).append(';')
                .append(waterFor(date)).append(';')
                .append(weightByDate[date]?.let { fmt(it) } ?: "").append(';')
                .append(if (isTrainingDay(date)) 1 else 0).append('\n')
        }
        return sb.toString()
    }

    private fun key(date: LocalDate) = "entries_$date"
}
