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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL

private const val UPDATE_INFO_URL = "https://raw.githubusercontent.com/genjur84/GENJUR-STREAM/main/latest.json"
private val Bg = Color(0xFF0D1117)
private val Panel = Color(0xFF171F2B)
private val Green = Color(0xFF42D6A4)
private val Muted = Color(0xFFACB7C5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { GenjurStreamHome(this) } }
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
    val scope = rememberCoroutineScope()
    val categories = listOf("Semua", "Film", "Serial", "Animasi", "Dokumenter")

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(UPDATE_INFO_URL).openConnection()
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
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("GENJUR STREAM", color = Green, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
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
                    Text("Semua hiburan\ndalam satu tempat.", color = Color.White, fontSize = 27.sp,
                        lineHeight = 33.sp, fontWeight = FontWeight.Bold)
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
            categories.chunked(3).forEach { group ->
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
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Panel)) {
                Column(Modifier.padding(18.dp)) {
                    Text(if (search.isBlank()) "Koleksi ${category.lowercase()}" else "Pencarian: $search",
                        color = Green, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Katalog tontonan akan ditampilkan di sini setelah sumber konten resmi ditambahkan.",
                        color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
            Spacer(Modifier.height(26.dp))
            Text("GENJUR STREAM  •  Versi ${BuildConfig.VERSION_NAME}", color = Color(0xFF718096),
                fontSize = 11.sp, modifier = Modifier.padding(bottom = 10.dp))
        }
    }
}
