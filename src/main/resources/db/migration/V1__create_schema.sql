CREATE EXTENSION IF NOT EXISTS postgis;

-- Bairros: polígonos em geometry (ST_Contains é mais rápido que em geography)
CREATE TABLE neighborhood (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    city        TEXT NOT NULL,
    population  INTEGER CHECK (population IS NULL OR population >= 0),
    geom        geometry(MultiPolygon, 4326) NOT NULL,
    UNIQUE (city, name)
);
CREATE INDEX idx_neighborhood_geom ON neighborhood USING GIST (geom);

-- Categorias e parâmetros de deduplicação
CREATE TABLE category (
    code               TEXT PRIMARY KEY,
    name               TEXT NOT NULL,
    dedup_radius_m     INTEGER NOT NULL CHECK (dedup_radius_m > 0),
    dedup_window_days  INTEGER NOT NULL CHECK (dedup_window_days > 0),
    severity_weight    NUMERIC(4, 2) NOT NULL CHECK (severity_weight > 0),
    active             BOOLEAN NOT NULL DEFAULT TRUE
);

-- Chamado consolidado: vários relatos do mesmo ponto viram um issue
CREATE TABLE issue (
    id                 BIGSERIAL PRIMARY KEY,
    category_code      TEXT NOT NULL REFERENCES category (code),
    location           geography(Point, 4326) NOT NULL,
    neighborhood_id    BIGINT REFERENCES neighborhood (id),
    status             TEXT NOT NULL DEFAULT 'OPEN'
                       CHECK (status IN ('OPEN', 'CONFIRMED', 'IN_PROGRESS', 'RESOLVED')),
    report_count       INTEGER NOT NULL DEFAULT 1 CHECK (report_count > 0),
    priority_score     NUMERIC(10, 4) NOT NULL DEFAULT 0,
    first_reported_at  TIMESTAMPTZ NOT NULL,
    last_reported_at   TIMESTAMPTZ NOT NULL,
    resolved_at        TIMESTAMPTZ,
    version            BIGINT NOT NULL DEFAULT 0,
    CHECK (last_reported_at >= first_reported_at),
    CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);
CREATE INDEX idx_issue_location ON issue USING GIST (location);
CREATE INDEX idx_issue_open_by_category ON issue (category_code, last_reported_at)
    WHERE status <> 'RESOLVED';
CREATE INDEX idx_issue_neighborhood ON issue (neighborhood_id);
CREATE INDEX idx_issue_priority ON issue (priority_score DESC)
    WHERE status <> 'RESOLVED';

-- Relato individual do cidadão
CREATE TABLE report (
    id                     BIGSERIAL PRIMARY KEY,
    issue_id               BIGINT NOT NULL REFERENCES issue (id),
    category_code          TEXT NOT NULL REFERENCES category (code),
    location               geography(Point, 4326) NOT NULL,
    photo_id               TEXT NOT NULL,          -- ObjectId do arquivo no GridFS
    reporter_hash          TEXT,                   -- HMAC do identificador, nunca o dado cru
    classification_status  TEXT NOT NULL DEFAULT 'PENDING'
                           CHECK (classification_status IN ('PENDING', 'AGREED', 'MISMATCH', 'LOW_CONFIDENCE')),
    predicted_category     TEXT,
    confidence             NUMERIC(4, 3) CHECK (confidence IS NULL OR confidence BETWEEN 0 AND 1),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_report_issue ON report (issue_id);
CREATE INDEX idx_report_reporter ON report (reporter_hash, created_at);

-- Histórico de status (auditoria)
CREATE TABLE issue_status_history (
    id           BIGSERIAL PRIMARY KEY,
    issue_id     BIGINT NOT NULL REFERENCES issue (id),
    from_status  TEXT,
    to_status    TEXT NOT NULL,
    changed_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_issue_status_history_issue ON issue_status_history (issue_id, changed_at);

-- Outbox transacional: eventos gravados junto com o relato e publicados depois
CREATE TABLE outbox_event (
    id            UUID PRIMARY KEY,
    aggregate_id  BIGINT NOT NULL,
    type          TEXT NOT NULL,
    payload       JSONB NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at  TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
