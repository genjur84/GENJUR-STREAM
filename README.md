# GENJUR STREAM

Satu aplikasi Android untuk HP dan Android TV. Aplikasi menggunakan Jetpack Compose untuk antarmuka dan AndroidX Media3/ExoPlayer untuk pemutaran video.

## Status saat ini

- Versi aplikasi pada konfigurasi: **0.5.1** (versionCode 6).
- Paket: `com.genjur.stream`.
- Minimum Android: API 23.
- Katalog, pencarian, kategori, daftar sumber feed HTTPS, pemutar Media3, dan pemeriksaan metadata pembaruan sudah ada di aplikasi.
- Tata letak awal menyesuaikan lebar layar; komponen Compose dapat dijelajahi dengan remote D-pad, tetapi perlu diuji pada perangkat TV nyata.
- Feed sumber sekarang wajib menyertakan metadata izin distribusi, lisensi, tautan ketentuan, dan atribusi. Lihat [kontrak feed sumber](docs/SOURCE_FEED_SPEC.md).

**Catatan versi:** repository sudah berada di 0.5.1. Jangan menurunkan nomor versi ke 0.1.0 karena dapat mengganggu jalur pembaruan APK yang sudah terpasang.

## Build APK melalui GitHub

1. Buka tab **Actions** pada repository.
2. Pilih workflow **Android APK Build**.
3. Tekan **Run workflow** pada branch `main`, atau tunggu build otomatis setelah perubahan kode didorong ke `main`.
4. Buka run yang selesai dengan status hijau.
5. Di bagian **Artifacts**, unduh `genjur-stream-debug-apk`, lalu ekstrak file APK.

Workflow memakai Java 17 dan Gradle 8.9. APK dibuat dari task `:app:assembleDebug`.

### Signing key

Jika secret `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, dan `ANDROID_KEY_PASSWORD` tersedia di GitHub Actions, build menggunakan signing key stabil tersebut. Tanpa secret itu, runner memakai debug key bawaan.

Untuk memasang APK sebagai pembaruan di atas aplikasi yang sudah ada, signing key harus sama dengan key yang dipakai saat memasang versi sebelumnya. Jangan membuat key baru jika ingin mempertahankan kemampuan update tanpa menghapus aplikasi lama.

## Uji awal

### HP Android
1. Unduh APK artifact dan pasang.
2. Buka GENJUR STREAM.
3. Uji kolom pencarian, kategori, tombol **Sumber**, dan tombol kembali.
4. Tambahkan hanya feed HTTPS dari pemegang hak atau penyedia yang memberi izin distribusi.

### Android TV / TV box
1. Pindahkan APK yang sama ke TV/TV box.
2. Pasang dan buka aplikasi.
3. Gunakan remote: tombol arah untuk berpindah fokus, OK untuk memilih, dan Back untuk kembali.
4. Periksa apakah kolom sumber, kategori, daftar tontonan, dan kontrol pemutar mudah dijangkau.

Pengujian remote fisik dan kompatibilitas setiap merek TV tetap perlu dilakukan; build berhasil tidak otomatis membuktikan semua perangkat TV kompatibel.

## Rencana berikutnya

Lihat [roadmap arsitektur](docs/ROADMAP.md) untuk favorit, riwayat tontonan, katalog lintas sumber, dan penyempurnaan navigasi TV.

## Batas penggunaan sumber

GENJUR STREAM tidak dibuat untuk mengikis situs, melewati login/DRM, atau memutar tautan film berhak cipta tanpa izin. Feed JSON adalah data katalog, bukan kode plugin yang dieksekusi.
