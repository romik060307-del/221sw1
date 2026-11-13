#!/usr/bin/env bash
set -Eeuo pipefail

# OHLI FREE SERVER PACK v3.0.0
# Ubuntu 22.04 / 24.04 / 26.04. Run: sudo bash install-one-click.sh

log(){ printf '\n[OHLI] %s\n' "$*"; }
die(){ printf '\n[OHLI] ERROR: %s\n' "$*" >&2; exit 1; }

[[ "$(id -u)" -eq 0 ]] || die 'Jalankan dengan: sudo bash install-one-click.sh'
[[ -f /etc/os-release ]] || die 'OS tidak dikenali.'
. /etc/os-release
[[ "${ID:-}" == ubuntu ]] || die "Hanya Ubuntu yang didukung. OS: ${PRETTY_NAME:-unknown}"
case "${VERSION_ID:-}" in 22.04|24.04|26.04) ;; *) die "Gunakan Ubuntu 22.04/24.04/26.04 LTS. Terdeteksi: ${VERSION_ID:-unknown}";; esac

export DEBIAN_FRONTEND=noninteractive
log 'Memasang kebutuhan dasar...'
apt-get update
apt-get install -y ca-certificates curl gnupg unzip rsync postgresql postgresql-contrib ufw unattended-upgrades openssl

log 'Memasang Node.js 24 LTS...'
NODE_MAJOR="$(node -p 'Number(process.versions.node.split(".")[0])' 2>/dev/null || echo 0)"
if [[ "$NODE_MAJOR" -ne 24 ]]; then
  curl -fsSL https://deb.nodesource.com/setup_24.x | bash -
  apt-get install -y nodejs
fi
node -e 'const m=Number(process.versions.node.split(".")[0]); if(m!==24) process.exit(1)'

log 'Memasang Caddy dari repositori resmi...'
install -d -m 0755 /usr/share/keyrings
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor --yes -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' -o /etc/apt/sources.list.d/caddy-stable.list
chmod 0644 /usr/share/keyrings/caddy-stable-archive-keyring.gpg /etc/apt/sources.list.d/caddy-stable.list
apt-get update
apt-get install -y caddy

log 'Menyiapkan user, folder aplikasi, PostgreSQL, dan rahasia...'
if ! id ohli >/dev/null 2>&1; then useradd --system --create-home --home-dir /opt/ohli --shell /usr/sbin/nologin ohli; fi
install -d -o ohli -g ohli -m 0750 /opt/ohli/app /var/lib/ohli/uploads /var/log/ohli
install -d -o root -g root -m 0750 /etc/ohli
install -d -o root -g root -m 0700 /var/backups/ohli
systemctl enable --now postgresql

DB_NAME='ohli_social'
DB_USER='ohli_app'
ROLE_EXISTS=0
if runuser -u postgres -- psql -tAc "SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'" | grep -q 1; then ROLE_EXISTS=1; fi
DB_PASS=''
if [[ ! -f /etc/ohli/ohli.env ]]; then
  DB_PASS="$(openssl rand -hex 24)"
fi
if [[ "$ROLE_EXISTS" -eq 0 ]]; then
  [[ -n "$DB_PASS" ]] || DB_PASS="$(openssl rand -hex 24)"
  runuser -u postgres -- psql -v ON_ERROR_STOP=1 -c "CREATE ROLE ${DB_USER} LOGIN PASSWORD '${DB_PASS}'"
elif [[ -n "$DB_PASS" ]]; then
  # First install only: create a password that can be saved in the private env file.
  runuser -u postgres -- psql -v ON_ERROR_STOP=1 -c "ALTER ROLE ${DB_USER} WITH LOGIN PASSWORD '${DB_PASS}'"
fi
if ! runuser -u postgres -- psql -tAc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1; then
  runuser -u postgres -- createdb --owner="$DB_USER" "$DB_NAME"
fi

log 'Menyiapkan subdomain DuckDNS...'
if [[ ! -s /etc/ohli/duckdns.env ]]; then
  read -r -p 'Nama subdomain DuckDNS (contoh ohli-social, tanpa .duckdns.org): ' DUCK_SUB
  [[ "$DUCK_SUB" =~ ^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?$ ]] || die 'Nama subdomain tidak valid.'
  read -r -s -p 'Token DuckDNS (tidak akan ditampilkan): ' DUCK_TOKEN; printf '\n'
  [[ "$DUCK_TOKEN" =~ ^[A-Za-z0-9-]{12,128}$ ]] || die 'Format token DuckDNS tidak valid.'
  printf 'DUCKDNS_SUBDOMAIN=%s\nDUCKDNS_TOKEN=%s\n' "$DUCK_SUB" "$DUCK_TOKEN" > /etc/ohli/duckdns.env
  chmod 0600 /etc/ohli/duckdns.env
  unset DUCK_TOKEN
fi
source /etc/ohli/duckdns.env
DOMAIN="${DUCKDNS_SUBDOMAIN}.duckdns.org"

# Secret values are regenerated only on first install; preserve existing app secrets on reruns.
if [[ ! -f /etc/ohli/ohli.env ]]; then
  SESSION_SECRET="$(openssl rand -hex 32)"
  cat > /etc/ohli/ohli.env <<ENV
NODE_ENV=production
HOST=127.0.0.1
PORT=8080
PUBLIC_URL=https://${DOMAIN}
UPLOAD_DIR=/var/lib/ohli/uploads
DATABASE_URL=postgresql://${DB_USER}:${DB_PASS}@127.0.0.1:5432/${DB_NAME}
SESSION_SECRET=${SESSION_SECRET}
ENV
  chmod 0600 /etc/ohli/ohli.env
else
  sed -i "s|^PUBLIC_URL=.*|PUBLIC_URL=https://${DOMAIN}|" /etc/ohli/ohli.env
  # If the database role was missing during a repair/reinstall, rotate its generated password and URL together.
  if [[ -n "$DB_PASS" ]]; then
    sed -i "s|^DATABASE_URL=.*|DATABASE_URL=postgresql://${DB_USER}:${DB_PASS}@127.0.0.1:5432/${DB_NAME}|" /etc/ohli/ohli.env
  fi
fi

log 'Menyiapkan updater DuckDNS otomatis...'
cat > /usr/local/sbin/ohli-duckdns-update <<'UPDATER'
#!/usr/bin/env bash
set -Eeuo pipefail
source /etc/ohli/duckdns.env
result="$(curl --fail --silent --show-error --get \
  --data-urlencode "domains=${DUCKDNS_SUBDOMAIN}" \
  --data-urlencode "token=${DUCKDNS_TOKEN}" \
  'https://www.duckdns.org/update')"
[[ "$result" == OK ]] || { echo "DuckDNS update failed: ${result}" >&2; exit 1; }
echo "DuckDNS updated: ${DUCKDNS_SUBDOMAIN}.duckdns.org"
UPDATER
chmod 0750 /usr/local/sbin/ohli-duckdns-update

cat > /etc/systemd/system/ohli-duckdns-update.service <<'UNIT'
[Unit]
Description=Update OHLI DuckDNS IPv4 record
Wants=network-online.target
After=network-online.target
[Service]
Type=oneshot
ExecStart=/usr/local/sbin/ohli-duckdns-update
UNIT
cat > /etc/systemd/system/ohli-duckdns-update.timer <<'UNIT'
[Unit]
Description=Refresh OHLI DuckDNS every five minutes
[Timer]
OnBootSec=30s
OnUnitActiveSec=5min
RandomizedDelaySec=15s
Persistent=true
[Install]
WantedBy=timers.target
UNIT

# Bootstrap landing server is not OHLI Social; it is only a public connectivity check until backend is deployed.
if [[ ! -f /opt/ohli/app/package.json ]]; then
  cp -a "$(dirname "$0")/bootstrap-app/." /opt/ohli/app/
  chown -R ohli:ohli /opt/ohli/app
fi

log 'Membuat layanan Node.js yang otomatis hidup kembali...'
cat > /etc/systemd/system/ohli-social.service <<'UNIT'
[Unit]
Description=OHLI Social Node.js application
Wants=network-online.target postgresql.service
After=network-online.target postgresql.service
[Service]
Type=simple
User=ohli
Group=ohli
WorkingDirectory=/opt/ohli/app
EnvironmentFile=/etc/ohli/ohli.env
ExecStart=/usr/bin/npm run start
Restart=always
RestartSec=5
TimeoutStopSec=30
KillSignal=SIGINT
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=full
ReadWritePaths=/var/lib/ohli /var/log/ohli /opt/ohli/app
[Install]
WantedBy=multi-user.target
UNIT
chown -R ohli:ohli /opt/ohli/app
runuser -u ohli -- npm --prefix /opt/ohli/app install --omit=dev --no-audit --no-fund

log 'Mengatur HTTPS Caddy dan reverse proxy...'
cat > /etc/caddy/Caddyfile <<CADDY
${DOMAIN} {
    encode zstd gzip
    reverse_proxy 127.0.0.1:8080
}
CADDY
caddy fmt --overwrite /etc/caddy/Caddyfile
caddy validate --config /etc/caddy/Caddyfile

log 'Menyiapkan firewall Ubuntu (aturan dibuat, aktivasi tidak otomatis demi mencegah SSH terkunci)...'
ufw allow OpenSSH >/dev/null || true
ufw allow 80/tcp >/dev/null || true
ufw allow 443/tcp >/dev/null || true

log 'Menyiapkan backup lokal harian...'
cp "$(dirname "$0")/backup-local.sh" /usr/local/sbin/ohli-backup-local
chmod 0750 /usr/local/sbin/ohli-backup-local
cat > /etc/systemd/system/ohli-backup.service <<'UNIT'
[Unit]
Description=Local OHLI database and uploads backup
[Service]
Type=oneshot
ExecStart=/usr/local/sbin/ohli-backup-local
UNIT
cat > /etc/systemd/system/ohli-backup.timer <<'UNIT'
[Unit]
Description=Daily OHLI local backup
[Timer]
OnCalendar=*-*-* 02:20:00
RandomizedDelaySec=10m
Persistent=true
[Install]
WantedBy=timers.target
UNIT

systemctl daemon-reload
systemctl enable --now ohli-duckdns-update.timer
systemctl start ohli-duckdns-update.service
systemctl enable --now ohli-social.service
systemctl enable --now caddy
systemctl enable --now ohli-backup.timer
systemctl enable unattended-upgrades

log 'Memeriksa status lokal...'
sleep 2
systemctl --no-pager --full status ohli-social caddy ohli-duckdns-update.timer || true
printf '\n============================================================\n'
printf 'OHLI server host dasar sudah disiapkan.\n'
printf 'Alamat publik: https://%s\n' "$DOMAIN"
printf 'Node: '; node --version
printf 'PostgreSQL: '; runuser -u postgres -- psql -tAc 'show server_version' | xargs
printf '\nWAJIB: di Oracle Console, izinkan ingress TCP 80 dan 443 ke VM.\n'
printf 'Lalu coba buka alamat HTTPS di atas. Sertifikat bisa perlu beberapa saat.\n'
printf '\nSaat ini halaman yang tampil adalah STATUS SETUP, bukan OHLI Social asli.\n'
printf 'Untuk memasang backend aslimu: sudo bash deploy-backend.sh /path/backend.zip\n'
printf 'Status: sudo bash status.sh\n'
printf 'Log: sudo journalctl -u ohli-social -f\n'
printf 'Rahasia: /etc/ohli/ohli.env (jangan dibagikan).\n'
printf '============================================================\n'
