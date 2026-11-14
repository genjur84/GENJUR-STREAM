package com.genjur.stream

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

private val Bg = Color(0xFF0D1117)
private val Panel = Color(0xFF171F2B)
private val Green = Color(0xFF42D6A4)
private val Muted = Color(0xFFACB7C5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { GenjurStreamHome() } }
    }
}

@Composable
fun GenjurStreamHome() {
    var search by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Semua") }
    val categories = listOf("Semua", "Film", "Serial", "Animasi", "Dokumenter")

    Surface(Modifier.fillMaxSize(), color = Bg) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
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
                        shape = RoundedCornerShape(12.dp)) {
                        Text("Jelajahi sekarang", fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(25.dp))
            Text("Jelajahi kategori", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            categories.chunked(3).forEach { group ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.forEach { item ->
                        FilterChip(selected = category == item, onClick = { category = item },
                            label = { Text(item) })
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
            Text("GENJUR STREAM  •  Versi 0.1.0", color = Color(0xFF718096),
                fontSize = 11.sp, modifier = Modifier.padding(bottom = 10.dp))
        }
    }
}
