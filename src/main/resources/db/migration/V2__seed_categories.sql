-- Raio: buraco é pontual; lixo se espalha.
-- Janela: buraco persiste por semanas; lixo costuma ser recolhido em dias.
INSERT INTO category (code, name, dedup_radius_m, dedup_window_days, severity_weight) VALUES
    ('POTHOLE', 'Buraco na via', 15, 30, 1.50),
    ('TRASH',   'Descarte irregular de lixo', 30, 7, 1.00);
