package com.dyra.calories

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Проверка новой версии приложения по последнему GitHub-релизу. */
object UpdateChecker {

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/dyra4eck/calories/releases/latest"

    data class Release(
        /** Версия без префикса «v», например «1.5». */
        val version: String,
        val notes: String,
        /** Прямая ссылка на APK, либо страница релиза, если APK не приложен. */
        val downloadUrl: String
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** null в колбэке (на главном потоке) — не удалось проверить. */
    fun check(callback: (Release?) -> Unit) {
        executor.execute {
            val release = try {
                parseRelease(fetch(LATEST_RELEASE_URL))
            } catch (e: Exception) {
                null
            }
            mainHandler.post { callback(release) }
        }
    }

    /** Сравнение версий вида «1.5» / «1.4.2» по числовым частям. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val b = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun parseRelease(json: String): Release? {
        val root = JSONObject(json)
        val tag = root.optString("tag_name").removePrefix("v")
        if (tag.isEmpty()) return null

        // Предпочитаем подписанный release-APK, потом любой APK, потом страницу релиза
        var apkUrl: String? = null
        val assets = root.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (!name.endsWith(".apk")) continue
                val url = asset.optString("browser_download_url")
                if (name.contains("release")) {
                    apkUrl = url
                    break
                }
                if (apkUrl == null) apkUrl = url
            }
        }
        return Release(
            version = tag,
            notes = root.optString("body").trim(),
            downloadUrl = apkUrl ?: root.optString("html_url")
        )
    }

    private fun fetch(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
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
