# OHLI TUNEL — Paket konektor VPS produksi

Paket ini memindahkan **konektor tunnel** dari HP ke VPS Linux yang selalu menyala. Ini bukan backend OHLI Social lengkap: ZIP sumber Android yang diterima hanya berisi aplikasi konektor, bukan source Node.js OHLI Social, skema database, atau sistem upload medianya.

## Arsitektur yang disarankan

Pengunjung → domain Cloudflare tetap → Named Cloudflare Tunnel → `cloudflared` di VPS → Node.js OHLI Social pada `127.0.0.1:8080`.

VPS harus tetap aktif dan langganannya dibayar. Domain juga harus tetap aktif. Tidak ada sistem yang dapat menjamin uptime 100% tanpa gangguan. Paket ini membuat konektor otomatis mulai bersama Docker dan otomatis restart jika container keluar.

## 1. Siapkan VPS

Gunakan VPS Linux Ubuntu 24.04 LTS atau distro Linux server yang didukung Docker. Untuk awal, pilih kapasitas yang dapat ditingkatkan; kebutuhan RAM/CPU sebenarnya bergantung pada jumlah pengguna aktif, realtime chat, dan pemrosesan media. Untuk produksi, siapkan backup di luar VPS.

Pasang Docker Engine dan Docker Compose plugin dari dokumentasi resmi:
- https://docs.docker.com/engine/install/
- https://docs.docker.com/compose/install/linux/

Aktifkan Docker saat boot:

```bash
sudo systemctl enable --now docker
```

## 2. Pasang backend OHLI Social pada VPS

Pindahkan **source backend OHLI Social yang sebenarnya** ke VPS, instal versi Node.js yang cocok dengan proyek, lalu jalankan sebagai service `systemd` atau container.

- Pastikan aplikasi mendengarkan pada `127.0.0.1:8080` (atau port lain yang kamu pilih).
- Jika menggunakan template `systemd/ohli-social.service.example`, sesuaikan `WorkingDirectory`, `User`, `ExecStart`, file environment, dan folder data/upload terlebih dahulu. Template itu tidak dapat langsung menggantikan source backend yang belum disertakan.
- Uji origin dari dalam VPS: `curl -i http://127.0.0.1:8080/`.
- Pastikan endpoint aplikasi, login, API, WebSocket/Socket.IO, dan upload media berjalan sebelum memasang domain publik.

Jangan menyalin database produksi tanpa backup. Untuk database, gunakan PostgreSQL persisten (managed atau dikelola sendiri) dan jalankan backup otomatis ke lokasi di luar VPS. Simpan media upload di object storage S3-compatible atau volume persisten yang dibackup; jangan mengandalkan filesystem container yang sementara.

## 3. Buat Named Tunnel dan domain tetap di Cloudflare

1. Domain milikmu harus ditambahkan dan DNS-nya dikelola di Cloudflare.
2. Buka https://one.dash.cloudflare.com/ lalu masuk ke **Networks → Tunnels**.
3. Buat **Named Tunnel** bernama misalnya `ohli-production`.
4. Tambahkan **Public Hostname**, misalnya `social.domainkamu.com`.
5. Atur Service type HTTP dan URL origin `http://127.0.0.1:8080` jika memakai port contoh di atas. Jika port Node.js berbeda, samakan.
6. Salin token melalui menu **Add a replica**. Perlakukan token seperti password.

Dokumentasi: https://developers.cloudflare.com/tunnel/get-started/ dan https://developers.cloudflare.com/tunnel/concepts/routing/.

## 4. Jalankan konektor di VPS

Salin folder `vps` ke VPS. Masuk ke folder tersebut, lalu jalankan:

```bash
cp .env.example .env
nano .env
chmod 600 .env
sudo bash install.sh
```

Isi `CLOUDFLARE_TUNNEL_TOKEN` di `.env` dengan token Named Tunnel yang benar. Script `install.sh` akan memeriksa Docker Compose, meminta token bila belum diisi, mengambil image resmi `cloudflare/cloudflared`, lalu menjalankan konektor dengan kebijakan `restart: unless-stopped`.

Compose menggunakan `network_mode: host` khusus Linux VPS, sehingga tujuan `127.0.0.1:8080` di dashboard Cloudflare menunjuk ke layanan pada network namespace host VPS. Jangan gunakan konfigurasi ini pada Docker Desktop Windows/macOS.

Perintah pengelolaan:

```bash
sudo bash status.sh       # status dan log
sudo bash restart.sh      # restart konektor
```

Untuk menghentikan:

```bash
docker compose down
```

Jangan pernah mengunggah `.env` ke GitHub atau mengirim token ke orang lain. Jika token bocor, rotasi token dari dashboard Cloudflare dan perbarui `.env`.

## 5. Tes sebelum mengumumkan link

1. Pastikan backend Node.js aktif pada origin lokal.
2. Pastikan container `ohli-tunel` hidup: `sudo bash status.sh`.
3. Pastikan dashboard Cloudflare menunjukkan konektor tersambung.
4. Buka hostname dari HP lain menggunakan jaringan seluler (bukan Wi-Fi VPS).
5. Tes login, kirim pesan realtime, balas komentar, unggah gambar/video, dan muat ulang halaman.
6. Reboot VPS dan pastikan backend serta konektor aktif kembali.
7. Lakukan uji beban bertahap dan siapkan pemantauan uptime sebelum migrasi seluruh pengguna.

## 6. Untuk ketersediaan lebih tinggi

Satu VPS tetap memiliki satu titik kegagalan. Setelah layanan stabil, siapkan VPS kedua dengan aplikasi versi sama, database dan object storage bersama, serta konektor tambahan pada Named Tunnel yang sama. Cloudflare mendukung beberapa connector/replica pada satu tunnel; setiap replica harus dapat menjangkau origin aplikasi yang benar. Untuk failover aplikasi yang sungguh-sungguh, database, media, sesi, dan job realtime juga harus dirancang bersama—menambah connector saja tidak membuat backend kedua otomatis tersedia.

Rujukan: https://developers.cloudflare.com/tunnel/concepts/ dan https://developers.cloudflare.com/tunnel/reference/tunnel-tokens/.

## 7. Peran APK OHLI TUNEL Android

APK Android yang ada tetap merupakan **konektor lokal dari HP ke Named Tunnel**. Itu berguna untuk pengujian atau meneruskan layanan yang memang berjalan di HP. Untuk server utama OHLI Social, jalankan konektor di VPS seperti panduan ini dan jangan jalankan token tunnel yang sama dari HP ke origin berbeda secara bersamaan. HP tidak lagi menjadi syarat agar website tetap online.

## Batas yang perlu dipahami

- Tunnel memberi jalur publik tetap; tunnel tidak meng-host kode backend dan tidak menyalakan aplikasi Node.js yang mati.
- VPS, domain, database, dan object storage adalah layanan terpisah dan dapat berbiaya.
- `restart: unless-stopped` membantu pemulihan container, bukan jaminan 100% uptime.
- Source backend OHLI Social belum ada di ZIP ini. Untuk benar-benar memigrasikan situs dan data, backend, konfigurasi environment, database, serta storage media harus dipindahkan dan diuji secara terpisah.
