#!/usr/bin/env bash
# Cópia dos bancos em deploy/backups, guardando os últimos 14 dias. Agende no cron do servidor:
#   30 3 * * * /caminho/do/repositorio/deploy/backup.sh >> /var/log/spbemestar-backup.log 2>&1
# Para restaurar o PostgreSQL: gunzip -c postgres-DATA.sql.gz | docker compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
# Para restaurar as fotos:     docker compose exec -T mongo mongorestore --archive --gzip -u "$MONGO_USER" -p "$MONGO_PASSWORD" < mongo-DATA.archive.gz
set -euo pipefail
cd "$(dirname "$0")"
# lê só o que precisa: o .env não é um script (valores com espaço e parênteses quebrariam um "source")
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
