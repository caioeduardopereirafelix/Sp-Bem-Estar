-- Login por código enviado por e-mail, para todo mundo: o cidadão passa a entrar com o e-mail para relatar,
-- e a equipe da prefeitura (e o CEO) deixa de usar senha.

-- O e-mail fica só aqui, ligado ao id que assina os relatos (reporter_id). Os relatos continuam guardando o
-- HMAC desse id: a equipe da prefeitura não vê o e-mail de quem relatou.
CREATE TABLE citizen_account (
    id            BIGSERIAL PRIMARY KEY,
    email         TEXT        NOT NULL UNIQUE,
    reporter_id   UUID        NOT NULL UNIQUE,
    created_at    TIMESTAMPTZ NOT NULL,
    last_login_at TIMESTAMPTZ
);

-- Só o hash do código (HMAC com uma chave derivada do JWT_SECRET): quem lê o banco não entra no lugar de ninguém
CREATE TABLE login_code (
    id          BIGSERIAL PRIMARY KEY,
    email       TEXT        NOT NULL,
    code_hash   TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    attempts    INT         NOT NULL DEFAULT 0,
    consumed_at TIMESTAMPTZ
);
CREATE INDEX login_code_email_created_idx ON login_code (email, created_at DESC);

-- Sem senha: as que existiam não servem mais para nada e não precisam ficar guardadas
ALTER TABLE app_user ALTER COLUMN password_hash DROP NOT NULL;
UPDATE app_user SET password_hash = NULL;
