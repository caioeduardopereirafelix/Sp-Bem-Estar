#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
env_value() { grep -E "^$1=" .env | head -1 | cut -d= -f2-; }
POSTGRES_USER=$(env_value POSTGRES_USER); POSTGRES_DB=$(env_value POSTGRES_DB)
MONGO_USER=$(env_value MONGO_USER); MONGO_PASSWORD=$(env_value MONGO_PASSWORD); MONGO_DB=$(env_value MONGO_DB)

stamp=$(date +%Y-%m-%d_%H%M)
umask 077 # só o dono lê as cópias: elas têm os dados de todos os relatos
mkdir -p backups

docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" | gzip > "backups/postgres-$stamp.sql.gz"
docker compose exec -T mongo mongodump --quiet --archive --gzip -u "$MONGO_USER" -p "$MONGO_PASSWORD" \
    --authenticationDatabase admin --db "$MONGO_DB" > "backups/mongo-$stamp.archive.gz"

find backups -type f -mtime +14 -delete
echo "$(date -Is) backup ok: $(du -sh backups | cut -f1) em deploy/backups"
