#!/usr/bin/env bash
set -u
DOMAIN='belum-diatur'
if [[ -r /etc/ohli/duckdns.env ]]; then source /etc/ohli/duckdns.env; DOMAIN="${DUCKDNS_SUBDOMAIN}.duckdns.org"; fi
echo "Alamat: https://${DOMAIN}"
for svc in ohli-social caddy ohli-duckdns-update.timer ohli-backup.timer postgresql; do
  printf '\n--- %s ---\n' "$svc"
  systemctl --no-pager --full status "$svc" 2>&1 | sed -n '1,12p' || true
done
printf '\n--- Local health ---\n'
curl -sS -i --max-time 5 http://127.0.0.1:8080/healthz || true
printf '\n--- Public HTTPS ---\n'
[[ "$DOMAIN" == 'belum-diatur' ]] || curl -sS -o /dev/null -w 'HTTP %{http_code}\n' --max-time 15 "https://${DOMAIN}/" || true
