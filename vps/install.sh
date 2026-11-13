#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

say() { printf '\n[OHLI TUNEL] %s\n' "$*"; }
fail() { printf '\n[OHLI TUNEL] ERROR: %s\n' "$*" >&2; exit 1; }

if [[ "${EUID:-$(id -u)}" -ne 0 ]]; then
  if command -v sudo >/dev/null 2>&1; then
    exec sudo -- bash "$0" "$@"
  fi
  fail "Jalankan sebagai root atau sediakan sudo."
fi

if ! command -v docker >/dev/null 2>&1; then
  fail "Docker belum terpasang. Pasang Docker Engine dan Docker Compose plugin sesuai dokumentasi resmi: https://docs.docker.com/engine/install/"
fi
if ! docker compose version >/dev/null 2>&1; then
  fail "Docker Compose plugin belum tersedia. Pasang Docker Compose plugin: https://docs.docker.com/compose/install/linux/"
fi

if [[ ! -f .env ]]; then
  cp .env.example .env
  chmod 600 .env
fi

CURRENT_TOKEN="$(sed -n 's/^CLOUDFLARE_TUNNEL_TOKEN=//p' .env | tail -n 1)"
if [[ -z "$CURRENT_TOKEN" || "$CURRENT_TOKEN" == "PASTE_NAMED_TUNNEL_TOKEN_HERE" ]]; then
  printf 'Tempel token Named Tunnel Cloudflare (input disembunyikan): '
  read -r -s TUNNEL_TOKEN
  printf '\n'
  [[ -n "$TUNNEL_TOKEN" ]] || fail "Token tidak boleh kosong."
  [[ "$TUNNEL_TOKEN" != *$'\n'* ]] || fail "Token tidak valid."
  printf 'CLOUDFLARE_TUNNEL_TOKEN=%s\n' "$TUNNEL_TOKEN" > .env
  unset TUNNEL_TOKEN
  chmod 600 .env
fi

if command -v systemctl >/dev/null 2>&1; then
  systemctl enable --now docker || true
fi

say "Memeriksa konfigurasi dan mengambil image Cloudflare Tunnel..."
docker compose config -q
docker compose pull
say "Menjalankan konektor sebagai container yang otomatis hidup kembali..."
docker compose up -d
sleep 3
docker compose ps
say "Log konektor terakhir:"
docker compose logs --tail=35 cloudflared
say "Selesai. Verifikasi status di Cloudflare Dashboard dan tes hostname dari jaringan lain."
