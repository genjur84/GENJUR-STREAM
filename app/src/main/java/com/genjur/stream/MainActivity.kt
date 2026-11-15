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
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import coil.request.ImageRequest
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.PlayerNotificationManager
import androidx.media3.common.Player
import androidx.core.content.FileProvider
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
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.ByteArrayInputStream
import java.net.URL
import org.jsoup.Jsoup

private const val UPDATE_INFO_URL = "https://raw.githubusercontent.com/genjur84/GENJUR-STREAM/main/latest.json"
private const val PLAYBACK_NOTIFICATION_CHANNEL = "genjur_playback"
private const val PLAYBACK_NOTIFICATION_ID = 8401

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
    private val onVideoDetected: (String, String, String, String, Long) -> Unit
) {
    @JavascriptInterface
    fun onVideoPlay(title: String, pageUrl: String) { onPlayback(title, pageUrl) }
    @JavascriptInterface
    fun onVideoAvailable(title: String, pageUrl: String, videoUrl: String, quality: String, sizeBytes: Long) {
        onVideoDetected(title, pageUrl, videoUrl, quality, sizeBytes)
    }
}

private data class StreamOption(val quality: String, val videoUrl: String, val sizeBytes: Long = 0L)

private fun detectedQualityLabel(label: String, videoUrl: String): String {
    val source = "$label $videoUrl"
    val match = Regex("""(?i)(2160|1440|1080|720|480|360)\\s*p?""").find(source)
    if (match != null) return "${match.groupValues[1]}p"
    if (source.contains("4k", ignoreCase = true)) return "2160p"
    return "Kualitas asli (resolusi tidak terdeteksi)"
}

private data class FeedMovie(
    val title: String,
    val description: String,
    val category: String,
    val videoUrl: String,
    val licenseId: String,
    val termsUrl: String,
    val attribution: String,
    val posterUrl: String = "",
    val releaseYear: String = "",
    val genres: List<String> = emptyList(),
    val duration: String = "",
    val downloadUrl: String = "",
    val downloadAllowed: Boolean = false,
    val subtitleUrl: String = "",
    val subtitleLanguage: String = "",
    val streamOptions: List<StreamOption> = emptyList()
)
private fun movieKey(movie: FeedMovie): String =
    "${movie.title.trim().lowercase()}|${movie.videoUrl.trim()}|${movie.licenseId}"

private fun encodeMovie(movie: FeedMovie): JSONObject = JSONObject().apply {
    put("title", movie.title); put("description", movie.description); put("category", movie.category)
    put("videoUrl", movie.videoUrl); put("licenseId", movie.licenseId); put("termsUrl", movie.termsUrl)
    put("attribution", movie.attribution); put("posterUrl", movie.posterUrl)
    put("releaseYear", movie.releaseYear); put("genres", JSONArray(movie.genres)); put("duration", movie.duration)
    put("downloadUrl", movie.downloadUrl); put("downloadAllowed", movie.downloadAllowed)
    put("subtitleUrl", movie.subtitleUrl); put("subtitleLanguage", movie.subtitleLanguage)
    put("streams", JSONArray().apply { movie.streamOptions.forEach { put(JSONObject().put("quality", it.quality).put("videoUrl", it.videoUrl)) } })
}

private fun decodeMovie(json: JSONObject): FeedMovie {
    val genres = json.optJSONArray("genres")
    return FeedMovie(
        title = json.optString("title"), description = json.optString("description"),
        category = json.optString("category"), videoUrl = json.optString("videoUrl"),
        licenseId = json.optString("licenseId"), termsUrl = json.optString("termsUrl"),
        attribution = json.optString("attribution"), posterUrl = json.optString("posterUrl"),
        releaseYear = json.optString("releaseYear"),
        genres = if (genres == null) emptyList() else
            (0 until genres.length()).map { genres.optString(it) }.filter { it.isNotBlank() },
        duration = json.optString("duration"),
        downloadUrl = json.optString("downloadUrl"),
        downloadAllowed = json.optBoolean("downloadAllowed", false),
        subtitleUrl = json.optString("subtitleUrl"),
        subtitleLanguage = json.optString("subtitleLanguage", "id"),
        streamOptions = json.optJSONArray("streams")?.let { arr ->
            (0 until arr.length()).mapNotNull { index -> arr.optJSONObject(index)?.let { item ->
                StreamOption(item.optString("quality", "Otomatis"), item.optString("videoUrl"))
            } }.filter { it.videoUrl.isNotBlank() }
        } ?: emptyList()
    )
}


private fun formatVideoSize(bytes: Long): String {
    if (bytes <= 0L) return "Tidak diketahui"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
        else String.format(java.util.Locale.US, "%.1f MB", mb)
}

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

private fun loadGenericHtmlCatalog(sourceUrl: String, html: String): List<FeedMovie> {
    val document = Jsoup.parse(html, sourceUrl)
    val pageDescription = document.selectFirst("meta[name=description]")?.attr("content").orEmpty()
    val categoryWords = listOf(
        "Action", "Adventure", "Animasi", "Animation", "Anime", "Comedy", "Komedi",
        "Crime", "Dokumenter", "Documentary", "Drama", "Family", "Fantasy", "Horor",
        "Horror", "Mystery", "Romance", "Sci-Fi", "Thriller", "Serial", "Series"
    )
    val results = mutableListOf<FeedMovie>()
    document.select("a[href]").forEach { link ->
        val href = link.absUrl("href").trim()
        val uri = Uri.parse(href)
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return@forEach
        if (uri.host != Uri.parse(sourceUrl).host) return@forEach
        val path = uri.path.orEmpty().lowercase()
        if (!Regex("/(film|movie|watch|title|series|serial|episode|drama)(/|$|-|_)").containsMatchIn(path)) return@forEach
        val image = link.selectFirst("img")
        val title = link.text().trim().ifBlank { image?.attr("alt").orEmpty().trim() }
            .replace(Regex("\\s+"), " ")
        if (title.length !in 2..100) return@forEach
        if (title.lowercase() in setOf("watch now", "play", "read more", "lihat semua", "more")) return@forEach
        val contextText = link.parent()?.parent()?.text().orEmpty().take(500)
        val category = categoryWords.firstOrNull { contextText.contains(it, true) } ?: "Sumber Web"
        val poster = image?.absUrl("src")?.takeIf { it.startsWith("https://") }.orEmpty()
        val description = link.attr("title").ifBlank { pageDescription }
            .ifBlank { "Judul ditemukan otomatis dari halaman sumber. Buka situs sumber untuk detail dan pemutaran." }
        results.add(FeedMovie(
            title = title,
            description = description,
            category = category,
            videoUrl = href,
            licenseId = "Metadata halaman sumber",
            termsUrl = sourceUrl,
            attribution = "Metadata ditemukan di $sourceUrl",
            posterUrl = poster
        ))
    }
    return results.distinctBy { it.videoUrl }.take(100)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        setContent { MaterialTheme { GenjurStreamApp(this) } }
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
    var category by remember { mutableStateOf("Semua") }
    var selectedTab by remember { mutableStateOf("Beranda") }
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
    var updateVersion by remember { mutableStateOf<String?>(null) }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var feedMovies by remember { mutableStateOf(emptyList<FeedMovie>()) }
    var feedLoading by remember { mutableStateOf(false) }
    var feedMessage by remember { mutableStateOf("Tambahkan sumber feed JSON, lalu muat katalog.") }
    var selectedMovie by remember { mutableStateOf<FeedMovie?>(null) }
    var playingMovie by remember { mutableStateOf<FeedMovie?>(null) }
    BackHandler(enabled = selectedMovie != null || playingMovie != null || selectedTab != "Beranda") {
        when {
            playingMovie != null -> playingMovie = null
            selectedMovie != null -> selectedMovie = null
            selectedTab != "Beranda" -> selectedTab = "Beranda"
        }
    }
    var favoriteMovies by remember { mutableStateOf(readSavedMovies(activity, "favorites")) }
    var watchHistory by remember { mutableStateOf(readSavedMovies(activity, "history")) }
    var downloads by remember { mutableStateOf(readDownloads(activity)) }
    var downloadStates by remember { mutableStateOf<Map<Long, Pair<Int, String>>>(emptyMap()) }
    val libraryPrefs = remember { activity.getSharedPreferences("genjur_stream_library", 0) }
    val downloadManager = remember { activity.getSystemService(DownloadManager::class.java) }
    fun persistDownloads(items: List<DownloadEntry>) {
        libraryPrefs.edit().putString("downloads", JSONArray().apply {
            items.forEach { put(JSONObject().put("id", it.id).put("movie", encodeMovie(it.movie))) }
        }.toString()).apply()
    }
    fun startDownload(movie: FeedMovie) {
        if (!movie.downloadAllowed || movie.downloadUrl.isBlank()) return
        if (downloads.any { it.movie.downloadUrl == movie.downloadUrl }) return
        val uri = Uri.parse(movie.downloadUrl)
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) return
        val path = uri.path.orEmpty().lowercase()
        if (path.endsWith(".m3u8") || path.endsWith(".mpd")) return
        val extension = when {
            path.endsWith(".webm") -> "webm"
            path.endsWith(".mov") -> "mov"
            path.endsWith(".mkv") -> "mkv"
            else -> "mp4"
        }
        val request = DownloadManager.Request(uri)
            .setTitle(movie.title)
            .setDescription("Mengunduh ke GENJUR STREAM")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalFilesDir(activity, Environment.DIRECTORY_MOVIES,
                "genjur_${System.currentTimeMillis()}.$extension")
        runCatching { android.webkit.CookieManager.getInstance().getCookie(movie.downloadUrl) }
            .getOrNull()?.takeIf { it.isNotBlank() }?.let { request.addRequestHeader("Cookie", it) }
        if (movie.termsUrl.startsWith("http")) request.addRequestHeader("Referer", movie.termsUrl)
        val id = downloadManager.enqueue(request)
        downloads = listOf(DownloadEntry(id, movie)) + downloads
        persistDownloads(downloads)
    }
    fun startWebVideoDownload(title: String, pageUrl: String, mediaUrl: String) {
        val uri = Uri.parse(mediaUrl)
        val path = uri.path.orEmpty().lowercase()
        val directFile = listOf(".mp4", ".webm", ".mov", ".mkv").any { path.endsWith(it) }
        if (!directFile || uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) return
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

    LaunchedEffect(downloads, selectedTab) {
        while (true) {
            val states = mutableMapOf<Long, Pair<Int, String>>()
            downloads.forEach { entry ->
                val query = DownloadManager.Query().setFilterById(entry.id)
                val cursor = downloadManager.query(query)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                        val soFar = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val total = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        val percent = if (total > 0) ((soFar * 100) / total).toInt() else 0
                        val label = when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> "Selesai"
                            DownloadManager.STATUS_FAILED -> "Gagal"
                            DownloadManager.STATUS_PAUSED -> "Dijeda"
                            DownloadManager.STATUS_PENDING -> "Menunggu"
                            else -> "Mengunduh"
                        }
                        states[entry.id] = percent to label
                    } else states[entry.id] = 0 to "Tidak ditemukan"
                }
            }
            downloadStates = states
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
                                movies.addAll(htmlItems)
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
                                        streamOptions = streamOptions
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
                feedMovies = movies.distinctBy { "${it.title.trim().lowercase()}|${it.releaseYear.trim()}" }
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
                        Text("Sedang mengunduh pembaruan...", color = Muted)
                    }
                    updateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !downloading, onClick = {
                    val apkLink = updateUrl!!
                    scope.launch {
                        downloading = true
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
                                    val file = File(activity.cacheDir, "genjur-stream-update.apk")
                                    connection.inputStream.use { input ->
                                        file.outputStream().buffered().use { output -> input.copyTo(output) }
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
                    if (selectedTab == "Beranda") {
                        // Bagian Lanjutkan Menonton di Beranda dihapus sesuai permintaan.
                    } else {
                        if (selectedTab != "Browser Sumber") {
                            Text(selectedTab, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(14.dp))
                        }
                        when (selectedTab) {
                            "Cari" -> {
                                OutlinedTextField(value = search, onValueChange = { search = it },
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
                                                    onClick = { selectedSearchSourceUrl = null; searchSourceDropdownExpanded = false }
                                                )
                                                savedSourceMenu.forEach { source ->
                                                    DropdownMenuItem(
                                                        text = { Text(source.name, color = Color.White, maxLines = 1) },
                                                        onClick = { selectedSearchSourceUrl = source.url; searchSourceDropdownExpanded = false }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                val selectedSource = savedSourceMenu.firstOrNull { it.url == selectedSearchSourceUrl }
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = {
                                        val sourceUrl = selectedSource?.url
                                        val host = sourceUrl?.let { Uri.parse(it).host?.lowercase()?.removePrefix("www.") }
                                        if (host != null && search.isNotBlank()) {
                                            val query = Uri.encode(search.trim())
                                            browserInitialUrl = if (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be") {
                                                "https://www.youtube.com/results?search_query=$query"
                                            } else {
                                                // Sertakan domain sumber sebagai kata kunci biasa, bukan operator site:
                                                "https://www.google.com/search?q=" + Uri.encode("${search.trim()} $host")
                                            }
                                            selectedTab = "Browser Sumber"
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = selectedSource != null && search.isNotBlank(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Green)
                                ) {
                                    Text(if (selectedSource != null) "Cari di sumber ${selectedSource.name}" else "Pilih sumber untuk mencari")
                                }
                                selectedSource?.let { source ->
                                    val sourceHost = Uri.parse(source.url).host?.removePrefix("www.") ?: source.url
                                    Text(
                                        "Hasil pencarian dibatasi ke alamat $sourceHost. Periksa alamat situs pada setiap hasil sebelum dibuka.",
                                        color = Muted,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            "Sumber Link" -> {
                                SourceLinksScreen(activity = activity, onOpenLink = { link ->
                                    browserInitialUrl = link
                                    selectedTab = "Browser Sumber"
                                })
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
                            "Browser Sumber" -> {
                                // Buat WebView baru setiap kali alamat sumber berubah.
                                // Ini mencegah callback loading dari situs sebelumnya
                                // menimpa status loading situs yang baru dibuka.
                                key(browserInitialUrl) {
                                    SourceBrowser(
                                        initialUrl = browserInitialUrl,
                                        fullScreen = true,
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
                                            playingMovie = FeedMovie(
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
                                        }
                                    )
                                }
                            }
                            "Kategori" -> {
                                categories.forEach { item ->
                                    FilterChip(selected = category == item, onClick = { category = item },
                                        label = { Text(item) })
                                }
                            }
                            "Unduhan" -> {
                                if (downloads.isEmpty()) {
                                    Text("Belum ada video yang diunduh.", color = Muted)
                                } else {
                                    downloads.forEach { entry ->
                                        val state = downloadStates[entry.id] ?: (0 to "Menyiapkan")
                                        val query = DownloadManager.Query().setFilterById(entry.id)
                                        var completedUri: Uri? = null
                                        downloadManager.query(query)?.use { cursor ->
                                            if (cursor.moveToFirst() &&
                                                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
                                                completedUri = downloadManager.getUriForDownloadedFile(entry.id)
                                            }
                                        }
                                        Card(
                                            Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                            colors = CardDefaults.cardColors(containerColor = Panel),
                                            shape = RoundedCornerShape(14.dp)
                                        ) {
                                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                                Text(entry.movie.title, color = Color.White, fontWeight = FontWeight.Bold)
                                                Text(state.second, color = Muted, fontSize = 11.sp)
                                                if (state.second == "Mengunduh" || state.second == "Menunggu") {
                                                    Spacer(Modifier.height(7.dp))
                                                    LinearProgressIndicator(
                                                        progress = { state.first / 100f },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        color = Green
                                                    )
                                                    Text("${state.first}%", color = Muted, fontSize = 10.sp)
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    if (completedUri != null) {
                                                        TextButton(onClick = {
                                                            val offlineMovie = entry.movie.copy(videoUrl = completedUri.toString())
                                                            recordWatch(offlineMovie)
                                                            playingMovie = offlineMovie
                                                        }) { Text("Putar offline", color = Green) }
                                                    }
                                                    TextButton(onClick = {
                                                        downloadManager.remove(entry.id)
                                                        downloads = downloads.filterNot { it.id == entry.id }
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
                            feedMovies.filter { movie ->
                                val selectedHost = selectedSearchSourceUrl?.let { Uri.parse(it).host?.lowercase()?.removePrefix("www.") }
                                val movieHost = listOf(movie.videoUrl, movie.termsUrl).mapNotNull { candidate ->
                                    runCatching { Uri.parse(candidate).host?.lowercase()?.removePrefix("www.") }.getOrNull()
                                }.firstOrNull()
                                (selectedTab != "Cari" || !movie.title.equals("Drama", true)) &&
                                    (selectedTab != "Cari" || selectedHost == null || movieHost == selectedHost || movieHost?.endsWith(".$selectedHost") == true) &&
                                    (category == "Semua" || selectedTab != "Kategori" || movie.category.equals(category, true)) &&
                                    (search.isBlank() || movie.title.contains(search, true) || movie.description.contains(search, true) || movie.genres.any { it.contains(search, true) })
                            }.forEach { movie ->
                                Card(Modifier.fillMaxWidth().padding(bottom = 9.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(movie.title, color = Color.White, fontWeight = FontWeight.Bold)
                                            if (selectedTab != "Cari") {
                                                Text(movie.category, color = Green, fontSize = 11.sp)
                                            }
                                        }
                                        if (selectedTab != "Cari") {
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

                if (selectedTab != "Browser Sumber") Row(
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
                            modifier = Modifier.width(if (tvLayout) 104.dp else 56.dp).clickable {
                                selectedTab = tab
                            }.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = navIcon,
                                contentDescription = tab,
                                tint = if (isSelected) Green else Muted,
                                modifier = Modifier.size(if (tvLayout) 27.dp else 25.dp)
                            )
                        }
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
                                sourceMenuExpanded = false
                            }
                        )
                        savedSourceMenu.forEach { source ->
                            DropdownMenuItem(
                                text = { Text(source.name, color = Color.White, maxLines = 1) },
                                onClick = {
                                    browserInitialUrl = source.url
                                    selectedSourceName = source.name
                                    selectedTab = "Browser Sumber"
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

    selectedMovie?.let { movie ->
        MovieDetailDialog(
            movie = movie,
            isFavorite = favoriteMovies.any { movieKey(it) == movieKey(movie) },
            onToggleFavorite = { toggleFavorite(movie) },
            isDownloaded = downloads.any { movieKey(it.movie) == movieKey(movie) },
            onDownload = { startDownload(movie) },
            onClose = { selectedMovie = null },
            onPlay = { selectedVersion ->
                if (selectedVersion.videoUrl.isNotBlank()) {
                    recordWatch(selectedVersion)
                    selectedMovie = null
                    playingMovie = selectedVersion
                }
            }
        )
    }
    playingMovie?.let { movie ->
        InAppVideoPlayer(activity, movie, onClose = { playingMovie = null })
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
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    isDownloaded: Boolean,
    onDownload: () -> Unit,
    onClose: () -> Unit,
    onPlay: (FeedMovie) -> Unit
) {
    var selectedStreamUrl by remember(movie) { mutableStateOf(movie.streamOptions.firstOrNull()?.videoUrl ?: movie.videoUrl) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().fillMaxHeight(0.9f).padding(12.dp),
            shape = RoundedCornerShape(22.dp), color = Panel) {
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
                    Spacer(Modifier.height(22.dp))
                    Divider(color = Color(0xFF393344))
                    Spacer(Modifier.height(16.dp))
                    Text("SINOPSIS", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(movie.description.ifBlank { "Sinopsis belum tersedia." },
                        color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
                    val availableStreams = movie.streamOptions.ifEmpty {
                        if (movie.videoUrl.isNotBlank()) listOf(StreamOption("Otomatis", movie.videoUrl)) else emptyList()
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
                    Button(onClick = { onPlay(movie.copy(videoUrl = selectedStreamUrl)) }, enabled = selectedStreamUrl.isNotBlank(), modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White,
                            disabledContainerColor = Color(0xFF393344), disabledContentColor = Muted)) {
                        Text(if (selectedStreamUrl.isBlank()) "Video belum tersedia" else "▶  Putar ${availableStreams.firstOrNull { it.videoUrl == selectedStreamUrl }?.quality ?: "sekarang"}")
                    }
                    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Kembali ke katalog", color = Green) }
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
private data class SavedSourceLink(val name: String, val url: String)

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
        Text("Daftar sumber (${links.size})", color = Color.White, fontWeight = FontWeight.Bold)
        if (links.isEmpty()) {
            Text("Belum ada link tersimpan.", color = Color(0xFFCBD2E0), fontSize = 12.sp)
        } else {
            val selectedIndex = selectedSavedSourceIndex?.takeIf { it in links.indices }
            Box {
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
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Tampilkan daftar sumber")
                }
                if (savedSourcesDropdownExpanded) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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
    var address by remember(initialUrl) { mutableStateOf(initialUrl) }
    var currentUrl by remember(initialUrl) { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("Masukkan alamat situs sumber video.") }
    var detectedVideoTitle by remember { mutableStateOf("") }
    var detectedVideoPage by remember { mutableStateOf("") }
    var detectedVideoOptions by remember { mutableStateOf<List<StreamOption>>(emptyList()) }
    var showDownloadChoices by remember { mutableStateOf(false) }
    var isLoading by remember(initialUrl) { mutableStateOf(initialUrl.isNotBlank()) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val loadingFallback = remember { Runnable { isLoading = false } }
    BackHandler(enabled = webView?.canGoBack() == true) {
        webView?.goBack()
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
        if (detectedVideoOptions.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Pilihan kualitas download tersedia", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(detectedVideoTitle.ifBlank { "Video dari situs" }, color = Muted, fontSize = 10.sp, maxLines = 2)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Button(onClick = {
                            detectedVideoOptions.firstOrNull()?.let {
                                onPlayCandidate(detectedVideoTitle, detectedVideoPage, it.videoUrl)
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White)) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Putar")
                        }
                        TextButton(onClick = { showDownloadChoices = true }) {
                            Icon(Icons.Filled.Download, contentDescription = null, tint = Green)
                            Text("Unduh", color = Green)
                        }
                    }
                }
            }
        }
        if (showDownloadChoices) {
            AlertDialog(
                onDismissRequest = { showDownloadChoices = false },
                title = { Text("Pilih resolusi / ukuran") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detectedVideoOptions.forEach { option ->
                            OutlinedButton(
                                modifier = Modifier.fillMaxWidth(),
                                onClick = {
                                    showDownloadChoices = false
                                    onDownloadCandidate(detectedVideoTitle, detectedVideoPage, option.videoUrl)
                                }
                            ) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(option.quality, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(if (option.sizeBytes > 0) "Ukuran sekitar ${formatVideoSize(option.sizeBytes)}"
                                        else "Ukuran file belum tersedia dari situs",
                                        color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showDownloadChoices = false }) { Text("Batal") } },
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
                    webChromeClient = WebChromeClient()
                    addJavascriptInterface(PlaybackStartedBridge(
                        onPlayback = { title, pageUrl -> post {
                            if (!playbackNotificationSent) {
                                playbackNotificationSent = true
                                onPlaybackStarted(title, pageUrl)
                            }
                        } },
                        onVideoDetected = { title, pageUrl, videoUrl, quality, sizeBytes ->
                            post {
                                val uri = Uri.parse(videoUrl)
                                val path = uri.path.orEmpty().lowercase()
                                val playableMedia = Regex("""\.(m3u8|mpd|mp4|m4v|webm|mov|mkv)(?:$|[?#&])""").containsMatchIn(videoUrl.lowercase())
                                if (playableMedia && uri.scheme in listOf("http", "https") && !uri.host.isNullOrBlank()) {
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
                                !requestedUri.host.isNullOrBlank() && mediaMatch) {
                                val label = detectedQualityLabel("", mediaUrlLower)
                                view.post {
                                    val mediaUrl = requestedUri.toString()
                                    if (!playbackNotificationSent) {
                                        playbackNotificationSent = true
                                        onPlaybackStarted(view.title?.takeIf { it.isNotBlank() } ?: "Video dari situs", view.url ?: currentUrl)
                                    }
                                    if (detectedVideoOptions.none { it.videoUrl == mediaUrl }) {
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
                                    if (window.__genjurPlaybackHooked) return;
                                    window.__genjurPlaybackHooked = true;
                                    function reportMedia(title, url, quality) {
                                        try {
                                            if (!url || !/\.(m3u8|mpd|mp4|m4v|webm|mov|mkv)(?:$|[?#&])/i.test(url)) return;
                                            var entry = performance.getEntriesByName(url).slice(-1)[0];
                                            var size = entry ? (entry.encodedBodySize || entry.transferSize || 0) : 0;
                                            GenjurPlayback.onVideoAvailable(title || document.title || 'Video dari situs', location.href, url, quality || 'Kualitas tersedia', size);
                                        } catch(e) {}
                                    }
                                    function reportLink(a) {
                                        try { reportMedia(a.textContent, a.href, a.getAttribute('data-quality') || 'Link video langsung'); } catch(e) {}
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
                                                reportMedia(title, source.src, label);
                                            });
                                            var media = v.currentSrc || v.src || '';
                                            if (media) {
                                                var quality = v.videoHeight ? (v.videoHeight + 'p') : 'Kualitas otomatis';
                                                reportMedia(title, media, quality);
                                            }
                                        }
                                        v.addEventListener('loadedmetadata', scanSources);
                                        v.addEventListener('play', function() {
                                            try {
                                                var title = document.title || 'Video dari situs';
                                                GenjurPlayback.onVideoPlay(title, location.href);
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
                                                    if (candidate) reportMedia(document.title, new URL(candidate, location.href).href, 'Sumber video ditemukan');
                                                });
                                            });
                                        } catch(e) {}
                                    }
                                    scanPage();
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
private fun InAppVideoPlayer(activity: MainActivity, movie: FeedMovie, onClose: () -> Unit) {
    var isFullscreen by remember { mutableStateOf(false) }
    val player = remember(movie.videoUrl) {
        ExoPlayer.Builder(activity).build().apply {
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
    DisposableEffect(player) {
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
            playbackNotification.setPlayer(null)
            player.release()
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                .show(WindowInsetsCompat.Type.systemBars())
        }
    }
    fun setFullscreen(enabled: Boolean) {
        isFullscreen = enabled
        activity.requestedOrientation = if (enabled) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
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
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = if (isFullscreen) Modifier.fillMaxSize() else
                Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(12.dp),
            shape = if (isFullscreen) RoundedCornerShape(0.dp) else RoundedCornerShape(20.dp),
            color = Color.Black
        ) {
            if (isFullscreen) {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                this.player = player
                                useController = true
                                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                            }
                        },
                        update = { it.player = player },
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { setFullscreen(false) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    ) {
                        Icon(Icons.Filled.FullscreenExit, contentDescription = "Keluar full screen", tint = Color.White)
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
    GenjurStreamHome(activity)
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
