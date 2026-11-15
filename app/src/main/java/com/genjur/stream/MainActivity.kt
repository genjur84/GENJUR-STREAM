package com.genjur.stream

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.net.URL

private const val UPDATE_INFO_URL = "https://raw.githubusercontent.com/genjur84/GENJUR-STREAM/main/latest.json"
private val Bg = Color(0xFF09090D)
private val Panel = Color(0xFF191821)
private val Green = Color(0xFF8B5CF6)
private val Muted = Color(0xFFACB7C5)


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
    val duration: String = ""
)
private fun savedFeedUrls(activity: MainActivity): List<String> = runCatching {
    val prefs = activity.getSharedPreferences("genjur_stream_sources", 0)
    val legacy = JSONArray(prefs.getString("items", "[]"))
    val installed = JSONArray(prefs.getString("installed_extensions", "[]"))
    val urls = mutableListOf<String>()
    for (i in 0 until legacy.length()) {
        legacy.optJSONObject(i)?.optString("url")?.takeIf { it.isNotBlank() }?.let(urls::add)
    }
    for (i in 0 until installed.length()) {
        installed.optJSONObject(i)?.optString("feedUrl")?.takeIf { it.isNotBlank() }?.let(urls::add)
    }
    urls.distinct()
}.getOrDefault(emptyList())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { GenjurStreamApp(this) } }
    }
}

@Composable
fun GenjurStreamHome(activity: MainActivity, onOpenSources: () -> Unit = {}) {
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Semua") }
    var selectedTab by remember { mutableStateOf("Beranda") }
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
    val scope = rememberCoroutineScope()

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
                            val root = JSONObject(connection.getInputStream().bufferedReader().use { it.readText() })
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
                                        duration = item.optString("duration").trim()
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
                feedMovies = movies
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
    val categories = listOf("Semua") + (listOf("Film", "Serial", "Animasi", "Dokumenter") + feedMovies.map { it.category }).map { it.trim() }.filter { it.isNotBlank() }.distinct()

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
                                val connection = URL(apkLink).openConnection()
                                connection.connectTimeout = 15000
                                connection.readTimeout = 30000
                                val file = File(activity.cacheDir, "genjur-stream-update.apk")
                                connection.getInputStream().use { input ->
                                    file.outputStream().use { output -> input.copyTo(output) }
                                }
                                file
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
                                updateError = "Tidak dapat membuka pemasang. Periksa izin instalasi aplikasi."
                            }
                        }.onFailure {
                            updateError = "Unduhan gagal. Periksa koneksi internet lalu coba lagi."
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { selectedTab = "Cari" }) { Text("⌕", color = Color.White, fontSize = 25.sp) }
                        TextButton(onClick = onOpenSources) { Text("⋮", color = Color.White, fontSize = 25.sp) }
                    }
                }

                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = horizontal)
                ) {
                    if (selectedTab == "Beranda") {
                        Card(
                            Modifier.fillMaxWidth().height(if (tvLayout) 340.dp else 300.dp),
                            shape = RoundedCornerShape(26.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF29203B))
                        ) {
                            Box(Modifier.fillMaxSize().background(Color(0xFF21172F))) {
                                Column(Modifier.align(Alignment.CenterEnd).padding(end = 18.dp)) {
                                    Text("✦", color = Color(0xFFB69AFF), fontSize = if (tvLayout) 130.sp else 105.sp)
                                    Text("GENJUR", color = Color(0xFF4B356A), fontSize = 28.sp, fontWeight = FontWeight.Black)
                                }
                                Column(
                                    Modifier.align(Alignment.CenterStart).fillMaxWidth(0.78f).padding(20.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text("PILIHAN HARI INI", color = Color(0xFFC4A7FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(12.dp))
                                    Text("Cerita hebat.\nPengalaman seru.", color = Color.White,
                                        fontSize = if (tvLayout) 38.sp else 29.sp, lineHeight = if (tvLayout) 43.sp else 34.sp,
                                        fontWeight = FontWeight.ExtraBold)
                                    Spacer(Modifier.height(8.dp))
                                    Text("Temukan tontonan dari katalog sumber resmi dan berizin.",
                                        color = Color(0xFFD4CEDD), fontSize = 13.sp, lineHeight = 18.sp)
                                    Spacer(Modifier.height(16.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                        Button(onClick = { selectedTab = "Kategori" },
                                            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White),
                                            shape = RoundedCornerShape(12.dp)) { Text("Jelajahi") }
                                        OutlinedButton(onClick = onOpenSources, shape = RoundedCornerShape(12.dp)) {
                                            Text("Sumber", color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Lanjutkan Menonton", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { selectedTab = "Unduhan" }) { Text("Lihat semua  →", color = Green) }
                        }
                        if (feedMovies.isEmpty()) {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.padding(18.dp)) {
                                    Text("Belum ada tontonan", color = Color.White, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(5.dp))
                                    Text("Tambahkan sumber resmi untuk mulai mengisi katalog GENJUR STREAM.",
                                        color = Muted, fontSize = 12.sp)
                                    TextButton(onClick = onOpenSources) { Text("＋ Tambah sumber", color = Green) }
                                }
                            }
                        } else {
                            feedMovies.take(12).chunked(if (tvLayout) 4 else 2).forEach { rowMovies ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    rowMovies.forEach { movie ->
                                        Card(Modifier.weight(1f).padding(bottom = 8.dp), shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(containerColor = Panel)) {
                                            Column(Modifier.padding(12.dp)) {
                                                MoviePoster(
                                                    url = movie.posterUrl,
                                                    title = movie.title,
                                                    height = if (tvLayout) 210.dp else 170.dp
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Text(movie.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2)
                                                Text(movie.category, color = Green, fontSize = 11.sp)
                                                TextButton(onClick = { selectedMovie = movie }) { Text("Lihat detail", color = Green) }
                                            }
                                        }
                                    }
                                    repeat((if (tvLayout) 4 else 2) - rowMovies.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
                        Text("Kategori", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(10.dp))
                        categories.drop(1).chunked(if (tvLayout) 4 else 2).forEach { items ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items.forEach { item ->
                                    Card(onClick = {
                                        category = item; selectedTab = "Kategori"
                                    }, modifier = Modifier.weight(1f).padding(bottom = 8.dp),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF24202E))) {
                                        Column(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(when(item) { "Film" -> "▣"; "Serial" -> "▤"; "Animasi" -> "✧"; else -> "◉" },
                                                color = Color(0xFFC4A7FF), fontSize = 28.sp)
                                            Text(item, color = Color.White, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                                if (items.size < (if (tvLayout) 4 else 2)) Spacer(Modifier.weight(1f))
                            }
                        }
                    } else {
                        Text(selectedTab, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.height(14.dp))
                        when (selectedTab) {
                            "Cari" -> {
                                OutlinedTextField(value = search, onValueChange = { search = it },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    placeholder = { Text("Cari judul tontonan...", color = Muted) },
                                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White, focusedBorderColor = Green,
                                        unfocusedBorderColor = Color(0xFF393344), cursorColor = Green))
                            }
                            "Kategori" -> {
                                categories.forEach { item ->
                                    FilterChip(selected = category == item, onClick = { category = item },
                                        label = { Text(item) })
                                }
                            }
                            "Unduhan" -> Text("Daftar unduhan akan tampil di sini setelah fitur unduhan diaktifkan.", color = Muted)
                        }
                        if (selectedTab == "Cari" || selectedTab == "Kategori") {
                            Spacer(Modifier.height(14.dp))
                            feedMovies.filter { movie ->
                                (category == "Semua" || selectedTab != "Kategori" || movie.category.equals(category, true)) &&
                                    (search.isBlank() || movie.title.contains(search, true) || movie.description.contains(search, true) || movie.genres.any { it.contains(search, true) })
                            }.forEach { movie ->
                                Card(Modifier.fillMaxWidth().padding(bottom = 9.dp), colors = CardDefaults.cardColors(containerColor = Panel)) {
                                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(movie.title, color = Color.White, fontWeight = FontWeight.Bold)
                                            Text(movie.category, color = Green, fontSize = 11.sp)
                                        }
                                        TextButton(onClick = { selectedMovie = movie }) { Text("Detail", color = Green) }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    if (selectedTab == "Beranda" || selectedTab == "Cari" || selectedTab == "Kategori") {
                        Button(onClick = { refreshCatalog() }, enabled = !feedLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White)) {
                            Text(if (feedLoading) "Memuat katalog..." else "Muat katalog dari sumber")
                        }
                        Text(feedMessage, color = Muted, fontSize = 11.sp)
                    }
                    Text("GENJUR STREAM • Versi ${BuildConfig.VERSION_NAME}", color = Color(0xFF777180),
                        fontSize = 10.sp, modifier = Modifier.padding(vertical = 16.dp))
                }

                Row(
                    Modifier.fillMaxWidth().background(Color(0xFF15141B)).padding(horizontal = 5.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("Beranda" to "⌂", "Kategori" to "▦", "Cari" to "⌕", "Unduhan" to "⇩", "Pengaturan" to "⚙").forEach { (tab, glyph) ->
                        TextButton(onClick = { if (tab == "Pengaturan") onOpenSources() else selectedTab = tab },
                            modifier = Modifier.weight(1f)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(glyph, color = if (selectedTab == tab) Green else Muted, fontSize = 22.sp)
                                Text(tab, color = if (selectedTab == tab) Green else Muted, fontSize = if (tvLayout) 12.sp else 9.sp,
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }

    selectedMovie?.let { movie ->
        MovieDetailDialog(movie, onClose = { selectedMovie = null }, onPlay = {
            if (movie.videoUrl.isNotBlank()) { selectedMovie = null; playingMovie = movie }
        })
    }
    playingMovie?.let { movie ->
        InAppVideoPlayer(activity, movie, onClose = { playingMovie = null })
    }
}


@Composable
private fun MoviePoster(url: String, title: String, height: androidx.compose.ui.unit.Dp) {
    var failed by remember(url) { mutableStateOf(false) }
    if (url.isBlank() || failed) {
        Box(
            Modifier.fillMaxWidth().height(height)
                .background(Color(0xFF302541), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("GENJUR STREAM", color = Color(0xFFC4A7FF), fontSize = 19.sp, fontWeight = FontWeight.Black)
                if (url.isNotBlank() && failed) {
                    Spacer(Modifier.height(5.dp))
                    Text("Poster gagal dimuat", color = Muted, fontSize = 10.sp)
                }
            }
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = "Poster $title",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(height),
            onError = { failed = true }
        )
    }
}

@Composable
private fun MovieDetailDialog(movie: FeedMovie, onClose: () -> Unit, onPlay: () -> Unit) {
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
                    Spacer(Modifier.height(20.dp))
                    Divider(color = Color(0xFF393344))
                    Spacer(Modifier.height(14.dp))
                    Text("INFORMASI SUMBER", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(movie.attribution, color = Muted, fontSize = 12.sp)
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onPlay, enabled = movie.videoUrl.isNotBlank(), modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Color.White,
                            disabledContainerColor = Color(0xFF393344), disabledContentColor = Muted)) {
                        Text(if (movie.videoUrl.isBlank()) "Video belum tersedia" else "▶  Putar sekarang")
                    }
                    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Kembali ke katalog", color = Green) }
                }
            }
        }
    }
}

@Composable
private fun InAppVideoPlayer(activity: MainActivity, movie: FeedMovie, onClose: () -> Unit) {
    val player = remember(movie.videoUrl) {
        ExoPlayer.Builder(activity).build().apply {
            setMediaItem(MediaItem.fromUri(movie.videoUrl))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.88f).padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = Panel
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(movie.title, color = Color.White, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("Tutup", color = Green) }
                }
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            this.player = player
                            useController = true
                        }
                    },
                    update = { it.player = player },
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
                Text("Pemutaran langsung di GENJUR STREAM", color = Muted, fontSize = 11.sp)
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
    val isCloudStream: Boolean = false
)

@Composable
private fun GenjurStreamApp(activity: MainActivity) {
    var showRepositories by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        GenjurStreamHome(activity) { showRepositories = true }
        if (showRepositories) RepositoryDialog(activity) { showRepositories = false }
    }
}

@Composable
private fun RepositoryDialog(activity: MainActivity, onClose: () -> Unit) {
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
                o.optBoolean("isCloudStream", false))
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
            .put("isCloudStream", it.isCloudStream)) }
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
                    val connection = URL(repository.url).openConnection()
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
                            require(id.isNotBlank() && extName.isNotBlank() &&
                                feedUri.scheme == "https" && !feedUri.host.isNullOrBlank()) {
                                "Ekstensi ditolak: id, nama, atau feedUrl tidak valid."
                            }
                            parsed.add(RepositoryExtension(id, extName,
                                item.optString("description", "Ekstensi katalog resmi."),
                                feed, repository.url))
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
                                    true
                                ))
                            }
                        }
                    } else {
                        error("Format repositori tidak dikenal. Mendukung GENJUR schemaVersion 1 dan CloudStream manifestVersion 1.")
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
        title = { Text("Repositori & Ekstensi") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Tambahkan repositori untuk menemukan ekstensi katalog. GENJUR STREAM hanya membaca metadata JSON/feed HTTPS; ekstensi tidak menjalankan kode dari pihak ketiga.",
                    color = Muted, fontSize = 12.sp)
                OutlinedTextField(value = name, onValueChange = { name = it },
                    label = { Text("Nama repositori") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it },
                    label = { Text("URL repositori") }, singleLine = true)
                Button(enabled = !loading, onClick = {
                    val n = name.trim()
                    val u = url.trim()
                    val valid = runCatching { Uri.parse(u).let { it.scheme == "https" && !it.host.isNullOrBlank() } }.getOrDefault(false)
                    when {
                        n.isBlank() -> error = "Masukkan nama repositori."
                        !valid -> error = "Masukkan URL repositori HTTPS yang benar."
                        repositories.any { it.url.equals(u, true) } -> error = "Repositori sudah ditambahkan."
                        else -> {
                            val repo = SavedRepository(n, u)
                            repositories = repositories + repo
                            saveRepositories()
                            name = ""; url = ""
                            loadRepository(repo)
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Bg)) {
                    Text(if (loading) "Memuat..." else "Tambah repositori")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                if (repositories.isNotEmpty()) {
                    Text("Repositori tersimpan", color = Color.White, fontWeight = FontWeight.Bold)
                    repositories.forEach { repo ->
                        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(repo.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(repo.url, color = Muted, fontSize = 10.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { loadRepository(repo) }) { Text("Periksa ekstensi") }
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
                    Text("Ekstensi ditemukan", color = Color.White, fontWeight = FontWeight.Bold)
                    discovered.forEach { extension ->
                        val isInstalled = installed.any { it.id == extension.id && it.repositoryUrl == extension.repositoryUrl }
                        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(extension.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(extension.description, color = Muted, fontSize = 11.sp)
                                if (extension.isCloudStream) {
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
                                    }) { Text(if (isInstalled) "Hapus ekstensi" else "Pasang ekstensi", color = Green) }
                                }
                            }
                        }
                    }
                } else if (!loading && repositories.isNotEmpty()) {
                    Text("Tekan Periksa ekstensi pada repositori untuk memuat daftar ekstensi.",
                        color = Muted, fontSize = 11.sp)
                }
                Text("Ekstensi terpasang: ${installed.size}", color = Color.White, fontWeight = FontWeight.Bold)
                installed.forEach { ext ->
                    Text("• ${ext.name}", color = Muted, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Selesai") } }
    )
}
