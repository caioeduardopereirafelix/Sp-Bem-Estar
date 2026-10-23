-- 1) A prioridade passa a contar pessoas, não relatos: quem reporta o mesmo problema cinco vezes conta uma.
--    Relatos anteriores à identidade anônima (sem reporter_hash) contam cada um como uma pessoa.
ALTER TABLE issue
    ADD COLUMN reporter_count INTEGER NOT NULL DEFAULT 1 CHECK (reporter_count >= 0);

WITH reporters AS (
    SELECT r.issue_id, count(DISTINCT coalesce(r.reporter_hash, 'relato-' || r.id)) AS total
    FROM report r
    WHERE r.moderation_status IS DISTINCT FROM 'REJECTED'
    GROUP BY r.issue_id
)
UPDATE issue i
SET reporter_count = coalesce(rp.total, 0)
FROM issue base
LEFT JOIN reporters rp ON rp.issue_id = base.id
WHERE base.id = i.id;

-- Mesma fórmula do IssueRepository, para a fila já sair certa sem esperar o recálculo de hora em hora
UPDATE issue i
SET priority_score = round((c.severity_weight * ln(1 + i.reporter_count)
        * (1 + GREATEST(EXTRACT(EPOCH FROM (now() - i.first_reported_at)), 0) / 2592000))::numeric, 4)
FROM category c
WHERE c.code = i.category_code
  AND i.status NOT IN ('RESOLVED', 'DISCARDED');

-- 2) Quem mudou o status. Mudanças anteriores a esta migration ficam sem autor.
ALTER TABLE issue_status_history
    ADD COLUMN changed_by BIGINT REFERENCES app_user (id);
