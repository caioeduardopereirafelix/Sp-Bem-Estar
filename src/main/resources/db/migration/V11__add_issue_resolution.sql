-- O que a equipe mostra ao resolver um chamado: a foto do conserto e uma nota curta, as duas públicas no mapa
ALTER TABLE issue ADD COLUMN resolution_photo_id VARCHAR(64);
ALTER TABLE issue ADD COLUMN resolution_note VARCHAR(500);
