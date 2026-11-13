#!/usr/bin/env bash
set -Eeuo pipefail
cd "$(dirname -- "${BASH_SOURCE[0]}")"
docker compose ps
printf '\n--- Log cloudflared (40 baris terakhir) ---\n'
docker compose logs --tail=40 cloudflared
