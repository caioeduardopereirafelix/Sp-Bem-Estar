#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

RESTIC_IMAGE=restic/restic:0.19.1
env_value() { grep -E "^$1=" .env | head -1 | cut -d= -f2-; }
POSTGRES_USER=$(env_value POSTGRES_USER); POSTGRES_DB=$(env_value POSTGRES_DB)
MONGO_USER=$(env_value MONGO_USER); MONGO_PASSWORD=$(env_value MONGO_PASSWORD); MONGO_DB=$(env_value MONGO_DB)
RESTIC_REPOSITORY=$(env_value RESTIC_REPOSITORY)
HEARTBEAT_URL=$(env_value BACKUP_HEARTBEAT_URL)

restic() {
    docker run --rm --network host --env-file <(grep -E '^(RESTIC_|AWS_)' .env) \
        -v "$PWD/backups:/backups:ro" -v "$PWD/restore:/restore" "$RESTIC_IMAGE" "$@"
}

heartbeat() {
    if [ -n "$HEARTBEAT_URL" ]; then
        curl -fsS --max-time 10 -o /dev/null "$HEARTBEAT_URL$1" || true
    fi
}
trap 'heartbeat /fail' ERR

stamp=$(date +%Y-%m-%d_%H%M)
umask 077
mkdir -p backups restore

docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" | gzip > "backups/postgres-$stamp.sql.gz"
docker compose exec -T mongo mongodump --quiet --archive --gzip -u "$MONGO_USER" -p "$MONGO_PASSWORD" \
    --authenticationDatabase admin --db "$MONGO_DB" > "backups/mongo-$stamp.archive.gz"
find backups -type f -mtime +14 -delete
echo "$(date -Is) cópia local ok: $(du -sh backups | cut -f1) em deploy/backups"

if [ -z "$RESTIC_REPOSITORY" ]; then
    echo "$(date -Is) sem RESTIC_REPOSITORY: a cópia ficou só neste servidor" >&2
    heartbeat ""
    exit 0
fi
if ! probe=$(restic cat config 2>&1); then
    if grep -q "Is there a repository" <<< "$probe"; then
        restic init
    else
        echo "$(date -Is) o repositório do backup externo não abriu: $probe" >&2
        heartbeat /fail
        exit 1
    fi
fi
restic backup --quiet --tag spbemestar \
    "/backups/postgres-$stamp.sql.gz" "/backups/mongo-$stamp.archive.gz"
restic forget --quiet --prune --tag spbemestar --keep-daily 7 --keep-weekly 4 --keep-monthly 6
restic check --quiet
echo "$(date -Is) cópia externa ok em $RESTIC_REPOSITORY"
heartbeat ""
