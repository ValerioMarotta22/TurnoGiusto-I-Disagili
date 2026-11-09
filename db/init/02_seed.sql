-- =====================================================================
-- Dati di prova SOLO per lo sviluppo (nomi inventati).
-- Il fabbisogno del sabato sera e' quello del "caso concreto" del testo
-- del progetto: 2 cuochi, 1 aiuto, 5 camerieri, 2 lavapiatti.
-- =====================================================================
SET NAMES utf8mb4;

INSERT INTO dipendente (id, nome, cognome, ruolo_id) VALUES
    (1, 'Mario',  'Rossi',   1),  -- cuoco
    (2, 'Giulia', 'Bianchi', 1),  -- cuoco
    (3, 'Luca',   'Verdi',   2),  -- aiuto cuoco
    (4, 'Sara',   'Neri',    3),  -- cameriere
    (5, 'Anna',   'Gialli',  3),  -- cameriere
    (6, 'Paolo',  'Blu',     4);  -- lavapiatti

INSERT INTO contratto (dipendente_id, tipo, monte_ore_settimanali, costo_orario, data_inizio) VALUES
    (1, 'TEMPO_PIENO', 40.0, 14.50, '2025-01-01'),
    (2, 'PART_TIME',   24.0, 12.00, '2025-03-01'),
    (3, 'TEMPO_PIENO', 40.0, 11.00, '2025-01-01'),
    (4, 'PART_TIME',   20.0,  9.50, '2025-06-01'),
    (5, 'TEMPO_PIENO', 40.0, 10.00, '2025-01-01'),
    (6, 'PART_TIME',   16.0,  8.50, '2025-09-01');

-- Disponibilita' dei part-time (Giulia e Sara); Paolo resta volutamente
-- senza disponibilita' inserite, per provare quel caso.
INSERT INTO disponibilita (dipendente_id, giorno_settimana, servizio) VALUES
    (2, 5, 'CENA'), (2, 6, 'CENA'), (2, 7, 'PRANZO'),
    (4, 5, 'CENA'), (4, 6, 'PRANZO'), (4, 6, 'CENA');

INSERT INTO fabbisogno (ruolo_id, tipo_giorno, servizio, numero_persone) VALUES
    (1, 'WEEKEND', 'CENA', 2),
    (2, 'WEEKEND', 'CENA', 1),
    (3, 'WEEKEND', 'CENA', 5),
    (4, 'WEEKEND', 'CENA', 2);
