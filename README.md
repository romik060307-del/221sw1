# OHLI TUNEL v2.1.0 — Android Connector + VPS Production Pack

OHLI TUNEL sekarang menyediakan dua cara penggunaan yang dibedakan dengan jelas:

- **Produksi (disarankan):** jalankan backend OHLI Social dan konektor Cloudflare Named Tunnel pada VPS Linux 24/7. Paket deploy ada di folder `vps/`. Android tidak perlu terus menyala.
- **Android/local connector:** APK dapat meneruskan server lokal dari HP ke Named Tunnel untuk pengujian, demo, atau origin yang memang berjalan di perangkat tersebut. Ini bukan pilihan utama untuk situs sosial produksi yang harus tersedia sepanjang hari.

## Perubahan v2.1.0

- Menambahkan paket konektor VPS berbasis Docker Compose dengan kebijakan `restart: unless-stopped`.
- Menambahkan script instalasi, pemeriksaan status/log, dan restart konektor.
- Menambahkan template `systemd` untuk backend Node.js (harus disesuaikan dengan source OHLI Social yang sebenarnya).
- Menegaskan perbedaan Quick Tunnel sementara, Named Tunnel ber-hostname tetap, dan hosting backend permanen di VPS.
- Menampilkan peringatan mode produksi pada aplikasi Android.
- Menaikkan versi Android menjadi 2.1.0; minimum tetap Android 8.0 Oreo / API 26.

## Mengapa VPS, bukan HP?

Untuk layanan yang digunakan puluhan orang, backend harus berjalan pada mesin yang tetap mendapat daya, jaringan, dan proses server. Android dapat mematikan proses karena force-stop, pembatasan baterai pabrikan, perangkat mati, atau koneksi internet terputus. Wake Lock hanya membantu perangkat Android; tidak mengubah HP menjadi hosting dengan jaminan uptime.

VPS adalah server sewaan yang hidup terus selama akun/layanan aktif. Domain tetap diarahkan melalui Cloudflare Named Tunnel. Cloudflare Tunnel membuat koneksi keluar dari VPS ke jaringan Cloudflare, sehingga VPS tidak harus membuka port inbound untuk jalur aplikasi. Named Tunnel memiliki identitas dan hostname stabil, tidak seperti Quick Tunnel yang menghasilkan `trycloudflare.com` sementara.

Dokumentasi Cloudflare: [Tunnel fundamentals](https://developers.cloudflare.com/tunnel/concepts/), [Get started](https://developers.cloudflare.com/tunnel/get-started/), [Routing](https://developers.cloudflare.com/tunnel/concepts/routing/).

## Cara deploy produksi

Baca panduan lengkap bahasa Indonesia: [`vps/README-ID.md`](vps/README-ID.md). Ringkasnya:

1. Siapkan VPS Linux dan pasang Docker Engine + Compose plugin.
2. Pindahkan dan jalankan **source backend OHLI Social yang sebenarnya** di VPS, misalnya Node.js pada `127.0.0.1:8080`.
3. Buat Named Tunnel dan Public Hostname di Cloudflare, arahkan ke `http://127.0.0.1:8080` (sesuaikan port).
4. Salin token Named Tunnel ke `vps/.env` dan jalankan `bash vps/install.sh`.
5. Tes dari jaringan luar, lalu uji login, chat realtime, upload, dan restart VPS.

## Batas penting dari ZIP ini

ZIP yang diberikan untuk dikerjakan adalah source aplikasi **RK Tunnel Lite**, bukan source backend OHLI Social. Karena itu ZIP ini tidak memuat kode Node.js sosial, skema/migrasi database, autentikasi, WebSocket, ataupun penyimpanan media OHLI Social. Paket VPS di sini menjalankan konektor `cloudflared`; ia tidak membuat ulang atau otomatis memindahkan backend situs. Untuk migrasi situs yang sungguh-sungguh, source backend, file konfigurasi, database, dan storage media perlu dipasang serta diuji di VPS.

## Data, database, dan media

- Gunakan PostgreSQL persisten (managed atau dikelola di VPS) untuk data sosial/chat; lakukan backup otomatis ke tempat lain.
- Gunakan object storage S3-compatible atau volume persisten ter-backup untuk foto/video. Jangan mengandalkan filesystem container yang bisa hilang saat container diganti.
- Pastikan secret/API key disimpan di environment rahasia, bukan di repository publik.
- Untuk banyak koneksi realtime, uji kapasitas dan penggunaan memori; gunakan Redis/shared adapter bila backend benar-benar diskalakan ke beberapa instance Socket.IO.

## APK Android

APK tetap disediakan untuk koneksi lokal/sekunder. Jangan memakai token tunnel produksi yang sama pada HP dan VPS jika masing-masing connector mengarah ke origin aplikasi berbeda; Cloudflare memperlakukan keduanya sebagai connector untuk tunnel yang sama. Untuk produksi, cukup jalankan connector dari VPS.

## Build APK

1. Upload isi folder proyek ke root repository GitHub.
2. Buka **Actions → Build OHLI TUNEL APK → Run workflow**.
3. Unduh artifact `OHLI-TUNEL-APK` setelah workflow sukses.
4. Install pada Android 8.0 Oreo atau lebih baru.

Build GitHub Actions mengompilasi `cloudflared` untuk ARM64, ARMv7, x86_64, dan x86. Binary tidak disertakan di ZIP sumber karena dibuat saat workflow berjalan.

## Keandalan dan keamanan

- `restart: unless-stopped` memulihkan container tunnel setelah crash/reboot Docker; layanan Node.js harus memiliki service manager sendiri, misalnya systemd dengan `Restart=always`.
- Satu VPS tetap satu titik kegagalan. Untuk HA, siapkan aplikasi pada VPS kedua, shared database/storage, dan connector tambahan; connector kedua saja tidak otomatis membuat aplikasi/database kedua tersedia.
- VPS/domain/storage dapat berbiaya dan tidak ada jaminan uptime 100%.
- Token Named Tunnel adalah rahasia. Jangan mengunggah `.env`; jika bocor, rotasi token di dashboard Cloudflare.

## Status pengerjaan

Paket sumber Android dan paket konfigurasi VPS telah disiapkan. APK belum dibangun dan belum diuji pada HP dalam sesi ini. Backend OHLI Social juga belum dimigrasikan karena source backend dan data produksinya tidak terdapat di arsip yang diterima. Lakukan build, deploy, dan uji eksternal sebelum mengarahkan seluruh pengguna.
