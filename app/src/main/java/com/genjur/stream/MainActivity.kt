package com.genjur.stream

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.Environment
import android.webkit.WebChromeClient
import android.webkit.DownloadListener
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.onFocusChanged
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import coil.request.ImageRequest
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerNotificationManager
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import androidx.media3.common.Player
import androidx.core.content.FileProvider
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.ByteArrayInputStream
import java.net.URL
import org.jsoup.Jsoup

private const val UPDATE_INFO_URL = "https://raw.githubusercontent.com/genjur84/GENJUR-STREAM/main/latest.json"
private const val PLAYBACK_NOTIFICATION_CHANNEL = "genjur_playback"
private const val PLAYBACK_NOTIFICATION_ID = 8401

private val VIDEO_FILTER_CATEGORIES = listOf("Animasi", "Drama", "Action", "Adventure", "Comedy", "Crime", "Documentary", "Family", "Fantasy", "Horror", "Mystery", "Romance", "Sci-Fi", "Thriller", "Adult 18+")
private val DEFAULT_VIDEO_FILTERS = VIDEO_FILTER_CATEGORIES.filterNot { it == "Adult 18+" }.toSet()

private fun matchesVideoFilters(movie: FeedMovie, enabled: Set<String>): Boolean {
    val text = (movie.title + " " + movie.category + " " + movie.genres.joinToString(" ") + " " + movie.description)
        .lowercase()
        .replace(Regex("""<[^>]*>"""), " ")
        .replace("&nbsp;", " ")
    // Deteksi istilah dewasa Indonesia/Inggris dari judul, kategori, genre, maupun detail film.
    // Frasa "film romance" ikut ditandai, tetapi genre "Romance" biasa tidak otomatis dianggap dewasa.
    val adult = Regex(
        """(?i)(\b18\s*\+\b|\b21\s*\+\b|\badult\b|\bdewasa\b|\bbokep\b|\bblue\s*film\b|\bfilm\s+semi\b|\bsemi\s+dewasa\b|\bsemi\b|\berotis\b|\berotic\b|\berotica\b|\bporn(?:o|ographic)?\b|\bxxx\b|\bhentai\b|\bjav\b|\buncensored\b|\bsex(?:ual)?\b|\bnudity\b|\bfilm\s+romance\b)"""
    ).containsMatchIn(text)
    if (adult) return "Adult 18+" in enabled
    val tags = (movie.genres + movie.category.split(",", "/", "|", "•")).map { raw ->
        when (raw.trim().lowercase()) {
            "animation", "animasi", "anime", "cartoon" -> "Animasi"
            "drama" -> "Drama"
            "action", "aksi" -> "Action"
            "adventure", "petualangan" -> "Adventure"
            "comedy", "komedi" -> "Comedy"
            "crime", "kriminal" -> "Crime"
            "documentary", "dokumenter" -> "Documentary"
            "family", "keluarga" -> "Family"
            "fantasy", "fantasi" -> "Fantasy"
            "horror", "horor" -> "Horror"
            "mystery", "misteri" -> "Mystery"
            "romance", "romantis" -> "Romance"
            "sci-fi", "science fiction", "science-fiction", "fiksi ilmiah" -> "Sci-Fi"
            "thriller", "seru" -> "Thriller"
            else -> ""
        }
    }.filter { it.isNotBlank() }.toSet()
    return tags.isEmpty() || tags.any { it in enabled }
}

/** Domain iklan dan pelacak umum untuk pemblokiran dasar di WebView. */
private val GENJUR_AD_HOSTS = setOf(
    "doubleclick.net", "googlesyndication.com", "googleadservices.com",
    "adservice.google.com", "adnxs.com", "popads.net", "popcash.net",
    "propellerads.com", "exoclick.com", "trafficjunky.net", "juicyads.com",
    "taboola.com", "outbrain.com", "mgid.com", "scorecardresearch.com",
    "zedo.com", "adsterra.com", "hilltopads.net", "onclickalgo.com"
)

private fun isGenjurAdHost(host: String?): Boolean {
    val normalized = host?.lowercase()?.trim('.') ?: return false
    return GENJUR_AD_HOSTS.any { normalized == it || normalized.endsWith(".$it") }
}
private val Bg = Color(0xFF09090D)
private val Panel = Color(0xFF191821)
private val Green = Color(0xFF8B5CF6)
private val Muted = Color(0xFFACB7C5)


private class PlaybackStartedBridge(
    private val onPlayback: (String, String) -> Unit,
    private val onAutoPlay: (String, String, String) -> Unit,
    private val onVideoDetected: (String, String, String, String, Long) -> Unit
) {
    @JavascriptInterface
    fun onVideoPlay(title: String, pageUrl: String, videoUrl: String) {
        // Jangan langsung membuka URL pertama saat tombol Play ditekan:
        // pada beberapa sumber, video pertama adalah iklan pre-roll.
        onPlayback(title, pageUrl)
    }
    @JavascriptInterface
    fun onVideoAvailable(title: String, pageUrl: String, videoUrl: String, quality: String, sizeBytes: Long) {
        onVideoDetected(title, pageUrl, videoUrl, quality, sizeBytes)
    }
}

internal data class StreamOption(val quality: String, val videoUrl: String, val sizeBytes: Long = 0L, val referer: String = "")

private fun detectedQualityLabel(label: String, videoUrl: String): String {
    val source = "$label $videoUrl"
    val match = Regex("""(?i)(2160|1440|1080|720|480|360)\s*p?""").find(source)
        ?: Regex("""(?i)(?:height|res|quality)[=_:/-]?(2160|1440|1080|720|480|360)""").find(source)
    if (match != null) return "${match.groupValues[1]}p"
    if (source.contains("4k", ignoreCase = true)) return "2160p"
    return "Kualitas sumber (otomatis)"
}

private fun catalogQualityFromText(text: String): String {
    val value = text.uppercase().replace(Regex("\\s+"), " ")
    val labels = listOf(
        "2160P" to Regex("\\b2160\\s*P?\\b"), "4K" to Regex("\\b4K\\b"),
        "1080P" to Regex("\\b1080\\s*P?\\b"), "FHD" to Regex("\\bFHD\\b"),
        "720P" to Regex("\\b720\\s*P?\\b"), "HD" to Regex("\\bHD\\b"),
        "480P" to Regex("\\b480\\s*P?\\b"), "SD" to Regex("\\bSD\\b"),
        "360P" to Regex("\\b360\\s*P?\\b"),
        "CAM" to Regex("\\b(?:CAM|HDCAM|HDTS|TS|TELESYNC)\\b")
    )
    return labels.firstOrNull { it.second.containsMatchIn(value) }?.first.orEmpty()
}

private fun movieCatalogQuality(movie: FeedMovie): String {
    val streamQuality = movie.streamOptions.map { catalogQualityFromText(it.quality) }
        .firstOrNull { it.isNotBlank() }.orEmpty()
    return streamQuality.ifBlank { movie.qualityLabel }
}

internal data class FeedMovie(
    val title: String,
    val description: String,
    val category: String,
    val videoUrl: String,
    val licenseId: String,
    val termsUrl: String,
    val attribution: String,
    val posterUrl: String = "",
    val qualityLabel: String = "",
    val releaseYear: String = "",
    val genres: List<String> = emptyList(),
    val duration: String = "",
    val downloadUrl: String = "",
    val downloadAllowed: Boolean = false,
    val subtitleUrl: String = "",
    val subtitleLanguage: String = "",
    val streamOptions: List<StreamOption> = emptyList(),
    val trailerUrl: String = "",
    val sourceName: String = ""
)
private fun catalogSourceDisplayName(sourceUrl: String): String {
    val host = runCatching { Uri.parse(sourceUrl).host.orEmpty().lowercase().removePrefix("www.") }.getOrDefault("")
    return when {
        host.contains("lk21") -> "LK21"
        host.contains("geeknotdead") || host.contains("dutamovie") -> "Duta Movie"
        host.contains("cimax") || host.contains("cmax") -> "Cmax"
        host.contains("klikxxi") -> "Klikxxi"
        else -> host.ifBlank { "Sumber tidak dikenal" }
    }
}

private fun movieKey(movie: FeedMovie): String =
    "${movie.title.trim().lowercase()}|${movie.videoUrl.trim()}|${movie.licenseId}"

private fun encodeMovie(movie: FeedMovie): JSONObject = JSONObject().apply {
    put("title", movie.title); put("description", movie.description); put("category", movie.category)
    put("videoUrl", movie.videoUrl); put("licenseId", movie.licenseId); put("termsUrl", movie.termsUrl)
    put("attribution", movie.attribution); put("posterUrl", movie.posterUrl)
    put("qualityLabel", movie.qualityLabel); put("releaseYear", movie.releaseYear); put("genres", JSONArray(movie.genres)); put("duration", movie.duration)
    put("downloadUrl", movie.downloadUrl); put("downloadAllowed", movie.downloadAllowed)
    put("subtitleUrl", movie.subtitleUrl); put("subtitleLanguage", movie.subtitleLanguage)
    put("trailerUrl", movie.trailerUrl); put("sourceName", movie.sourceName)
    put("streams", JSONArray().apply { movie.streamOptions.forEach { put(JSONObject().put("quality", it.quality).put("videoUrl", it.videoUrl).put("referer", it.referer)) } })
}

private fun decodeMovie(json: JSONObject): FeedMovie {
    val genres = json.optJSONArray("genres")
    return FeedMovie(
        title = json.optString("title"), description = json.optString("description"),
        category = json.optString("category"), videoUrl = json.optString("videoUrl"),
        licenseId = json.optString("licenseId"), termsUrl = json.optString("termsUrl"),
        attribution = json.optString("attribution"), posterUrl = json.optString("posterUrl"),
        qualityLabel = json.optString("qualityLabel"), releaseYear = json.optString("releaseYear"),
        genres = if (genres == null) emptyList() else
            (0 until genres.length()).map { genres.optString(it) }.filter { it.isNotBlank() },
        duration = json.optString("duration"),
        downloadUrl = json.optString("downloadUrl"),
        downloadAllowed = json.optBoolean("downloadAllowed", false),
        subtitleUrl = json.optString("subtitleUrl"),
        subtitleLanguage = json.optString("subtitleLanguage", "id"),
        trailerUrl = json.optString("trailerUrl"),
        sourceName = json.optString("sourceName"),
        streamOptions = json.optJSONArray("streams")?.let { arr ->
            (0 until arr.length()).mapNotNull { index -> arr.optJSONObject(index)?.let { item ->
                StreamOption(item.optString("quality", "Otomatis"), item.optString("videoUrl"), referer = item.optString("referer"))
            } }.filter { it.videoUrl.isNotBlank() }
        } ?: emptyList()
    )
}


private fun downloadFileName(title: String, year: String, extension: String): String {
    val detectedYear = year.trim().ifBlank {
        Regex("\\b(?:18|19|20)\\d{2}\\b").find(title)?.value.orEmpty()
    }
    val titleWithoutYear = title.replace(Regex("\\b(?:18|19|20)\\d{2}\\b"), " ")
    val safeTitle = titleWithoutYear.lowercase(java.util.Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), "_").trim('_')
        .ifBlank { "video" }
    val safeYear = detectedYear.takeIf { it.matches(Regex("(18|19|20)\\d{2}")) }
        ?.let { "_$it" }.orEmpty()
    val safeExtension = extension.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "mp4" }
    return "$safeTitle$safeYear.$safeExtension"
}

private fun formatVideoSize(bytes: Long): String {
    if (bytes <= 0L) return "Tidak diketahui"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
        else String.format(java.util.Locale.US, "%.1f MB", mb)
}

private fun formatDownloadSpeed(bytesPerSecond: Double): String {
    val mbps = (bytesPerSecond.coerceAtLeast(0.0) * 8.0) / 1_000_000.0
    return String.format(java.util.Locale.US, "%.2f Mbps", mbps)
}

private data class DownloadMetrics(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSecond: Double
)

private fun cleanCommonsText(value: String): String =
    value.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim()

private fun loadWikimediaPublicDomainMovies(): List<FeedMovie> {
    val apiUrl = "https://commons.wikimedia.org/w/api.php?action=query&generator=categorymembers&gcmtitle=Category%3AVideos_of_films_in_the_public_domain&gcmtype=file&gcmlimit=50&prop=imageinfo&iiprop=url%7Cmime%7Cextmetadata&iiurlwidth=480&format=json&formatversion=2"
    val connection = URL(apiUrl).openConnection()
    connection.connectTimeout = 15000
    connection.readTimeout = 30000
    connection.setRequestProperty("User-Agent", "GENJUR-STREAM/1.0 (Android app; public-domain catalog)")
    val root = JSONObject(connection.getInputStream().bufferedReader().use { it.readText() })
    val pages = root.optJSONObject("query")?.optJSONArray("pages")
        ?: error("Wikimedia Commons tidak mengembalikan daftar film.")
    val movies = mutableListOf<FeedMovie>()
    for (i in 0 until pages.length()) {
        val page = pages.optJSONObject(i) ?: continue
        val fileName = page.optString("title").removePrefix("File:").trim()
        val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
        val mime = info.optString("mime").lowercase()
        if (!mime.startsWith("video/")) continue
        val videoUrl = info.optString("url").trim()
        if (!videoUrl.startsWith("https://")) continue
        val metadata = info.optJSONObject("extmetadata") ?: continue
        fun meta(key: String): String =
            cleanCommonsText(metadata.optJSONObject(key)?.optString("value").orEmpty())
        val license = meta("LicenseShortName")
        val licenseLower = license.lowercase()
        val isPublicDomain = licenseLower.contains("public domain") || licenseLower == "pd"
        val isCc0 = licenseLower.contains("cc0")
        val isAttributionLicense = licenseLower.startsWith("cc by") &&
            !licenseLower.contains("nc") && !licenseLower.contains("nd")
        if (!isPublicDomain && !isCc0 && !isAttributionLicense) continue
        val licenseUrl = meta("LicenseUrl").ifBlank {
            if (isPublicDomain) "https://creativecommons.org/publicdomain/mark/1.0/" else ""
        }
        if (!licenseUrl.startsWith("https://")) continue
        val title = fileName.substringBeforeLast(".").replace('_', ' ')
            .replace(Regex("\\s+"), " ").trim()
        if (title.isBlank()) continue
        val artist = meta("Artist")
        val description = meta("ImageDescription").ifBlank {
            "Video film dari kategori domain publik Wikimedia Commons. Periksa halaman sumber untuk konteks dan ketentuan penggunaan."
        }
        val attribution = if (artist.isBlank()) "Wikimedia Commons — $fileName"
            else "$artist; Wikimedia Commons — $fileName"
        val poster = info.optString("thumburl").takeIf { it.startsWith("https://") }.orEmpty()
        val year = Regex("\\b(?:18|19|20)\\d{2}\\b").find(fileName)?.value.orEmpty()
        movies.add(FeedMovie(
            title = title,
            description = description,
            category = "Film Domain Publik",
            videoUrl = videoUrl,
            licenseId = license.ifBlank { "Public Domain" },
            termsUrl = licenseUrl,
            attribution = attribution,
            posterUrl = poster,
            releaseYear = year
        ))
    }
    return movies.distinctBy { it.videoUrl }.sortedBy { it.title.lowercase() }
}

private fun publishMp4ToPublicMovies(activity: MainActivity, source: File, title: String): Uri {
    require(source.exists() && source.length() > 0L) { "File MP4 sementara kosong." }
    val fileName = downloadFileName(title, "", "mp4")
    val resolver = activity.contentResolver
    val values = android.content.ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/GENJUR STREAM")
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "GENJUR STREAM")
            if (!dir.exists()) dir.mkdirs()
            put(android.provider.MediaStore.Video.Media.DATA, File(dir, fileName).absolutePath)
        }
    }
    val uri = resolver.insert(android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: error("Gagal membuat file di Movies/GENJUR STREAM.")
    try {
        resolver.openOutputStream(uri, "w")?.use { output -> source.inputStream().use { it.copyTo(output) } } ?: error("Tidak dapat membuka file tujuan.")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val ready = android.content.ContentValues().apply { put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, ready, null, null)
        }
        source.delete()
        return uri
    } catch (error: Throwable) { runCatching { resolver.delete(uri, null, null) }; throw error }
}
private fun deleteDownloadedUri(activity: MainActivity, value: String) {
    runCatching { val uri = Uri.parse(value); if (uri.scheme == "content") activity.contentResolver.delete(uri, null, null) else uri.path?.let { File(it).delete() } }
}
private data class DownloadEntry(val id: Long, val movie: FeedMovie)

private fun readDownloads(activity: MainActivity): List<DownloadEntry> = runCatching {
    val array = JSONArray(activity.getSharedPreferences("genjur_stream_library", 0).getString("downloads", "[]"))
    (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        val movie = item.optJSONObject("movie") ?: return@mapNotNull null
        DownloadEntry(item.optLong("id"), decodeMovie(movie))
    }
}.getOrDefault(emptyList())

private fun readSavedMovies(activity: MainActivity, key: String): List<FeedMovie> = runCatching {
    val array = JSONArray(activity.getSharedPreferences("genjur_stream_library", 0).getString(key, "[]"))
    (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::decodeMovie) }
}.getOrDefault(emptyList())

private fun savedFeedUrls(activity: MainActivity): List<String> = runCatching {
    // Ambil langsung link situs yang disimpan pengguna pada menu Sumber Link.
    val prefs = activity.getSharedPreferences("genjur_stream_saved_links", 0)
    val saved = JSONArray(prefs.getString("items", "[]"))
    (0 until saved.length()).mapNotNull { index ->
        saved.optJSONObject(index)?.optString("url")?.trim()
            ?.takeIf { it.startsWith("https://") }
    }.distinct()
}.getOrDefault(emptyList())

internal fun loadGenericHtmlCatalog(sourceUrl: String, html: String, sourceName: String = ""): List<FeedMovie> {
    val document = Jsoup.parse(html, sourceUrl)
    val sourceHost = Uri.parse(sourceUrl).host.orEmpty().removePrefix("www.").lowercase()
    val sourceLabel = sourceName.ifBlank { sourceHost }
    val pageDescription = document.selectFirst("meta[name=description], meta[property=og:description], meta[name=twitter:description]")?.attr("content").orEmpty().trim()
    val categoryWords = listOf("Action", "Adventure", "Animasi", "Animation", "Anime", "Comedy", "Komedi", "Crime", "Dokumenter", "Documentary", "Drama", "Family", "Fantasy", "Horor", "Horror", "Mystery", "Romance", "Sci-Fi", "Thriller", "Serial", "Series")
    val siteKey = when {
        sourceHost.contains("cimax21") || sourceLabel.contains("cmax", true) -> "cmax"
        sourceHost.contains("klikxxi") || sourceLabel.contains("klikxxi", true) -> "klikxxi"
        sourceHost.contains("lk21") || sourceLabel.contains("lk21", true) -> "lk21"
        sourceHost.contains("geeknotdead") || sourceLabel.contains("duta", true) -> "duta"
        else -> "generic"
    }
    val cardSelector = when (siteKey) {
        "cmax" -> "article, .item, .movie-item, .film-item, .ml-item, .poster, .thumb, .grid-item, .movie"
        "klikxxi" -> "article, .item, .movie-item, .film-item, .ml-item, .poster, .thumb, .grid-item, .movie, .series"
        "lk21" -> "article, .item, .movie-item, .film-item, .ml-item, .poster, .thumb, .grid-item, .movie, .film"
        "duta" -> "article, .item, .movie-item, .film-item, .ml-item, .poster, .thumb, .grid-item, .movie, .film"
        else -> "article, .item, .movie-item, .film-item, .ml-item, .poster, .thumb, .grid-item"
    }
    val candidateLinks = LinkedHashMap<String, org.jsoup.nodes.Element>()
    fun addLink(link: org.jsoup.nodes.Element) {
        val href = link.absUrl("href").trim()
        if (href.isBlank()) return
        val uri = Uri.parse(href)
        val host = uri.host.orEmpty().removePrefix("www.").lowercase()
        if (uri.scheme != "https" || host != sourceHost) return
        val path = uri.path.orEmpty().lowercase()
        if (path.isBlank() || path == "/" || path in setOf("/login", "/register", "/contact", "/about", "/genre", "/privacy-policy", "/dmca")) return
        candidateLinks.putIfAbsent(href, link)
    }
    document.select(cardSelector).forEach { card ->
        card.select("a[href]").forEach { link -> if (link.selectFirst("img") != null) addLink(link) }
    }
    document.select("a[href]").forEach { link -> if (link.selectFirst("img") != null) addLink(link) }

    val results = mutableListOf<FeedMovie>()
    candidateLinks.forEach { (href, link) ->
        val card = link.closest("article, .item, .movie-item, .film-item, .ml-item, .poster, .thumb, .grid-item, .movie, .film")
            ?: link.parent()?.parent() ?: link.parent() ?: link
        val image = link.selectFirst("img") ?: card.selectFirst("img")
        val imageTitle = image?.let { img ->
            sequenceOf("alt", "title", "data-title", "aria-label")
                .map { attr -> img.attr(attr).trim() }.firstOrNull { it.length in 2..120 }
        }.orEmpty()
        val heading = card.selectFirst("h1, h2, h3, h4, .title, .name, .film-title, .movie-title")?.text()?.trim().orEmpty()
        val dataTitle = sequenceOf("data-title", "title", "aria-label")
            .map { attr -> link.attr(attr).trim() }.firstOrNull { it.length in 2..120 }.orEmpty()
        val title = sequenceOf(heading, imageTitle, dataTitle, link.text().trim())
            .firstOrNull { it.length in 2..120 }?.replace(Regex("\\s+"), " ").orEmpty()
        if (title.length !in 2..120) return@forEach
        if (title.lowercase() in setOf("watch now", "watch movie", "tonton film", "play", "read more", "lihat semua", "more", "hd", "trailer")) return@forEach
        val cardText = card.text().replace(Regex("\\s+"), " ").trim().take(900)
        // Banyak tema LK21 menaruh label kualitas pada elemen terpisah, bukan teks judul kartu.
        val qualityText = buildString {
            append(title).append(' ').append(cardText)
            card.select("[class*=quality], [class*=resolution], [class*=ribbon], [class*=badge], [class*=label], [data-quality], [data-resolution], .mli-quality, .quality")
                .forEach { element ->
                    append(' ').append(element.text()).append(' ')
                    append(element.attr("class")).append(' ')
                    append(element.attr("data-quality")).append(' ')
                    append(element.attr("data-resolution")).append(' ')
                }
            append(' ').append(link.attr("class")).append(' ')
            append(link.attr("data-quality"))
        }
        val year = Regex("\\b(?:19|20)\\d{2}\\b").find("$title $cardText")?.value.orEmpty()
        val category = categoryWords.firstOrNull { cardText.contains(it, true) } ?: "Film"
        val poster = image?.let { img ->
            sequenceOf("data-src", "data-lazy-src", "data-original", "data-original-src", "src", "srcset")
                .map { attr -> img.attr(attr).substringBefore(",").substringBefore(" ").trim() }
                .mapNotNull { value ->
                    when {
                        value.startsWith("https://") -> value
                        value.startsWith("http://") -> value.replaceFirst("http://", "https://")
                        value.startsWith("//") -> "https:$value"
                        value.isNotBlank() -> runCatching { URL(URL(sourceUrl), value).toString() }.getOrNull()
                        else -> null
                    }
                }.firstOrNull { it.startsWith("https://") }.orEmpty()
        }.orEmpty()
        val description = card.selectFirst(".description, .desc, .synopsis, .overview, [itemprop=description]")?.text()?.trim()?.take(700)
            ?.ifBlank { null } ?: link.attr("title").ifBlank { pageDescription }
                .ifBlank { "Informasi film diambil otomatis dari katalog $sourceLabel." }
        results.add(FeedMovie(
            title = title, description = description, category = category, videoUrl = href,
            licenseId = "Metadata halaman sumber", termsUrl = sourceUrl,
            attribution = "Metadata katalog $sourceLabel", posterUrl = poster,
            qualityLabel = catalogQualityFromText(qualityText),
            releaseYear = year, genres = categoryWords.filter { cardText.contains(it, true) }.distinct().take(5)
        ))
    }
    return results.distinctBy { it.videoUrl }.take(120)
}

/** Ambil metadata film dan URL media langsung yang memang dipublikasikan halaman sumber. */
private suspend fun loadSourceMovieDetails(movie: FeedMovie): FeedMovie = withContext(Dispatchers.IO) {
    val pageUrl = movie.videoUrl
    val userAgent = "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
    val streams = LinkedHashSet<String>()
    val streamReferers = LinkedHashMap<String, String>()
    val visitedPages = LinkedHashSet<String>()
    val mediaExtensions = listOf(".m3u8", ".mpd", ".mp4", ".m4v", ".webm")

    fun isAdMedia(url: String): Boolean {
        val uri = Uri.parse(url)
        val pathAndQuery = (uri.path.orEmpty() + "?" + uri.encodedQuery.orEmpty()).lowercase()
        return isGenjurAdHost(uri.host) ||
            Regex("""(^|[/_.?&=-])(ads?|advert|advertisement|preroll|pre-roll|vast|vpaid|popunder|banner|sponsor|tracking)([/_.?&=-]|$)""")
                .containsMatchIn(pathAndQuery)
    }

    fun resolveUrl(rawValue: String, baseUrl: String): String {
        val raw = rawValue.trim().trim('"', '\'')
            .replace("\\/", "/")
            .replace("&amp;", "&").replace("\\u0026", "&")
        if (raw.isBlank() || raw.startsWith("blob:", true) || raw.startsWith("data:", true) ||
            raw.startsWith("javascript:", true)) return ""
        return runCatching {
            when {
                raw.startsWith("//") -> "https:$raw"
                raw.startsWith("https://", true) -> raw
                raw.startsWith("http://", true) -> raw.replaceFirst("http://", "https://")
                else -> URL(URL(baseUrl), raw).toString()
            }
        }.getOrDefault("")
    }

    fun addMedia(raw: String, baseUrl: String) {
        val absolute = resolveUrl(raw, baseUrl)
        if (absolute.isBlank()) return
        val uri = Uri.parse(absolute)
        val path = uri.path.orEmpty().lowercase()
        val looksLikeMedia = mediaExtensions.any { path.endsWith(it) } ||
            mediaExtensions.any { uri.encodedQuery.orEmpty().lowercase().contains(it) }
        if (uri.scheme == "https" && !uri.host.isNullOrBlank() && looksLikeMedia &&
            !isAdMedia(absolute)) {
            streams.add(absolute)
            streamReferers.putIfAbsent(absolute, baseUrl)
        }
    }

    fun scanDocument(doc: org.jsoup.nodes.Document, baseUrl: String): List<String> {
        doc.select("video[src], video source[src], source[src], audio[src]").forEach { element ->
            addMedia(element.attr("src"), baseUrl)
        }
        val mediaPattern = Regex(
            """(?i)(?:https?:)?//[^"'\s<>]+?\.(?:m3u8|mpd|mp4|m4v|webm)(?:\?[^"'\s<>]*)?|(?:/|\./|\.\./)[^"'\s<>]+?\.(?:m3u8|mpd|mp4|m4v|webm)(?:\?[^"'\s<>]*)?"""
        )
        val configPattern = Regex(
            """(?i)(?:file|src|source|hls|dash|url|videoUrl|hlsUrl|mpdUrl)\s*[:=]\s*["']([^"']+)["']"""
        )
        doc.select("script").forEach { script ->
            val scriptText = (script.data() + " " + script.html())
                .replace("\\/", "/").replace("\\u0026", "&")
            mediaPattern.findAll(scriptText).forEach { addMedia(it.value, baseUrl) }
            configPattern.findAll(scriptText).forEach { addMedia(it.groupValues[1], baseUrl) }
        }
        return doc.select("iframe[src]").mapNotNull { frame ->
            val url = resolveUrl(frame.attr("src"), baseUrl)
            url.takeIf { it.startsWith("https://") && !isAdMedia(it) }
        }.distinct()
    }

    val queue = ArrayDeque<Pair<String, Int>>()
    queue.add(pageUrl to 0)
    while (queue.isNotEmpty() && visitedPages.size < 13) {
        val (currentUrl, depth) = queue.removeFirst()
        if (!visitedPages.add(currentUrl)) continue
        runCatching {
            val connection = Jsoup.connect(currentUrl)
                .userAgent(userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .timeout(if (depth == 0) 18000 else 12000)
                .followRedirects(true)
            if (currentUrl != pageUrl) connection.referrer(pageUrl)
            val doc = connection.get()
            val embeds = scanDocument(doc, currentUrl)
            if (depth < 2) embeds.take(8).forEach { embed ->
                if (embed !in visitedPages && queue.none { it.first == embed }) queue.add(embed to depth + 1)
            }
        }
    }

    val document = Jsoup.connect(pageUrl).userAgent(userAgent).timeout(18000)
        .followRedirects(true).get()
    fun meta(vararg selectors: String): String =
        selectors.asSequence().mapNotNull { selector ->
            document.selectFirst(selector)?.attr("content")?.trim()?.takeIf { it.isNotBlank() }
        }.firstOrNull().orEmpty()

    val title = meta("meta[property=og:title]", "meta[name=twitter:title]")
        .ifBlank { document.selectFirst("h1")?.text()?.trim().orEmpty() }.ifBlank { movie.title }
    val description = meta("meta[property=og:description]", "meta[name=description]", "meta[name=twitter:description]")
        .ifBlank { document.selectFirst(".synopsis, .description, .desc, .movie-description, .entry-content, [itemprop=description]")?.text()?.trim()?.take(1200).orEmpty() }
        .ifBlank { movie.description }
    val rawPoster = meta("meta[property=og:image]", "meta[name=twitter:image]")
    val poster = if (rawPoster.isBlank()) movie.posterUrl else resolveUrl(rawPoster, pageUrl).takeIf { it.startsWith("https://") } ?: movie.posterUrl
    val pageText = document.text().take(12000)
    val year = Regex("\\b(?:19|20)\\d{2}\\b").find(pageText)?.value ?: movie.releaseYear
    val genreWords = listOf("Action", "Adventure", "Animation", "Anime", "Comedy", "Crime", "Documentary", "Drama", "Family", "Fantasy", "Horror", "Mystery", "Romance", "Sci-Fi", "Thriller", "Aksi", "Petualangan", "Animasi", "Komedi", "Kriminal", "Dokumenter", "Horor")
    val genres = (document.select(".genre a, .genres a, [itemprop=genre], a[rel=tag]").map { it.text().trim() } +
        genreWords.filter { pageText.contains(it, true) }).filter { it.isNotBlank() && it.length <= 32 }
        .distinct().take(8).ifEmpty { movie.genres }
    val duration = document.selectFirst("[itemprop=duration], .runtime, .duration, .movie-runtime")
        ?.text()?.trim()?.takeIf { it.isNotBlank() } ?: movie.duration
    val trailerRaw = document.select("iframe[src], a[href], video[src], video source[src]")
        .mapNotNull { element ->
            val raw = if (element.hasAttr("src")) element.attr("src") else element.attr("href")
            val label = (element.text() + " " + element.attr("title") + " " +
                element.attr("aria-label") + " " + element.attr("class")).lowercase()
            val host = runCatching { Uri.parse(raw).host.orEmpty().lowercase() }.getOrDefault("")
            val isVideoHost = host.contains("youtube.com") || host == "youtu.be" ||
                host.contains("youtube-nocookie.com") || host.contains("vimeo.com")
            if (raw.isNotBlank() && (isVideoHost || label.contains("trailer") || label.contains("teaser"))) raw else null
        }.firstOrNull().orEmpty()
    val trailerUrl = runCatching {
        val absolute = if (trailerRaw.isBlank()) "" else resolveUrl(trailerRaw, pageUrl)
        val uri = Uri.parse(absolute)
        when {
            uri.host.orEmpty().contains("youtube.com") && uri.path.orEmpty().startsWith("/embed/") -> absolute
            uri.host.orEmpty().contains("youtube.com") -> uri.getQueryParameter("v")?.let { "https://www.youtube-nocookie.com/embed/$it" } ?: absolute
            uri.host.orEmpty() == "youtu.be" -> uri.lastPathSegment?.let { "https://www.youtube-nocookie.com/embed/$it" } ?: absolute
            uri.host.orEmpty().contains("youtube-nocookie.com") || uri.host.orEmpty().contains("vimeo.com") -> absolute
            uri.scheme == "https" -> absolute
            else -> ""
        }
    }.getOrDefault("")
    val options = streams.map { url -> StreamOption(detectedQualityLabel("", url), url, referer = streamReferers[url] ?: pageUrl) }
    movie.copy(
        title = title.take(140), description = description.take(1200), posterUrl = poster,
        releaseYear = year, genres = genres, duration = duration, termsUrl = pageUrl,
        licenseId = if (options.isEmpty()) "Source page fallback" else "Direct source media",
        qualityLabel = catalogQualityFromText(pageText).ifBlank { movie.qualityLabel },
        streamOptions = options, trailerUrl = trailerUrl.ifBlank { movie.trailerUrl }
    )
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // Tampilkan UI dan animasi pembuka terlebih dahulu; pekerjaan pendukung dilakukan sesudahnya.
        setContent { MaterialTheme { GenjurStreamApp(this) } }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PLAYBACK_NOTIFICATION_CHANNEL,
                "Pemutaran GENJUR STREAM",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Kontrol film yang sedang diputar"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        // Bersihkan data repositori/ekstensi lama agar katalog repositori tidak muncul lagi.
        getSharedPreferences("genjur_stream_sources", 0).edit()
            .remove("repositories")
            .remove("installed_extensions")
            .apply()
        scheduleBackgroundCatalogChecks()
    }

    fun showWebPlaybackNotification(title: String, pageUrl: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val openApp = PendingIntent.getActivity(
            this, 8403,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, PLAYBACK_NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title.ifBlank { "Video terdeteksi" })
            .setContentText("Video mulai diputar di halaman sumber GENJUR STREAM")
            .setSubText("Pemutar situs aktif")
            .setContentIntent(openApp)
            .setOngoing(false)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        getSystemService(NotificationManager::class.java).notify(PLAYBACK_NOTIFICATION_ID, notification)
    }

    private fun scheduleBackgroundCatalogChecks() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<CatalogRefreshWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "genjur_stream_catalog_refresh",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

@Composable
fun GenjurStreamHome(activity: MainActivity, onOpenSources: () -> Unit = {}) {
    var search by remember { mutableStateOf("") }
    var searchSubmitted by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf("Semua") }
    var enabledVideoFilters by remember { mutableStateOf(DEFAULT_VIDEO_FILTERS) }
    var videoFiltersLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val prefs = activity.getSharedPreferences("genjur_stream_video_filters", 0)
        val saved = prefs.getString("enabled", null)
        if (saved != null) runCatching { JSONArray(saved) }.getOrNull()?.let { array ->
            enabledVideoFilters = (0 until array.length()).map { array.optString(it) }
                .filter { it in VIDEO_FILTER_CATEGORIES }.toSet()
        }
        videoFiltersLoaded = true
    }
    LaunchedEffect(enabledVideoFilters, videoFiltersLoaded) {
        if (videoFiltersLoaded) {
            val array = JSONArray()
            enabledVideoFilters.forEach { array.put(it) }
            activity.getSharedPreferences("genjur_stream_video_filters", 0).edit()
                .putString("enabled", array.toString()).apply()
        }
    }
    var selectedTab by remember { mutableStateOf("Beranda") }
    var downloadIconBlink by remember { mutableStateOf(false) }
    var downloadBlinkTargetId by remember { mutableStateOf<Long?>(null) }
    var savedSourceMenu by remember { mutableStateOf(emptyList<SavedSourceLink>()) }
    var selectedSearchSourceUrl by remember { mutableStateOf<String?>(null) }
    var searchSourceDropdownExpanded by remember { mutableStateOf(false) }
    var sourceMenuExpanded by remember { mutableStateOf(false) }
    var selectedSourceName by remember { mutableStateOf("Sumber Link") }
    LaunchedEffect(selectedTab) {
        val prefs = activity.getSharedPreferences("genjur_stream_saved_links", 0)
        savedSourceMenu = runCatching {
            val array = JSONArray(prefs.getString("items", "[]"))
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val sourceName = item.optString("name").trim()
                val sourceUrl = item.optString("url").trim()
                if (sourceName.isNotBlank() && sourceUrl.startsWith("https://")) SavedSourceLink(sourceName, sourceUrl) else null
            }
        }.getOrDefault(emptyList())
    }
    var browserInitialUrl by remember { mutableStateOf("") }
    var catalogSourceUrl by remember { mutableStateOf("") }
    var catalogSourceName by remember { mutableStateOf("") }
    var sourceCatalogMovies by remember { mutableStateOf(emptyList<FeedMovie>()) }
    var sourceCatalogLoading by remember { mutableStateOf(false) }
    var sourceCatalogMessage by remember { mutableStateOf<String?>(null) }
    var updateVersion by remember { mutableStateOf<String?>(null) }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableStateOf(0f) }
    var updateDownloadedBytes by remember { mutableStateOf(0L) }
    var updateTotalBytes by remember { mutableStateOf(-1L) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var feedMovies by remember { mutableStateOf(emptyList<FeedMovie>()) }
    var feedLoading by remember { mutableStateOf(false) }
    var feedMessage by remember { mutableStateOf("Tambahkan sumber feed JSON, lalu muat katalog.") }
    var selectedMovie by remember { mutableStateOf<FeedMovie?>(null) }
    var sourceDetailLoading by remember { mutableStateOf(false) }
    LaunchedEffect(selectedMovie?.videoUrl) {
        val current = selectedMovie ?: return@LaunchedEffect
        if (current.licenseId != "Metadata halaman sumber" || current.videoUrl.isBlank()) return@LaunchedEffect
        sourceDetailLoading = true
        val result = runCatching { loadSourceMovieDetails(current) }
        if (selectedMovie?.videoUrl == current.videoUrl) {
            result.onSuccess { selectedMovie = it }
            sourceDetailLoading = false
        }
    }
    var playingMovie by remember { mutableStateOf<FeedMovie?>(null) }
    var pendingPlaybackChoice by remember { mutableStateOf<FeedMovie?>(null) }
    BackHandler(enabled = selectedMovie != null || playingMovie != null || selectedTab != "Beranda") {
        when {
            playingMovie != null -> playingMovie = null
            selectedMovie != null -> selectedMovie = null
            selectedTab == "Browser Sumber" && catalogSourceUrl.isNotBlank() -> selectedTab = "Katalog Sumber"
            selectedTab == "Katalog Sumber" -> selectedTab = "Sumber Link"
            selectedTab != "Beranda" -> selectedTab = "Beranda"
        }
    }
    var favoriteMovies by remember { mutableStateOf(readSavedMovies(activity, "favorites")) }
    var watchHistory by remember { mutableStateOf(readSavedMovies(activity, "history")) }
    var downloads by remember { mutableStateOf(readDownloads(activity)) }
    var downloadMessage by remember { mutableStateOf<String?>(null) }
    var confirmClearDownloads by remember { mutableStateOf(false) }
    var downloadStates by remember { mutableStateOf<Map<Long, Pair<Int, String>>>(emptyMap()) }
    var downloadMetrics by remember { mutableStateOf<Map<Long, DownloadMetrics>>(emptyMap()) }
    val libraryPrefs = remember { activity.getSharedPreferences("genjur_stream_library", 0) }
    val downloadManager = remember { activity.getSystemService(DownloadManager::class.java) }
    fun persistDownloads(items: List<DownloadEntry>) {
        libraryPrefs.edit().putString("downloads", JSONArray().apply {
            items.forEach { entry ->
                put(JSONObject().put("id", entry.id).put("movie", encodeMovie(entry.movie)))
            }
        }.toString()).apply()
    }
    fun startDownload(movie: FeedMovie) {
        try {
            if (!movie.downloadAllowed || movie.downloadUrl.isBlank()) {
                downloadMessage = "Sumber ini belum memberikan tautan unduhan langsung."
                return
            }
            if (downloads.any { it.movie.downloadUrl == movie.downloadUrl }) {
                downloadMessage = "Tautan ini sudah ada di daftar Unduhan."
                return
            }
            val uri = Uri.parse(movie.downloadUrl)
            if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
                downloadMessage = "Alamat unduhan tidak valid."
                return
            }
            val path = uri.path.orEmpty().lowercase()
            if (path.endsWith(".m3u8") || path.endsWith(".mpd")) {
                downloadMessage = "Format streaming adaptif belum didukung oleh pengunduh ini."
                return
            }
            val extension = when {
                path.endsWith(".webm") -> "webm"
                path.endsWith(".mov") -> "mov"
                path.endsWith(".mkv") -> "mkv"
                else -> "mp4"
            }
            val fileName = downloadFileName(movie.title, movie.releaseYear, extension)
            val request = DownloadManager.Request(uri)
                .setTitle(fileName)
                .setDescription("Unduhan GENJUR STREAM")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_MOVIES, fileName)
            val pageUri = Uri.parse(movie.termsUrl)
            request.addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
            request.addRequestHeader("Accept", "video/*,application/octet-stream,*/*;q=0.8")
            request.addRequestHeader("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            if (movie.termsUrl.startsWith("http")) {
                request.addRequestHeader("Referer", movie.termsUrl)
                if (!pageUri.host.isNullOrBlank()) request.addRequestHeader("Origin", "${pageUri.scheme}://${pageUri.host}")
            }
            val cookieManager = android.webkit.CookieManager.getInstance()
            val cookies = sequenceOf(movie.downloadUrl, movie.termsUrl)
                .mapNotNull { url -> runCatching { cookieManager.getCookie(url) }.getOrNull() }
                .firstOrNull { it.isNotBlank() }
            cookies?.let { request.addRequestHeader("Cookie", it) }
            val queuedId = downloadManager.enqueue(request)
            downloads = listOf(DownloadEntry(queuedId, movie)) + downloads
            persistDownloads(downloads)
            downloadMessage = "Download dimulai. Pantau progresnya di menu Unduhan."
            android.util.Log.i("GENJUR_DOWNLOAD", "DownloadManager menerima tugas id=$queuedId file=$fileName")
        } catch (error: Exception) {
            downloadMessage = "Gagal memulai download: ${error.message ?: error.javaClass.simpleName}"
            android.util.Log.e("GENJUR_DOWNLOAD", "Gagal menyiapkan unduhan dari dialog resolusi", error)
        }
    }
    val adaptiveSessions = remember { mutableMapOf<Long, Long>() }
    fun setAdaptiveState(id: Long, percent: Int, label: String) {
        activity.runOnUiThread {
            downloadStates = downloadStates + (id to (percent.coerceIn(0, 100) to label))
        }
    }
    fun startAdaptiveDownload(title: String, pageUrl: String, mediaUrl: String, retryId: Long? = null) {
        downloadMessage = null
        val uri = Uri.parse(mediaUrl)
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
            downloadMessage = "Alamat streaming tidak valid."
            return
        }
        if (downloads.any { it.id != retryId && it.movie.downloadUrl == mediaUrl }) {
            downloadMessage = "Streaming ini sudah ada di daftar Unduhan."
            return
        }
        if (retryId != null) {
            // Entri yang bisa ditekan "Mulai ulang" sudah berstatus terhenti/gagal.
            // Jangan memanggil FFmpegKit.cancel pada sesi lama: sesi native bisa
            // sudah tidak ada (mis. proses aplikasi sebelumnya dihentikan Android).
            adaptiveSessions.remove(retryId)
            val oldEntry = downloads.firstOrNull { it.id == retryId }
            oldEntry?.let { entry ->
                runCatching {
                    val oldPath = Uri.parse(entry.movie.videoUrl).path
                    if (!oldPath.isNullOrBlank()) File(oldPath).delete()
                }.onFailure { error ->
                    android.util.Log.w("GENJUR_DOWNLOAD", "Tidak dapat membersihkan file unduhan lama", error)
                }
            }
            downloads = downloads.filterNot { it.id == retryId }
            downloadStates = downloadStates - retryId
            persistDownloads(downloads)
        }
        val id = -System.currentTimeMillis().coerceAtLeast(1L)
        val outputDir = activity.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        if (outputDir == null || (!outputDir.exists() && !outputDir.mkdirs())) {
            downloadMessage = "Gagal menyiapkan folder penyimpanan MP4."
            return
        }
        val safeTitle = title.ifBlank { "Video dari situs" }
        val outputFile = File(outputDir, downloadFileName(safeTitle, "", "mp4"))
        val movie = FeedMovie(
            title = safeTitle,
            description = "Streaming HLS/DASH yang dikonversi menjadi MP4.",
            category = "Sumber Web",
            videoUrl = Uri.fromFile(outputFile).toString(),
            licenseId = "User-provided website",
            termsUrl = pageUrl,
            attribution = "Situs sumber: ${Uri.parse(pageUrl).host.orEmpty()}",
            downloadUrl = mediaUrl,
            downloadAllowed = true
        )
        downloads = listOf(DownloadEntry(id, movie)) + downloads
        persistDownloads(downloads)
        downloadStates = downloadStates + (id to (0 to "Menyiapkan"))
        downloadMessage = "Sedang membaca durasi streaming sebelum membuat MP4."

        val pageUri = Uri.parse(pageUrl)
        val cookieManager = android.webkit.CookieManager.getInstance()
        val cookies = sequenceOf(mediaUrl, pageUrl)
            .mapNotNull { url -> runCatching { cookieManager.getCookie(url) }.getOrNull() }
            .firstOrNull { it.isNotBlank() }
        val headers = buildString {
            append("User-Agent: Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36\r\n")
            if (pageUrl.startsWith("http")) {
                append("Referer: $pageUrl\r\n")
                if (!pageUri.host.isNullOrBlank()) append("Origin: ${pageUri.scheme}://${pageUri.host}\r\n")
            }
            if (!cookies.isNullOrBlank()) append("Cookie: $cookies\r\n")
        }
        // Mulai FFmpeg langsung. FFprobe sebelumnya berjalan lebih dulu dan dapat
        // membuat proses native berhenti pada sebagian perangkat; durasi tidak wajib
        // untuk mengukur byte dan Mbps karena ukuran file dipantau dari output MP4.
        val args = mutableListOf(
            "-y", "-hide_banner", "-loglevel", "verbose",
            "-headers", headers,
            // Izinkan protokol HTTPS dan protokol turunan yang dipakai playlist HLS
            // untuk mengambil playlist, key, dan segmen video.
            "-protocol_whitelist", "file,http,https,tcp,tls,crypto,data",
            "-allowed_extensions", "ALL",
            // FFmpeg baru memeriksa kecocokan ekstensi URL dengan format segmen HLS.
            // CDN ini mengirim MPEG-TS memakai akhiran .pict; nonaktifkan pemeriksaan
            // kecocokan ketat untuk stream yang dipilih pengguna agar segmen tetap terbaca.
            "-extension_picky", "0",
            "-allowed_segment_extensions", "3gp,aac,avi,ac3,eac3,flac,mkv,m3u8,m4a,m4s,m4v,mpg,mov,mp2,mp3,mp4,mpeg,mpegts,ogg,ogv,oga,ts,vob,vtt,wav,webvtt,cmfv,cmfa,ec3,fmp4,pict",
            "-i", mediaUrl,
            "-map", "0:v:0",
            "-map", "0:a:0?",
            "-c", "copy",
            "-movflags", "+faststart",
            "-f", "mp4",
            outputFile.absolutePath
        )
        downloadStates = downloadStates + (id to (0 to "Mengunduh"))
        downloadMessage = "Unduhan dimulai. Ukuran dan Mbps diperbarui di menu Unduhan."
        try {
            FFmpegKit.executeWithArgumentsAsync(
                args.toTypedArray(),
                { session ->
                    activity.runOnUiThread {
                        adaptiveSessions.remove(id)
                        if (ReturnCode.isSuccess(session.returnCode) && outputFile.exists() && outputFile.length() > 0L) {
                            val completedSize = outputFile.length()
                            Thread {
                                runCatching { publishMp4ToPublicMovies(activity, outputFile, safeTitle) }
                                    .onSuccess { publicUri -> activity.runOnUiThread {
                                        downloads = downloads.map { entry -> if (entry.id == id) entry.copy(movie = entry.movie.copy(videoUrl = publicUri.toString())) else entry }
                                        persistDownloads(downloads)
                                        downloadStates = downloadStates + (id to (100 to "Selesai"))
                                        downloadMessage = "MP4 tersimpan di Movies/GENJUR STREAM (" + formatVideoSize(completedSize) + ")."
                                    } }
                                    .onFailure { error -> activity.runOnUiThread {
                                        downloadStates = downloadStates + (id to (0 to "Gagal"))
                                        downloadMessage = "MP4 selesai tetapi gagal disimpan ke folder publik: " + (error.message ?: error.javaClass.simpleName)
                                        android.util.Log.e("GENJUR_DOWNLOAD", "Gagal menyalin MP4 ke MediaStore", error)
                                    } }
                            }.start()
                        } else {
                            outputFile.delete()
                            val fullLog = buildString {
                                append(session.failStackTrace.orEmpty())
                                append("\n")
                                append(session.output.orEmpty())
                            }.trim()
                            val diagnosticLines = fullLog.lineSequence()
                                .map { it.trim() }
                                .filter { it.isNotBlank() && !it.startsWith("frame=") }
                                .toList()
                            val detail = diagnosticLines.takeLast(8).joinToString(" | ").take(1800)
                            android.util.Log.e(
                                "GENJUR_DOWNLOAD",
                                "FFmpeg HLS gagal. URL=${mediaUrl.take(500)}\nLog lengkap:\n${fullLog.takeLast(12000)}"
                            )
                            downloadStates = downloadStates + (id to (0 to "Gagal"))
                            downloadMessage = "Gagal membuka streaming. ${detail.ifBlank { "Log FFmpeg kosong; periksa dukungan protokol HTTPS pada APK." }}"
                        }
                    }
                },
                null,
                { _ -> setAdaptiveState(id, 0, "Mengunduh") }
            ).also { session -> adaptiveSessions[id] = session.sessionId }
        } catch (error: Throwable) {
            // Termasuk LinkageError/UnsatisfiedLinkError jika pustaka native FFmpeg
            // tidak dapat dimuat pada perangkat. Jangan biarkan proses aplikasi tertutup.
            adaptiveSessions.remove(id)
            downloadStates = downloadStates + (id to (0 to "Gagal"))
            val detail = error.message ?: error.javaClass.simpleName
            downloadMessage = "Mesin unduhan tidak dapat dijalankan: $detail"
            android.util.Log.e("GENJUR_DOWNLOAD", "FFmpeg gagal dijalankan atau pustaka native tidak tersedia", error)
        }
    }
    fun startWebVideoDownload(title: String, pageUrl: String, mediaUrl: String) {
        // JavascriptInterface dipanggil dari thread WebView khusus, sedangkan state Compose
        // dan DownloadManager harus disentuh dari main/UI thread.
        activity.runOnUiThread {
            try {
                val uri = Uri.parse(mediaUrl)
                val path = uri.path.orEmpty().lowercase()
                val adaptiveStream = path.endsWith(".m3u8") || path.endsWith(".mpd")
                val validHttpUrl = uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()
                if (!validHttpUrl) {
                    downloadMessage = "Alamat video tidak valid."
                    return@runOnUiThread
                }
                if (adaptiveStream) {
                    startAdaptiveDownload(title, pageUrl, mediaUrl)
                    return@runOnUiThread
                }
                val safeTitle = title.ifBlank { "Video dari situs" }
                startDownload(FeedMovie(
                    title = safeTitle,
                    description = "Video langsung terdeteksi dari halaman sumber.",
                    category = "Sumber Web",
                    videoUrl = mediaUrl,
                    licenseId = "User-provided website",
                    termsUrl = pageUrl,
                    attribution = "Situs sumber: ${Uri.parse(pageUrl).host.orEmpty()}",
                    downloadUrl = mediaUrl,
                    downloadAllowed = true
                ))
            } catch (error: Exception) {
                downloadMessage = "Download gagal disiapkan: ${error.message ?: error.javaClass.simpleName}"
                android.util.Log.e("GENJUR_DOWNLOAD", "Gagal memulai unduhan dari WebView", error)
            }
        }
    }
    fun persistMovies(key: String, movies: List<FeedMovie>) {
        libraryPrefs.edit().putString(key, JSONArray().apply {
            movies.forEach { put(encodeMovie(it)) }
        }.toString()).apply()
    }
    fun toggleFavorite(movie: FeedMovie) {
        favoriteMovies = if (favoriteMovies.any { movieKey(it) == movieKey(movie) }) {
            favoriteMovies.filterNot { movieKey(it) == movieKey(movie) }
        } else listOf(movie) + favoriteMovies
        persistMovies("favorites", favoriteMovies)
    }
    fun recordWatch(movie: FeedMovie) {
        watchHistory = (listOf(movie) + watchHistory.filterNot { movieKey(it) == movieKey(movie) }).take(50)
        persistMovies("history", watchHistory)
    }
    val scope = rememberCoroutineScope()

    val downloadBlinkStatus = downloadBlinkTargetId?.let { downloadStates[it]?.second }
    LaunchedEffect(downloadBlinkTargetId, downloadBlinkStatus) {
        val targetId = downloadBlinkTargetId
        if (targetId == null) {
            downloadIconBlink = false
            return@LaunchedEffect
        }
        if (downloadBlinkStatus == "Selesai" || downloadBlinkStatus?.startsWith("Gagal") == true ||
            downloadBlinkStatus?.startsWith("Terhenti") == true || downloadBlinkStatus == "Tidak ditemukan") {
            downloadIconBlink = false
            downloadBlinkTargetId = null
            return@LaunchedEffect
        }
        while (downloadBlinkTargetId == targetId) {
            downloadIconBlink = !downloadIconBlink
            delay(350)
        }
    }

    LaunchedEffect(downloads, selectedTab) {
        val previousSamples = mutableMapOf<Long, Pair<Long, Long>>()
        while (true) {
            val states = mutableMapOf<Long, Pair<Int, String>>()
            val metrics = mutableMapOf<Long, DownloadMetrics>()
            val sampleTime = System.currentTimeMillis()
            downloads.forEach { entry ->
                if (entry.id < 0L) {
                    val path = Uri.parse(entry.movie.videoUrl).path.orEmpty()
                    val file = File(path)
                    val currentBytes = if (file.exists()) file.length() else 0L
                    val previous = previousSamples.put(entry.id, currentBytes to sampleTime)
                    val elapsed = previous?.let { ((sampleTime - it.second).coerceAtLeast(1L)) / 1000.0 } ?: 1.0
                    val speed = previous?.let { ((currentBytes - it.first).coerceAtLeast(0L) / elapsed).toDouble() } ?: 0.0
                    states[entry.id] = when {
                        adaptiveSessions.containsKey(entry.id) -> downloadStates[entry.id] ?: (0 to "Mengunduh")
                        downloadStates[entry.id]?.second == "Gagal" -> downloadStates[entry.id]!!
                        downloadStates[entry.id]?.second == "Selesai" -> downloadStates[entry.id]!!
                        file.exists() && currentBytes > 0L -> 100 to "Selesai"
                        else -> 0 to "Terhenti — mulai ulang unduhan"
                    }
                    metrics[entry.id] = DownloadMetrics(currentBytes, 0L, speed)
                } else {
                    val query = DownloadManager.Query().setFilterById(entry.id)
                    val cursor = downloadManager.query(query)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                            val soFar = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val reasonColumn = it.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val failureReason = if (reasonColumn >= 0) it.getInt(reasonColumn) else 0
                            val percent = if (total > 0) ((soFar * 100) / total).toInt() else 0
                            val failureDetail = when (failureReason) {
                                DownloadManager.ERROR_CANNOT_RESUME -> "Tidak bisa melanjutkan"
                                DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Penyimpanan tidak ditemukan"
                                DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File sudah ada"
                                DownloadManager.ERROR_FILE_ERROR -> "Kesalahan file/penyimpanan"
                                DownloadManager.ERROR_HTTP_DATA_ERROR -> "Data HTTP terputus"
                                DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Ruang penyimpanan tidak cukup"
                                DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Terlalu banyak pengalihan"
                                DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Kode HTTP tidak didukung"
                                DownloadManager.ERROR_UNKNOWN -> "Kesalahan jaringan tidak diketahui"
                                in 400..599 -> "Server menolak (HTTP $failureReason)"
                                else -> if (failureReason != 0) "Kode gagal $failureReason" else "Penyebab tidak diberikan sistem"
                            }
                            val label = when (status) {
                                DownloadManager.STATUS_SUCCESSFUL -> "Selesai"
                                DownloadManager.STATUS_FAILED -> "Gagal • $failureDetail"
                                DownloadManager.STATUS_PAUSED -> "Dijeda"
                                DownloadManager.STATUS_PENDING -> "Menunggu"
                                else -> "Mengunduh"
                            }
                            if (status == DownloadManager.STATUS_FAILED) {
                                android.util.Log.e("GENJUR_DOWNLOAD", "DownloadManager gagal id=${entry.id}, reason=$failureReason ($failureDetail), url=${entry.movie.downloadUrl.take(500)}")
                            }
                            val previous = previousSamples.put(entry.id, soFar to sampleTime)
                            val elapsed = previous?.let { ((sampleTime - it.second).coerceAtLeast(1L)) / 1000.0 } ?: 1.0
                            val speed = previous?.let { ((soFar - it.first).coerceAtLeast(0L) / elapsed).toDouble() } ?: 0.0
                            states[entry.id] = percent to label
                            metrics[entry.id] = DownloadMetrics(soFar, total, speed)
                        } else states[entry.id] = 0 to "Tidak ditemukan"
                    }
                }
            }
            downloadStates = states
            downloadMetrics = metrics
            delay(1000)
        }
    }

    fun refreshCatalog() {
        scope.launch {
            feedLoading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val urls = savedFeedUrls(activity)
                    require(urls.isNotEmpty()) { "Belum ada sumber. Tambahkan sumber terlebih dahulu." }
                    val movies = mutableListOf<FeedMovie>()
                    val sourceErrors = mutableListOf<String>()
                    var loadedSources = 0

                    urls.forEach { sourceUrl ->
                        val sourceResult = runCatching {
                            val connection = URL(sourceUrl).openConnection()
                            connection.connectTimeout = 12000
                            connection.readTimeout = 20000
                            val responseBody = connection.getInputStream().bufferedReader().use { it.readText() }
                            val root = runCatching { JSONObject(responseBody) }.getOrNull()
                            if (root == null) {
                                val htmlItems = loadGenericHtmlCatalog(sourceUrl, responseBody)
                                require(htmlItems.isNotEmpty()) {
                                    "Halaman terbaca, tetapi pola daftar film tidak dikenali otomatis."
                                }
                                movies.addAll(htmlItems.map { it.copy(sourceName = catalogSourceDisplayName(sourceUrl)) })
                                return@runCatching
                            }
                            if (root.optString("catalogType") == "wikimedia_public_domain_videos") {
                                movies.addAll(loadWikimediaPublicDomainMovies())
                                return@runCatching
                            }
                            val rights = root.optJSONObject("rights")
                                ?: error("metadata hak distribusi tidak tersedia")
                            val redistributionAllowed = rights.optBoolean("redistributionAllowed", false)
                            val licenseId = rights.optString("licenseId").trim()
                            val termsUrl = rights.optString("termsUrl").trim()
                            val attribution = rights.optString("attribution").trim()
                            val termsUri = Uri.parse(termsUrl)
                            require(
                                redistributionAllowed &&
                                    licenseId.isNotBlank() &&
                                    termsUri.scheme == "https" &&
                                    !termsUri.host.isNullOrBlank() &&
                                    attribution.isNotBlank()
                            ) {
                                "izin distribusi, lisensi, tautan ketentuan, atau atribusi belum lengkap"
                            }
                            val items = root.optJSONArray("items") ?: error("feed tidak memiliki array items")
                            for (i in 0 until items.length()) {
                                val item = items.optJSONObject(i) ?: continue
                                if (!item.optBoolean("freeToWatch", false)) continue
                                val title = item.optString("title").trim()
                                val videoUrl = item.optString("videoUrl").trim()
                                if (title.isNotEmpty()) {
                                    val videoUri = Uri.parse(videoUrl)
                                    require(
                                        videoUrl.isBlank() ||
                                            (videoUri.scheme == "https" && !videoUri.host.isNullOrBlank())
                                    ) {
                                        "URL video harus menggunakan HTTPS yang valid"
                                    }
                                    val posterUrl = item.optString("posterUrl").trim()
                                    val posterUri = Uri.parse(posterUrl)
                                    require(posterUrl.isBlank() || (posterUri.scheme == "https" && !posterUri.host.isNullOrBlank())) {
                                        "URL poster harus menggunakan HTTPS yang valid"
                                    }
                                    val genresArray = item.optJSONArray("genres")
                                    val genres = if (genresArray != null) {
                                        (0 until genresArray.length()).map { genresArray.optString(it).trim() }
                                            .filter { it.isNotBlank() }.distinct()
                                    } else {
                                        item.optString("genre").split(",").map { it.trim() }
                                            .filter { it.isNotBlank() }.distinct()
                                    }
                                    val subtitleLink = item.optString("subtitleUrl").trim()
                                    if (subtitleLink.isNotBlank()) {
                                        val subtitleUri = Uri.parse(subtitleLink)
                                        require(subtitleUri.scheme == "https" && !subtitleUri.host.isNullOrBlank()) {
                                            "URL subtitle harus HTTPS yang valid"
                                        }
                                    }
                                    val streamsArray = item.optJSONArray("streams")
                                    val streamOptions = if (streamsArray != null) {
                                        (0 until streamsArray.length()).mapNotNull { index ->
                                            val stream = streamsArray.optJSONObject(index) ?: return@mapNotNull null
                                            val quality = stream.optString("quality", "Otomatis").trim().ifBlank { "Otomatis" }
                                            val streamUrl = stream.optString("videoUrl").trim()
                                            if (streamUrl.isBlank()) return@mapNotNull null
                                            val streamUri = Uri.parse(streamUrl)
                                            require(streamUri.scheme == "https" && !streamUri.host.isNullOrBlank()) {
                                                "URL video untuk kualitas $quality harus HTTPS yang valid"
                                            }
                                            StreamOption(quality, streamUrl)
                                        }.distinctBy { it.quality.lowercase() }
                                    } else emptyList()
                                    val downloadLink = item.optString("downloadUrl").trim()
                                    val downloadAllowed = item.optBoolean("downloadAllowed", false)
                                    require(!downloadAllowed || downloadLink.isNotBlank()) {
                                        "Unduhan diizinkan tetapi downloadUrl belum tersedia"
                                    }
                                    if (downloadLink.isNotBlank()) {
                                        val downloadUri = Uri.parse(downloadLink)
                                        require(downloadUri.scheme == "https" && !downloadUri.host.isNullOrBlank() &&
                                            downloadUri.path.orEmpty().lowercase().endsWith(".mp4")) {
                                            "URL unduhan harus berupa file MP4 HTTPS langsung"
                                        }
                                    }
                                    movies.add(FeedMovie(
                                        title = title,
                                        description = item.optString("description", "Sinopsis belum tersedia."),
                                        category = item.optString("category", "Film"),
                                        videoUrl = videoUrl,
                                        licenseId = licenseId,
                                        termsUrl = termsUrl,
                                        attribution = attribution,
                                        posterUrl = posterUrl,
                                        releaseYear = item.optString("releaseYear").trim(),
                                        genres = genres,
                                        duration = item.optString("duration").trim(),
                                        downloadUrl = downloadLink,
                                        downloadAllowed = downloadAllowed,
                                        subtitleUrl = subtitleLink,
                                        subtitleLanguage = item.optString("subtitleLanguage", "id").trim(),
                                        streamOptions = streamOptions,
                                        sourceName = catalogSourceDisplayName(sourceUrl)
                                    ))
                                }
                            }
                        }
                        sourceResult.onSuccess {
                            loadedSources++
                        }.onFailure { failure ->
                            val host = Uri.parse(sourceUrl).host ?: "Sumber"
                            sourceErrors.add("$host: ${failure.message ?: "gagal dibaca"}")
                        }
                    }
                    Triple(movies, loadedSources, sourceErrors)
                }
            }
            feedLoading = false
            result.onSuccess { (movies, loadedSources, sourceErrors) ->
                feedMovies = movies.groupBy { "${it.title.trim().lowercase()}|${it.releaseYear.trim()}" }
                    .values.map { sameMovie ->
                        val first = sameMovie.first()
                        first.copy(sourceName = sameMovie.map { it.sourceName }.filter { it.isNotBlank() }.distinct().joinToString(" • "))
                    }
                feedMessage = when {
                    loadedSources == 0 -> "Tidak ada sumber yang berhasil dibaca. " +
                        sourceErrors.take(2).joinToString(" • ")
                    sourceErrors.isNotEmpty() -> "${movies.size} tontonan dimuat dari $loadedSources sumber. " +
                        "${sourceErrors.size} sumber dilewati."
                    movies.isEmpty() -> "Feed terbaca, tetapi belum ada item tontonan."
                    else -> "${movies.size} tontonan berhasil dimuat dari $loadedSources sumber."
                }
            }.onFailure { feedMessage = it.message ?: "Gagal memuat katalog." }
        }
    }
    LaunchedEffect(Unit) {
        refreshCatalog()
        while (true) {
            delay(6 * 60 * 60 * 1000L)
            refreshCatalog()
        }
    }

    val categories = listOf("Semua") + (listOf("Film", "Serial", "Animasi", "Dokumenter") + feedMovies.map { it.category }).map { it.trim() }.filter { it.isNotBlank() && !it.equals("Drama", true) }.distinct()

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL("$UPDATE_INFO_URL?nocache=${System.currentTimeMillis()}").openConnection()
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-cache, no-store")
                connection.setRequestProperty("Pragma", "no-cache")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                val json = connection.getInputStream().bufferedReader().use { it.readText() }
                JSONObject(json)
            }.getOrNull()
        }
        checking = false
        if (result != null && result.optInt("versionCode", 0) > BuildConfig.VERSION_CODE) {
            updateVersion = result.optString("versionName")
            updateUrl = result.optString("apkUrl")
        }
    }

    if (updateVersion != null && updateUrl != null) {
        AlertDialog(
            onDismissRequest = { if (!downloading) updateVersion = null },
            title = { Text("Versi terbaru tersedia") },
            text = {
                Column {
                    Text("GENJUR STREAM versi $updateVersion sudah tersedia. Perbarui aplikasi sekarang?")
                    if (downloading) {
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
                        Text(
                            if (updateTotalBytes > 0L) "${(updateProgress * 100).toInt()}%  •  ${formatVideoSize(updateDownloadedBytes)} / ${formatVideoSize(updateTotalBytes)}"
                            else "${formatVideoSize(updateDownloadedBytes)} diunduh • ukuran total belum diketahui",
                            color = Muted
                        )
                    }
                    updateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !downloading, onClick = {
                    val apkLink = updateUrl!!
                    scope.launch {
                        downloading = true
                        updateProgress = 0f
                        updateDownloadedBytes = 0L
                        updateTotalBytes = -1L
                        updateError = null
                        val downloaded = withContext(Dispatchers.IO) {
                            runCatching {
                                val connection = (URL(apkLink).openConnection() as java.net.HttpURLConnection).apply {
                                    instanceFollowRedirects = true
                                    connectTimeout = 20000
                                    readTimeout = 60000
                                    requestMethod = "GET"
                                    setRequestProperty("User-Agent", "GENJUR-STREAM-Android")
                                    setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
                                    setRequestProperty("Cache-Control", "no-cache")
                                }
                                try {
                                    val status = connection.responseCode
                                    require(status in 200..299) { "Server unduhan membalas HTTP $status" }
                                    val contentType = connection.contentType.orEmpty()
                                    require(!contentType.contains("text/html", ignoreCase = true)) {
                                        "Server mengirim halaman web, bukan berkas APK"
                                    }
                                    val totalBytes = connection.contentLengthLong
                                    withContext(Dispatchers.Main) { updateTotalBytes = totalBytes }
                                    val file = File(activity.cacheDir, "genjur-stream-update.apk")
                                    var copiedBytes = 0L
                                    connection.inputStream.use { input ->
                                        file.outputStream().buffered().use { output ->
                                            val buffer = ByteArray(64 * 1024)
                                            while (true) {
                                                val count = input.read(buffer)
                                                if (count < 0) break
                                                output.write(buffer, 0, count)
                                                copiedBytes += count
                                                withContext(Dispatchers.Main) {
                                                    updateDownloadedBytes = copiedBytes
                                                    if (totalBytes > 0L) updateProgress = (copiedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                                }
                                            }
                                        }
                                    }
                                    require(totalBytes <= 0L || copiedBytes == totalBytes) {
                                        "Unduhan belum lengkap (${formatVideoSize(copiedBytes)} dari ${formatVideoSize(totalBytes)})"
                                    }
                                    require(file.length() > 1_000_000L) {
                                        "Berkas APK tidak lengkap (${file.length()} byte)"
                                    }
                                    val signature = file.inputStream().use { input ->
                                        byteArrayOf(input.read().toByte(), input.read().toByte())
                                    }
                                    require(signature[0] == 0x50.toByte() && signature[1] == 0x4b.toByte()) {
                                        "Berkas yang diterima bukan APK yang valid"
                                    }
                                    file
                                } finally {
                                    connection.disconnect()
                                }
                            }
                        }
                        downloading = false
                        downloaded.onSuccess { file ->
                            try {
                                val uri: Uri = FileProvider.getUriForFile(
                                    activity, "${activity.packageName}.fileprovider", file
                                )
                                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, "application/vnd.android.package-archive")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                activity.startActivity(installIntent)
                                updateVersion = null
                            } catch (_: Exception) {
                                updateError = "APK berhasil diunduh, tetapi pemasang tidak dapat dibuka. Periksa izin instalasi aplikasi."
                            }
                        }.onFailure { error ->
                            val detail = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName
                            updateError = "Unduhan pembaruan gagal: $detail"
                        }
                    }
                }) { Text(if (downloading) "Mengunduh..." else "Perbarui sekarang") }
            },
            dismissButton = {
                TextButton(enabled = !downloading, onClick = { updateVersion = null }) { Text("Nanti") }
            }
        )
    }

    Surface(Modifier.fillMaxSize(), color = Bg) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tvLayout = maxWidth >= 720.dp
            val horizontal = if (tvLayout) 36.dp else 18.dp
            Column(Modifier.fillMaxSize().widthIn(max = 1280.dp).align(Alignment.TopCenter)) {
                if (selectedTab != "Browser Sumber") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = horizontal, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("▶", color = Green, fontSize = 30.sp, fontWeight = FontWeight.Black)
                            Column {
                                Text("GENJUR STREAM", color = Color.White, fontSize = if (tvLayout) 25.sp else 19.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Hiburan pilihanmu", color = Muted, fontSize = 10.sp)
                            }
                        }
                        TextButton(onClick = { selectedTab = "Cari" }) {
                            Text("⌕", color = Color.White, fontSize = 25.sp)
                        }
                    }
                }

                Column(
                    if (selectedTab == "Browser Sumber") Modifier.weight(1f).fillMaxWidth()
                    else Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = horizontal)
                ) {
                    if (selectedTab == "Beranda" || selectedTab == "Browser Sumber") {
                        if (selectedTab == "Beranda") {
                            // Pilihan Hari Ini berganti otomatis setiap hari dari poster katalog
                            // yang berhasil dimuat. Refresh katalog berkala juga memperbarui daftar kandidat.
                            val todayCandidates = feedMovies.filter { it.posterUrl.isNotBlank() }
                            val todayIndex = if (todayCandidates.isEmpty()) 0
                                else ((System.currentTimeMillis() / 86_400_000L) % todayCandidates.size).toInt()
                            val todayMovie = todayCandidates.getOrNull(todayIndex)
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                                shape = RoundedCornerShape(28.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF24172F))
                            ) {
                                Box(Modifier.fillMaxWidth().height(300.dp).padding(22.dp)) {
                                    if (!todayMovie?.posterUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(todayMovie?.posterUrl)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = "Poster pilihan hari ini",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .align(Alignment.CenterEnd)
                                                .width(174.dp)
                                                .fillMaxHeight()
                                                .clip(RoundedCornerShape(20.dp))
                                        )
                                        Box(
                                            Modifier.fillMaxSize().background(
                                                Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color(0xFF24172F),
                                                        Color(0xFF24172F).copy(alpha = 0.94f),
                                                        Color(0xFF24172F).copy(alpha = 0.55f),
                                                        Color.Transparent
                                                    )
                                                )
                                            )
                                        )
                                    }
                                    Column(
                                        modifier = Modifier.align(Alignment.CenterStart).fillMaxWidth(0.70f),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text("PILIHAN HARI INI", color = Color(0xFFC6A1FF),
                                            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                        Text(
                                            todayMovie?.title ?: "Cerita hebat. Pengalaman seru.",
                                            color = Color.White, fontSize = 29.sp,
                                            lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold,
                                            maxLines = 3, overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            if (todayMovie != null) todayMovie.description.ifBlank { "Temukan tontonan pilihan hari ini." }
                                            else "Temukan tontonan dari katalog sumber yang sudah kamu tambahkan.",
                                            color = Color(0xFFD5C9DC), fontSize = 14.sp,
                                            lineHeight = 21.sp, maxLines = 3, overflow = TextOverflow.Ellipsis
                                        )
                                        Button(
                                            onClick = {
                                                if (todayMovie != null) selectedMovie = todayMovie
                                                else selectedTab = "Sumber Link"
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B55F6)),
                                            shape = RoundedCornerShape(16.dp),
                                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                        ) { Text(if (todayMovie != null) "Lihat film" else "Jelajahi", color = Color.White, fontWeight = FontWeight.Bold) }
                                    }
                                    Text("✦", modifier = Modifier.align(Alignment.CenterEnd),
                                        color = Color(0xFFB58BFF), fontSize = 92.sp, fontWeight = FontWeight.Light)
                                    Text("GENJUR", modifier = Modifier.align(Alignment.BottomEnd),
                                        color = Color(0xFF8B55F6).copy(alpha = 0.38f),
                                        fontSize = 27.sp, fontWeight = FontWeight.Black)
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Lanjutkan Menonton", color = Color.White, fontSize = 21.sp,
                                    fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                                TextButton(onClick = { selectedTab = "Riwayat" }) {
                                    Text("Lihat semua  →", color = Color(0xFFAA79FF), fontWeight = FontWeight.Bold)
                                }
                            }
                            if (watchHistory.isEmpty()) {
                                Text("Film yang sudah kamu putar akan muncul di sini.",
                                    color = Muted, fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 18.dp))
                            } else {
                                Row(
                                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                                        .padding(bottom = 20.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    watchHistory.take(12).forEach { movie ->
                                        Column(
                                            Modifier.width(150.dp).clickable {
                                                if (movie.category == "Sumber Web") {
                                                    browserInitialUrl = movie.videoUrl
                                                    selectedTab = "Browser Sumber"
                                                } else selectedMovie = movie
                                            }
                                        ) {
                                            MoviePoster(movie.posterUrl, movie.title, 190.dp, 140.dp)
                                            Spacer(Modifier.height(7.dp))
                                            Text(movie.title, color = Color.White, fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold, maxLines = 2,
                                                overflow = TextOverflow.Ellipsis)
                                            Text(movie.category, color = Muted, fontSize = 10.sp,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                        // Halaman sumber ditampilkan pada lapisan WebView persisten di area Beranda.
                    } else {
                        if (selectedTab != "Browser Sumber") {
                            Text(selectedTab, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(14.dp))
                        }
                        when (selectedTab) {
                            "Cari" -> {
                                OutlinedTextField(value = search, onValueChange = { search = it; searchSubmitted = false },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    placeholder = { Text("Cari judul tontonan...", color = Muted) },
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White, focusedBorderColor = Green,
                                        unfocusedBorderColor = Color(0xFF393344), cursorColor = Green))
                                Spacer(Modifier.height(8.dp))
                                Text("Cari dari sumber:", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(4.dp))
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(
                                        onClick = { searchSourceDropdownExpanded = !searchSourceDropdownExpanded },
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(savedSourceMenu.firstOrNull { it.url == selectedSearchSourceUrl }?.name ?: "Semua sumber",
                                            modifier = Modifier.weight(1f), color = Color.White, maxLines = 1)
                                        Text(if (searchSourceDropdownExpanded) "⌃" else "⌄", color = Green, fontSize = 18.sp)
                                    }
                                    if (searchSourceDropdownExpanded) {
                                        Spacer(Modifier.height(6.dp))
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = Panel),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth()
                                                    .heightIn(max = 220.dp)
                                                    .verticalScroll(rememberScrollState())
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text("Semua sumber", color = Color.White) },
                                                    onClick = { selectedSearchSourceUrl = null; searchSourceDropdownExpanded = false; searchSubmitted = false }
                                                )
                                                savedSourceMenu.forEach { source ->
                                                    DropdownMenuItem(
                                                        text = { Text(source.name, color = Color.White, maxLines = 1) },
                                                        onClick = { selectedSearchSourceUrl = source.url; searchSourceDropdownExpanded = false; searchSubmitted = false }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Button(
                                    onClick = { searchSubmitted = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = search.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935), contentColor = Color.White)
                                ) {
                                    Text("Cari", fontWeight = FontWeight.Bold)
                                }
                                Text(
                                    if (selectedSearchSourceUrl == null) "Pencarian akan dilakukan pada semua sumber."
                                    else "Pencarian dibatasi pada sumber terpilih.",
                                    color = Muted,
                                    fontSize = 11.sp
                                )
                            }
                            "Sumber Link" -> {
                                SourceLinksScreen(activity = activity, onOpenLink = { link ->
                                    catalogSourceUrl = link
                                    catalogSourceName = savedSourceMenu.firstOrNull { it.url == link }?.name
                                        ?: Uri.parse(link).host.orEmpty().ifBlank { "Sumber Link" }
                                    sourceCatalogMovies = emptyList()
                                    sourceCatalogMessage = null
                                    selectedTab = "Katalog Sumber"
                                })
                            }
                            "Katalog Sumber" -> {
                                // Katalog tampil sebagai lapisan penuh di atas halaman utama,
                                // termasuk menutupi header dan navigasi bawah.
                                Dialog(
                                    onDismissRequest = { selectedTab = "Beranda" },
                                    properties = DialogProperties(usePlatformDefaultWidth = false)
                                ) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        color = Bg,
                                        shape = RoundedCornerShape(0.dp)
                                    ) {
                                        Box(Modifier.fillMaxSize()) {
                                            Column(
                                                Modifier.fillMaxSize()
                                                    .verticalScroll(rememberScrollState())
                                                    .padding(start = horizontal, end = horizontal, top = 10.dp, bottom = 88.dp)
                                            ) {
                                                SourceCatalogScreen(
                                                    sourceName = catalogSourceName,
                                                    sourceUrl = catalogSourceUrl,
                                                    movies = sourceCatalogMovies,
                                                    enabledVideoFilters = enabledVideoFilters,
                                                    loading = sourceCatalogLoading,
                                                    message = sourceCatalogMessage,
                                                    onMoviesLoaded = { sourceCatalogMovies = it },
                                                    onLoadingChanged = { sourceCatalogLoading = it },
                                                    onMessageChanged = { sourceCatalogMessage = it },
                                                    onOpenMovie = { movie -> selectedMovie = movie },
                                                    onOpenSource = {
                                                        browserInitialUrl = catalogSourceUrl
                                                        selectedTab = "Browser Sumber"
                                                    }
                                                )
                                            }
                                            if (savedSourceMenu.isNotEmpty()) {
                                                Column(
                                                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 18.dp),
                                                    horizontalAlignment = Alignment.End
                                                ) {
                                                    DropdownMenu(
                                                        expanded = sourceMenuExpanded,
                                                        onDismissRequest = { sourceMenuExpanded = false },
                                                        modifier = Modifier.heightIn(max = 320.dp).background(Panel)
                                                    ) {
                                                        DropdownMenuItem(
                                                            text = { Text("None", color = Color.White, maxLines = 1) },
                                                            onClick = {
                                                                selectedTab = "Beranda"
                                                                selectedSourceName = "Sumber Link"
                                                                browserInitialUrl = ""
                                                                sourceMenuExpanded = false
                                                            }
                                                        )
                                                        savedSourceMenu.forEach { source ->
                                                            DropdownMenuItem(
                                                                text = { Text(source.name, color = Color.White, maxLines = 1) },
                                                                onClick = {
                                                                    catalogSourceUrl = source.url
                                                                    catalogSourceName = source.name
                                                                    sourceCatalogMovies = emptyList()
                                                                    sourceCatalogMessage = null
                                                                    selectedSourceName = source.name
                                                                    sourceMenuExpanded = false
                                                                }
                                                            )
                                                        }
                                                    }
                                                    Button(
                                                        onClick = { sourceMenuExpanded = !sourceMenuExpanded },
                                                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
                                                        shape = RoundedCornerShape(24.dp),
                                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                                    ) {
                                                        Text("$selectedSourceName  ▾", fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            "Pengaturan" -> {
                                Card(
                                    Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Panel),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                                        Text("Pengaturan aplikasi", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Spacer(Modifier.height(6.dp))
                                        Text("Kelola sumber situs yang tersimpan di GENJUR STREAM.", color = Muted, fontSize = 13.sp)
                                        Spacer(Modifier.height(14.dp))
                                        HorizontalDivider(color = Color(0xFF393344))
                                        Spacer(Modifier.height(12.dp))
                                        Text("Filter Video", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text("Centang kategori video yang ingin ditampilkan.", color = Muted, fontSize = 12.sp)
                                        Spacer(Modifier.height(8.dp))
                                        VIDEO_FILTER_CATEGORIES.forEach { filter ->
                                            Row(Modifier.fillMaxWidth().clickable {
                                                enabledVideoFilters = if (filter in enabledVideoFilters) enabledVideoFilters - filter else enabledVideoFilters + filter
                                            }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(checked = filter in enabledVideoFilters, onCheckedChange = { checked ->
                                                    enabledVideoFilters = if (checked) enabledVideoFilters + filter else enabledVideoFilters - filter
                                                }, colors = CheckboxDefaults.colors(checkedColor = Green))
                                                Text(filter, color = if (filter == "Adult 18+") Color(0xFFFF8A8A) else Color.White, fontSize = 14.sp)
                                                if (filter == "Adult 18+") {
                                                    Spacer(Modifier.weight(1f))
                                                    Text("Mati secara default", color = Muted, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                        TextButton(onClick = { enabledVideoFilters = DEFAULT_VIDEO_FILTERS }) {
                                            Text("Atur ulang filter default", color = Green)
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        TextButton(onClick = { selectedTab = "Sumber Link" }) {
                                            Text("Kelola Sumber Link  ›", color = Green)
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        HorizontalDivider(color = Color(0xFF393344))
                                        Spacer(Modifier.height(10.dp))
                                        Text("GENJUR STREAM", color = Muted, fontSize = 11.sp)
                                        Text("Genjur versi ${BuildConfig.VERSION_NAME}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                            "Kategori" -> {
                                categories.forEach { item ->
                                    FilterChip(selected = category == item, onClick = { category = item },
                                        label = { Text(item) })
                                }
                            }
                            "Unduhan" -> {
                                downloadMessage?.let { Text(it, color = if (it.startsWith("Gagal") || it.contains("belum")) MaterialTheme.colorScheme.error else Green, fontSize = 12.sp) }
                                if (downloads.isNotEmpty()) {
                                    TextButton(onClick = { confirmClearDownloads = true }) {
                                        Text("Hapus semua riwayat unduhan", color = Color(0xFFFF8A8A))
                                    }
                                }
                                if (downloads.isEmpty()) {
                                    Text("Belum ada video yang diunduh.", color = Muted)
                                } else {
                                    downloads.forEach { entry ->
                                        val state = downloadStates[entry.id] ?: (0 to "Menyiapkan")
                                        var completedUri: Uri? = null
                                        if (entry.id < 0L) {
                                            val savedUri = Uri.parse(entry.movie.videoUrl)
                                            if (savedUri.scheme == "content") completedUri = savedUri
                                            else {
                                                val localFile = File(savedUri.path.orEmpty())
                                                if (localFile.exists() && localFile.length() > 0L) completedUri = Uri.fromFile(localFile)
                                            }
                                        } else {
                                            val query = DownloadManager.Query().setFilterById(entry.id)
                                            downloadManager.query(query)?.use { cursor ->
                                                if (cursor.moveToFirst() &&
                                                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                                                    completedUri = downloadManager.getUriForDownloadedFile(entry.id)
                                                }
                                            }
                                        }
                                        Card(
                                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                            colors = CardDefaults.cardColors(containerColor = Panel),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                                val savedFileName = if (entry.id < 0L) {
                                                    File(Uri.parse(entry.movie.videoUrl).path.orEmpty()).name
                                                } else {
                                                    val originalPath = Uri.parse(entry.movie.downloadUrl).path.orEmpty()
                                                    downloadFileName(entry.movie.title, entry.movie.releaseYear,
                                                        originalPath.substringAfterLast('.', "mp4"))
                                                }
                                                Text(savedFileName, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                                                Text(state.second, color = Muted, fontSize = 11.sp)
                                                val metrics = downloadMetrics[entry.id]
                                                if (metrics != null) {
                                                    val sizeText = if (metrics.totalBytes > 0L) {
                                                        "${formatVideoSize(metrics.downloadedBytes)} / ${formatVideoSize(metrics.totalBytes)}"
                                                    } else {
                                                        formatVideoSize(metrics.downloadedBytes).let {
                                                            if (it == "Tidak diketahui") "Menunggu data ukuran" else "Terunduh: $it"
                                                        }
                                                    }
                                                    Text("Ukuran: $sizeText", color = Muted, fontSize = 11.sp)
                                                    if (state.second == "Mengunduh" || state.second == "Menyiapkan") {
                                                        Text("Kecepatan: ${formatDownloadSpeed(metrics.speedBytesPerSecond)}", color = Green, fontSize = 11.sp)
                                                    }
                                                }
                                                if (state.second == "Mengunduh" || state.second == "Menunggu") {
                                                    Spacer(Modifier.height(7.dp))
                                                    if (entry.id < 0L) {
                                                        // HLS/DASH tidak menyediakan ukuran total file MP4 di awal.
                                                        // Jangan tampilkan 0% palsu; gunakan indikator berjalan sampai mux selesai.
                                                        LinearProgressIndicator(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            color = Green
                                                        )
                                                        Text(
                                                            "Progres: data sedang diterima • persentase belum tersedia",
                                                            color = Muted,
                                                            fontSize = 10.sp
                                                        )
                                                    } else {
                                                        LinearProgressIndicator(
                                                            progress = { state.first / 100f },
                                                            modifier = Modifier.fillMaxWidth(),
                                                            color = Green
                                                        )
                                                        Text("${state.first}%", color = Muted, fontSize = 10.sp)
                                                    }
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    if (completedUri != null) {
                                                        TextButton(onClick = {
                                                            val offlineMovie = entry.movie.copy(videoUrl = completedUri.toString())
                                                            recordWatch(offlineMovie)
                                                            playingMovie = offlineMovie
                                                        }) { Text("Putar offline", color = Green) }
                                                    }
                                                    if (entry.id < 0L && (state.second.startsWith("Terhenti") || state.second == "Gagal")) {
                                                        TextButton(onClick = {
                                                            startAdaptiveDownload(entry.movie.title, entry.movie.termsUrl, entry.movie.downloadUrl, entry.id)
                                                        }) { Text("Mulai ulang", color = Green) }
                                                    }
                                                    TextButton(onClick = {
                                                        if (entry.id < 0L) {
                                                            adaptiveSessions[entry.id]?.let { FFmpegKit.cancel(it) }
                                                            deleteDownloadedUri(activity, entry.movie.videoUrl)
                                                        } else {
                                                            downloadManager.remove(entry.id)
                                                        }
                                                        downloads = downloads.filterNot { it.id == entry.id }
                                                        downloadStates = downloadStates - entry.id
                                                        persistDownloads(downloads)
                                                    }) { Text("Hapus", color = Muted) }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            "Favorit", "Riwayat" -> {
                                val savedList = if (selectedTab == "Favorit") favoriteMovies else watchHistory
                                if (savedList.isEmpty()) {
                                    Text(
                                        if (selectedTab == "Favorit") "Belum ada film favorit. Buka detail film lalu tekan ♡ Favorit."
                                        else "Belum ada riwayat. Film yang diputar akan muncul di sini.",
                                        color = Muted, fontSize = 13.sp
                                    )
                                } else {
                                    savedList.forEach { movie ->
                                        Card(
                                            Modifier.fillMaxWidth().padding(bottom = 9.dp),
                                            colors = CardDefaults.cardColors(containerColor = Panel),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                MoviePoster(movie.posterUrl, movie.title, 92.dp, 72.dp)
                                                Spacer(Modifier.width(12.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(movie.title, color = Color.White, fontWeight = FontWeight.Bold)
                                                    Text(movie.category, color = Green, fontSize = 11.sp)
                                                    TextButton(onClick = {
                                                        if (selectedTab == "Riwayat" && movie.category == "Sumber Web") {
                                                            browserInitialUrl = movie.videoUrl
                                                            selectedTab = "Browser Sumber"
                                                        } else selectedMovie = movie
                                                    }) {
                                                        Text(if (selectedTab == "Riwayat" && movie.category == "Sumber Web") "Lanjutkan" else "Lihat detail", color = Green)
                                                    }
                                                }
                                                if (selectedTab == "Favorit") {
                                                    TextButton(onClick = { toggleFavorite(movie) }) { Text("Hapus", color = Muted) }
                                                }
                                            }
                                        }
                                    }
                                    if (selectedTab == "Riwayat") {
                                        TextButton(onClick = {
                                            watchHistory = emptyList()
                                            persistMovies("history", watchHistory)
                                        }) { Text("Hapus semua riwayat", color = Muted) }
                                    }
                                }
                            }
                        }
                        if (selectedTab == "Cari" || selectedTab == "Kategori") {
                            Spacer(Modifier.height(14.dp))
                            val matchingMovies = feedMovies.filter { movie ->
                                val selectedHost = selectedSearchSourceUrl?.let { Uri.parse(it).host?.lowercase()?.removePrefix("www.") }
                                val movieHost = listOf(movie.videoUrl, movie.termsUrl).mapNotNull { candidate ->
                                    runCatching { Uri.parse(candidate).host?.lowercase()?.removePrefix("www.") }.getOrNull()
                                }.firstOrNull()
                                matchesVideoFilters(movie, enabledVideoFilters) &&
                                (selectedTab != "Cari" || !movie.title.equals("Drama", true)) &&
                                    (selectedTab != "Cari" || selectedHost == null || movieHost == selectedHost || movieHost?.endsWith(".$selectedHost") == true) &&
                                    (category == "Semua" || selectedTab != "Kategori" || movie.category.equals(category, true)) &&
                                    (search.isBlank() || movie.title.contains(search, true) || movie.description.contains(search, true) || movie.genres.any { it.contains(search, true) })
                            }
                            if (selectedTab == "Cari") {
                                if (!searchSubmitted) {
                                    Text("Ketik judul film, pilih sumber atau Semua sumber, lalu tekan Cari.", color = Muted, fontSize = 13.sp)
                                } else if (matchingMovies.isEmpty()) {
                                    Text("Film tidak ditemukan pada sumber yang dipilih.", color = Muted, fontSize = 13.sp)
                                } else {
                                    matchingMovies.chunked(3).forEach { posterRow ->
                                        Row(
                                            Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(9.dp)
                                        ) {
                                            posterRow.forEach { movie ->
                                                Box(
                                                    Modifier.weight(1f).clickable { selectedMovie = movie }
                                                ) {
                                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                        MoviePoster(
                                                            url = movie.posterUrl,
                                                            title = movie.title,
                                                            height = 190.dp
                                                        )
                                                        if (movie.sourceName.isNotBlank()) {
                                                            Text(
                                                                text = movie.sourceName,
                                                                color = Green,
                                                                fontSize = 10.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                                textAlign = TextAlign.Center
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            repeat(3 - posterRow.size) {
                                                Spacer(Modifier.weight(1f))
                                            }
                                        }
                                        Spacer(Modifier.height(12.dp))
                                    }
                                }
                            } else {
                                matchingMovies.forEach { movie ->
                                    Card(Modifier.fillMaxWidth().padding(bottom = 9.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(movie.title, color = Color.White, fontWeight = FontWeight.Bold)
                                                Text(movie.category, color = Green, fontSize = 11.sp)
                                            }
                                            TextButton(onClick = {
                                                if (movie.licenseId == "Metadata halaman sumber") {
                                                    browserInitialUrl = movie.videoUrl
                                                    selectedTab = "Browser Sumber"
                                                } else selectedMovie = movie
                                            }) { Text(if (movie.licenseId == "Metadata halaman sumber") "Buka sumber" else "Detail", color = Green) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF15141B)).horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pencarian tetap berada di ikon kaca pembesar bagian atas.
                    listOf(
                        "Beranda" to Icons.Filled.Home,
                        "Sumber Link" to Icons.Filled.Link,
                        "Unduhan" to Icons.Filled.Download,
                        "Favorit" to Icons.Filled.Favorite,
                        "Riwayat" to Icons.Filled.History,
                        "Pengaturan" to Icons.Filled.Settings
                    ).forEach { (tab, navIcon) ->
                        val isSelected = selectedTab == tab
                        Column(
                            modifier = Modifier.width(if (tvLayout) 104.dp else 56.dp)
                                .clickable {
                                selectedTab = tab
                            }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = navIcon,
                                contentDescription = tab,
                                tint = if (tab == "Unduhan" && downloadBlinkTargetId != null) { if (downloadIconBlink) Green else Muted } else if (isSelected) Green else Muted,
                                modifier = Modifier.size(if (tvLayout) 27.dp else 25.dp)
                            )
                        }
                    }
                }
            }
            // WebView tetap berada di komposisi saat berpindah tab agar posisi,
            // sesi login, dan halaman situs tidak di-reset. Saat tab lain aktif,
            // WebView dipindahkan ke luar layar tanpa dihancurkan.
            if (browserInitialUrl.isNotBlank()) {
                val sourceVisible = selectedTab == "Browser Sumber"
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = if (selectedTab == "Browser Sumber") 0.dp else 78.dp)
                        .height(maxHeight - if (selectedTab == "Browser Sumber") 70.dp else 148.dp)
                        .offset(x = if (sourceVisible) 0.dp else 2000.dp)
                ) {
                    key(browserInitialUrl) {
                        SourceBrowser(
                            initialUrl = browserInitialUrl,
                            fullScreen = true,
                            active = sourceVisible && playingMovie == null,
                            onPlaybackStarted = { pageTitle, pageUrl ->
                                val safeTitle = pageTitle.trim().takeIf { it.isNotBlank() }
                                    ?: Uri.parse(pageUrl).host.orEmpty().ifBlank { "Video dari situs" }
                                activity.showWebPlaybackNotification(safeTitle, pageUrl)
                                recordWatch(FeedMovie(
                                    title = safeTitle,
                                    description = "Tontonan yang mulai diputar melalui Browser Sumber.",
                                    category = "Sumber Web",
                                    videoUrl = pageUrl,
                                    licenseId = "User-provided website",
                                    termsUrl = pageUrl,
                                    attribution = "Situs sumber: ${Uri.parse(pageUrl).host.orEmpty()}"
                                ))
                            },
                            onPlayCandidate = { title, pageUrl, mediaUrl ->
                                val safeTitle = title.ifBlank { "Video dari situs" }
                                pendingPlaybackChoice = FeedMovie(
                                    title = safeTitle,
                                    description = "Video terdeteksi dari pemutar situs.",
                                    category = "Sumber Web",
                                    videoUrl = mediaUrl,
                                    licenseId = "User-provided website",
                                    termsUrl = pageUrl,
                                    attribution = "Situs sumber: ${Uri.parse(pageUrl).host.orEmpty()}"
                                )
                            },
                            onDownloadCandidate = { title, pageUrl, mediaUrl ->
                                startWebVideoDownload(title, pageUrl, mediaUrl)
                                downloadBlinkTargetId = downloads.firstOrNull()?.id
                            }
                        )
                    }
                }
            }
            if (savedSourceMenu.isNotEmpty() && (selectedTab == "Beranda" || selectedTab == "Browser Sumber")) {
                Column(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 82.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    DropdownMenu(
                        expanded = sourceMenuExpanded,
                        onDismissRequest = { sourceMenuExpanded = false },
                        modifier = Modifier.heightIn(max = 320.dp).background(Panel)
                    ) {
                        DropdownMenuItem(
                            text = { Text("None", color = Color.White, maxLines = 1) },
                            onClick = {
                                selectedTab = "Beranda"
                                selectedSourceName = "Sumber Link"
                                browserInitialUrl = ""
                                sourceMenuExpanded = false
                            }
                        )
                        savedSourceMenu.forEach { source ->
                            DropdownMenuItem(
                                text = { Text(source.name, color = Color.White, maxLines = 1) },
                                onClick = {
                                    catalogSourceUrl = source.url
                                    catalogSourceName = source.name
                                    sourceCatalogMovies = emptyList()
                                    sourceCatalogMessage = null
                                    selectedSourceName = source.name
                                    selectedTab = "Katalog Sumber"
                                    sourceMenuExpanded = false
                                }
                            )
                        }
                    }
                    Button(
                        onClick = { sourceMenuExpanded = !sourceMenuExpanded },
                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Text("$selectedSourceName  ▾", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (confirmClearDownloads) {
        AlertDialog(
            onDismissRequest = { confirmClearDownloads = false },
            title = { Text("Hapus semua riwayat unduhan?") },
            text = { Text("Daftar unduhan akan dikosongkan. File MP4 hasil konversi dan unduhan GENJUR yang tersimpan juga akan dihapus.") },
            confirmButton = {
                TextButton(onClick = {
                    downloads.forEach { entry ->
                        if (entry.id < 0L) {
                            adaptiveSessions[entry.id]?.let { FFmpegKit.cancel(it) }
                            File(Uri.parse(entry.movie.videoUrl).path.orEmpty()).delete()
                        } else {
                            runCatching { downloadManager.remove(entry.id) }
                        }
                    }
                    downloads = emptyList()
                    downloadStates = emptyMap()
                    persistDownloads(emptyList())
                    downloadMessage = null
                    confirmClearDownloads = false
                }) { Text("Hapus semua", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDownloads = false }) { Text("Batal") }
            }
        )
    }

    pendingPlaybackChoice?.let { candidate ->
        AlertDialog(
            onDismissRequest = { pendingPlaybackChoice = null },
            title = { Text("Pilih cara memutar", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(candidate.title, color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Bagaimana Anda ingin menonton film ini?", color = Muted)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingPlaybackChoice = null
                    recordWatch(candidate)
                    playingMovie = candidate
                }) { Text("Player GENJUR STREAM", color = Green) }
            },
            dismissButton = {
                TextButton(onClick = { pendingPlaybackChoice = null }) { Text("Player Situs", color = Color.White) }
            },
            containerColor = Panel,
            titleContentColor = Color.White,
            textContentColor = Color.White
        )
    }

    selectedMovie?.let { movie ->
        MovieDetailDialog(
            movie = movie,
            detailLoading = sourceDetailLoading,
            isFavorite = favoriteMovies.any { movieKey(it) == movieKey(movie) },
            onToggleFavorite = { toggleFavorite(movie) },
            isDownloaded = downloads.any { movieKey(it.movie) == movieKey(movie) },
            onDownload = { startDownload(movie) },
            onClose = { selectedMovie = null },
            onPlay = { selectedVersion ->
                if (selectedVersion.videoUrl.isNotBlank()) {
                    recordWatch(selectedVersion)
                    // Utamakan URL stream langsung yang sudah berhasil diekstrak
                    // dari metadata halaman/iframe publik, seperti alur provider CloudStream.
                    // Halaman sumber hanya dibuka sebagai fallback bila belum ada stream.
                    val firstDirectOption = selectedVersion.streamOptions.firstOrNull {
                        it.videoUrl.startsWith("https://") || it.videoUrl.startsWith("http://")
                    }
                    val firstDirectStream = firstDirectOption?.videoUrl
                    val playbackMovie = if (firstDirectStream != null) {
                        selectedVersion.copy(
                            videoUrl = firstDirectStream,
                            downloadUrl = firstDirectStream,
                            termsUrl = firstDirectOption?.referer?.ifBlank { selectedVersion.termsUrl } ?: selectedVersion.termsUrl,
                            licenseId = "Direct source media"
                        )
                    } else {
                        // Jika ekstraksi awal belum menemukan media, jalankan provider fallback
                        // di WebView untuk mengamati stream yang diminta player halaman sumber.
                        selectedVersion.copy(licenseId = "Source page fallback")
                    }
                    // Pertahankan halaman detail di belakang player.
                    // Saat player ditutup, pengguna kembali ke detail film, bukan katalog.
                    playingMovie = playbackMovie
                }
            }
        )
    }
    playingMovie?.let { movie ->
        val closePlayback = { playingMovie = null }
        val downloadPlayback = {
            if (movie.downloadAllowed && movie.downloadUrl.isNotBlank()) {
                startDownload(movie)
            } else {
                startWebVideoDownload(movie.title, movie.termsUrl, movie.videoUrl)
            }
        }
        if (movie.licenseId == "Source page fallback") {
            SourceFallbackPlayer(activity, movie, onDownload = downloadPlayback, onClose = closePlayback)
        } else {
            InAppVideoPlayer(
                activity,
                movie,
                onDownload = downloadPlayback,
                onClose = closePlayback,
                fallbackVideoUrls = movie.streamOptions.map { it.videoUrl }
                    .filter { it != movie.videoUrl }
            )
        }
    }
}


@Composable
private fun MoviePoster(
    url: String,
    title: String,
    height: androidx.compose.ui.unit.Dp,
    width: androidx.compose.ui.unit.Dp? = null
) {
    var failed by remember(url) { mutableStateOf(false) }
    val context = LocalContext.current
    val posterModifier = if (width == null) Modifier.fillMaxWidth().height(height)
        else Modifier.width(width).height(height)
    if (url.isBlank() || failed) {
        Box(
            posterModifier
                .background(Color(0xFF302541), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("GENJUR STREAM", color = Color(0xFFC4A7FF), fontSize = 19.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(5.dp))
                Text(
                    if (url.isBlank()) "Poster belum tersedia" else "Poster tidak dapat dimuat",
                    color = Muted, fontSize = 10.sp
                )
            }
        }
    } else {
        val imageRequest = remember(url) {
            ImageRequest.Builder(context)
                .data(url)
                .addHeader("User-Agent", "GENJUR-STREAM/1.0 (Android app; Wikimedia Commons media viewer)")
                .crossfade(true)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = "Poster $title",
            contentScale = ContentScale.Crop,
            modifier = posterModifier,
            onError = { failed = true }
        )
    }
}

@Composable
private fun MovieDetailDialog(
    movie: FeedMovie,
    detailLoading: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    isDownloaded: Boolean,
    onDownload: () -> Unit,
    onClose: () -> Unit,
    onPlay: (FeedMovie) -> Unit
) {
    val isSourceMetadataOnly = movie.licenseId == "Metadata halaman sumber"
    var selectedStreamUrl by remember(movie) {
        mutableStateOf(if (isSourceMetadataOnly) movie.streamOptions.firstOrNull()?.videoUrl ?: movie.termsUrl
            else movie.streamOptions.firstOrNull()?.videoUrl ?: movie.videoUrl)
    }
    var showTrailer by remember(movie) { mutableStateOf(false) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Halaman detail menutup katalog sepenuhnya, bukan dialog mengambang.
        Surface(Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp), color = Panel) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box {
                    MoviePoster(url = movie.posterUrl, title = movie.title, height = 300.dp)
                    TextButton(
                        onClick = onClose,
                        modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                        colors = ButtonDefaults.textButtonColors(containerColor = Color(0xAA09090D))
                    ) { Text("✕ Tutup", color = Color.White) }
                }
                Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text(movie.title, color = Color.White, fontSize = 25.sp, lineHeight = 30.sp,
                        fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isFavorite) Color(0xFFFF7AA8) else Color.White)
                    ) {
                        Text(if (isFavorite) "♥  Hapus dari Favorit" else "♡  Tambahkan ke Favorit")
                    }
                    Spacer(Modifier.height(9.dp))
                    Text(
                        listOf(movie.category, movie.releaseYear, movie.duration)
                            .filter { it.isNotBlank() }.joinToString("  •  "),
                        color = Green, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                    )
                    if (movie.genres.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        Text(movie.genres.joinToString("  •  "), color = Color(0xFFD5C9E8), fontSize = 12.sp)
                    }
                    if (movie.trailerUrl.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { showTrailer = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Green)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Tonton Trailer")
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                    Divider(color = Color(0xFF393344))
                    Spacer(Modifier.height(16.dp))
                    Text("SINOPSIS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(movie.description.ifBlank { "Sinopsis belum tersedia." },
                        color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
                    val availableStreams = if (isSourceMetadataOnly) movie.streamOptions else movie.streamOptions.ifEmpty {
                        if (movie.videoUrl.isNotBlank()) listOf(StreamOption("Otomatis", movie.videoUrl)) else emptyList()
                    }
                    if (detailLoading) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
                        Text("Memuat detail film…", color = Muted, fontSize = 11.sp)
                    }
                    if (isSourceMetadataOnly) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (movie.streamOptions.isNotEmpty()) "Sumber video langsung ditemukan. PLAY akan memakai player internal GENJUR STREAM."
                            else "Belum ditemukan URL video langsung pada halaman ini. Situs mungkin memuat player secara dinamis atau memerlukan metode resmi; halaman situs tidak akan dipakai sebagai video.",
                            color = Muted, fontSize = 11.sp, lineHeight = 17.sp
                        )
                    }
                    if (availableStreams.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Text("PILIH KUALITAS VIDEO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        availableStreams.forEach { stream ->
                            FilterChip(
                                selected = selectedStreamUrl == stream.videoUrl,
                                onClick = { selectedStreamUrl = stream.videoUrl },
                                label = { Text(stream.quality) }
                            )
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Divider(color = Color(0xFF393344))
                    Spacer(Modifier.height(14.dp))
                    Text("INFORMASI SUMBER", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(movie.attribution, color = Muted, fontSize = 12.sp)
                    if (movie.downloadAllowed && movie.downloadUrl.isNotBlank()) {
                        OutlinedButton(
                            onClick = onDownload,
                            enabled = !isDownloaded,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isDownloaded) "✓  Ada di Unduhan" else "⇩  Unduh untuk Offline")
                        }
                    } else {
                        Text("Unduhan tidak diizinkan oleh sumber ini.", color = Muted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val useSitePlayer = isSourceMetadataOnly && movie.streamOptions.isEmpty()
                            onPlay(movie.copy(
                                videoUrl = selectedStreamUrl,
                                licenseId = if (useSitePlayer) "Source page fallback" else movie.licenseId
                            ))
                        },
                        enabled = selectedStreamUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White,
                            disabledContainerColor = Color(0xFF393344), disabledContentColor = Muted)) {
                        Text(if (selectedStreamUrl.isBlank()) "▶  PLAY — Stream belum tersedia" else "▶  PLAY sekarang")
                    }
                    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Kembali ke katalog", color = Green) }
                }
            }
        }
    }
    if (showTrailer) {
        Dialog(
            onDismissRequest = { showTrailer = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(Modifier.fillMaxSize(), color = Color.Black, shape = RoundedCornerShape(0.dp)) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().background(Panel).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Trailer • " + movie.title, color = Color.White,
                            modifier = Modifier.weight(1f), maxLines = 1, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showTrailer = false }) { Text("Tutup", color = Green) }
                    }
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mediaPlaybackRequiresUserGesture = true
                                webViewClient = WebViewClient()
                                webChromeClient = WebChromeClient()
                                loadUrl(movie.trailerUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}


private data class SourceSearchResult(val title: String, val url: String, val host: String)

private fun searchWebSources(query: String): List<SourceSearchResult> {
    val encoded = java.net.URLEncoder.encode(query, "UTF-8")
    // Coba beberapa endpoint Google terlebih dahulu; jangan bergantung pada Bing saja.
    val endpoints = listOf(
        "https://www.google.com/search?gbv=1&q=$encoded",
        "https://www.google.co.id/search?gbv=1&q=$encoded",
        "https://www.google.com/search?q=$encoded",
        "https://www.bing.com/search?format=rss&q=$encoded",
        "https://www.bing.com/search?q=$encoded"
    )
    val blockedHosts = setOf(
        "bit.ly", "tinyurl.com", "t.co", "is.gd", "cutt.ly", "shorturl.at",
        "google.com", "www.google.com", "google.co.id", "www.google.co.id",
        "bing.com", "www.bing.com"
    )
    val errors = mutableListOf<String>()

    for (endpoint in endpoints) {
        var connection: java.net.HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 20000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Accept", "application/rss+xml,application/xml,text/html,application/xhtml+xml;q=0.9,*/*;q=0.8")
                setRequestProperty("Accept-Language", "id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7")
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                errors.add("${URL(endpoint).host}: HTTP $status")
                continue
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val document = Jsoup.parse(body)
            val candidates = mutableListOf<Pair<String, String>>()

            if (endpoint.contains("format=rss")) {
                document.select("item").forEach { item ->
                    candidates.add(item.selectFirst("title")?.text().orEmpty() to
                        item.selectFirst("link")?.text().orEmpty())
                }
            } else {
                val selected = if (endpoint.contains("bing.com")) {
                    document.select("li.b_algo h2 a, h2 a")
                } else {
                    document.select("a:has(h3)")
                }
                selected.forEach { anchor ->
                    candidates.add(anchor.text().trim() to
                        anchor.absUrl("href").ifBlank { anchor.attr("href") })
                }
                if (candidates.isEmpty()) {
                    document.select("a[href]").forEach { anchor ->
                        val title = anchor.text().trim()
                        val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                        if (title.length >= 3 && href.startsWith("http")) candidates.add(title to href)
                    }
                }
            }

            val results = candidates.mapNotNull { (candidateTitle, raw) ->
                if (raw.isBlank()) return@mapNotNull null
                val resolved = runCatching {
                    val wrapped = Uri.parse(raw)
                    wrapped.getQueryParameter("q")?.takeIf { it.startsWith("http") }
                        ?: wrapped.getQueryParameter("url")?.takeIf { it.startsWith("http") }
                        ?: wrapped.getQueryParameter("uddg")?.takeIf { it.startsWith("http") }
                        ?: raw
                }.getOrDefault(raw)
                val uri = runCatching { Uri.parse(resolved) }.getOrNull() ?: return@mapNotNull null
                val host = uri.host?.lowercase()?.removePrefix("www.") ?: return@mapNotNull null
                val suspicious = uri.scheme != "https" || host.isBlank() || !host.contains(".") ||
                    host.startsWith("xn--") || host.split(".").any { it.startsWith("xn--") } ||
                    blockedHosts.any { host == it || host.endsWith(".$it") } ||
                    uri.userInfo != null || host.matches(Regex("\\d{1,3}(\\.\\d{1,3}){3}"))
                if (suspicious) return@mapNotNull null
                SourceSearchResult(candidateTitle.trim().ifBlank { host }, uri.toString(), host)
            }.distinctBy { it.host }.take(12)

            if (results.isNotEmpty()) return results
            errors.add("${URL(endpoint).host}: halaman terbuka, tetapi tautan hasil tidak ditemukan.")
        } catch (e: Exception) {
            errors.add("${runCatching { URL(endpoint).host }.getOrDefault("server")}: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            connection?.disconnect()
        }
    }
    throw java.io.IOException(
        if (errors.isEmpty()) "Tidak dapat menghubungi mesin pencari."
        else "Semua jalur pencarian gagal. " + errors.take(3).joinToString(" | ")
    )
}
private const val SOURCE_DOMAIN_REGISTRY_URL = "https://raw.githubusercontent.com/genjur84/GENJUR-STREAM/main/source-domains.json"
private const val SOURCE_REPOSITORY_URL = "https://raw.githubusercontent.com/genjur84/GENJUR-STREAM/main/source-repository.json"

private fun normalizeSourceIdentity(value: String): String =
    value.lowercase(java.util.Locale.ROOT).filter { it.isLetterOrDigit() }

private fun refreshSavedSourceDomains(activity: MainActivity): Int {
    val connection = URL(SOURCE_DOMAIN_REGISTRY_URL).openConnection()
    connection.connectTimeout = 10000
    connection.readTimeout = 15000
    connection.setRequestProperty("Cache-Control", "no-cache")
    connection.setRequestProperty("User-Agent", "GENJUR-STREAM")
    val root = JSONObject(connection.getInputStream().bufferedReader().use { it.readText() })
    require(root.optInt("schemaVersion", 0) == 1) { "Format daftar domain tidak dikenal." }
    val entries = root.optJSONArray("sources") ?: error("Daftar domain kosong atau tidak valid.")
    val aliases = mutableMapOf<String, String>()
    for (index in 0 until entries.length()) {
        val item = entries.optJSONObject(index) ?: continue
        val targetUrl = item.optString("url").trim()
        val targetUri = Uri.parse(targetUrl)
        if (targetUri.scheme != "https" || targetUri.host.isNullOrBlank()) continue
        val identities = mutableListOf(item.optString("name"))
        val names = item.optJSONArray("aliases")
        if (names != null) for (aliasIndex in 0 until names.length()) identities.add(names.optString(aliasIndex))
        identities.map(::normalizeSourceIdentity).filter { it.isNotBlank() }.forEach { identity ->
            aliases[identity] = targetUrl
        }
    }
    val prefs = activity.getSharedPreferences("genjur_stream_saved_links", 0)
    val saved = JSONArray(prefs.getString("items", "[]"))
    var changed = 0
    for (index in 0 until saved.length()) {
        val item = saved.optJSONObject(index) ?: continue
        val identity = normalizeSourceIdentity(item.optString("name"))
        val replacement = aliases[identity] ?: continue
        val oldUrl = item.optString("url").trim()
        if (oldUrl != replacement) {
            item.put("url", replacement)
            changed++
        }
    }
    if (changed > 0) prefs.edit().putString("items", saved.toString()).apply()
    return changed
}

private data class SourceRepositoryEntry(val id: String, val name: String, val url: String, val category: String = "", val description: String = "", val pluginId: String = "")

private data class SavedSourceLink(val name: String, val url: String)

@Composable
private fun SourceCatalogScreen(
    sourceName: String, sourceUrl: String, movies: List<FeedMovie>, enabledVideoFilters: Set<String>,
    loading: Boolean, message: String?,
    onMoviesLoaded: (List<FeedMovie>) -> Unit, onLoadingChanged: (Boolean) -> Unit,
    onMessageChanged: (String?) -> Unit, onOpenMovie: (FeedMovie) -> Unit, onOpenSource: () -> Unit
) {
    var reloadToken by remember(sourceUrl) { mutableIntStateOf(0) }
    var gridColumns by remember(sourceUrl) { mutableIntStateOf(3) }
    var selectedGenre by remember(sourceUrl) { mutableStateOf("Semua") }
    var categoryMenuExpanded by remember(sourceUrl) { mutableStateOf(false) }
    val plugin = remember(sourceUrl) { SourcePluginRegistry.resolve(sourceUrl) }

    LaunchedEffect(sourceUrl, reloadToken) {
        if (sourceUrl.isBlank()) return@LaunchedEffect
        onMoviesLoaded(emptyList())
        onLoadingChanged(true)
        if (plugin == null) {
            onLoadingChanged(false)
            onMessageChanged("Plugin untuk sumber ini belum tersedia di GENJUR STREAM. Saat ini plugin pertama yang sedang diuji adalah Cmax.")
            return@LaunchedEffect
        }
        onMessageChanged("Plugin ${plugin.displayName} sedang mengambil data katalog…")
        val result = runCatching { plugin.loadCatalog(sourceName, sourceUrl) }
        result.onSuccess { items ->
            onMoviesLoaded(items)
            onLoadingChanged(false)
            onMessageChanged(
                if (items.isEmpty()) "Plugin ${plugin.displayName} belum menemukan daftar film pada halaman katalog sumber."
                else "${items.size} judul berhasil dimuat oleh plugin ${plugin.displayName}."
            )
        }.onFailure { error ->
            onLoadingChanged(false)
            onMessageChanged("Plugin ${plugin.displayName} gagal memuat katalog: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun normalizeGenre(raw: String): String {
        val value = raw.trim().lowercase()
        return when {
            value in listOf("action", "aksi") -> "Action"
            value in listOf("adventure", "petualangan") -> "Adventure"
            value in listOf("comedy", "komedi") -> "Comedy"
            value in listOf("crime", "kriminal") -> "Crime"
            value in listOf("documentary", "dokumenter") -> "Documentary"
            value == "drama" -> "Drama"
            value in listOf("family", "keluarga") -> "Family"
            value in listOf("fantasy", "fantasi") -> "Fantasy"
            value in listOf("horror", "horor") -> "Horror"
            value in listOf("mystery", "misteri") -> "Mystery"
            value in listOf("romance", "romantis") -> "Romance"
            value in listOf("sci-fi", "science fiction", "science-fiction", "fiksi ilmiah") -> "Sci-Fi"
            value in listOf("thriller", "seru") -> "Thriller"
            value in listOf("animation", "animasi", "anime") -> "Animation"
            else -> raw.trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }
    val genreOptions = remember(movies) {
        movies.flatMap { movie -> (movie.genres + movie.category.split(",", "/", "|", "•")).map(::normalizeGenre) }
            .filter { it.isNotBlank() && it != "Film" && it != "Serial" && it != "Series" }
            .distinct().sorted()
    }
    val visibleMovies = remember(movies, selectedGenre, enabledVideoFilters) {
        movies.filter { movie ->
            matchesVideoFilters(movie, enabledVideoFilters) && (selectedGenre == "Semua" ||
                (movie.genres + movie.category.split(",", "/", "|", "•")).map(::normalizeGenre)
                    .any { it.equals(selectedGenre, ignoreCase = true) })
        }
    }
    val featuredMovies = remember(visibleMovies) {
        visibleMovies.filter { it.posterUrl.isNotBlank() }.take(12)
    }
    var featuredIndex by remember(sourceUrl) { mutableIntStateOf(0) }
    LaunchedEffect(featuredMovies) {
        featuredIndex = 0
        while (featuredMovies.size > 1) {
            delay(4000)
            featuredIndex = (featuredIndex + 1) % featuredMovies.size
        }
    }
    val featuredMovie = featuredMovies.getOrNull(featuredIndex)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { onMoviesLoaded(emptyList()); reloadToken++ },
                enabled = !loading
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh katalog", tint = Green)
            }
            IconButton(onClick = onOpenSource) {
                Icon(Icons.Filled.Language, contentDescription = "Buka situs sumber", tint = Green)
            }
        }
        Text(sourceName, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
        Text(
            if (plugin == null) "Plugin sumber belum terpasang"
            else "Katalog native • Plugin ${plugin.displayName}",
            color = Muted, fontSize = 11.sp
        )
        if (featuredMovie != null) {
            androidx.compose.animation.Crossfade(targetState = featuredMovie) { movie ->
                Card(
                    Modifier.fillMaxWidth().height(205.dp).clickable { onOpenMovie(movie) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel)
                ) {
                    Box(Modifier.fillMaxSize()) {
                        MoviePoster(movie.posterUrl, movie.title, 205.dp)
                        Column(
                            Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.68f)).padding(12.dp)
                        ) {
                            Text("FILM PILIHAN", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(movie.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Text("Ketuk untuk melihat detail", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                featuredMovies.forEachIndexed { index, _ ->
                    Box(
                        Modifier.padding(horizontal = 3.dp).size(if (index == featuredIndex) 8.dp else 6.dp)
                            .background(if (index == featuredIndex) Green else Muted.copy(alpha = 0.5f), RoundedCornerShape(50))
                    )
                }
            }
        }
        if (movies.isNotEmpty()) {
            Text("Kategori film", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                (listOf("Semua") + genreOptions).forEach { genre ->
                    val isSelected = selectedGenre == genre
                    Surface(
                        modifier = Modifier.clickable { selectedGenre = genre },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) Green else Panel,
                        border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                    ) {
                        Text(
                            genre,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                            color = if (isSelected) Color.White else Muted,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tampilan poster", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.width(8.dp))
                SingleChoiceSegmentedButtonRow {
                    SegmentedButton(
                        selected = gridColumns == 2,
                        onClick = { gridColumns = 2 },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("2 kolom") }
                    SegmentedButton(
                        selected = gridColumns == 3,
                        onClick = { gridColumns = 3 },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text("3 kolom") }
                }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
        message?.let {
            Text(it, color = if (it.startsWith("Plugin") && (it.contains("gagal") || it.contains("belum tersedia"))) MaterialTheme.colorScheme.error else Muted, fontSize = 12.sp)
        }
        if (movies.isEmpty() && !loading) {
            Text(
                "Belum ada judul yang dapat ditampilkan di katalog aplikasi.",
                color = Muted, fontSize = 12.sp
            )
        } else {
            if (visibleMovies.isEmpty()) {
                Text(
                    "Belum ada film dalam kategori $selectedGenre.",
                    color = Muted, fontSize = 12.sp
                )
            }
            visibleMovies.chunked(gridColumns).forEach { rowMovies ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    rowMovies.forEach { movie ->
                        Card(
                            Modifier.weight(1f).clickable { onOpenMovie(movie) },
                            colors = CardDefaults.cardColors(containerColor = Panel),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(6.dp)) {
                                Box(Modifier.fillMaxWidth()) {
                                    MoviePoster(movie.posterUrl, movie.title, 164.dp)
                                    movieCatalogQuality(movie).takeIf { it.isNotBlank() }?.let { quality ->
                                        Surface(
                                            modifier = Modifier.align(Alignment.TopEnd).padding(5.dp),
                                            color = when (quality) {
                                                "CAM", "TS" -> Color(0xFFB3261E)
                                                "SD", "360P", "480P" -> Color(0xFF616161)
                                                "4K", "2160P" -> Color(0xFF6A1B9A)
                                                else -> Color(0xFF167C50)
                                            },
                                            shape = RoundedCornerShape(5.dp)
                                        ) {
                                            Text(quality, color = Color.White, fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                                        }
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    movie.title,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    minLines = 2
                                )
                                Text(
                                    listOfNotNull(movie.releaseYear.takeIf { it.isNotBlank() }, movie.category.takeIf { it.isNotBlank() }).joinToString(" • "),
                                    color = Green,
                                    fontSize = 9.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                    repeat(gridColumns - rowMovies.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceLinksScreen(activity: MainActivity, onOpenLink: (String) -> Unit) {
    val prefs = remember { activity.getSharedPreferences("genjur_stream_saved_links", 0) }
    fun readLinks(): List<SavedSourceLink> = runCatching {
        val array = JSONArray(prefs.getString("items", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").trim()
            val url = item.optString("url").trim()
            if (name.isNotBlank() && url.isNotBlank()) SavedSourceLink(name, url) else null
        }
    }.getOrDefault(emptyList())

    var links by remember { mutableStateOf(readLinks()) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var sourceSearch by remember { mutableStateOf("") }
    var savedSourcesDropdownExpanded by remember { mutableStateOf(false) }
    var selectedSavedSourceIndex by remember { mutableStateOf<Int?>(null) }
    var searchResults by remember { mutableStateOf<List<SourceSearchResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    var domainRefreshMessage by remember { mutableStateOf("Memeriksa pembaruan alamat sumber…") }
    val repositoryScope = rememberCoroutineScope()
    var repositoryUrl by remember { mutableStateOf(prefs.getString("source_repository_url", SOURCE_REPOSITORY_URL) ?: SOURCE_REPOSITORY_URL) }
    var repositoryEntries by remember { mutableStateOf<List<SourceRepositoryEntry>>(emptyList()) }
    var selectedRepositoryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var repositoryLoading by remember { mutableStateOf(false) }
    var repositoryMessage by remember { mutableStateOf<String?>(null) }

    fun loadSourceRepository() {
        val requestedUrl = repositoryUrl.trim()
        val requestedUri = Uri.parse(requestedUrl)
        if (requestedUri.scheme != "https" || requestedUri.host.isNullOrBlank()) {
            repositoryMessage = "Link repositori harus menggunakan HTTPS."
            return
        }
        repositoryScope.launch {
            repositoryLoading = true
            repositoryMessage = "Mengambil katalog repositori…"
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(requestedUrl).openConnection().apply {
                        connectTimeout = 12000
                        readTimeout = 20000
                        useCaches = false
                        setRequestProperty("Cache-Control", "no-cache")
                        setRequestProperty("User-Agent", "GENJUR-STREAM")
                    }
                    val jsonText = connection.getInputStream().bufferedReader().use { it.readText() }.removePrefix("\uFEFF").trim()
                    val root = JSONObject(jsonText)
                    require(root.optInt("schemaVersion", 0) == 1) { "Versi format repositori tidak didukung." }
                    val entries = root.optJSONArray("sources") ?: error("Repositori tidak memiliki daftar sources.")
                    val parsed = mutableListOf<SourceRepositoryEntry>()
                    for (index in 0 until entries.length()) {
                        val item = entries.optJSONObject(index) ?: continue
                        val authorizedValue = item.opt("authorized")
                        val isAuthorized = authorizedValue == true || authorizedValue?.toString()?.equals("true", ignoreCase = true) == true
                        if (!isAuthorized) continue
                        val sourceName = item.optString("name").trim()
                        val sourceUrl = item.optString("url").trim()
                        val sourceUri = Uri.parse(sourceUrl)
                        if (sourceName.isBlank() || sourceUri.scheme != "https" || sourceUri.host.isNullOrBlank()) continue
                        val sourceId = item.optString("id").trim().ifBlank { sourceUri.host.orEmpty() }
                        parsed.add(SourceRepositoryEntry(sourceId, sourceName, sourceUrl,
                            item.optString("category").trim(), item.optString("description").trim(), item.optString("pluginId").trim()))
                    }
                    parsed.distinctBy { it.id.lowercase() }
                }
            }
            repositoryLoading = false
            result.onSuccess { entries ->
                repositoryEntries = entries
                selectedRepositoryIds = emptySet()
                prefs.edit().putString("source_repository_url", requestedUrl).apply()
                repositoryMessage = if (entries.isEmpty()) {
                    "Repositori berhasil dibaca, tetapi belum berisi sumber yang disetujui."
                } else "${entries.size} sumber tersedia. Pilih yang ingin ditambahkan."
            }.onFailure { error ->
                repositoryMessage = "Gagal membaca repositori: ${error.message ?: error.javaClass.simpleName}"
            }
        }
    }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            runCatching { refreshSavedSourceDomains(activity) }
        }
        result.onSuccess { changed ->
            links = readLinks()
            domainRefreshMessage = if (changed > 0) {
                "$changed alamat sumber berhasil diperbarui otomatis."
            } else {
                "Pemeriksaan domain otomatis selesai."
            }
        }.onFailure {
            domainRefreshMessage = "Pembaruan domain belum tersedia. Daftar sumber yang tersimpan tetap aman."
        }
    }

    LaunchedEffect(sourceSearch) {
        val term = sourceSearch.trim()
        searchResults = emptyList()
        searchError = null
        if (term.length < 2) {
            searching = false
        } else {
            searching = true
            delay(650)
            val result = runCatching { withContext(Dispatchers.IO) { searchWebSources(term) } }
            result.onSuccess { searchResults = it }
                .onFailure { error -> searchError = "Pencarian gagal: ${error.message ?: error.javaClass.simpleName}" }
            searching = false
        }
    }
    fun persist(items: List<SavedSourceLink>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("name", it.name).put("url", it.url)) }
        prefs.edit().putString("items", array.toString()).apply()
    }

    val readableFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedLabelColor = Color(0xFFD5DCEC),
        unfocusedLabelColor = Color(0xFFD5DCEC),
        focusedPlaceholderColor = Color(0xFFB8C2D4),
        unfocusedPlaceholderColor = Color(0xFFB8C2D4),
        focusedBorderColor = Green,
        unfocusedBorderColor = Color(0xFF77718A),
        cursorColor = Green
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("REPOSITORI SUMBER", color = Color.White, fontWeight = FontWeight.Bold)
        Text("Masukkan satu link katalog untuk melihat kumpulan sumber. Pilih sumber yang diizinkan untuk ditambahkan ke daftar APK.",
            color = Color(0xFFCBD2E0), fontSize = 12.sp)
        OutlinedTextField(
            value = repositoryUrl, onValueChange = { repositoryUrl = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Link repositori sumber (HTTPS)") },
            colors = readableFieldColors
        )
        Button(onClick = { loadSourceRepository() }, enabled = !repositoryLoading,
            colors = ButtonDefaults.buttonColors(containerColor = Green)) {
            Text(if (repositoryLoading) "Memuat repositori…" else "Muat repositori")
        }
        repositoryMessage?.let {
            Text(it, color = if (it.startsWith("Gagal") || it.startsWith("Link")) MaterialTheme.colorScheme.error else Muted, fontSize = 11.sp)
        }
        if (repositoryEntries.isNotEmpty()) {
            Text("Pilih sumber (${selectedRepositoryIds.size}/${repositoryEntries.size})",
                color = Color.White, fontWeight = FontWeight.Bold)
            repositoryEntries.forEach { entry ->
                val alreadySaved = links.any { it.url.equals(entry.url, true) }
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !alreadySaved) {
                        selectedRepositoryIds = if (entry.id in selectedRepositoryIds) selectedRepositoryIds - entry.id
                            else selectedRepositoryIds + entry.id
                    }.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = entry.id in selectedRepositoryIds || alreadySaved,
                        enabled = !alreadySaved,
                        onCheckedChange = { checked ->
                            selectedRepositoryIds = if (checked) selectedRepositoryIds + entry.id
                                else selectedRepositoryIds - entry.id
                        },
                        colors = CheckboxDefaults.colors(checkedColor = Green)
                    )
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(entry.url, color = Muted, fontSize = 10.sp, maxLines = 1)
                        if (entry.category.isNotBlank() || entry.description.isNotBlank()) {
                            Text(listOf(entry.category, entry.description).filter { it.isNotBlank() }.joinToString(" • "),
                                color = Muted, fontSize = 10.sp, maxLines = 2)
                        }
                    }
                    if (alreadySaved) Text("Sudah ada", color = Green, fontSize = 10.sp)
                }
            }
            Button(
                onClick = {
                    val chosen = repositoryEntries.filter { it.id in selectedRepositoryIds }
                    val additions = chosen.filterNot { item -> links.any { it.url.equals(item.url, true) } }
                    if (additions.isNotEmpty()) {
                        links = links + additions.map { SavedSourceLink(it.name, it.url) }
                        persist(links)
                        repositoryMessage = "${additions.size} sumber berhasil ditambahkan ke Daftar Sumber."
                    } else repositoryMessage = "Pilih sumber yang belum ada di Daftar Sumber."
                    selectedRepositoryIds = emptySet()
                },
                enabled = selectedRepositoryIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Green)
            ) { Text("Tambahkan pilihan ke Daftar Sumber") }
        }
        Divider(color = Muted.copy(alpha = 0.25f))
        Text("Cari alamat situs di internet. Hasil disaring dengan pemeriksaan dasar sebelum dipilih.",
            color = Color(0xFFCBD2E0), fontSize = 12.sp)
        OutlinedTextField(
            value = sourceSearch, onValueChange = { sourceSearch = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Cari situs atau nama domain") },
            placeholder = { Text("Ketik kata kunci pencarian") },
            colors = readableFieldColors
        )
        if (searching) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
            Text("Mencari di internet…", color = Color(0xFFCBD2E0), fontSize = 11.sp)
        }
        searchError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
        if (!searching && searchError == null && sourceSearch.trim().length >= 2) {
            if (searchResults.isEmpty()) {
                Text("Tidak ada hasil yang lolos penyaringan dasar.", color = Muted, fontSize = 11.sp)
            } else {
                Text("Hasil pencarian (${searchResults.size})", color = Color.White, fontWeight = FontWeight.Bold)
                searchResults.forEach { candidate ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Panel),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(candidate.title, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(candidate.host, color = Color(0xFFCBD2E0), fontSize = 11.sp)
                                Text("Lolos filter dasar — belum terverifikasi aman", color = Color(0xFFFFC857), fontSize = 10.sp)
                            }
                            TextButton(onClick = {
                                name = candidate.title
                                url = candidate.url
                                editingIndex = null
                                message = "Hasil dipilih. Periksa alamat sebelum menyimpan."
                            }) { Text("Pilih", color = Green) }
                        }
                    }
                }
            }
        }
        Divider(color = Muted.copy(alpha = 0.25f))
        Text("Tambah alamat situs secara manual",
            color = Color.White, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Nama sumber") },
            placeholder = { Text("Contoh: Situs Film Resmi") },
            colors = readableFieldColors
        )
        OutlinedTextField(
            value = url, onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text("Link situs (HTTPS)") },
            placeholder = { Text("https://contoh.com") },
            colors = readableFieldColors
        )
        message?.let { Text(it, color = if (it.startsWith("Tersimpan")) Green else MaterialTheme.colorScheme.error, fontSize = 11.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                val cleanName = name.trim()
                val cleanUrl = url.trim()
                val uri = Uri.parse(cleanUrl)
                when {
                    cleanName.isBlank() -> message = "Masukkan nama sumber."
                    uri.scheme != "https" || uri.host.isNullOrBlank() -> message = "Link harus berupa alamat HTTPS yang valid."
                    links.anyIndexed { index, item -> item.url.equals(cleanUrl, true) && index != editingIndex } ->
                        message = "Link tersebut sudah tersimpan."
                    else -> {
                        val updated = links.toMutableList()
                        val item = SavedSourceLink(cleanName, cleanUrl)
                        val index = editingIndex
                        if (index == null) updated.add(item) else updated[index] = item
                        links = updated
                        persist(links)
                        name = ""; url = ""; editingIndex = null
                        message = "Tersimpan."
                    }
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Green)) {
                Text(if (editingIndex == null) "Simpan link" else "Simpan perubahan")
            }
            if (editingIndex != null) {
                TextButton(onClick = { name = ""; url = ""; editingIndex = null; message = null }) {
                    Text("Batal", color = Muted)
                }
            }
        }
        Text(domainRefreshMessage, color = Muted, fontSize = 10.sp)
        Text("Daftar sumber (${links.size})", color = Color.White, fontWeight = FontWeight.Bold)
        if (links.isEmpty()) {
            Text("Belum ada link tersimpan.", color = Color(0xFFCBD2E0), fontSize = 12.sp)
        } else {
            val selectedIndex = selectedSavedSourceIndex?.takeIf { it in links.indices }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = { savedSourcesDropdownExpanded = !savedSourcesDropdownExpanded },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFF77718A))
                ) {
                    Text(
                        selectedIndex?.let { links[it].name } ?: "Pilih sumber (${links.size})",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start,
                        maxLines = 1
                    )
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = if (savedSourcesDropdownExpanded) "Tutup daftar sumber" else "Tampilkan daftar sumber"
                    )
                }
                AnimatedVisibility(
                    visible = savedSourcesDropdownExpanded,
                    enter = slideInVertically(initialOffsetY = { -it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it / 2 }) + fadeOut()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Panel),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column {
                            links.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        selectedSavedSourceIndex = index
                                        savedSourcesDropdownExpanded = false
                                        onOpenLink(item.url)
                                    }.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        item.name,
                                        modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    IconButton(onClick = {
                                        name = item.name
                                        url = item.url
                                        editingIndex = index
                                        message = null
                                        savedSourcesDropdownExpanded = false
                                    }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Edit sumber", tint = Green)
                                    }
                                    IconButton(onClick = {
                                        links = links.filterIndexed { i, _ -> i != index }
                                        persist(links)
                                        selectedSavedSourceIndex = null
                                        if (editingIndex == index) { name = ""; url = ""; editingIndex = null }
                                        message = "Link dihapus."
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Hapus sumber", tint = Muted)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text("Pilih nama untuk membuka situs, atau gunakan ikon pensil dan sampah untuk mengelola sumber.", color = Muted, fontSize = 11.sp)
        }
    }
}

private inline fun <T> List<T>.anyIndexed(predicate: (Int, T) -> Boolean): Boolean {
    forEachIndexed { index, item -> if (predicate(index, item)) return true }
    return false
}
@Composable
private fun SourceBrowser(
    initialUrl: String,
    fullScreen: Boolean = false,
    active: Boolean = true,
    onPlaybackStarted: (String, String) -> Unit,
    onPlayCandidate: (String, String, String) -> Unit,
    onDownloadCandidate: (String, String, String) -> Unit
) {
    val context = LocalContext.current
    val hostActivity = context as? ComponentActivity
    LaunchedEffect(hostActivity) {
        if (Build.VERSION.SDK_INT >= 33 && hostActivity != null &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            hostActivity.requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 8402)
        }
    }
    var playbackNotificationSent by remember(initialUrl) { mutableStateOf(false) }
    var sitePlaybackStarted by remember(initialUrl) { mutableStateOf(false) }
    var autoPlayerOpened by remember(initialUrl) { mutableStateOf(false) }
    val playbackGestureAt = remember(initialUrl) { AtomicLong(0L) }
    var address by remember(initialUrl) { mutableStateOf(initialUrl) }
    var currentUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("Masukkan alamat situs sumber video.") }
    var detectedVideoTitle by remember { mutableStateOf("") }
    var detectedVideoPage by remember { mutableStateOf("") }
    var detectedVideoOptions by remember { mutableStateOf<List<StreamOption>>(emptyList()) }
    var showDownloadChoices by remember { mutableStateOf(false) }
    var selectedDownloadOption by remember { mutableStateOf<StreamOption?>(null) }
    var isLoading by remember(initialUrl) { mutableStateOf(initialUrl.isNotBlank()) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var siteFullscreenView by remember { mutableStateOf<android.view.View?>(null) }
    var siteFullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    fun exitSiteFullscreen() {
        val activity = hostActivity
        val fullscreen = siteFullscreenView
        if (fullscreen != null) {
            (fullscreen.parent as? android.view.ViewGroup)?.removeView(fullscreen)
            siteFullscreenView = null
            siteFullscreenCallback?.onCustomViewHidden()
            siteFullscreenCallback = null
            if (activity != null) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.setDecorFitsSystemWindows(activity.window, true)
                WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { exitSiteFullscreen() }
    }
    val loadingFallback = remember { Runnable { isLoading = false } }
    fun stopSitePlayback() {
        webView?.evaluateJavascript(
            """(function(){
                try {
                    document.querySelectorAll('video,audio').forEach(function(media) {
                        try {
                            media.pause();
                            media.muted = true;
                            media.volume = 0;
                            media.removeAttribute('src');
                            media.querySelectorAll('source').forEach(function(source) { source.removeAttribute('src'); });
                            media.load();
                        } catch(e) {}
                    });
                    document.querySelectorAll('iframe').forEach(function(frame) {
                        try { frame.src = 'about:blank'; } catch(e) {}
                    });
                } catch(e) {}
            })();""",
            null
        )
        webView?.stopLoading()
        runCatching { webView?.settings?.blockNetworkLoads = true }
        webView?.onPause()
        webView?.pauseTimers()
    }
    LaunchedEffect(active) {
        if (active) {
            runCatching { webView?.settings?.blockNetworkLoads = false }
            webView?.resumeTimers()
            webView?.onResume()
        } else {
            stopSitePlayback()
        }
    }
    BackHandler(enabled = active && (siteFullscreenView != null || webView?.canGoBack() == true)) {
        if (siteFullscreenView != null) exitSiteFullscreen() else webView?.goBack()
    }
    Column(Modifier.fillMaxSize().padding(if (fullScreen) 0.dp else 8.dp), verticalArrangement = Arrangement.spacedBy(if (fullScreen) 4.dp else 8.dp)) {
        if (!fullScreen) Text("Buka situs sumber video di dalam GENJUR STREAM. Gunakan hanya situs dan video yang boleh kamu akses.", color = Muted, fontSize = 12.sp)
        if (!fullScreen) {
            OutlinedTextField(value = address, onValueChange = { address = it },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("https://contoh.com", color = Muted) },
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White, focusedBorderColor = Green,
                    unfocusedBorderColor = Color(0xFF393344), cursorColor = Green))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val entered = address.trim()
                    val normalized = if (entered.startsWith("https://", true) || entered.startsWith("http://", true)) entered else "https://" + entered
                    val uri = Uri.parse(normalized)
                    if ((uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrBlank()) {
                        currentUrl = normalized
                        webView?.loadUrl(normalized)
                    } else pageTitle = "Alamat situs tidak valid."
                }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Buka situs") }
                TextButton(onClick = { webView?.goBack() }, enabled = webView?.canGoBack() == true) { Text("Kembali", color = Green) }
                TextButton(onClick = { webView?.reload() }, enabled = currentUrl.isNotBlank()) { Text("Muat ulang", color = Green) }
            }
            Text(pageTitle, color = Muted, fontSize = 11.sp, maxLines = 2)
        }
        if (showDownloadChoices) {
            AlertDialog(
                onDismissRequest = { showDownloadChoices = false },
                title = { Text("Pilih resolusi / ukuran") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pilih kualitas yang tersedia dari situs, lalu tekan Download.", color = Muted, fontSize = 12.sp)
                        detectedVideoOptions.forEach { option ->
                            val isSelected = selectedDownloadOption?.videoUrl == option.videoUrl
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { selectedDownloadOption = option },
                                border = BorderStroke(1.dp, if (isSelected) Green else Color(0xFF514B5C)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) Green.copy(alpha = 0.14f) else Color.Transparent
                                )
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedDownloadOption = option },
                                        colors = RadioButtonDefaults.colors(selectedColor = Green)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(option.quality, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        Text(if (option.sizeBytes > 0) "Ukuran sekitar ${formatVideoSize(option.sizeBytes)}"
                                            else "Ukuran file belum tersedia dari situs",
                                            color = Muted, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = selectedDownloadOption != null,
                        onClick = {
                            val option = selectedDownloadOption
                            if (option != null) {
                                showDownloadChoices = false
                                onDownloadCandidate(detectedVideoTitle, detectedVideoPage, option.videoUrl)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White)
                    ) { Text("Download") }
                },
                dismissButton = {
                    TextButton(onClick = { showDownloadChoices = false }) { Text("Batal") }
                },
                containerColor = Panel,
                titleContentColor = Color.White,
                textContentColor = Color.White
            )
        }
        Box(
            modifier = if (fullScreen) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(420.dp),
            contentAlignment = Alignment.Center
        ) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    // Catat sentuhan pengguna di area WebView. Ini juga menangkap tombol
                    // Play yang berada di iframe dan tidak bisa dijangkau JS halaman utama.
                    setOnTouchListener { _, event ->
                        if (event.action == android.view.MotionEvent.ACTION_UP) {
                            playbackGestureAt.set(android.os.SystemClock.elapsedRealtime())
                        }
                        false
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Hilangkan penanda Android WebView agar Google tidak menyajikan
                    // halaman pencarian versi terbatas untuk embedded WebView.
                    settings.userAgentString = settings.userAgentString
                        .replace("; wv)", ")")
                        .replace(" Version/4.0", "")
                    settings.loadsImagesAutomatically = true
                    settings.mediaPlaybackRequiresUserGesture = true
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.setSupportMultipleWindows(false)
                    // Jangan gunakan progress WebView untuk indikator buffering.
                    // Iklan/iframe dapat mengubah progres setelah halaman utama selesai.
                    webChromeClient = object : WebChromeClient() {
                        override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) {
                            val activity = hostActivity
                            val decor = activity?.window?.decorView as? android.view.ViewGroup
                            if (activity == null || decor == null) {
                                callback.onCustomViewHidden()
                                return
                            }
                            if (siteFullscreenView != null) {
                                callback.onCustomViewHidden()
                                return
                            }
                            siteFullscreenCallback = callback
                            // Lepaskan custom video view dari parent asal sebelum dipindahkan.
                            (view.parent as? android.view.ViewGroup)?.removeView(view)
                            view.visibility = android.view.View.VISIBLE
                            val fullscreenContainer = android.widget.FrameLayout(activity).apply {
                                setBackgroundColor(android.graphics.Color.BLACK)
                                addView(view, android.widget.FrameLayout.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                ))
                            }
                            siteFullscreenView = fullscreenContainer
                            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                            WindowInsetsControllerCompat(activity.window, decor)
                                .apply {
                                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                    hide(WindowInsetsCompat.Type.systemBars())
                                }
                            decor.addView(fullscreenContainer, android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            ))
                            fullscreenContainer.bringToFront()
                        }

                        override fun onHideCustomView() {
                            exitSiteFullscreen()
                        }
                    }
                    // Tangkap unduhan dari situs di dalam aplikasi, bukan melemparkannya
                    // ke browser atau aplikasi unduhan eksternal.
                    setDownloadListener(DownloadListener { url, _, _, _, _ ->
                        val downloadUri = Uri.parse(url)
                        val downloadPath = url.lowercase()
                        val isVideoFile = Regex("""(?i)\.(m3u8|mpd|mp4|m4v|webm|mov|mkv)(?:$|[?#&])""").containsMatchIn(downloadPath)
                        val looksLikeAd = isGenjurAdHost(downloadUri.host) ||
                            Regex("""(?i)(/ads?/|advert|doubleclick|popunder|banner|tracking|analytics)""").containsMatchIn(downloadPath)
                        if (sitePlaybackStarted && url.isNotBlank() && isVideoFile && !looksLikeAd) {
                            onDownloadCandidate(
                                this.title?.takeIf { it.isNotBlank() } ?: "Video dari situs",
                                this.url?.takeIf { it.startsWith("http") } ?: currentUrl,
                                url
                            )
                        }
                    })
                    addJavascriptInterface(PlaybackStartedBridge(
                        onPlayback = { title, pageUrl -> post {
                            sitePlaybackStarted = true
                            if (!playbackNotificationSent) {
                                playbackNotificationSent = true
                                onPlaybackStarted(title, pageUrl)
                            }
                        } },
                        onAutoPlay = { title, pageUrl, mediaUrl -> post {
                            val uri = Uri.parse(mediaUrl)
                            if (!autoPlayerOpened && uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()) {
                                autoPlayerOpened = true
                                val safeTitle = title.ifBlank { "Video dari situs" }
                                onPlayCandidate(safeTitle, pageUrl, mediaUrl)
                            }
                        } },
                        onVideoDetected = { title, pageUrl, videoUrl, quality, sizeBytes ->
                            post {
                                val uri = Uri.parse(videoUrl)
                                val path = uri.path.orEmpty().lowercase()
                                val adaptiveStream = uri.path.orEmpty().lowercase().let { it.endsWith(".m3u8") || it.endsWith(".mpd") }
                                if (uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank() &&
                                    !isGenjurAdHost(uri.host) &&
                                    !Regex("""(?i)(/ads?/|advert|doubleclick|popunder|banner|tracking|analytics)""").containsMatchIn(videoUrl)) {
                                    if (!sitePlaybackStarted) return@post
                                    if (!autoPlayerOpened) {
                                        autoPlayerOpened = true
                                        onPlayCandidate(title.ifBlank { "Video dari situs" }, pageUrl, videoUrl)
                                    }
                                    detectedVideoTitle = title
                                    detectedVideoPage = pageUrl
                                    val label = detectedQualityLabel(quality, videoUrl)
                                    val item = StreamOption(label, videoUrl, sizeBytes)
                                    detectedVideoOptions = (detectedVideoOptions.filterNot {
                                        it.quality == label || it.videoUrl == videoUrl
                                    } + item)
                                }
                            }
                        }
                    ), "GenjurPlayback")
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): android.webkit.WebResourceResponse? {
                            val requestedUri = request.url
                            val mediaPath = requestedUri.path.orEmpty().lowercase()
                            val mediaUrlLower = requestedUri.toString().lowercase()
                            val mediaMatch = Regex("""\.(m3u8|mpd|mp4|m4v|webm|mov|mkv)(?:$|[?#&])""").containsMatchIn(mediaUrlLower)
                            if (requestedUri.scheme in listOf("http", "https") &&
                                !requestedUri.host.isNullOrBlank() && mediaMatch &&
                                !isGenjurAdHost(requestedUri.host) &&
                                !Regex("""(?i)(/ads?/|advert|doubleclick|popunder|banner|tracking|analytics)""").containsMatchIn(mediaUrlLower)) {
                                val label = detectedQualityLabel("", mediaUrlLower)
                                view.post {
                                    val mediaUrl = requestedUri.toString()
                                    if (!playbackNotificationSent) {
                                        playbackNotificationSent = true
                                        onPlaybackStarted(view.title?.takeIf { it.isNotBlank() } ?: "Video dari situs", view.url ?: currentUrl)
                                    }
                                    // Request media jaringan dapat berupa iklan pre-roll.
                                    // Jangan auto-play dari request pertama; tunggu metadata video HTML5
                                    // yang sudah lolos pemeriksaan durasi di GenjurPlayback.
                                    if (sitePlaybackStarted && detectedVideoOptions.none { it.videoUrl == mediaUrl }) {
                                        detectedVideoTitle = view.title?.takeIf { it.isNotBlank() } ?: "Video dari situs"
                                        detectedVideoPage = view.url ?: currentUrl
                                        detectedVideoOptions = (detectedVideoOptions.filterNot {
                                            it.quality == label || it.videoUrl == mediaUrl
                                        } + StreamOption(label, mediaUrl))
                                    }
                                }
                            }
                            if (!request.isForMainFrame && isGenjurAdHost(requestedUri.host)) {
                                return android.webkit.WebResourceResponse(
                                    "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
                                )
                            }
                            return super.shouldInterceptRequest(view, request)
                        }
                        override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                            playbackNotificationSent = false
                            sitePlaybackStarted = false
                            autoPlayerOpened = false
                            playbackGestureAt.set(0L)
                            isLoading = true
                            view.removeCallbacks(loadingFallback)
                            // Pengaman: jangan biarkan overlay menutupi situs tanpa batas waktu.
                            view.postDelayed(loadingFallback, 12000L)
                        }
                        override fun onPageCommitVisible(view: WebView, url: String) {
                            // Konten utama sudah mulai tampil; iklan/iframe tidak perlu menahan overlay.
                            view.removeCallbacks(loadingFallback)
                            isLoading = false
                        }
                        override fun onPageFinished(view: WebView, url: String) {
                            view.removeCallbacks(loadingFallback)
                            isLoading = false
                            pageTitle = view.title?.takeIf { it.isNotBlank() } ?: url
                            address = url
                            view.evaluateJavascript(
                                """(function(){
                                    // Coba lewati hanya tombol iklan yang jelas; jangan klik tombol play/download situs.
                                    if (!window.__genjurAdSkipTimer) {
                                        function trySkipAd() {
                                            try {
                                                var candidates = document.querySelectorAll(
                                                    'button, a, [role="button"], input[type="button"], input[type="submit"]'
                                                );
                                                candidates.forEach(function(el) {
                                                    if (!el || el.disabled || el.getAttribute('aria-hidden') === 'true') return;
                                                    var label = [
                                                        el.innerText, el.textContent, el.getAttribute('aria-label'),
                                                        el.getAttribute('title'), el.value
                                                    ].filter(Boolean).join(' ').replace(/\\s+/g, ' ').trim().toLowerCase();
                                                    if (!label || label.length > 45) return;
                                                    if (/^(skip ad|skip ads|lewati iklan|lewati iklannya|close ad|tutup iklan|skip advertisement)$/.test(label)) {
                                                        el.click();
                                                    }
                                                });
                                            } catch(e) {}
                                        }
                                        trySkipAd();
                                        window.__genjurAdSkipTimer = window.setInterval(trySkipAd, 800);
                                    }
                                    if (window.__genjurPlaybackHooked) return;
                                    window.__genjurPlaybackHooked = true;
                                    function reportMedia(title, url, quality, duration) {
                                        try {
                                            if (!url || !/^https?:\/\//i.test(url)) return;
                                            // Abaikan video pendek (umumnya iklan pre-roll 5–20 detik).
                                            if (typeof duration === 'number' && isFinite(duration) && duration > 0 && duration <= 25) return;
                                            window.__genjurMediaFound = true;
                                            var entry = performance.getEntriesByName(url).slice(-1)[0];
                                            var size = entry ? (entry.encodedBodySize || entry.transferSize || 0) : 0;
                                            GenjurPlayback.onVideoAvailable(title || document.title || 'Video dari situs', location.href, url, quality || 'Kualitas tersedia', size);
                                        } catch(e) {}
                                    }
                                    function reportLink(a) {
                                        try {
                                            if (/\.(m3u8|mpd|mp4|m4v|webm|mov|mkv)(?:$|[?#&])/i.test(a.href))
                                                reportMedia(a.textContent, a.href, a.getAttribute('data-quality') || 'Link video langsung');
                                        } catch(e) {}
                                    }
                                    function hookVideo(v) {
                                        if (v.__genjurPlaybackHooked) return;
                                        v.__genjurPlaybackHooked = true;
                                        function scanSources() {
                                            var title = document.title || 'Video dari situs';
                                            var sources = v.querySelectorAll('source[src]');
                                            sources.forEach(function(source) {
                                                var label = source.getAttribute('label') || source.getAttribute('data-quality') ||
                                                    source.getAttribute('size') || (v.videoHeight ? (v.videoHeight + 'p') : 'Kualitas tersedia');
                                                reportMedia(title, source.src, label, v.duration);
                                            });
                                            var media = v.currentSrc || v.src || '';
                                            if (media) {
                                                var quality = v.videoHeight ? (v.videoHeight + 'p') : 'Kualitas otomatis';
                                                reportMedia(title, media, quality, v.duration);
                                            }
                                        }
                                        v.addEventListener('loadedmetadata', scanSources);
                                        v.addEventListener('play', function() {
                                            try {
                                                var title = document.title || 'Video dari situs';
                                                var activeMedia = v.currentSrc || v.src || '';
                                                if (activeMedia) {
                                                    GenjurPlayback.onVideoPlay(title, location.href, new URL(activeMedia, location.href).href);
                                                } else {
                                                    GenjurPlayback.onVideoPlay(title, location.href, '');
                                                }
                                                scanSources();
                                            } catch(e) {}
                                        }, { once: true });
                                    }
                                    function scanPage() {
                                        try {
                                            document.querySelectorAll('video').forEach(hookVideo);
                                            document.querySelectorAll('a[href]').forEach(reportLink);
                                            document.querySelectorAll('source[src],iframe[src],[data-src],[data-video],[data-url]').forEach(function(el) {
                                                ['src','data-src','data-video','data-url'].forEach(function(attr) {
                                                    var candidate = el.getAttribute(attr);
                                                    if (candidate && (el.tagName === 'SOURCE' || /\.(m3u8|mpd|mp4|m4v|webm|mov|mkv)(?:$|[?#&])/i.test(candidate))) {
                                                        reportMedia(document.title, new URL(candidate, location.href).href, 'Sumber video ditemukan');
                                                    }
                                                });
                                            });
                                        } catch(e) {}
                                    }
                                    scanPage();
                                    // LK21: coba server/player berikutnya otomatis bila player aktif
                                    // belum memberikan URL media yang dapat diputar oleh GENJUR.
                                    (function autoTryNextLk21Server() {
                                        if ((location.hostname || '').toLowerCase().indexOf('lk21') === -1) return;
                                        var selector = 'button,a,[role="button"],input[type="button"],input[type="submit"],[data-server],[data-player]';
                                        function collect() {
                                            var result = [];
                                            document.querySelectorAll(selector).forEach(function(el) {
                                                var label = [el.innerText, el.textContent, el.getAttribute('aria-label'),
                                                    el.getAttribute('title'), el.value, el.getAttribute('data-server'),
                                                    el.getAttribute('data-player')].filter(Boolean).join(' ')
                                                    .replace(/\\s+/g, ' ').trim();
                                                if (!label || !/(player\\s*[1-9]|p2p|cast|hydrax|server\\s*[1-9])/i.test(label)) return;
                                                if (el.disabled || el.getAttribute('aria-hidden') === 'true') return;
                                                var state = ((el.className || '') + ' ' + (el.getAttribute('aria-pressed') || '') +
                                                    ' ' + (el.getAttribute('aria-selected') || '')).toLowerCase();
                                                if (/active|selected|current|true/.test(state)) return;
                                                if (result.every(function(x) { return x.el !== el; })) result.push({el:el,label:label});
                                            });
                                            return result;
                                        }
                                        var options = collect();
                                        var index = 0;
                                        function tryNext() {
                                            if (index >= options.length) return;
                                            var option = options[index++];
                                            try { option.el.click(); } catch(e) {}
                                            setTimeout(function() {
                                                tryNext();
                                            }, 6500);
                                        }
                                        if (options.length > 1) setTimeout(tryNext, 6500);
                                        new MutationObserver(function() {
                                            options = collect();
                                        }).observe(document.documentElement, {childList:true,subtree:true});
                                    })();
                                    var scanTimer = null;
                                    new MutationObserver(function(records) {
                                        var needsScan = false;
                                        records.forEach(function(record) {
                                            record.addedNodes.forEach(function(node) {
                                                if (node.nodeType !== 1) return;
                                                if (node.matches && node.matches('video')) hookVideo(node);
                                                if (node.matches && node.matches('a[href]')) reportLink(node);
                                                if (node.querySelectorAll) {
                                                    node.querySelectorAll('video').forEach(hookVideo);
                                                    node.querySelectorAll('a[href]').forEach(reportLink);
                                                }
                                                needsScan = true;
                                            });
                                            if (record.type === 'attributes') needsScan = true;
                                        });
                                        if (needsScan) {
                                            clearTimeout(scanTimer);
                                            scanTimer = setTimeout(scanPage, 120);
                                        }
                                    }).observe(document.documentElement, {
                                        childList: true, subtree: true, attributes: true,
                                        attributeFilter: ['src','data-src','data-video','data-url']
                                    });
                                    setTimeout(scanPage, 300);
                                })();""",
                                null
                            )
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                            if (request.isForMainFrame) {
                                view.removeCallbacks(loadingFallback)
                                isLoading = false
                            }
                        }
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val scheme = request.url.scheme
                            return scheme != "https" && scheme != "http"
                        }
                    }
                    webView = this
                }
            },
            update = { view ->
                webView = view
                if (currentUrl.isNotBlank() && view.tag != currentUrl) {
                    view.tag = currentUrl
                    isLoading = true
                    view.loadUrl(currentUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        // Floating download action over the source-site video player.
        if (sitePlaybackStarted && detectedVideoOptions.isNotEmpty()) {
            Button(
                onClick = {
                    selectedDownloadOption = detectedVideoOptions.firstOrNull()
                    showDownloadChoices = true
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
            ) {
                Icon(Icons.Filled.Download, contentDescription = "Unduh video situs")
            }
        }
        if (isLoading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = Green)
                Text("Memuat situs... Mohon tunggu", color = Color.White, fontSize = 13.sp)
            }
        }
        }
        if (!fullScreen) Text("Situs dengan DRM, login khusus, atau pemutar tanpa tautan video langsung mungkin tidak dapat dialihkan ke GENJUR Player.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun ProviderWebDialog(url: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(8.dp),
            shape = RoundedCornerShape(18.dp),
            color = Panel
        ) {
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Layanan streaming resmi", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Pemutaran melalui pemutar resmi situs jika didukung", color = Muted, fontSize = 10.sp)
                    }
                    TextButton(onClick = onClose) { Text("Tutup", color = Green) }
                }
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            settings.mediaPlaybackRequiresUserGesture = true
                            webChromeClient = WebChromeClient()
                            webViewClient = WebViewClient()
                            loadUrl(url)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                Text(
                    "GENJUR STREAM tidak mengambil URL video, melewati login, atau membuka DRM. Jika penyedia mewajibkan aplikasi resminya, pemutaran di sini mungkin tidak tersedia.",
                    color = Muted, fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun SourceFallbackPlayer(
    activity: MainActivity,
    movie: FeedMovie,
    onDownload: () -> Unit,
    onClose: () -> Unit
) {
    var fullscreenView by remember { mutableStateOf<android.view.View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<android.webkit.WebChromeClient.CustomViewCallback?>(null) }
    var detectedDirectUrl by remember(movie) { mutableStateOf("") }
    var sourcePageLoaded by remember(movie) { mutableStateOf(false) }
    val detectedServerUrls = remember(movie) { mutableStateListOf<String>() }
    val webViewRef = remember { mutableStateOf<android.webkit.WebView?>(null) }
    val isLandscape = LocalContext.current.resources.configuration.orientation ==
        android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Begitu proses pencarian sumber dimulai, langsung kunci perangkat ke landscape
    // dan sembunyikan system bar; jangan menunggu stream film ditemukan.
    SideEffect {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val bars = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        bars.hide(WindowInsetsCompat.Type.systemBars())
        bars.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    // Simpan URL media langsung yang benar-benar diminta player situs.
    // Halaman server/iframe biasa tidak diperlakukan sebagai URL video.
    fun addServerCandidate(candidate: String, durationSeconds: Double = 0.0) {
        val uri = Uri.parse(candidate)
        val path = uri.path.orEmpty().lowercase()
        val mediaUrlText = (path + "?" + uri.encodedQuery.orEmpty()).lowercase()
        val isPlayableMedia = path.endsWith(".m3u8") || path.endsWith(".mpd") ||
            path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".webm")
        // Tolak URL media yang jelas berasal dari jaringan iklan atau endpoint
        // pre-roll/VAST. Beberapa situs memakai host CDN berbeda untuk iklannya.
        val adPath = Regex("""(^|[/_.?&=-])(ads?|advert|advertisement|preroll|pre-roll|vast|vpaid|popunder|banner|sponsor|tracking)([/_.?&=-]|$)""")
            .containsMatchIn(mediaUrlText)
        if (uri.scheme !in listOf("https", "http") || uri.host.isNullOrBlank() ||
            !isPlayableMedia || (durationSeconds > 0.0 && durationSeconds < 45.0) || isGenjurAdHost(uri.host) || adPath) return
        if (detectedServerUrls.none { it == candidate }) detectedServerUrls.add(candidate)
    }

    // Hentikan player situs sebelum beralih ke ExoPlayer agar tidak ada dua
    // pemutar yang terus mengambil data secara bersamaan.
    fun stopSourceAndUseInternalPlayer(view: android.webkit.WebView, mediaUrl: String) {
        view.post {
            if (detectedDirectUrl.isNotBlank()) return@post
            runCatching {
                view.evaluateJavascript(
                    "(function(){document.querySelectorAll('video,audio').forEach(function(m){try{m.pause();m.removeAttribute('src');m.querySelectorAll('source').forEach(function(s){s.removeAttribute('src');});m.load();}catch(e){}});})();",
                    null
                )
            }
            runCatching { view.stopLoading() }
            runCatching { view.onPause() }
            runCatching { view.loadUrl("about:blank") }
            runCatching { view.removeAllViews() }
            webViewRef.value = null
            runCatching { view.destroy() }
            detectedDirectUrl = mediaUrl
        }
    }

    // Begitu metadata video berdurasi film tersedia, beri jeda singkat agar
    // player menyelesaikan pergantian dari iklan ke konten utama.
    // Effect tidak diulang untuk setiap URL baru agar waktu tunggu tidak terus mundur.
    LaunchedEffect(Unit) {
        while (detectedDirectUrl.isBlank()) {
            if (detectedServerUrls.isNotEmpty()) {
                delay(1800)
                val selected = detectedServerUrls.lastOrNull()
                val sourceView = webViewRef.value
                if (!selected.isNullOrBlank() && sourceView != null && detectedDirectUrl.isBlank()) {
                    stopSourceAndUseInternalPlayer(sourceView, selected)
                }
                break
            }
            delay(250)
        }
    }

    BackHandler(enabled = fullscreenView != null) {
        fullscreenCallback?.onCustomViewHidden()
    }

    DisposableEffect(Unit) {
        onDispose {
            fullscreenCallback?.onCustomViewHidden()
            webViewRef.value?.apply {
                stopLoading()
                loadUrl("about:blank")
                removeAllViews()
                destroy()
            }
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Satu jendela induk dipertahankan sepanjang perpindahan dari pencarian sumber
    // ke ExoPlayer. Dialog anak boleh berganti tanpa membuka katalog di belakangnya.
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val sourceDialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            sourceDialogWindow?.let { window ->
                window.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.MATCH_PARENT
                )
                window.setBackgroundDrawableResource(android.R.color.black)
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }
        }
        Surface(Modifier.fillMaxSize(), color = Color.Black) {
    // Jika halaman sumber menyediakan URL media HTML5 biasa yang dapat diakses,
    // pindahkan pemutaran ke ExoPlayer GENJUR. Jika tidak, tetap gunakan player situs.
    if (detectedDirectUrl.isNotBlank()) {
        InAppVideoPlayer(
            activity,
            movie.copy(videoUrl = detectedDirectUrl, licenseId = "Direct source media"),
            onDownload = onDownload,
            onClose = onClose,
            fallbackVideoUrls = detectedServerUrls.toList().filterNot { it == detectedDirectUrl }
        )
    } else {
        Dialog(
            onDismissRequest = onClose,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val searchingDialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect {
                searchingDialogWindow?.let { window ->
                    window.setLayout(
                        android.view.WindowManager.LayoutParams.MATCH_PARENT,
                        android.view.WindowManager.LayoutParams.MATCH_PARENT
                    )
                    window.setBackgroundDrawableResource(android.R.color.black)
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    WindowInsetsControllerCompat(window, window.decorView).apply {
                        hide(WindowInsetsCompat.Type.systemBars())
                        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(0.dp),
                color = Color.Black
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    AndroidView(
                        factory = { context ->
                            android.webkit.WebView(context).apply {
                                webViewRef.value = this
                                setBackgroundColor(android.graphics.Color.BLACK)
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadsImagesAutomatically = true
                                settings.mediaPlaybackRequiresUserGesture = true
                                settings.javaScriptCanOpenWindowsAutomatically = false
                                settings.setSupportMultipleWindows(false)
                                settings.userAgentString = settings.userAgentString
                                    .replace("; wv)", ")")
                                    .replace(" Version/4.0", "")
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                }
                                addJavascriptInterface(object {
                                    @JavascriptInterface
                                    fun onPlayableMedia(url: String, durationSeconds: Double) {
                                        // Hanya kandidat video berdurasi film yang diteruskan ke ExoPlayer.
                                        this@apply.post { addServerCandidate(url, durationSeconds) }
                                    }
                                    @JavascriptInterface
                                    fun onStartFilmPrompt() {
                                        // Prompt LK21 memerlukan sentuhan native, bukan click() JavaScript.
                                        this@apply.post {
                                            val target = this@apply
                                            if (target.width > 0 && target.height > 0) {
                                                val x = target.width / 2f
                                                val y = target.height / 2f
                                                val downTime = android.os.SystemClock.uptimeMillis()
                                                val down = android.view.MotionEvent.obtain(
                                                    downTime, downTime,
                                                    android.view.MotionEvent.ACTION_DOWN, x, y, 0
                                                )
                                                val up = android.view.MotionEvent.obtain(
                                                    downTime, android.os.SystemClock.uptimeMillis() + 70,
                                                    android.view.MotionEvent.ACTION_UP, x, y, 0
                                                )
                                                target.dispatchTouchEvent(down)
                                                target.dispatchTouchEvent(up)
                                                down.recycle()
                                                up.recycle()
                                            }
                                        }
                                    }
                                }, "GenjurMediaBridge")
                                webViewClient = object : android.webkit.WebViewClient() {
                                    override fun shouldInterceptRequest(
                                        view: android.webkit.WebView,
                                        request: android.webkit.WebResourceRequest
                                    ): android.webkit.WebResourceResponse? {
                                        // Jangan pilih request MP4/HLS mentah sebagai film.
                                        // Iklan pre-roll dapat memakai format yang sama; kandidat film
                                        // hanya berasal dari GenjurMediaBridge setelah durasi >= 45 detik.
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: android.webkit.WebView,
                                        request: android.webkit.WebResourceRequest
                                    ): Boolean {
                                        val uri = request.url
                                        val scheme = uri.scheme.orEmpty()
                                        if (scheme != "https" && scheme != "http") return true
                                        // Cegah redirect iklan yang mengganti halaman utama; iframe player tetap diizinkan.
                                        if (request.isForMainFrame) {
                                            val host = uri.host.orEmpty().lowercase()
                                            val isLk21 = host == "lk21official.cc" || host.endsWith(".lk21official.cc")
                                            if (!isLk21) return true
                                        }
                                        return false
                                    }

                                    override fun onPageFinished(view: android.webkit.WebView, url: String) {
                                        super.onPageFinished(view, url)
                                        // Halaman player sudah siap; jangan biarkan overlay pencarian
                                        // menutupi player situs ketika stream tidak bisa diekstrak.
                                        sourcePageLoaded = true
                                        val script = """
                                            (function() {
                                              // Klik otomatis Skip Ads LK21 segera setelah tombol aktif (termasuk label hitung mundur).
                                              function genjurTrySkipAds(doc) {
                                                try {
                                                  var nodes = doc.querySelectorAll('button, a, [role="button"], input[type="button"], input[type="submit"], [class*="skip"], [id*="skip"]');
                                                  nodes.forEach(function(el) {
                                                    if (!el || el.disabled || el.getAttribute('aria-disabled') === 'true' ||
                                                        el.getAttribute('aria-hidden') === 'true') return;
                                                    var label = [el.innerText, el.textContent, el.getAttribute('aria-label'),
                                                      el.getAttribute('title'), el.value, el.id, el.className]
                                                      .filter(Boolean).join(' ').replace(/\\s+/g, ' ').trim().toLowerCase();
                                                    if (!label || label.length > 100) return;
                                                    if (/\\bskip\\s*ads?\\b|\\bskip\\s+advertisement\\b|lewati\\s+iklan/.test(label)) {
                                                      try { el.click(); } catch(e) {}
                                                    }
                                                  });
                                                  Array.prototype.forEach.call(doc.querySelectorAll('iframe'), function(frame) {
                                                    try { if (frame.contentDocument) genjurTrySkipAds(frame.contentDocument); } catch(e) {}
                                                  });
                                                } catch(e) {}
                                              }
                                              if (!window.__genjurSkipAdsTimer) {
                                                genjurScanFrames(document);
                                                window.__genjurSkipAdsTimer = window.setInterval(function() {
                                                  genjurScanFrames(document);
                                                }, 350);
                                              }
                                              // Setelah Skip Ads, LK21 meminta satu ketukan pada area player.
                                              // Kirim satu klik di tengah player hanya setelah pesan mulai film terlihat.
                                              function genjurTryStartFilm(doc) {
                                                try {
                                                  if (doc.__genjurFilmStartRequested) return;
                                                  var bodyText = (doc.body && (doc.body.innerText || doc.body.textContent) || '').toLowerCase();
                                                  var asksToStart = /klik\\s+di\\s+mana\\s+saja\\s+untuk\\s+memulai\\s+film|klik\\s+dimana\\s+saja\\s+untuk\\s+memulai\\s+film|click\\s+anywhere\\s+to\\s+start\\s+(the\\s+)?(movie|film|video)/i.test(bodyText);
                                                  if (!asksToStart) return;
                                                  // Minta Android mengirim satu sentuhan native ke tengah WebView.
                                                  if (window.GenjurMediaBridge && GenjurMediaBridge.onStartFilmPrompt) {
                                                    GenjurMediaBridge.onStartFilmPrompt();
                                                    doc.__genjurFilmStartRequested = true;
                                                  }
                                                } catch(e) {}
                                              }
                                              function genjurScanFrames(doc) {
                                                try {
                                                  genjurTrySkipAds(doc);
                                                  genjurTryStartFilm(doc);
                                                  Array.prototype.forEach.call(doc.querySelectorAll('iframe'), function(frame) {
                                                    try { if (frame.contentDocument) genjurScanFrames(frame.contentDocument); } catch(e) {}
                                                  });
                                                } catch(e) {}
                                              }
                                              function attach(media) {
                                                if (!media || media.dataset.genjurBridge === '1') return;
                                                media.dataset.genjurBridge = '1';
                                                function report() {
                                                  var source = media.currentSrc || media.src ||
                                                    (media.querySelector('source') && media.querySelector('source').src) || '';
                                                  if (source && /^https?:/i.test(source) &&
                                                      /\\.(mp4|m4v|webm|m3u8)(?:$|[?#])/i.test(source) &&
                                                      Number.isFinite(media.duration) && media.duration >= 45) {
                                                    try { GenjurMediaBridge.onPlayableMedia(source, media.duration); } catch(e) {}
                                                  }
                                                }
                                                media.addEventListener('loadedmetadata', report);
                                                media.addEventListener('durationchange', report);
                                                media.addEventListener('playing', report);
                                                report();
                                              }
                                              document.querySelectorAll('video,audio').forEach(attach);
                                              new MutationObserver(function() {
                                                document.querySelectorAll('video,audio').forEach(attach);
                                              }).observe(document.documentElement || document, {childList:true,subtree:true});
                                            })();
                                        """.trimIndent()
                                        view.evaluateJavascript(script, null)
                                    }
                                }
                                webChromeClient = object : android.webkit.WebChromeClient() {
                                    override fun onShowCustomView(
                                        view: android.view.View,
                                        callback: android.webkit.WebChromeClient.CustomViewCallback
                                    ) {
                                        val decor = view.rootView as? android.view.ViewGroup
                                        if (decor == null || fullscreenView != null) {
                                            callback.onCustomViewHidden()
                                            return
                                        }
                                        (view.parent as? android.view.ViewGroup)?.removeView(view)
                                        fullscreenView = view
                                        fullscreenCallback = callback
                                        decor.addView(view, android.view.ViewGroup.LayoutParams(
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                        ))
                                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                                        WindowInsetsControllerCompat(activity.window, decor)
                                            .hide(WindowInsetsCompat.Type.systemBars())
                                    }

                                    override fun onHideCustomView() {
                                        val decor = fullscreenView?.rootView as? android.view.ViewGroup
                                        fullscreenView?.let { view -> decor?.removeView(view) }
                                        fullscreenView = null
                                        fullscreenCallback = null
                                        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                                        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
                                            hide(WindowInsetsCompat.Type.systemBars())
                                            systemBarsBehavior =
                                                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                        }
                                    }
                                }
                                loadUrl(movie.termsUrl.ifBlank { movie.videoUrl })
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { webViewRef.value = it }
                    )
                    if (!sourcePageLoaded) {
                        Box(
                            Modifier.fillMaxSize().background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(color = Green, strokeWidth = 3.dp)
                                Spacer(Modifier.height(18.dp))
                                Text(
                                    "Mencari sumber video…",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(Modifier.height(7.dp))
                                Text(
                                    "Menyiapkan halaman pemutar film.",
                                    color = Muted,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
        }
    }
    }

@Composable
private fun InAppVideoPlayer(
    activity: MainActivity,
    movie: FeedMovie,
    onDownload: () -> Unit,
    onClose: () -> Unit,
    fallbackVideoUrls: List<String> = emptyList()
) {
    // Player selalu dibuka langsung dalam mode landscape layar penuh.
    var isFullscreen by remember { mutableStateOf(true) }
    var landscapeControlsVisible by remember { mutableStateOf(true) }
    var landscapePlayerView by remember { mutableStateOf<PlayerView?>(null) }
    SideEffect {
        activity.requestedOrientation = if (isFullscreen) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val activityBars = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (isFullscreen) {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            activityBars.hide(WindowInsetsCompat.Type.systemBars())
            activityBars.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            activityBars.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        }
    }
    // -1 = Otomatis: menyesuaikan rasio video dengan layar landscape.
    var videoResizeMode by remember { mutableIntStateOf(-1) }
    var videoAspectRatio by remember { mutableFloatStateOf(0f) }
    var playerAreaAspectRatio by remember { mutableFloatStateOf(0f) }
    // Auto compares the actual video ratio with the available player area.
    // If they are close, fill the screen; if they differ significantly,
    // show the complete frame rather than cropping too much.
    val effectiveResizeMode = when (videoResizeMode) {
        -1 -> if (videoAspectRatio > 0f && playerAreaAspectRatio > 0f &&
            kotlin.math.abs(videoAspectRatio / playerAreaAspectRatio - 1f) > 0.12f
        ) AspectRatioFrameLayout.RESIZE_MODE_FIT
          else AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> videoResizeMode
    }
    val videoResizeLabel = when (videoResizeMode) {
        -1 -> "Auto"
        AspectRatioFrameLayout.RESIZE_MODE_FIT -> "Pas"
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Regang"
        else -> "Penuh"
    }
    val player = remember(movie.videoUrl) {
        val requestHeaders = buildMap {
            put("User-Agent", "Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
            if (movie.termsUrl.startsWith("https://")) {
                put("Referer", movie.termsUrl)
                Uri.parse(movie.termsUrl).host?.let { put("Origin", "https://$it") }
            }
        }
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(requestHeaders["User-Agent"]!!)
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(requestHeaders)
        ExoPlayer.Builder(activity)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build().apply {
            val mediaItem = MediaItem.Builder().setUri(movie.videoUrl)
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(movie.title)
                        .setArtist("GENJUR STREAM")
                        .build()
                ).apply {
                if (movie.subtitleUrl.isNotBlank()) {
                    val subtitleUri = Uri.parse(movie.subtitleUrl)
                    val mimeType = if (subtitleUri.path.orEmpty().lowercase().endsWith(".vtt"))
                        MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
                    setSubtitleConfigurations(listOf(
                        MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                            .setMimeType(mimeType)
                            .setLanguage(movie.subtitleLanguage.ifBlank { "id" })
                            .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                            .build()
                    ))
                }
            }.build()
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player, fallbackVideoUrls) {
        // Jika server aktif gagal, coba URL media alternatif yang ditemukan
        // langsung dari player situs. Setiap kandidat hanya dicoba satu kali.
        val attemptedServerUrls = mutableSetOf(movie.videoUrl)
        val shortAdSkippedUrls = mutableSetOf<String>()
        fun tryNextServer(reason: String): Boolean {
            val nextServer = fallbackVideoUrls.firstOrNull {
                it.isNotBlank() && it !in attemptedServerUrls
            } ?: return false
            attemptedServerUrls.add(nextServer)
            android.util.Log.w("GENJUR_PLAYER", "$reason; otomatis mencoba server alternatif nomor " + attemptedServerUrls.size)
            player.setMediaItem(MediaItem.fromUri(nextServer))
            player.prepare()
            player.playWhenReady = true
            return true
        }
        fun skipShortPreRollIfNeeded() {
            val durationMs = player.duration
            val currentUrl = player.currentMediaItem?.localConfiguration?.uri?.toString().orEmpty()
            // Iklan pembuka LK21 dapat berdurasi hingga sekitar 45 detik.
            // Periksa lagi di ExoPlayer karena metadata HTML kadang belum tersedia
            // saat URL pertama kali terdeteksi dari permintaan jaringan.
            if (durationMs in 1L..45_000L && currentUrl.isNotBlank() &&
                currentUrl !in shortAdSkippedUrls) {
                shortAdSkippedUrls.add(currentUrl)
                if (!tryNextServer("Terdeteksi klip pendek ${durationMs / 1000} detik (kemungkinan iklan)")) {
                    android.util.Log.w("GENJUR_PLAYER", "Klip pendek terdeteksi tetapi tidak ada server alternatif.")
                }
            }
        }
        val fallbackListener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                if (videoSize.height > 0) {
                    videoAspectRatio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }
            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                skipShortPreRollIfNeeded()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) skipShortPreRollIfNeeded()
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                if (!tryNextServer("Server gagal")) {
                    android.util.Log.e("GENJUR_PLAYER", "Semua server langsung yang terdeteksi gagal diputar", error)
                }
            }
        }
        player.addListener(fallbackListener)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(activity, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                8402
            )
        }
        val playbackNotification = PlayerNotificationManager.Builder(
            activity,
            PLAYBACK_NOTIFICATION_ID,
            PLAYBACK_NOTIFICATION_CHANNEL
        )
            .setSmallIconResourceId(android.R.drawable.ic_media_play)
            .setMediaDescriptionAdapter(object : PlayerNotificationManager.MediaDescriptionAdapter {
                override fun getCurrentContentTitle(player: Player): CharSequence = movie.title
                override fun createCurrentContentIntent(player: Player): PendingIntent? = null
                override fun getCurrentContentText(player: Player): CharSequence = "Sedang diputar di GENJUR STREAM"
                override fun getCurrentLargeIcon(
                    player: Player,
                    callback: PlayerNotificationManager.BitmapCallback
                ): android.graphics.Bitmap? = null
            })
            .build().apply {
                setUseNextAction(false)
                setUsePreviousAction(false)
                setUsePlayPauseActions(true)
                setUseStopAction(true)
                setPlayer(player)
            }
        onDispose {
            player.removeListener(fallbackListener)
            playbackNotification.setPlayer(null)
            player.release()
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
    fun requestPlayerDownload() {
        // Hentikan sementara pemutaran sebelum mesin unduhan mengambil stream yang sama.
        // Ini mencegah dua proses media mengakses sumber secara bersamaan pada perangkat
        // dengan memori terbatas; pemutaran dapat dilanjutkan dari kontrol player.
        player.pause()
        runCatching { onDownload() }.onFailure { error ->
            android.util.Log.e("GENJUR_DOWNLOAD", "Tombol unduh di player gagal", error)
        }
    }
    fun setFullscreen(enabled: Boolean) {
        isFullscreen = enabled
        activity.requestedOrientation = if (enabled) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (enabled) {
            WindowCompat.setDecorFitsSystemWindows(activity.window, false)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(activity.window, true)
        }
    }

    // Tombol kembali Android keluar dari mode layar penuh terlebih dahulu.
    BackHandler(enabled = true) {
        onClose()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Ambil window dialog yang benar: pada Android, menyembunyikan system bar
        // hanya pada activity window tidak selalu membuat dialog ikut edge-to-edge.
        val playerDialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            playerDialogWindow?.let { window ->
                if (isFullscreen) {
                    // Paksa window dialog menutup seluruh area layar landscape.
                    // Tanpa ini, beberapa perangkat masih menyisakan sisi layar
                    // yang memperlihatkan halaman detail di belakang player.
                    window.setLayout(
                        android.view.WindowManager.LayoutParams.MATCH_PARENT,
                        android.view.WindowManager.LayoutParams.MATCH_PARENT
                    )
                    window.setBackgroundDrawableResource(android.R.color.black)
                }
                WindowCompat.setDecorFitsSystemWindows(window, !isFullscreen)
                val bars = WindowInsetsControllerCompat(window, window.decorView)
                if (isFullscreen) {
                    bars.hide(WindowInsetsCompat.Type.systemBars())
                    bars.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    bars.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            color = Color.Black
        ) {
            if (isFullscreen) {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                this.player = player
                                useController = true
                                controllerShowTimeoutMs = 4500
                                setControllerVisibilityListener(
                                    PlayerView.ControllerVisibilityListener { visibility: Int ->
                                        landscapeControlsVisible = visibility == android.view.View.VISIBLE
                                    }
                                )
                                landscapePlayerView = this
                                // Perbesar video agar memenuhi layar landscape; sisi video
                                // yang terpotong mengikuti rasio layar, tanpa meregangkan gambar.
                                resizeMode = effectiveResizeMode
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            }
                        },
                        update = {
                            it.player = player
                            it.resizeMode = effectiveResizeMode
                            it.setControllerVisibilityListener(
                                PlayerView.ControllerVisibilityListener { visibility: Int ->
                                    landscapeControlsVisible = visibility == android.view.View.VISIBLE
                                }
                            )
                            landscapePlayerView = it
                        },
                        modifier = Modifier.fillMaxSize().onSizeChanged { size ->
                            if (size.height > 0) playerAreaAspectRatio = size.width.toFloat() / size.height
                        }
                    )
                    if (landscapeControlsVisible) {
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            videoResizeMode = when (videoResizeMode) {
                                -1 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                else -> -1
                            }
                        }) {
                            Text(videoResizeLabel, color = Color.White)
                        }
                        IconButton(onClick = { requestPlayerDownload() }) {
                            Icon(Icons.Filled.Download, contentDescription = "Unduh video", tint = Color.White)
                        }
                        IconButton(onClick = {
                            requestPlayerDownload()
                            landscapePlayerView?.showController()
                        }) {
                            Icon(Icons.Filled.Download, contentDescription = "Unduh video", tint = Color.White)
                        }
                    }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().background(Panel).padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(movie.title, color = Color.White, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f), maxLines = 1)
                        IconButton(onClick = { requestPlayerDownload() }) {
                            Icon(Icons.Filled.Download, contentDescription = "Unduh video", tint = Green)
                        }
                        IconButton(onClick = { setFullscreen(true) }) {
                            Icon(Icons.Filled.Fullscreen, contentDescription = "Full screen", tint = Green)
                        }
                        TextButton(onClick = onClose) { Text("Tutup", color = Green) }
                    }
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                this.player = player
                                useController = true
                                resizeMode = effectiveResizeMode
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            }
                        },
                        update = { it.player = player },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )
                    Text(
                        if (movie.subtitleUrl.isNotBlank()) "Subtitle: ${movie.subtitleLanguage.ifBlank { "Indonesia" }} (aktifkan CC di pemutar)" else "Subtitle eksternal belum tersedia untuk judul ini.",
                        color = Muted, fontSize = 11.sp
                    )
                }
            }
        }
    }
}

private data class SavedRepository(val name: String, val url: String)
private data class RepositoryExtension(
    val id: String,
    val name: String,
    val description: String,
    val feedUrl: String,
    val repositoryUrl: String,
    val pluginUrl: String = "",
    val language: String = "",
    val tvTypes: String = "",
    val isCloudStream: Boolean = false,
    val subtitleLanguages: String = "",
    val contentTypes: String = "",
    val websiteUrl: String = "",
    val isExternal: Boolean = false,
    val freeNote: String = ""
)

@Composable
private fun GenjurStreamApp(activity: MainActivity) {
    var showSplash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(3500)
        showSplash = false
    }
    Box(modifier = Modifier.fillMaxSize()) {
        GenjurStreamHome(activity)
        AnimatedVisibility(
            visible = showSplash,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(550))
        ) {
            GenjurSplashScreen()
        }
    }
}

@Composable
private fun GenjurSplashScreen() {
    val transition = rememberInfiniteTransition(label = "cinematic_intro")
    val filmProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "film_strip_motion"
    )
    val glowAlpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cinematic_glow"
    )
    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colors = listOf(Color(0xFF03040A), Color(0xFF0B1022), Color(0xFF03040A))
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        // Poster film sungguhan bergerak di dalam pita klise.
        val moviePosters = listOf(
            "https://image.tmdb.org/t/p/w342/or06FN3Dka5tukK1e9sl16pB3iy.jpg", // Avengers: Endgame
            "https://image.tmdb.org/t/p/w342/1g0dhYtq4irTY1GPXvft6k4YLjm.jpg", // Spider-Man: No Way Home
            "https://image.tmdb.org/t/p/w342/ombsmhYUqR4qqOLOxAyr5V8hbyv.jpg", // Superman
            "https://image.tmdb.org/t/p/w342/qJ2tW6WMUDux911r6m7haRef0WH.jpg", // Batman: The Dark Knight
            "https://image.tmdb.org/t/p/w342/74xTEgt7R36Fpooo50r9T25onhq.jpg", // The Batman
            "https://image.tmdb.org/t/p/w342/7WsyChQLEftFiDOVTGkum3p6vs.jpg" // Avengers: Infinity War
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(164.dp)
                .clipToBounds()
                .background(Color(0xFF080D1B))
        ) {
            Row(
                modifier = Modifier
                    .width((moviePosters.size * 120 * 3).dp)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = -filmProgress * 112.dp.toPx() },
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) {
                    moviePosters.forEachIndexed { index, posterUrl ->
                        Box(
                            modifier = Modifier
                                .width(112.dp)
                                .fillMaxHeight()
                                .padding(vertical = 13.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFF18233A))
                                .border(
                                    BorderStroke(1.dp, Color(0xFF20C8F5).copy(alpha = 0.55f)),
                                    RoundedCornerShape(7.dp)
                                )
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(posterUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Poster film superhero nomor ${index + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
            // Lubang perforasi di sisi atas dan bawah pita film.
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(9.dp).background(Color(0xFF050811)),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(12) {
                        Box(Modifier.size(13.dp, 5.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF9CA9C8).copy(alpha = 0.55f)))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(9.dp).background(Color(0xFF050811)),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(12) {
                        Box(Modifier.size(13.dp, 5.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF9CA9C8).copy(alpha = 0.55f)))
                    }
                }
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF03040A).copy(alpha = 0.12f), Color.Transparent, Color(0xFF03040A).copy(alpha = 0.12f))
                    )
                )
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .graphicsLayer(alpha = glowAlpha)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF9B35FF), Color(0xFF176BFF), Color(0xFF00D5F2))
                        ),
                        RoundedCornerShape(30.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "GENJUR STREAM",
                    tint = Color.White,
                    modifier = Modifier.size(76.dp)
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = "GENJUR",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 4.sp
            )
            Text(
                text = "STREAM",
                color = Color(0xFF19C8F5),
                fontSize = 27.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 7.sp
            )
            Spacer(modifier = Modifier.height(13.dp))
            Text(
                text = "YOUR CINEMATIC WORLD",
                color = Color(0xFFB9C4D8),
                fontSize = 10.sp,
                letterSpacing = 3.sp
            )
        }
    }
}

@Composable
private fun RepositoryDialog(activity: MainActivity, onOpenWebsite: (String) -> Unit, onClose: () -> Unit) {
    val prefs = activity.getSharedPreferences("genjur_stream_sources", 0)
    fun readRepositories(): List<SavedRepository> = runCatching {
        val a = JSONArray(prefs.getString("repositories", "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            SavedRepository(o.optString("name"), o.optString("url"))
        }
    }.getOrDefault(emptyList())
    fun readInstalled(): List<RepositoryExtension> = runCatching {
        val a = JSONArray(prefs.getString("installed_extensions", "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            RepositoryExtension(o.optString("id"), o.optString("name"),
                o.optString("description"), o.optString("feedUrl"), o.optString("repositoryUrl"),
                o.optString("pluginUrl"), o.optString("language"), o.optString("tvTypes"),
                o.optBoolean("isCloudStream", false), o.optString("subtitleLanguages"),
                o.optString("contentTypes"), o.optString("websiteUrl"),
                o.optBoolean("isExternal", false), o.optString("freeNote"))
        }
    }.getOrDefault(emptyList())

    var repositories by remember { mutableStateOf(readRepositories()) }
    var installed by remember { mutableStateOf(readInstalled()) }
    var discovered by remember { mutableStateOf(emptyList<RepositoryExtension>()) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun saveRepositories() {
        val a = JSONArray()
        repositories.forEach { a.put(JSONObject().put("name", it.name).put("url", it.url)) }
        prefs.edit().putString("repositories", a.toString()).apply()
    }
    fun saveInstalled() {
        val a = JSONArray()
        installed.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)
            .put("description", it.description).put("feedUrl", it.feedUrl)
            .put("repositoryUrl", it.repositoryUrl).put("pluginUrl", it.pluginUrl)
            .put("language", it.language).put("tvTypes", it.tvTypes)
            .put("isCloudStream", it.isCloudStream).put("subtitleLanguages", it.subtitleLanguages)
            .put("contentTypes", it.contentTypes).put("websiteUrl", it.websiteUrl)
            .put("isExternal", it.isExternal).put("freeNote", it.freeNote)) }
        prefs.edit().putString("installed_extensions", a.toString()).apply()
    }
    fun loadRepository(repository: SavedRepository) {
        scope.launch {
            loading = true
            error = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(repository.url)
                    require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "URL repositori harus HTTPS." }
                    val freshUrl = repository.url + (if (repository.url.contains("?")) "&" else "?") + "genjur_refresh=" + System.currentTimeMillis()
                    val connection = URL(freshUrl).openConnection()
                    connection.useCaches = false
                    connection.setRequestProperty("Cache-Control", "no-cache, no-store")
                    connection.setRequestProperty("Pragma", "no-cache")
                    connection.connectTimeout = 12000
                    connection.readTimeout = 20000
                    val root = JSONObject(connection.getInputStream().bufferedReader().use { it.readText() })
                    val parsed = mutableListOf<RepositoryExtension>()
                    if (root.optInt("schemaVersion", 0) == 1) {
                        val array = root.optJSONArray("extensions")
                            ?: error("Repositori GENJUR tidak memiliki daftar extensions.")
                        for (i in 0 until array.length()) {
                            val item = array.optJSONObject(i) ?: continue
                            val id = item.optString("id").trim()
                            val extName = item.optString("name").trim()
                            val feed = item.optString("feedUrl").trim()
                            val feedUri = Uri.parse(feed)
                            val website = item.optString("websiteUrl").trim()
                            val websiteUri = Uri.parse(website)
                            val isExternal = websiteUri.scheme == "https" && !websiteUri.host.isNullOrBlank()
                            val validFeed = feedUri.scheme == "https" && !feedUri.host.isNullOrBlank()
                            require(id.isNotBlank() && extName.isNotBlank() && (validFeed || isExternal)) {
                                "Penyedia ditolak: isi feedUrl atau websiteUrl HTTPS yang valid."
                            }
                            val subtitleArray = item.optJSONArray("subtitleLanguages")
                            val subtitleLanguages = if (subtitleArray == null) "" else
                                (0 until subtitleArray.length()).map { subtitleArray.optString(it).trim() }
                                    .filter { it.isNotBlank() }.distinct().joinToString(", ")
                            val typeArray = item.optJSONArray("contentTypes")
                            val contentTypes = if (typeArray == null) "" else
                                (0 until typeArray.length()).map { typeArray.optString(it).trim() }
                                    .filter { it.isNotBlank() }.distinct().joinToString(", ")
                            parsed.add(RepositoryExtension(id, extName,
                                item.optString("description", "Penyedia katalog film."),
                                feed, repository.url, language = item.optString("language"),
                                subtitleLanguages = subtitleLanguages, contentTypes = contentTypes,
                                websiteUrl = website, isExternal = isExternal,
                                freeNote = item.optString("freeNote")))
                        }
                    } else if (root.optInt("manifestVersion", 0) == 1) {
                        val pluginLists = root.optJSONArray("pluginLists")
                            ?: error("Manifest CloudStream tidak memiliki pluginLists.")
                        for (listIndex in 0 until pluginLists.length()) {
                            val listUrl = pluginLists.optString(listIndex).trim()
                            val listUri = Uri.parse(listUrl)
                            require(listUri.scheme == "https" && !listUri.host.isNullOrBlank()) {
                                "URL daftar plugin CloudStream harus HTTPS."
                            }
                            val listConnection = URL(listUrl).openConnection()
                            listConnection.connectTimeout = 12000
                            listConnection.readTimeout = 20000
                            val plugins = JSONArray(listConnection.getInputStream().bufferedReader().use { it.readText() })
                            for (pluginIndex in 0 until plugins.length()) {
                                val item = plugins.optJSONObject(pluginIndex) ?: continue
                                val pluginName = item.optString("name").trim()
                                val pluginUrl = item.optString("url").trim()
                                val pluginUri = Uri.parse(pluginUrl)
                                if (pluginName.isBlank() || pluginUri.scheme != "https" ||
                                    pluginUri.host.isNullOrBlank()) continue
                                val internalName = item.optString("internalName").ifBlank { pluginName }
                                val types = item.optJSONArray("tvTypes")
                                val typeNames = if (types == null) "" else
                                    (0 until types.length()).map { types.optString(it) }.filter { it.isNotBlank() }.distinct().joinToString(", ")
                                val language = item.optString("language").trim()
                                parsed.add(RepositoryExtension(
                                    "cloudstream:$internalName:$pluginUrl",
                                    pluginName,
                                    "Plugin CloudStream • ${language.ifBlank { "bahasa tidak dicantumkan" }}",
                                    "",
                                    repository.url,
                                    pluginUrl,
                                    language,
                                    typeNames,
                                    true,
                                    item.optJSONArray("subtitleLanguages")?.let { a ->
                                        (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.joinToString(", ")
                                    }.orEmpty(),
                                    typeNames
                                ))
                            }
                        }
                    } else {
                        error("Format repositori tidak dikenal. Mendukung repositori GENJUR schemaVersion 1 dan daftar metadata CloudStream (plugin .cs3 tidak dijalankan).")
                    }
                    parsed
                }
            }
            loading = false
            result.onSuccess { discovered = it }
                .onFailure { error = it.message ?: "Gagal membaca repositori." }
        }
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Repositori Penyedia Film") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Tambahkan repositori untuk menemukan penyedia film. Daftar dapat berisi katalog feed GENJUR atau tautan ke layanan streaming resmi. GENJUR STREAM tidak menjalankan kode/plugin pihak ketiga.",
                    color = Muted, fontSize = 12.sp)
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Nama repositori") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it },
                    label = { Text("URL repositori") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                if (repositories.isNotEmpty()) {
                    Text("Repositori tersimpan", color = Color.White, fontWeight = FontWeight.Bold)
                    repositories.forEach { repo ->
                        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(repo.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(repo.url, color = Muted, fontSize = 10.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { loadRepository(repo) }) { Text("Lihat penyedia") }
                                    TextButton(onClick = {
                                        repositories = repositories.filterNot { it.url == repo.url }
                                        discovered = discovered.filterNot { it.repositoryUrl == repo.url }
                                        installed = installed.filterNot { it.repositoryUrl == repo.url }
                                        saveRepositories(); saveInstalled()
                                    }) { Text("Hapus") }
                                }
                            }
                        }
                    }
                }
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Green)
                if (discovered.isNotEmpty()) {
                    Text("Daftar penyedia (${discovered.size})", color = Color.White, fontWeight = FontWeight.Bold)
                    discovered.forEach { extension ->
                        val isInstalled = installed.any { it.id == extension.id && it.repositoryUrl == extension.repositoryUrl }
                        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(extension.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(extension.description, color = Muted, fontSize = 11.sp)
                                if (extension.language.isNotBlank()) Text("Bahasa konten: ${extension.language}", color = Muted, fontSize = 10.sp)
                                if (extension.subtitleLanguages.isNotBlank()) Text("Subtitle: ${extension.subtitleLanguages}", color = Muted, fontSize = 10.sp)
                                if (extension.contentTypes.isNotBlank()) Text("Jenis: ${extension.contentTypes}", color = Muted, fontSize = 10.sp)
                                if (extension.isExternal) {
                                    if (extension.freeNote.isNotBlank()) Text(extension.freeNote, color = Muted, fontSize = 10.sp)
                                    TextButton(onClick = {
                                        onOpenWebsite(extension.websiteUrl)
                                    }) { Text("Buka di GENJUR STREAM", color = Green) }
                                } else if (extension.isCloudStream) {
                                    if (extension.language.isNotBlank()) Text("Bahasa: ${extension.language}", color = Muted, fontSize = 10.sp)
                                    if (extension.tvTypes.isNotBlank()) Text("Kategori: ${extension.tvTypes}", color = Muted, fontSize = 10.sp)
                                    Text("File: ${extension.pluginUrl.substringAfterLast('/')}", color = Muted, fontSize = 10.sp)
                                    Text("Daftar berhasil dibaca. Plugin .cs3 belum dapat dijalankan di GENJUR STREAM.",
                                        color = Color(0xFFFFC857), fontSize = 11.sp)
                                } else {
                                    TextButton(onClick = {
                                        installed = if (isInstalled) installed.filterNot {
                                            it.id == extension.id && it.repositoryUrl == extension.repositoryUrl
                                        } else installed + extension
                                        saveInstalled()
                                    }) { Text(if (isInstalled) "Hapus ekstensi" else "Pasang penyedia", color = Green) }
                                }
                            }
                        }
                    }
                } else if (!loading && repositories.isNotEmpty()) {
                    Text(error ?: "Tekan Lihat penyedia pada repositori untuk memuat daftar penyedia.",
                        color = if (error != null) MaterialTheme.colorScheme.error else Muted, fontSize = 11.sp)
                }
                Text("Penyedia terpasang: ${installed.size}", color = Color.White, fontWeight = FontWeight.Bold)
                installed.forEach { ext ->
                    Text("• ${ext.name}", color = Muted, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("Selesai") }
                Button(
                    enabled = !loading,
                    modifier = Modifier.weight(1.6f),
                    onClick = {
                        val n = name.trim()
                        val u = url.trim()
                        val valid = runCatching {
                            Uri.parse(u).let { it.scheme == "https" && !it.host.isNullOrBlank() }
                        }.getOrDefault(false)
                        when {
                            n.isBlank() -> error = "Masukkan nama repositori."
                            !valid -> error = "Masukkan URL repositori HTTPS yang benar."
                            repositories.any { it.url.equals(u, true) } -> error = "Repositori sudah ditambahkan."
                            else -> {
                                val repo = SavedRepository(n, u)
                                repositories = repositories + repo
                                saveRepositories()
                                name = ""
                                url = ""
                                loadRepository(repo)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Bg)
                ) { Text(if (loading) "Memuat..." else "Tambah repositori") }
            }
        }
    )
}