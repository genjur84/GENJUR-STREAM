# GENJUR STREAM — Arsitektur dan Rencana

## Kondisi repository

Repository ini sudah berada pada versi aplikasi **0.5.1** (versionCode 6). Versi tidak diturunkan ke 0.1.0 agar urutan pembaruan APK dan kompatibilitas instalasi tetap terjaga. Versi 0.1.0 dapat diperlakukan sebagai milestone awal historis.

## Struktur saat ini

- `settings.gradle.kts` — konfigurasi modul Gradle.
- `build.gradle.kts` — versi plugin Android/Kotlin.
- `app/build.gradle.kts` — namespace, SDK, versi aplikasi, Compose, dan Media3.
- `app/src/main/AndroidManifest.xml` — izin internet serta launcher HP dan Android TV.
- `app/src/main/java/com/genjur/stream/MainActivity.kt` — layar utama, katalog feed, pencarian/kategori, pengelolaan sumber, pemeriksaan update, dan pemutar Media3.
- `app/src/main/res/xml/file_paths.xml` — jalur FileProvider untuk pemasang update.
- `.github/workflows/android-build.yml` — build APK debug melalui GitHub Actions.
- `docs/SOURCE_FEED_SPEC.md` — format feed JSON dan aturan sumber legal.

## Sasaran antarmuka

- Satu APK untuk HP Android dan Android TV.
- Tata letak memakai lebar layar: layar lebar mendapat ruang konten lebih besar; layar HP tetap satu kolom.
- Komponen Compose yang dapat diaktifkan mendukung fokus keyboard/remote D-pad; pengujian remote fisik tetap diperlukan.
- Pemutar menggunakan AndroidX Media3/ExoPlayer.

## Rencana pengembangan

1. **Build & smoke test** — pastikan APK dapat dibangun, dipasang, dibuka, dan dinavigasi dengan sentuh serta remote.
2. **Favorit & riwayat** — simpan item favorit, progres/terakhir ditonton, dan riwayat lokal menggunakan penyimpanan lokal.
3. **Multi-sumber yang aman** — registri feed JSON HTTPS, informasi penyedia, lisensi, atribusi, status sumber, dan penghapusan sumber.
4. **Katalog lebih lengkap** — poster, detail, filter, pencarian lintas sumber, dan penanganan sumber yang gagal.
5. **TV polish** — urutan fokus, indikator fokus yang jelas, tata letak lanskap, dan uji remote pada beberapa perangkat.
6. **Rilis stabil** — signing key yang sama untuk semua pembaruan, nomor versi naik, changelog, dan APK rilis.

## Build otomatis

GitHub Actions menjalankan `gradle :app:assembleDebug` dengan Java 17 dan Gradle 8.9, lalu mengunggah APK sebagai artifact. Jika belum ada signing key stabil, build memakai debug key bawaan runner. Untuk pembaruan di atas instalasi yang sudah ada, gunakan signing key yang sama; APK dari debug key runner yang berbeda mungkin tidak dapat dipasang sebagai pembaruan.
