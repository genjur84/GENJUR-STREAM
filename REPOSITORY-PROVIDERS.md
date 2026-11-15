# GENJUR STREAM — Repositori Penyedia Legal

Tambahkan URL berikut pada menu **Repositori Penyedia Film** di GENJUR STREAM:

`https://raw.githubusercontent.com/genjur84/GENJUR-STREAM/main/repository.json`

## Penyedia yang tersedia

- **Film Domain Publik — Wikimedia Commons**: katalog internal yang diambil langsung dari Wikimedia Commons. Aplikasi menyaring lisensi Public Domain, CC0, dan CC BY yang memenuhi ketentuan atribusi.
- **Internet Archive — Koleksi Film**: tautan ke situs resmi. Hak penggunaan berbeda untuk setiap item; periksa lisensi dan ketentuan item.

## Format repositori

Repositori menggunakan `schemaVersion: 1` dengan daftar `extensions`. Penyedia katalog internal memakai `feedUrl`; tautan layanan resmi memakai `websiteUrl`.

Repositori ini tidak menyertakan pengambil tautan streaming tidak berizin, tidak melewati DRM/login, dan tidak mengklaim semua judul bebas hak cipta.
