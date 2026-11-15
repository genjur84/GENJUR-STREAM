# GENJUR STREAM — Kontrak Feed Sumber Legal

## Prinsip

GENJUR STREAM menggunakan feed katalog JSON melalui HTTPS. Feed adalah **data**, bukan kode plugin yang dieksekusi di dalam aplikasi. Aplikasi tidak mengikis halaman situs, tidak melewati login, tidak mengakali DRM, dan tidak mengambil tautan dari layanan yang tidak memberi izin distribusi.

Pemilik/pengelola sumber harus memiliki hak untuk menayangkan atau mendistribusikan setiap video. Metadata hak di bawah merupakan pernyataan dari penyedia; aplikasi tidak dapat membuktikan kepemilikan hak hanya dari JSON. Pengguna tetap wajib memeriksa izin dan ketentuan sumber.

## Format minimum

```json
{
  "schemaVersion": 1,
  "provider": {
    "id": "contoh-penyedia",
    "name": "Nama pemegang hak",
    "website": "https://example.org"
  },
  "rights": {
    "redistributionAllowed": true,
    "licenseId": "CC-BY-4.0",
    "termsUrl": "https://creativecommons.org/licenses/by/4.0/",
    "attribution": "Nama pemegang hak — judul karya"
  },
  "items": [
    {
      "id": "karya-001",
      "title": "Judul karya",
      "description": "Deskripsi singkat",
      "category": "Film",
      "videoUrl": "https://media.example.org/karya-001.mp4"
    }
  ]
}
```

## Pemeriksaan yang dilakukan aplikasi

- Feed harus menyediakan objek `rights`.
- `redistributionAllowed` harus bernilai `true`.
- `licenseId`, `termsUrl`, dan `attribution` harus terisi.
- `termsUrl` dan setiap `videoUrl` harus menggunakan HTTPS.
- Item tanpa URL video boleh tampil sebagai informasi katalog, tetapi tidak dapat diputar.
- Jika metadata hak tidak lengkap, feed ditolak dan item tidak dimuat.

## Catatan lisensi

- CC BY dan lisensi lain dapat memiliki syarat atribusi yang berbeda. Tampilkan kredit yang diminta pemegang hak.
- Domain HTTPS atau video yang bisa diputar **bukan** bukti izin distribusi.
- Jangan menambahkan sumber yang menautkan film berhak cipta tanpa izin, meskipun tautannya tersedia secara publik.
- Untuk sumber komersial, gunakan API/feed resmi dan perjanjian distribusi dari penyedia.

## Batas versi saat ini

Versi aplikasi saat ini memeriksa pernyataan hak pada tingkat feed. Pemeriksaan ini belum merupakan verifikasi hukum independen dan belum menggantikan audit per-item atau daftar penyedia tepercaya.
