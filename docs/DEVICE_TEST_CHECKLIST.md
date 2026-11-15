# Checklist Uji GENJUR STREAM

Ikuti urutan ini. Jangan lanjut ke pemasangan sebelum build Actions berstatus hijau.

## A. Build APK

1. Buka repository GENJUR-STREAM di GitHub.
2. Buka tab **Actions**.
3. Pilih **Android APK Build**.
4. Buka run terbaru. Pastikan job **Build debug APK** berstatus hijau.
5. Di bagian Artifacts, unduh `genjur-stream-debug-apk` dan ekstrak ZIP.
6. Pastikan file `app-debug.apk` tersedia.

Jika run merah, jangan pasang APK. Buka job yang gagal dan simpan pesan error untuk diperiksa.

## B. Uji HP Android

1. Pasang APK pada HP uji.
2. Buka aplikasi dan pastikan layar GENJUR STREAM tampil.
3. Geser layar ke bawah dan pastikan konten tidak terpotong.
4. Ketik kata pada kolom pencarian dan uji kategori.
5. Buka **Sumber**, lalu pastikan dialog dapat dibuka dan ditutup.
6. Tambahkan hanya feed HTTPS dari pemegang hak/penyedia yang mengizinkan distribusi.
7. Tekan **Muat katalog dari sumber**, lalu coba buka satu item yang memiliki video HTTPS.
8. Uji tombol kembali dan putar/jeda pada pemutar.

## C. Uji Android TV / TV box

1. Pindahkan APK yang sama ke TV/TV box dan pasang.
2. Buka aplikasi dengan remote.
3. Gunakan tombol arah untuk berpindah fokus dan OK untuk memilih.
4. Pastikan pencarian, kategori, tombol **Sumber**, dan tombol kembali dapat digunakan.
5. Putar konten uji berizin, lalu uji arah/OK/Back pada kontrol pemutar.
6. Periksa apakah teks dan tombol tetap terbaca dari jarak sofa.

## D. Catatan hasil

Catat model perangkat, versi Android, hasil build, dan bagian yang gagal. Jangan menghapus versi yang sudah terpasang jika APK baru ditolak karena tanda tangan berbeda; itu biasanya berarti signing key tidak sama. Gunakan key yang sama sebelum melakukan pembaruan instalasi.
