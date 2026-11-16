package com.genjur.stream

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Kontrak plugin sumber GENJUR STREAM.
 * Plugin hanya dipilih dari daftar bawaan aplikasi; repositori jarak jauh
 * tidak dapat mengirim atau mengeksekusi kode Kotlin.
 */
internal interface SourcePlugin {
    val id: String
    val displayName: String
    fun supports(sourceUrl: String): Boolean
    suspend fun loadCatalog(sourceName: String, sourceUrl: String): List<FeedMovie>
}

internal class CmaxSourcePlugin : SourcePlugin {
    override val id: String = "cmax"
    override val displayName: String = "Cmax"

    override fun supports(sourceUrl: String): Boolean {
        val host = android.net.Uri.parse(sourceUrl).host.orEmpty().removePrefix("www.")
        return host.equals("cimax21.my", ignoreCase = true) ||
            host.endsWith(".cimax21.my", ignoreCase = true)
    }

    override suspend fun loadCatalog(sourceName: String, sourceUrl: String): List<FeedMovie> =
        withContext(Dispatchers.IO) {
            val connection = URL(sourceUrl).openConnection().apply {
                connectTimeout = 15000
                readTimeout = 20000
                useCaches = false
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "id-ID,id;q=0.9,en;q=0.8")
            }
            val html = connection.getInputStream().bufferedReader().use { it.readText() }
            loadGenericHtmlCatalog(sourceUrl, html, sourceName)
        }
}

/** Daftar plugin yang benar-benar tersedia di APK. */
internal object SourcePluginRegistry {
    private val installedPlugins: List<SourcePlugin> = listOf(CmaxSourcePlugin())

    fun resolve(sourceUrl: String): SourcePlugin? =
        installedPlugins.firstOrNull { it.supports(sourceUrl) }

    fun installedPluginIds(): Set<String> = installedPlugins.map { it.id }.toSet()
}
