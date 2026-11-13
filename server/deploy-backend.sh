#!/usr/bin/env bash
set -Eeuo pipefail
log(){ printf '[OHLI DEPLOY] %s\n' "$*"; }
die(){ printf '[OHLI DEPLOY] ERROR: %s\n' "$*" >&2; exit 1; }
[[ "$(id -u)" -eq 0 ]] || { echo 'Jalankan: sudo bash deploy-backend.sh /path/backend.zip' >&2; exit 1; }
ARCHIVE="${1:-}"
[[ -n "$ARCHIVE" && -f "$ARCHIVE" ]] || die 'Masukkan lokasi ZIP source backend OHLI Social.'
command -v unzip >/dev/null 2>&1 || die 'unzip belum terpasang. Jalankan installer utama dulu.'
[[ -f /etc/ohli/ohli.env ]] || die 'Server belum disiapkan. Jalankan install-one-click.sh dulu.'
TMP="$(mktemp -d /tmp/ohli-deploy.XXXXXX)"
trap 'rm -rf "$TMP"' EXIT
unzip -q "$ARCHIVE" -d "$TMP/unpacked" || die 'ZIP tidak valid.'
mapfile -t PKGS < <(find "$TMP/unpacked" -mindepth 1 -maxdepth 4 -type f -name package.json -printf '%h\n' | sort -u)
[[ "${#PKGS[@]}" -eq 1 ]] || die 'ZIP harus mempunyai tepat satu package.json backend pada folder utama atau satu subfolder.'
SRC="${PKGS[0]}"
node -e "const p=require(process.argv[1]); if(!p.scripts || !p.scripts.start) process.exit(2)" "$SRC/package.json" || die 'package.json harus memiliki scripts.start.'
if [[ -f "$SRC/.env" ]]; then log 'Menemukan .env di ZIP. Mengabaikannya demi keamanan; gunakan /etc/ohli/ohli.env.'; rm -f "$SRC/.env"; fi
if [[ -d /opt/ohli/app && -f /opt/ohli/app/package.json ]]; then
  mkdir -p /var/backups/ohli/deploy
  tar -czf "/var/backups/ohli/deploy/app-before-$(date -u +%Y%m%dT%H%M%SZ).tar.gz" -C /opt/ohli app || true
fi
rm -rf /opt/ohli/app.new
install -d -o ohli -g ohli -m 0750 /opt/ohli/app.new
rsync -a --delete "$SRC/" /opt/ohli/app.new/
chown -R ohli:ohli /opt/ohli/app.new
if [[ -f /opt/ohli/app.new/package-lock.json || -f /opt/ohli/app.new/npm-shrinkwrap.json ]]; then
  runuser -u ohli -- npm --prefix /opt/ohli/app.new ci --omit=dev --no-audit --no-fund
else
  runuser -u ohli -- npm --prefix /opt/ohli/app.new install --omit=dev --no-audit --no-fund
fi
if [[ -f /opt/ohli/app.new/.npmrc ]]; then rm -f /opt/ohli/app.new/.npmrc; fi
rm -rf /opt/ohli/app.old
if [[ -d /opt/ohli/app ]]; then mv /opt/ohli/app /opt/ohli/app.old; fi
mv /opt/ohli/app.new /opt/ohli/app
chown -R ohli:ohli /opt/ohli/app
systemctl daemon-reload
systemctl enable ohli-social
systemctl restart ohli-social
sleep 3
if ! systemctl is-active --quiet ohli-social; then
  systemctl --no-pager --full status ohli-social || true
  journalctl -u ohli-social -n 80 --no-pager || true
  die 'Backend gagal start. Folder app.old tetap tersedia untuk pemulihan manual.'
fi
log 'Backend mulai berjalan. Periksa endpoint dan login/chat/upload dari perangkat lain sebelum mengalihkan semua pengguna.'
