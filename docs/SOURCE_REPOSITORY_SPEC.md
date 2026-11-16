# GENJUR STREAM — Source Repository

Repositori ini adalah katalog tautan sumber yang dapat dibaca aplikasi. Isi repositori berupa JSON biasa; aplikasi tidak menjalankan kode dari katalog.

## URL katalog bawaan

`https://raw.githubusercontent.com/genjur84/GENJUR-STREAM/main/source-repository.json`

## Format

```json
{
  "schemaVersion": 1,
  "name": "GENJUR STREAM Source Repository",
  "updatedAt": "2026-11-16",
  "sources": [
    {
      "id": "contoh-sumber",
      "name": "Contoh Sumber Resmi",
      "url": "https://example.org/",
      "category": "Streaming resmi",
      "description": "Situs resmi penyedia konten berizin",
      "authorized": true
    }
  ]
}
```

## Aturan katalog

- Hanya URL HTTPS dengan host yang valid yang dimuat.
- Entri harus memiliki `authorized: true`; entri lain dilewati.
- `id` harus stabil dan unik. Jika kosong, aplikasi memakai host sebagai identitas.
- Nama dan URL wajib tersedia.
- Aplikasi tidak mengunduh atau menyalin konten dari tautan katalog. Tautan hanya ditambahkan ke menu Daftar Sumber setelah dipilih pengguna.
- Pastikan pengelola katalog memiliki izin untuk mencantumkan dan mendistribusikan tautan tersebut serta mematuhi ketentuan layanan terkait.

Setelah katalog diperbarui di branch `main`, pengguna dapat menekan **Muat repositori** di menu Sumber untuk mengambil daftar terbaru.
```
