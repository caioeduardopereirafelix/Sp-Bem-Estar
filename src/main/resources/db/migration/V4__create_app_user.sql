-- Usuários do órgão público. O cidadão continua anônimo: só quem altera chamados faz login.
CREATE TABLE app_user (
    id             BIGSERIAL PRIMARY KEY,
    email          TEXT NOT NULL UNIQUE CHECK (email = lower(email)),
    name           TEXT NOT NULL,
    password_hash  TEXT NOT NULL,                  -- formato {id}hash do DelegatingPasswordEncoder
    role           TEXT NOT NULL CHECK (role IN ('OPERATOR')),
    active         BOOLEAN NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
