# GENJUR STREAM — Source & Plugin Repository

Repositori ini menyimpan metadata sumber dan identitas plugin yang dipakai aplikasi. Repositori tidak mengirim kode yang dieksekusi langsung oleh aplikasi.

## URL repositori
`https://raw.githubusercontent.com/genjur84/GENJUR-STREAM/main/source-repository.json`

## Format entri
Setiap sumber dapat menyertakan `pluginId`:

- `cmax`: plugin Cmax bawaan APK.
- `pending`: plugin belum tersedia; sumber tidak akan dipaksa dibuka sebagai katalog native.
- ID lain hanya dapat digunakan setelah plugin yang cocok tersedia dan terdaftar di APK.

Contoh:
```json
{
  "schemaVersion": 1,
  "name": "GENJUR STREAM Source & Plugin Repository",
  "sources": [
    {
      "id": "cmax",
      "name": "Cmax",
      "url": "https://cimax21.my/",
      "pluginId": "cmax",
      "category": "Film",
      "description": "Plugin katalog Cmax",
      "authorized": true
    }
  ]
}
```

## Arsitektur keamanan

1. APK memiliki kontrak `SourcePlugin` dan `SourcePluginRegistry`.
2. Plugin yang dapat berjalan harus sudah disertakan dan diizinkan di APK.
3. File repositori hanya mendeklarasikan metadata, bukan kode Kotlin/DEX atau skrip yang dieksekusi.
4. Sumber tanpa plugin yang cocok akan diberi status plugin belum tersedia; aplikasi tidak menganggap halaman situs sebagai katalog.
5. Plugin mengambil metadata katalog dan mengembalikan daftar `FeedMovie` untuk ditampilkan oleh antarmuka native GENJUR STREAM.

## Status tahap pertama

Plugin Cmax adalah implementasi pertama. Plugin Klikxxi, LK21, dan Duta Movie akan dibuat satu per satu setelah pengujian Cmax selesai.
