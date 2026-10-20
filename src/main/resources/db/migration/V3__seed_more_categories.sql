-- Categorias mais frequentes na zeladoria de São Paulo (SP156 e Ouvidoria) que aparecem numa foto.
-- Raio: o quanto o problema se espalha. Janela: quanto tempo ele costuma durar até ser resolvido.
-- Peso: risco à segurança ou à saúde; entra no priority score.

-- Lixo e entulho são o mesmo atendimento (remoção de descarte irregular)
UPDATE category SET name = 'Descarte irregular de lixo ou entulho' WHERE code = 'TRASH';

INSERT INTO category (code, name, dedup_radius_m, dedup_window_days, severity_weight) VALUES
    -- Queda de árvore ou galho é risco de vida; fila de avaliação longa
    ('TREE',          'Árvore caída ou com risco de queda',        20,  30, 2.00),
    -- Some quando a água baixa: janela curta, área grande
    ('FLOODING',      'Alagamento',                                50,   2, 2.00),
    -- Risco sanitário; persiste até o reparo da rede
    ('SEWAGE',        'Esgoto a céu aberto',                       20,  30, 1.80),
    -- Bueiro é pontual e, entupido, causa alagamento
    ('CLOGGED_DRAIN', 'Bueiro ou boca de lobo entupida',           10,  30, 1.50),
    -- Problema persistente; afeta acessibilidade
    ('SIDEWALK',      'Calçada danificada ou sem acessibilidade',  20,  90, 1.20),
    -- Poste é um ponto exato
    ('STREETLIGHT',   'Poste ou iluminação pública com defeito',   10,  30, 1.20),
    ('BUS_STOP',      'Ponto de ônibus danificado',                10,  60, 1.00),
    -- Mato se espalha pela área e cresce devagar; baixo risco
    ('OVERGROWN',     'Mato alto ou terreno sem capinação',        50,  60, 0.80);
