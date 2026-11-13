OHLI TUNEL - SATU REPOSITORY GITHUB
Paket gabungan v3.1.0

CARA UPLOAD (SATU KALI):
1. Ekstrak ZIP ini di komputer.
2. Buka folder OHLI-TUNEL-GITHUB-ONE-FOLDER-v3.1.0.
3. Buat repository GitHub kosong bernama OHLI-TUNEL.
4. Upload SEMUA ISI folder ini ke ROOT repository GitHub.
   Jangan upload folder pembungkusnya sebagai subfolder.
   Folder .github wajib ikut ter-upload.
5. Buka tab Actions. Workflow bernama "Build OHLI TUNEL APK" akan berjalan
   otomatis saat push ke branch main/master, atau bisa dijalankan manual.
6. Setelah berhasil, buka hasil workflow dan unduh artifact OHLI-TUNEL-APK.

ISI REPOSITORY:
- app/, build.gradle, settings.gradle, gradle.properties: source Android.
- .github/workflows/build-apk.yml: pembuat APK otomatis di GitHub Actions.
- server/: installer host Ubuntu, template Node.js, service systemd, backup,
  status, dan skrip untuk memasang backend.
- vps/: konfigurasi pendukung VPS dari source Android.
- docs/: batasan dan catatan paket.

PENTING - JANGAN TERTUKAR:
A. Upload satu folder ini ke GitHub lalu Actions membangun APK Android.
B. Folder server ikut ada di repository yang sama, tetapi GitHub tidak otomatis
   menjalankan server 24 jam dan folder server tidak dimasukkan ke dalam APK.
   Untuk membuat OHLI Social online, isi folder server harus dipasang pada
   mesin Ubuntu/VPS. Paket ini belum menyertakan source backend OHLI Social
   asli (feed/chat/login/upload milik situsmu); bootstrap-app hanya halaman
   pemeriksaan/status awal, bukan pengganti website sosialmu.
C. Jangan memasukkan token DuckDNS, password database, private key, atau
   rahasia lain ke GitHub. Masukkan rahasia hanya di server privat.
D. APK belum diklaim berhasil dibangun atau diuji di HP. Status build nyata
   terlihat di GitHub Actions.

TARGET APK:
- Minimum Android 8.0 (API 26).
- Nama aplikasi: OHLI TUNEL.
- Build debug APK menggunakan Java 17, Android SDK 35, Gradle 8.9.

Untuk panduan server, baca server/README-SERVER-ID.txt dan
vps/README-ID.md.


KOMPATIBILITAS VIVO Y71 / ANDROID OREO:
- applicationId baru: com.ohli.tunel (menghindari konflik tanda tangan dengan APK lama).
- minimum Android 8.0 / API 26; targetSdk 28 untuk menghindari perubahan perilaku target Android terbaru pada perangkat Oreo.
- APK universal debug; instalasi baru dapat berdampingan dengan aplikasi lama yang memakai ID com.rk.tunnel.
- Jika ada kegagalan instalasi, kirim teks lengkap pesan Android atau logcat INSTALL_FAILED.
