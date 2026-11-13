PETUNJUK SERVER OHLI — VERSI SANGAT SINGKAT

SEBELUM MULAI
- Buat VM Ubuntu LTS di Oracle Cloud dan pastikan bentuk/volume bertanda Always Free.
- Pastikan VM memiliki Public IPv4.
- Buat subdomain DuckDNS dan salin tokennya.
- Di Oracle Console, buka inbound TCP 80 dan 443 dari 0.0.0.0/0. Batasi SSH 22 ke IP admin.

PASANG
1. Upload folder server ini ke VM menggunakan SFTP/SCP.
2. Masuk terminal SSH, buka folder server.
3. Jalankan: sudo bash install-one-click.sh
4. Ketik nama subdomain DuckDNS dan token ketika diminta.
5. Tunggu installer selesai, lalu buka URL https://...duckdns.org.

HASIL PERTAMA
Halaman yang terlihat adalah halaman status instalasi, BUKAN aplikasi OHLI Social. Ini memastikan DNS, HTTPS, reverse proxy, dan Node.js dasar merespons.

PASANG BACKEND ASLI
Upload ZIP backend OHLI Social ke VM, lalu jalankan:
  sudo bash deploy-backend.sh /lokasi/zip-backend-ohli.zip

ZIP backend harus memiliki satu package.json pada root atau satu subfolder dan scripts.start yang menjalankan server HTTP. Server harus mendengarkan HOST=127.0.0.1 dan PORT=8080 atau menghormati variabel HOST/PORT yang diberikan. Gunakan UPLOAD_DIR untuk file persisten dan DATABASE_URL bila backend mendukung PostgreSQL.

CEK
  sudo bash status.sh
  sudo journalctl -u ohli-social -f
  sudo journalctl -u caddy -f

CATATAN
- PostgreSQL lokal dibuat oleh installer dengan kredensial otomatis. Ambil DATABASE_URL dari /etc/ohli/ohli.env hanya melalui SSH admin.
- Bila backend lama memakai Supabase, jangan pindahkan database secara otomatis. Konfigurasikan backend dengan secret Supabase yang benar atau rencanakan migrasi database terpisah.
- Installer tidak tahu struktur backend OHLI Social tanpa source-nya. Periksa environment variable dan skema database aplikasi sebelum produksi.
