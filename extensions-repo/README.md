# GENJUR EXTENSIONS

Repositori data ekstensi untuk GENJUR STREAM. Ekstensi memakai format JSON deklaratif; repositori ini **tidak menjalankan kode/plugin jarak jauh**.

## URL repositori

`https://raw.githubusercontent.com/genjur84/GENJUR-STREAM/main/extensions-repo/repo.json`

Tambahkan URL tersebut melalui **Pengaturan → Repositori → Tambah repositori**.

## Struktur

- `repo.json`: daftar ekstensi dan URL feed masing-masing.
- `feeds/public-domain.json`: feed katalog untuk konten domain publik atau konten yang memiliki izin distribusi.

## Format feed

Setiap feed wajib menyediakan objek `rights`:
- `redistributionAllowed`: harus `true`.
- `licenseId`: identitas lisensi yang jelas.
- `termsUrl`: URL HTTPS ke ketentuan/lisensi.
- `attribution`: atribusi sumber.

Setiap item katalog dapat berisi:
- `title`, `description`, `category`
- `posterUrl` (HTTPS)
- `videoUrl` (tautan HTTPS langsung ke MP4 atau HLS/M3U8 yang berizin)
- `releaseYear`, `genres`, `duration`
- `downloadUrl` dan `downloadAllowed` (izin unduh terpisah)

## Catatan kompatibilitas dan keamanan

GENJUR STREAM saat ini membaca feed JSON dan memutar tautan media langsung yang didukung pemutar internal. File plugin CloudStream `.cs3` tidak dieksekusi. Jangan memasukkan tautan streaming tanpa hak distribusi/pemutaran yang jelas. Feed kosong ini adalah kerangka awal; katalog akan diisi setelah sumber video dan lisensinya diverifikasi.
