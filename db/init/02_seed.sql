-- =====================================================================
-- Dati di prova SOLO per lo sviluppo (nomi inventati).
-- Il fabbisogno del sabato sera e' quello del "caso concreto" del testo
-- del progetto: 2 cuochi, 1 aiuto, 5 camerieri, 2 lavapiatti.
--
-- 18 dipendenti: 8 camerieri, 4 cuochi, 4 aiuto cuochi, 2 lavapiatti.
-- Sono abbastanza per coprire il weekend: cosi' la bozza dei turni (US06A)
-- non ha posti scoperti e si puo' provare la modifica del piano (US06B).
-- =====================================================================
SET NAMES utf8mb4;

INSERT INTO dipendente (id, nome, cognome, ruolo) VALUES
    -- cuochi (4)
    (1,  'Mario',     'Rossi',   'CUOCO'),
    (2,  'Giulia',    'Bianchi', 'CUOCO'),
    (7,  'Chiara',    'Serra',   'CUOCO'),
    (8,  'Davide',    'Fabbri',  'CUOCO'),
    -- aiuto cuochi (4)
    (3,  'Luca',      'Verdi',   'AIUTO_CUOCO'),
    (9,  'Elena',     'Greco',   'AIUTO_CUOCO'),
    (10, 'Omar',      'Khan',    'AIUTO_CUOCO'),
    (11, 'Marta',     'Conti',   'AIUTO_CUOCO'),
    -- camerieri (8)
    (4,  'Sara',      'Neri',    'CAMERIERE'),
    (5,  'Anna',      'Gialli',  'CAMERIERE'),
    (12, 'Piero',     'Villa',   'CAMERIERE'),
    (13, 'Tiziano',   'Moro',    'CAMERIERE'),
    (14, 'Francesca', 'Riva',    'CAMERIERE'),
    (15, 'Matteo',    'Costa',   'CAMERIERE'),
    (16, 'Beatrice',  'Longo',   'CAMERIERE'),
    (17, 'Nicolò',    'Ferri',   'CAMERIERE'),
    -- lavapiatti (2)
    (6,  'Paolo',     'Blu',     'LAVAPIATTI'),
    (18, 'Karim',     'Haddad',  'LAVAPIATTI');

-- Part-time: Giulia (2), Sara (4), Paolo (6), Marta (11), Beatrice (16), Nicolo' (17).
-- Tutti gli altri sono a tempo pieno.
-- Sara (id 4) ha uno storico: un vecchio contratto chiuso e quello attuale.
INSERT INTO contratto (dipendente_id, tipo, ore_settimanali, costo_orario, data_inizio, data_fine) VALUES
    (1,  'FULL_TIME', 40, 14.50, '2025-01-01', NULL),
    (2,  'PART_TIME', 24, 12.00, '2025-03-01', NULL),
    (3,  'FULL_TIME', 40, 11.00, '2025-01-01', NULL),
    (4,  'PART_TIME', 16,  9.00, '2025-01-01', '2025-05-31'),
    (4,  'PART_TIME', 20,  9.50, '2025-06-01', NULL),
    (5,  'FULL_TIME', 40, 10.00, '2025-01-01', NULL),
    (6,  'PART_TIME', 16,  8.50, '2025-09-01', NULL),
    (7,  'FULL_TIME', 40, 14.00, '2025-02-01', NULL),
    (8,  'FULL_TIME', 40, 13.50, '2025-04-01', NULL),
    (9,  'FULL_TIME', 40, 11.00, '2025-01-15', NULL),
    (10, 'FULL_TIME', 40, 11.00, '2025-03-01', NULL),
    (11, 'PART_TIME', 24, 10.50, '2025-06-01', NULL),
    (12, 'FULL_TIME', 40, 10.00, '2025-01-01', NULL),
    (13, 'FULL_TIME', 40, 10.00, '2025-02-01', NULL),
    (14, 'FULL_TIME', 40, 10.20, '2025-03-15', NULL),
    (15, 'FULL_TIME', 40, 10.00, '2025-05-01', NULL),
    (16, 'PART_TIME', 20,  9.50, '2025-07-01', NULL),
    (17, 'PART_TIME', 16,  9.00, '2025-10-01', NULL),
    (18, 'FULL_TIME', 40,  8.50, '2025-01-01', NULL);

-- Disponibilita' dei part-time, con giorno 1 = lunedi ... 7 = domenica.
-- Le fasce che finiscono dopo la mezzanotte (es. 18:00 - 01:00) coprono tutta la cena.
-- Giulia (id 2) il sabato e' libera solo fino alle 23: non copre l'intera cena (finisce a mezzanotte e mezza).
-- Nicolo' (id 17) resta volutamente senza disponibilita' inserite, per provare quel caso (US-04).
INSERT INTO disponibilita (dipendente_id, giorno_settimana, ora_inizio, ora_fine) VALUES
    (2,  5, '18:00:00', '23:00:00'),
    (2,  6, '18:00:00', '23:00:00'),
    (2,  7, '12:00:00', '15:00:00'),
    (4,  5, '18:00:00', '23:00:00'),
    (4,  6, '12:00:00', '15:00:00'),
    (4,  6, '18:00:00', '02:00:00'),
    (6,  6, '18:00:00', '01:00:00'),
    (6,  7, '18:00:00', '01:00:00'),
    (11, 6, '18:00:00', '01:00:00'),
    (11, 7, '18:00:00', '01:00:00'),
    (16, 5, '18:00:00', '01:00:00'),
    (16, 6, '18:00:00', '01:00:00'),
    (16, 7, '18:00:00', '01:00:00');

INSERT INTO fabbisogno (ruolo, tipo_giorno, servizio, numero_minimo_dipendenti) VALUES
    ('CUOCO',       'WEEKEND', 'CENA', 2),
    ('AIUTO_CUOCO', 'WEEKEND', 'CENA', 1),
    ('CAMERIERE',   'WEEKEND', 'CENA', 5),
    ('LAVAPIATTI',  'WEEKEND', 'CENA', 2);
