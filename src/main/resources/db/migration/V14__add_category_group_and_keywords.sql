ALTER TABLE category ADD COLUMN service_group TEXT;
ALTER TABLE category ADD COLUMN keywords TEXT;

UPDATE category SET service_group = 'Vias e calçadas', keywords = 'buraco, cratera, asfalto, pavimento, rua'
WHERE code = 'POTHOLE';
UPDATE category SET service_group = 'Vias e calçadas', keywords = 'calçada, passeio, piso, rampa, acessibilidade'
WHERE code = 'SIDEWALK';
UPDATE category SET service_group = 'Limpeza urbana', keywords = 'lixo, entulho, descarte, sujeira, caçamba'
WHERE code = 'TRASH';
UPDATE category SET service_group = 'Limpeza urbana', keywords = 'mato, capina, terreno, roçada, vegetação'
WHERE code = 'OVERGROWN';
UPDATE category SET service_group = 'Drenagem e saneamento', keywords = 'alagamento, enchente, inundação, água, chuva'
WHERE code = 'FLOODING';
UPDATE category SET service_group = 'Drenagem e saneamento', keywords = 'bueiro, boca de lobo, galeria, entupido, ralo'
WHERE code = 'CLOGGED_DRAIN';
UPDATE category SET service_group = 'Drenagem e saneamento', keywords = 'esgoto, vazamento, mau cheiro, céu aberto'
WHERE code = 'SEWAGE';
UPDATE category SET service_group = 'Iluminação pública', keywords = 'poste, lâmpada, luz, apagado, iluminação'
WHERE code = 'STREETLIGHT';
UPDATE category SET service_group = 'Áreas verdes', keywords = 'árvore, galho, queda, poda, risco'
WHERE code = 'TREE';
UPDATE category SET service_group = 'Transporte', keywords = 'ponto de ônibus, abrigo, parada, ônibus'
WHERE code = 'BUS_STOP';
