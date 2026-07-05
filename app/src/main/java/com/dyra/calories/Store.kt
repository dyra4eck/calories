package com.dyra.calories

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Хранилище на SharedPreferences: записи лежат по ключу даты в виде JSON. */
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
                    time = obj.optString("time")
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
            )
        }
        prefs.edit().putString(key(date), array.toString()).apply()
    }

    private fun key(date: LocalDate) = "entries_$date"
}
