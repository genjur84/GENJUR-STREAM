package com.genjur.stream

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL

/**
 * Background catalog checker for feeds that explicitly declare redistribution permission.
 * It records a fresh snapshot; the app displays the latest catalog when opened/refreshed.
 */
class CatalogRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val sourcePrefs = applicationContext.getSharedPreferences("genjur_stream_sources", Context.MODE_PRIVATE)
        val libraryPrefs = applicationContext.getSharedPreferences("genjur_stream_library", Context.MODE_PRIVATE)
        return try {
            val urls = linkedSetOf<String>()
            val legacy = JSONArray(sourcePrefs.getString("items", "[]"))
            for (i in 0 until legacy.length()) {
                legacy.optJSONObject(i)?.optString("url")?.takeIf { it.startsWith("https://") }?.let(urls::add)
            }
            val installed = JSONArray(sourcePrefs.getString("installed_extensions", "[]"))
            for (i in 0 until installed.length()) {
                installed.optJSONObject(i)?.optString("feedUrl")?.takeIf { it.startsWith("https://") }?.let(urls::add)
            }
            if (urls.isEmpty()) return Result.success()

            val snapshot = JSONArray()
            var totalTitles = 0
            urls.forEach { sourceUrl ->
                val root = JSONObject(URL(sourceUrl).openConnection().apply {
                    connectTimeout = 12000
                    readTimeout = 20000
                    useCaches = false
                    setRequestProperty("Cache-Control", "no-cache, no-store")
                }.getInputStream().bufferedReader().use { it.readText() })
                val rights = root.optJSONObject("rights") ?: return@forEach
                val terms = rights.optString("termsUrl")
                val termsUri = android.net.Uri.parse(terms)
                val allowed = rights.optBoolean("redistributionAllowed", false) &&
                    rights.optString("licenseId").isNotBlank() &&
                    termsUri.scheme == "https" && !termsUri.host.isNullOrBlank() &&
                    rights.optString("attribution").isNotBlank()
                if (!allowed) return@forEach
                val items = root.optJSONArray("items") ?: return@forEach
                val sourceSnapshot = JSONArray()
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val title = item.optString("title").trim()
                    if (title.isBlank()) continue
                    sourceSnapshot.put(JSONObject()
                        .put("title", title)
                        .put("releaseYear", item.optString("releaseYear"))
                        .put("posterUrl", item.optString("posterUrl")))
                    totalTitles++
                }
                snapshot.put(JSONObject()
                    .put("sourceUrl", sourceUrl)
                    .put("checkedAt", System.currentTimeMillis())
                    .put("items", sourceSnapshot))
            }
            libraryPrefs.edit()
                .putString("background_catalog_snapshot", snapshot.toString())
                .putLong("background_catalog_checked_at", System.currentTimeMillis())
                .putInt("background_catalog_title_count", totalTitles)
                .apply()
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
