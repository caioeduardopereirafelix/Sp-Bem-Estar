-- Moderação dos relatos que o classificador marcou (categoria diferente da foto ou baixa confiança).
-- Relato rejeitado deixa de contar no chamado; o chamado que fica sem relatos válidos é descartado.

ALTER TABLE report
    ADD COLUMN moderation_status TEXT CHECK (moderation_status IN ('APPROVED', 'REJECTED')),
    ADD COLUMN moderated_at      TIMESTAMPTZ,
    ADD COLUMN moderated_by      BIGINT REFERENCES app_user (id),
    ADD CONSTRAINT report_moderation_complete CHECK (
        (moderation_status IS NULL) = (moderated_at IS NULL)
        AND (moderation_status IS NULL) = (moderated_by IS NULL));

-- Fila de moderação: marcados e ainda sem decisão, do mais antigo para o mais novo
CREATE INDEX idx_report_moderation_queue ON report (created_at)
    WHERE classification_status IN ('MISMATCH', 'LOW_CONFIDENCE') AND moderation_status IS NULL;

ALTER TABLE issue
    DROP CONSTRAINT issue_status_check,
    ADD CONSTRAINT issue_status_check
        CHECK (status IN ('OPEN', 'CONFIRMED', 'IN_PROGRESS', 'RESOLVED', 'DISCARDED')),
    DROP CONSTRAINT issue_report_count_check,
    ADD CONSTRAINT issue_report_count_check CHECK (report_count > 0 OR status = 'DISCARDED');
