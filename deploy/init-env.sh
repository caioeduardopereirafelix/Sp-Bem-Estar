#!/usr/bin/env bash
# Gera o deploy/.env de produção com segredos aleatórios.
# Uso: ./init-env.sh <domínio> <e-mail do responsável> <e-mail do administrador> ["Nome do órgão"]
# Ex.: ./init-env.sh zeladoria.prefeitura.sp.gov.br ti@prefeitura.sp.gov.br marina@prefeitura.sp.gov.br "Prefeitura de São Paulo"
set -euo pipefail
cd "$(dirname "$0")"

if [ $# -lt 3 ]; then
    sed -n '3,4p' "$0"
    exit 1
fi
if [ -e .env ]; then
    echo "deploy/.env já existe; apague-o antes se quiser gerar outro (os segredos atuais deixam de valer)." >&2
    exit 1
fi
command -v openssl >/dev/null || { echo "Instale o openssl." >&2; exit 1; }

domain="$1"; contact="$2"; admin="$3"; controller="${4:-}"
secret() { openssl rand -base64 48 | tr -d '\n'; }
password() { openssl rand -base64 24 | tr -d '/+=\n' | cut -c1-24; }
b64url() { base64 | tr -d '\n=' | tr '/+' '_-'; }

# Par VAPID P-256: a privada são os 32 bytes do escalar; a pública, o ponto não comprimido (65 bytes)
key=$(mktemp)
trap 'rm -f "$key"' EXIT
openssl ecparam -name prime256v1 -genkey -noout -out "$key" 2>/dev/null
vapid_private=$(openssl ec -in "$key" -outform DER 2>/dev/null | tail -c +8 | head -c 32 | b64url)
vapid_public=$(openssl ec -in "$key" -pubout -outform DER 2>/dev/null | tail -c 65 | b64url)
admin_password=$(password)

umask 077 # só o dono lê o .env
sed -e "s|^DOMAIN=.*|DOMAIN=$domain|" \
    -e "s|^ACME_EMAIL=.*|ACME_EMAIL=$contact|" \
    -e "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(password)|" \
    -e "s|^RABBITMQ_PASSWORD=.*|RABBITMQ_PASSWORD=$(password)|" \
    -e "s|^MONGO_PASSWORD=.*|MONGO_PASSWORD=$(password)|" \
    -e "s|^JWT_SECRET=.*|JWT_SECRET=$(secret)|" \
    -e "s|^REPORTER_HASH=.*|REPORTER_HASH=$(secret)|" \
    -e "s|^OPERATOR_EMAIL=.*|OPERATOR_EMAIL=$admin|" \
    -e "s|^OPERATOR_PASSWORD=.*|OPERATOR_PASSWORD=$admin_password|" \
    -e "s|^PUSH_VAPID_PUBLIC_KEY=.*|PUSH_VAPID_PUBLIC_KEY=$vapid_public|" \
    -e "s|^PUSH_VAPID_PRIVATE_KEY=.*|PUSH_VAPID_PRIVATE_KEY=$vapid_private|" \
    -e "s|^PUSH_VAPID_SUBJECT=.*|PUSH_VAPID_SUBJECT=mailto:$contact|" \
    -e "s|^PRIVACY_CONTROLLER=.*|PRIVACY_CONTROLLER=$controller|" \
    -e "s|^PRIVACY_CONTACT=.*|PRIVACY_CONTACT=$contact|" \
    -e "s|^METRICS_PASSWORD=.*|METRICS_PASSWORD=$(password)|" \
    .env.example > .env

echo "deploy/.env criado."
echo "Administrador: $admin"
echo "Senha inicial: $admin_password"
echo "Anote a senha e troque-a no primeiro acesso (Prefeitura › Minha conta)."
