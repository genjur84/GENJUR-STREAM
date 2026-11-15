# GENJUR MOVIE PROVIDERS

Repositori penyedia situs/katalog film untuk GENJUR STREAM. Satu entri ekstensi mewakili satu penyedia (provider). Setelah dipasang, URL feed penyedia akan dibaca GENJUR dan item katalog tampil di aplikasi.

## URL repositori

`https://raw.githubusercontent.com/genjur84/GENJUR-STREAM/main/extensions-repo/repo.json`

Tambahkan melalui **Pengaturan → Repositori → Tambah repositori**.

## Cara kerja

1. Repositori menampilkan daftar penyedia film.
2. Pengguna memasang penyedia yang diinginkan.
3. GENJUR membaca katalog dari feed penyedia.
4. Pengguna membuka detail dan memutar tautan MP4/HLS langsung yang kompatibel.

## Format

- `repo.json`: daftar penyedia dalam array `extensions`.
- Setiap penyedia memiliki `id`, `name`, `description`, dan `feedUrl`.
- Feed penyedia menggunakan format JSON GENJUR dan wajib mencantumkan metadata hak distribusi, lisensi, terms URL HTTPS, dan atribusi.
- Item dapat menyertakan `posterUrl`, `videoUrl`, metadata film, dan izin unduh terpisah.

## Kompatibilitas

GENJUR saat ini menggunakan provider berbasis data JSON. Ini memungkinkan katalog dan tautan video langsung yang sah diputar melalui pemutar internal. Plugin CloudStream `.cs3` hanya dapat dikenali metadatanya; kode plugin tidak dieksekusi. Untuk setiap situs yang ingin didukung, perlu dibuat adapter/provider GENJUR yang aman dan mematuhi hak akses situs tersebut.

## Hak cipta dan keamanan

Jangan menambahkan tautan streaming tanpa hak pemutaran/distribusi yang jelas. Jangan mengeksekusi kode provider jarak jauh. Feed awal masih berupa kerangka kosong sampai sumber video yang sah diverifikasi.
