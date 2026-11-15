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
import androidx.compose.ui.focus.focusGroup
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
private val Bg = Color(0xFF0D1117)
private val Panel = Color(0xFF171F2B)
private val Green = Color(0xFF42D6A4)
private val Muted = Color(0xFFACB7C5)


private data class FeedMovie(
    val title: String,
    val description: String,
    val category: String,
    val videoUrl: String,
    val licenseId: String,
    val termsUrl: String,
    val attribution: String
)
private fun savedFeedUrls(activity: MainActivity): List<String> = runCatching {
    val a = JSONArray(activity.getSharedPreferences("genjur_stream_sources", 0).getString("items", "[]"))
    (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("url")?.takeIf { u -> u.isNotBlank() } }
}.getOrDefault(emptyList())

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { GenjurStreamApp(this) } }
    }
}

@Composable
fun GenjurStreamHome(activity: MainActivity) {
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Semua") }
    var updateVersion by remember { mutableStateOf<String?>(null) }
    var updateUrl by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf(false) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var feedMovies by remember { mutableStateOf(emptyList<FeedMovie>()) }
    var feedLoading by remember { mutableStateOf(false) }
    var feedMessage by remember { mutableStateOf("Tambahkan sumber feed JSON, lalu muat katalog.") }
    var selectedMovie by remember { mutableStateOf<FeedMovie?>(null) }
    val scope = rememberCoroutineScope()

    fun refreshCatalog() {
        scope.launch {
            feedLoading = true
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val urls = savedFeedUrls(activity)
                    require(urls.isNotEmpty()) { "Belum ada sumber. Tambahkan sumber terlebih dahulu." }
                    val movies = mutableListOf<FeedMovie>()
                    urls.forEach { sourceUrl ->
                        val connection = URL(sourceUrl).openConnection()
                        connection.connectTimeout = 12000
                        connection.readTimeout = 20000
                        val root = JSONObject(connection.getInputStream().bufferedReader().use { it.readText() })
                        val rights = root.optJSONObject("rights")
                            ?: error("Sumber ditolak: metadata hak distribusi tidak tersedia.")
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
                            "Sumber ditolak: izin distribusi, lisensi, tautan ketentuan, atau atribusi belum lengkap."
                        }
                        val items = root.optJSONArray("items") ?: error("Feed tidak memiliki array items.")
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
                                    "Sumber ditolak: URL video harus menggunakan HTTPS yang valid."
                                }
                                movies.add(FeedMovie(
                                    title,
                                    item.optString("description", "Sinopsis belum tersedia."),
                                    item.optString("category", "Film"),
                                    videoUrl,
                                    licenseId,
                                    termsUrl,
                                    attribution
                                ))
                            }
                        }
                    }
                    movies
                }
            }
            feedLoading = false
            result.onSuccess {
                feedMovies = it
                feedMessage = if (it.isEmpty()) "Feed terbaca, tetapi belum ada item tontonan." else "${it.size} tontonan berhasil dimuat."
            }.onFailure { feedMessage = it.message ?: "Gagal memuat katalog." }
        }
    }
    val categories = listOf("Semua", "Film", "Serial", "Animasi", "Dokumenter")

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
            Column(
                Modifier
                    .widthIn(max = 1200.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(
                        horizontal = if (tvLayout) 36.dp else 20.dp,
                        vertical = if (tvLayout) 28.dp else 20.dp
                    )
                    .focusGroup()
            ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("GENJUR STREAM", color = Green, fontSize = if (tvLayout) 34.sp else 25.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Temukan hiburan favoritmu", color = Muted, fontSize = 13.sp)
                }
                Text("GS", color = Bg, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Green, RoundedCornerShape(14.dp)).padding(horizontal = 13.dp, vertical = 9.dp))
            }
            Spacer(Modifier.height(22.dp))
            OutlinedTextField(
                value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, shape = RoundedCornerShape(16.dp),
                placeholder = { Text("Cari film atau serial...", color = Muted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                    focusedBorderColor = Green, unfocusedBorderColor = Color(0xFF344252), cursorColor = Green
                )
            )
            Spacer(Modifier.height(22.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(22.dp)) {
                    Text("SELAMAT DATANG", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("Semua hiburan\ndalam satu tempat.", color = Color.White, fontSize = if (tvLayout) 38.sp else 27.sp,
                        lineHeight = if (tvLayout) 44.sp else 33.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(9.dp))
                    Text("Jelajahi pilihan tontonan dari sumber resmi dan berizin.",
                        color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = { category = "Semua" },
                        colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Bg),
                        shape = RoundedCornerShape(12.dp)) { Text("Jelajahi sekarang", fontWeight = FontWeight.Bold) }
                }
            }
            Spacer(Modifier.height(25.dp))
            Text("Jelajahi kategori", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            categories.chunked(if (tvLayout) 5 else 3).forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.forEach { item ->
                        FilterChip(selected = category == item, onClick = { category = item }, label = { Text(item) })
                    }
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(if (search.isBlank()) "Mulai dari sini" else "Hasil pencarian",
                color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { refreshCatalog() }, enabled = !feedLoading,
                colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Bg)) {
                Text(if (feedLoading) "Memuat katalog..." else "Muat katalog dari sumber")
            }
            Text(feedMessage, color = Muted, fontSize = 12.sp)
            feedMovies.filter { movie ->
                (category == "Semua" || movie.category.equals(category, ignoreCase = true)) &&
                    (search.isBlank() || movie.title.contains(search, ignoreCase = true) ||
                        movie.description.contains(search, ignoreCase = true))
            }.forEach { movie ->
                Card(Modifier.fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(movie.title, color = Color.White, fontWeight = FontWeight.Bold)
                        Text(movie.category, color = Green, fontSize = 11.sp)
                        Text(movie.description, color = Muted, fontSize = 13.sp)
                        Text("Lisensi: ${movie.licenseId}", color = Muted, fontSize = 10.sp)
                        Text("Ketentuan: ${movie.termsUrl}", color = Muted, fontSize = 10.sp)
                        Text(movie.attribution, color = Muted, fontSize = 10.sp)
                        if (movie.videoUrl.startsWith("https://")) {
                            TextButton(onClick = { selectedMovie = movie }) {
                                Text("Putar di GENJUR STREAM")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(26.dp))
            Text("GENJUR STREAM  •  Versi ${BuildConfig.VERSION_NAME}", color = Color(0xFF718096),
                fontSize = 11.sp, modifier = Modifier.padding(bottom = 10.dp))
            }
        }
    }

    selectedMovie?.let { movie ->
        InAppVideoPlayer(activity, movie, onClose = { selectedMovie = null })
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


private data class SavedSource(val name: String, val url: String)

@Composable
private fun GenjurStreamApp(activity: MainActivity) {
    var showSources by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        GenjurStreamHome(activity)
        Button(onClick = { showSources = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Bg)
        ) { Text("Sumber") }
        if (showSources) SourcesDialog(activity) { showSources = false }
    }
}

@Composable
private fun SourcesDialog(activity: MainActivity, onClose: () -> Unit) {
    val prefs = activity.getSharedPreferences("genjur_stream_sources", 0)
    fun readSources(): List<SavedSource> = runCatching {
        val a = JSONArray(prefs.getString("items", "[]"))
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            SavedSource(o.optString("name"), o.optString("url"))
        }
    }.getOrDefault(emptyList())
    var sources by remember { mutableStateOf(readSources()) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Sumber tontonan") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Tambahkan katalog dari sumber yang menyediakan API, RSS, atau feed resmi. URL website biasa belum tentu bisa dibaca otomatis.", color = Muted, fontSize = 12.sp)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama sumber") }, singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("URL HTTPS") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                Button(onClick = {
                    val n = name.trim(); val u = url.trim()
                    val valid = runCatching { Uri.parse(u).let { it.scheme == "https" && !it.host.isNullOrBlank() } }.getOrDefault(false)
                    when {
                        n.isBlank() -> error = "Masukkan nama sumber."
                        !valid -> error = "Masukkan URL HTTPS yang benar."
                        sources.any { it.url.equals(u, true) } -> error = "Sumber sudah ada."
                        else -> {
                            sources = sources + SavedSource(n, u)
                            val a = JSONArray()
                            sources.forEach { a.put(JSONObject().put("name", it.name).put("url", it.url)) }
                            prefs.edit().putString("items", a.toString()).apply()
                            name = ""; url = ""; error = null
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Green, contentColor = Bg)) { Text("Tambah sumber") }
                if (sources.isEmpty()) Text("Belum ada sumber tersimpan.", color = Muted)
                sources.forEachIndexed { index, item ->
                    Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(item.url, color = Muted, fontSize = 10.sp)
                            }
                            TextButton(onClick = {
                                sources = sources.filterIndexed { i, _ -> i != index }
                                val a = JSONArray()
                                sources.forEach { a.put(JSONObject().put("name", it.name).put("url", it.url)) }
                                prefs.edit().putString("items", a.toString()).apply()
                            }) { Text("Hapus") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Selesai") } }
    )
}
