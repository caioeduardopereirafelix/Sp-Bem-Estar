#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

RESTIC_IMAGE=restic/restic:0.19.1
env_value() { grep -E "^$1=" .env | head -1 | cut -d= -f2-; }
POSTGRES_USER=$(env_value POSTGRES_USER); POSTGRES_DB=$(env_value POSTGRES_DB)
MONGO_USER=$(env_value MONGO_USER); MONGO_PASSWORD=$(env_value MONGO_PASSWORD); MONGO_DB=$(env_value MONGO_DB)
RESTIC_REPOSITORY=$(env_value RESTIC_REPOSITORY)

snapshot=latest
apply=false
for arg in "$@"; do
    case "$arg" in
        --apply) apply=true ;;
        --list) list=true ;;
        -*) echo "uso: ./restore.sh [--list] [snapshot] [--apply]" >&2; exit 1 ;;
        *) snapshot=$arg ;;
    esac
done

restic() {
    docker run --rm --network host --env-file <(grep -E '^(RESTIC_|AWS_)' .env) \
        -v "$PWD/restore:/restore" "$RESTIC_IMAGE" "$@"
}

umask 077
mkdir -p restore
if [ -n "$RESTIC_REPOSITORY" ]; then
    if [ "${list:-false}" = true ]; then
        restic snapshots --tag spbemestar
        exit 0
    fi
    target="restore/$snapshot-$(date +%Y%m%d%H%M%S)"
    restic restore "$snapshot" --tag spbemestar --target "/$target"
    postgres=$(find "$target" -name 'postgres-*.sql.gz' | sort | tail -1)
    mongo=$(find "$target" -name 'mongo-*.archive.gz' | sort | tail -1)
else
    echo "sem RESTIC_REPOSITORY: usando a cópia local mais recente de deploy/backups" >&2
    postgres=$(find backups -name 'postgres-*.sql.gz' | sort | tail -1)
    mongo=$(find backups -name 'mongo-*.archive.gz' | sort | tail -1)
fi
if [ -z "$postgres" ] || [ -z "$mongo" ]; then
    echo "a cópia não tem o dump do PostgreSQL e o do MongoDB" >&2
    exit 1
fi
gunzip -t "$postgres"
echo "PostgreSQL: $postgres ($(du -h "$postgres" | cut -f1))"
echo "MongoDB:    $mongo ($(du -h "$mongo" | cut -f1))"

if [ "$apply" != true ]; then
    echo "Nada foi alterado. Para trocar os dados atuais por esta cópia, rode de novo com --apply."
    exit 0
fi
read -r -p "Isso APAGA os relatos e as fotos atuais e põe os da cópia no lugar. Digite restaurar para seguir: " answer
if [ "$answer" != restaurar ]; then
    echo "cancelado" >&2
    exit 1
fi

running=$(docker compose ps --services --status running | grep -xE 'api|worker' || true)
if [ -n "$running" ]; then
    docker compose stop $running
fi
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" -d postgres \
    -c "DROP DATABASE IF EXISTS \"$POSTGRES_DB\" WITH (FORCE)" -c "CREATE DATABASE \"$POSTGRES_DB\""
gunzip -c "$postgres" | docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -q -U "$POSTGRES_USER" \
    -d "$POSTGRES_DB" > /dev/null
docker compose exec -T mongo mongorestore --quiet --drop --archive --gzip -u "$MONGO_USER" -p "$MONGO_PASSWORD" \
    --authenticationDatabase admin --nsInclude "$MONGO_DB.*" < "$mongo"
if [ -n "$running" ]; then
    docker compose start $running
fi
echo "$(date -Is) dados restaurados de $postgres e $mongo"
