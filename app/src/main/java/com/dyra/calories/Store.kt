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

    fun entriesFor(date: LocalDate): MutableList<Entry> {
        val raw = prefs.getString(key(date), null) ?: return mutableListOf()
        val array = JSONArray(raw)
        val list = mutableListOf<Entry>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                Entry(
                    name = obj.optString("name"),
                    kcal = obj.optInt("kcal"),
                    time = obj.optString("time"),
                    protein = obj.optDouble("protein", 0.0),
                    fat = obj.optDouble("fat", 0.0),
                    carbs = obj.optDouble("carbs", 0.0)
                )
            )
        }
        return list
    }

    fun save(date: LocalDate, entries: List<Entry>) {
        if (entries.isEmpty()) {
            prefs.edit().remove(key(date)).apply()
            return
        }
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
            )
        }
        prefs.edit().putString(key(date), array.toString()).apply()
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
        for ((key, value) in prefs.all) {
            if (key.startsWith("entries_") && value is String) {
                days.put(key.removePrefix("entries_"), JSONArray(value))
            }
        }
        return JSONObject()
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
        productsValue?.let { editor.putString("products", it.toString()) }
        daysValue?.let { days ->
            for (key in days.keys()) {
                editor.putString("entries_$key", days.getJSONArray(key).toString())
            }
        }
        editor.apply()
    }

    private fun key(date: LocalDate) = "entries_$date"
}
