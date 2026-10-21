-- Identidade anônima do cidadão. A API emite um JWT curto e um refresh token; o id do cidadão
-- (sub do JWT) só aparece aqui. Nos relatos e na lista de bloqueio fica o HMAC desse id, com uma
-- chave que não está no banco: um vazamento do banco não liga os relatos aos tokens.

CREATE TABLE reporter_refresh_token (
    id           UUID PRIMARY KEY,
    reporter_id  UUID NOT NULL,                 -- sub do JWT do cidadão
    token_hash   TEXT NOT NULL UNIQUE CHECK (length(token_hash) = 64), -- SHA-256 do token, nunca o token
    issued_at    TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    used_at      TIMESTAMPTZ,                   -- trocado por um novo par (rotação)
    revoked_at   TIMESTAMPTZ,                   -- invalidado por reuso ou bloqueio
    CHECK (expires_at > issued_at)
);

CREATE INDEX idx_refresh_token_reporter ON reporter_refresh_token (reporter_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_token_expires ON reporter_refresh_token (expires_at);

CREATE TABLE blocked_reporter (
    reporter_hash  TEXT PRIMARY KEY,            -- mesmo valor de report.reporter_hash
    blocked_at     TIMESTAMPTZ NOT NULL,
    blocked_by     BIGINT NOT NULL REFERENCES app_user (id),
    report_id      BIGINT REFERENCES report (id) -- relato que motivou o bloqueio
);
