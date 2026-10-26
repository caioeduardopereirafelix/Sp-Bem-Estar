-- Inscrições de notificação push (Web Push) do cidadão. Cada navegador gera o próprio endpoint e as
-- chaves que cifram as mensagens; o cidadão aparece só pelo HMAC, como nos relatos.

CREATE TABLE push_subscription (
    id             BIGSERIAL PRIMARY KEY,
    reporter_hash  TEXT NOT NULL,                -- mesmo valor de report.reporter_hash
    endpoint       TEXT NOT NULL UNIQUE,         -- URL do serviço de push (Google, Mozilla, Apple)
    p256dh         TEXT NOT NULL,                -- chave pública do navegador, base64url
    auth           TEXT NOT NULL,                -- segredo do navegador, base64url
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_push_subscription_reporter ON push_subscription (reporter_hash, updated_at);
