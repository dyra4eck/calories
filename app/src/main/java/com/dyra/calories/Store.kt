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
                    carbs100 = obj.optDouble("carbs", 0.0)
                )
            )
        }
        return list
    }

    fun saveProducts(products: List<Product>) {
        val array = JSONArray()
        for (product in products) {
            array.put(
                JSONObject()
                    .put("name", product.name)
                    .put("kcal", product.kcal100)
                    .put("protein", product.protein100)
                    .put("fat", product.fat100)
                    .put("carbs", product.carbs100)
            )
        }
        prefs.edit().putString("products", array.toString()).apply()
    }

    private fun key(date: LocalDate) = "entries_$date"
}
