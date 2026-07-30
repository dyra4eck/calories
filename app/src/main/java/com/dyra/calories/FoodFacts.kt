package com.dyra.calories

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

/**
 * Клиент Open Food Facts — открытой общей базы продуктов (миллионы товаров,
 * включая российские). Сначала спрашиваем русское зеркало ru.openfoodfacts.org
 * (русские названия), при неудаче — мировое.
 */
object FoodFacts {

    private const val FIELDS = "code,product_name,product_name_ru,brands,nutriments"
    private const val USER_AGENT = "CaloriesApp/2.0 (https://github.com/dyra4eck/calories)"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Поиск по штрих-коду; в колбэк (на главном потоке) придёт null, если не нашли или нет сети. */
    fun byBarcode(code: String, callback: (Product?) -> Unit) {
        executor.execute {
            val product = lookupBarcode("ru.openfoodfacts.org", code)
                ?: lookupBarcode("world.openfoodfacts.org", code)
            mainHandler.post { callback(product) }
        }
    }

    /**
     * Поиск по названию; null — ошибка сети, пустой список — ничего не нашлось.
     * Результаты отсортированы по популярности (числу сканирований).
     */
    fun search(query: String, callback: (List<Product>?) -> Unit) {
        executor.execute {
            val result = try {
                val url = "https://ru.openfoodfacts.org/cgi/search.pl" +
                    "?action=process&json=1&search_simple=1&page_size=25" +
                    "&sort_by=unique_scans_n&fields=$FIELDS" +
                    "&search_terms=" + URLEncoder.encode(query, "UTF-8")
                val products = JSONObject(fetch(url)).optJSONArray("products") ?: JSONArray()
                (0 until products.length()).mapNotNull { i ->
                    val obj = products.getJSONObject(i)
                    parseProduct(obj, obj.optString("code").ifEmpty { null })
                }
            } catch (e: Exception) {
                null
            }
            mainHandler.post { callback(result) }
        }
    }

    private fun lookupBarcode(host: String, code: String): Product? = try {
        val root = JSONObject(fetch("https://$host/api/v2/product/$code.json?fields=$FIELDS"))
        if (root.optInt("status") == 1) {
            root.optJSONObject("product")?.let { parseProduct(it, code) }
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }

    /** null — в записи нет названия или калорийности, добавлять нечего. */
    private fun parseProduct(obj: JSONObject, barcode: String?): Product? {
        val nutriments = obj.optJSONObject("nutriments") ?: return null
        var kcal = nutriments.optDouble("energy-kcal_100g", Double.NaN)
        if (kcal.isNaN()) {
            // Часть записей хранит только энергию в кДж
            val kj = nutriments.optDouble("energy_100g", Double.NaN)
            if (kj.isNaN()) return null
            kcal = kj / 4.184
        }
        val name = obj.optString("product_name_ru").trim()
            .ifEmpty { obj.optString("product_name").trim() }
        if (name.isEmpty()) return null
        val brands = obj.optString("brands").trim()
        val fullName = if (brands.isNotEmpty() && !name.contains(brands, ignoreCase = true)) {
            "$name ($brands)"
        } else {
            name
        }
        return Product(
            name = fullName,
            kcal100 = round1(kcal),
            protein100 = round1(nutriments.optDouble("proteins_100g", 0.0)),
            fat100 = round1(nutriments.optDouble("fat_100g", 0.0)),
            carbs100 = round1(nutriments.optDouble("carbohydrates_100g", 0.0)),
            barcode = barcode
        )
    }

    private fun fetch(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("User-Agent", USER_AGENT)
        try {
            if (connection.responseCode != 200) {
                throw IOException("HTTP ${connection.responseCode}")
            }
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}
