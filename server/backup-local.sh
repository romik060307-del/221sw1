#!/usr/bin/env bash
set -Eeuo pipefail
DEST=/var/backups/ohli
install -d -m 0700 "$DEST"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
# Local snapshot only; copy these files off the VM for disaster recovery.
runuser -u postgres -- pg_dump -Fc ohli_social > "$DEST/ohli_social-${STAMP}.dump"
if [[ -d /var/lib/ohli/uploads ]]; then
  tar -czf "$DEST/uploads-${STAMP}.tar.gz" -C /var/lib/ohli uploads
fi
chmod 0600 "$DEST"/*
find "$DEST" -type f -mtime +7 -delete
printf 'Backup lokal dibuat di %s. Salin ke storage/perangkat lain.\n' "$DEST"
