#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")"

env_value() { grep -E "^$1=" .env 2>/dev/null | head -1 | cut -d= -f2-; }
DOMAIN=$(env_value DOMAIN)
ALERT_EMAIL=$(env_value ALERT_EMAIL)
ALERT_EMAIL=${ALERT_EMAIL:-$(env_value ACME_EMAIL)}
BREVO_API_KEY=$(env_value BREVO_API_KEY)
BREVO_URL=$(env_value BREVO_URL)
BREVO_URL=${BREVO_URL:-https://api.brevo.com}
MAIL_FROM=$(env_value MAIL_FROM)
MAIL_FROM_NAME=$(env_value MAIL_FROM_NAME)
MAIL_FROM_NAME=${MAIL_FROM_NAME:-SP Bem-Estar}
HEALTH_URL=$(env_value MONITOR_HEALTH_URL)
HEALTH_URL=${HEALTH_URL:-https://$DOMAIN/actuator/health}
HEARTBEAT_URL=$(env_value MONITOR_HEARTBEAT_URL)
DISK_LIMIT=$(env_value MONITOR_DISK_LIMIT)
DISK_LIMIT=${DISK_LIMIT:-90}

problems=()

body=$(curl -s --max-time 10 "$HEALTH_URL" 2>/dev/null)
status=$(printf '%s' "$body" | grep -o '"status" *: *"[A-Z_]*"' | head -1 | sed 's/.*"\([A-Z_]*\)"$/\1/')
case "$status" in
    UP) ;;
    DEGRADED) problems+=("A API responde, mas está DEGRADED: a fila para o classificador está parada ou o Redis caiu.") ;;
    "") problems+=("$HEALTH_URL não respondeu: o site está fora do ar para quem tenta abrir.") ;;
    *) problems+=("A API respondeu $status em $HEALTH_URL.") ;;
esac

containers=$(docker compose ps -a --format '{{.Service}} {{.State}} {{.Health}}' 2>/dev/null)
for service in $(docker compose config --services 2>/dev/null); do
    line=$(printf '%s\n' "$containers" | awk -v s="$service" '$1 == s')
    state=$(printf '%s' "$line" | awk '{print $2}')
    health=$(printf '%s' "$line" | awk '{print $3}')
    if [ -z "$line" ]; then
        problems+=("O container $service não existe (rode docker compose up -d).")
    elif [ "$state" != "running" ]; then
        problems+=("O container $service está $state.")
    elif [ "$health" = "unhealthy" ]; then
        problems+=("O container $service está rodando, mas unhealthy.")
    fi
done

disk=$(df -P . | awk 'NR == 2 {gsub("%", "", $5); print $5}')
if [ -n "$disk" ] && [ "$disk" -ge "$DISK_LIMIT" ]; then
    problems+=("O disco do servidor está com ${disk}% de uso (limite ${DISK_LIMIT}%).")
fi

json() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g' | awk 'NR > 1 {printf "\\n"} {printf "%s", $0}'; }

send() {
    if [ -z "$BREVO_API_KEY" ] || [ -z "$MAIL_FROM" ] || [ -z "$ALERT_EMAIL" ]; then
        echo "$(date -Is) sem BREVO_API_KEY, MAIL_FROM ou ALERT_EMAIL: alerta não enviado: $1" >&2
        return 1
    fi
    payload=$(printf '{"sender":{"name":"%s","email":"%s"},"to":[{"email":"%s"}],"subject":"%s","textContent":"%s"}' \
        "$(json "$MAIL_FROM_NAME")" "$(json "$MAIL_FROM")" "$(json "$ALERT_EMAIL")" "$(json "$1")" "$(json "$2")")
    if curl -fsS --max-time 15 -X POST "$BREVO_URL/v3/smtp/email" -H "api-key: $BREVO_API_KEY" \
            -H "content-type: application/json" -d "$payload" >/dev/null; then
        echo "$(date -Is) alerta enviado para $ALERT_EMAIL: $1"
    else
        echo "$(date -Is) o Brevo recusou o alerta: $1" >&2
        return 1
    fi
}

current=$(printf '%s\n' "${problems[@]+"${problems[@]}"}" | sed '/^$/d')
observed=$(cat .monitor-observed 2>/dev/null || true)
notified=$(cat .monitor-notified 2>/dev/null || true)
printf '%s' "$current" > .monitor-observed

if [ "$current" = "$observed" ] && [ "$current" != "$notified" ]; then
    host=${DOMAIN:-$(hostname)}
    if [ -n "$current" ]; then
        subject="[SP Bem-Estar] Problema em $host"
        text="O monitor encontrou, em duas verificações seguidas:

$(printf '%s\n' "$current" | sed 's/^/- /')

Para investigar no servidor:
  cd $(pwd) && docker compose ps && docker compose logs --tail 100 api

Você recebe outro e-mail quando tudo voltar ao normal."
    else
        subject="[SP Bem-Estar] $host voltou ao normal"
        text="A API responde UP, todos os containers estão rodando e o disco está abaixo de ${DISK_LIMIT}%."
    fi
    send "$subject" "$text" && printf '%s' "$current" > .monitor-notified
fi

if [ -n "$HEARTBEAT_URL" ]; then
    [ -n "$current" ] && HEARTBEAT_URL="$HEARTBEAT_URL/fail"
    curl -fsS --max-time 10 -o /dev/null "$HEARTBEAT_URL" || true
fi
