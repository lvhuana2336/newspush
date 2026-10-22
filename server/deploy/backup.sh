#!/usr/bin/env bash
set -euo pipefail

DB="${DB_PATH:-/opt/newspush/server/data/newspush.db}"
DEST="${BACKUP_DIR:-/var/backups/newspush}"
KEEP_DAYS="${KEEP_DAYS:-14}"

mkdir -p "$DEST"
STAMP="$(date +%Y%m%d-%H%M%S)"
sqlite3 "$DB" ".backup '$DEST/newspush-$STAMP.db'"
find "$DEST" -name 'newspush-*.db' -mtime +"$KEEP_DAYS" -delete
echo "backup written: $DEST/newspush-$STAMP.db"
