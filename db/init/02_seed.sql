-- =====================================================================
-- Dati di prova SOLO per lo sviluppo (nomi inventati).
-- Il fabbisogno del sabato sera e' quello del "caso concreto" del testo
-- del progetto: 2 cuochi, 1 aiuto, 5 camerieri, 2 lavapiatti.
-- =====================================================================
SET NAMES utf8mb4;

INSERT INTO dipendente (id, nome, cognome, ruolo) VALUES
                                                      (1, 'Mario',  'Rossi',   'CUOCO'),
                                                      (2, 'Giulia', 'Bianchi', 'CUOCO'),
                                                      (3, 'Luca',   'Verdi',   'AIUTO_CUOCO'),
                                                      (4, 'Sara',   'Neri',    'CAMERIERE'),
                                                      (5, 'Anna',   'Gialli',  'CAMERIERE'),
                                                      (6, 'Paolo',  'Blu',     'LAVAPIATTI');

-- Sara (id 4) ha uno storico: un vecchio contratto chiuso e quello attuale.
INSERT INTO contratto (dipendente_id, tipo, ore_settimanali, costo_orario, data_inizio, data_fine) VALUES
                                                                                                       (1, 'FULL_TIME', 40, 14.50, '2025-01-01', NULL),
                                                                                                       (2, 'PART_TIME', 24, 12.00, '2025-03-01', NULL),
                                                                                                       (3, 'FULL_TIME', 40, 11.00, '2025-01-01', NULL),
                                                                                                       (4, 'PART_TIME', 16,  9.00, '2025-01-01', '2025-05-31'),
                                                                                                       (4, 'PART_TIME', 20,  9.50, '2025-06-01', NULL),
                                                                                                       (5, 'FULL_TIME', 40, 10.00, '2025-01-01', NULL),
                                                                                                       (6, 'PART_TIME', 16,  8.50, '2025-09-01', NULL);

-- Disponibilita' dei part-time Giulia (id 2) e Sara (id 4), con giorno 1 = lunedi
-- ... 7 = domenica. Sara il sabato sera e' libera fino alle 2 (finestra oltre la
-- mezzanotte). Paolo (id 6) resta volutamente senza disponibilita' inserite,
-- per provare quel caso.
INSERT INTO disponibilita (dipendente_id, giorno_settimana, ora_inizio, ora_fine) VALUES
                                                                                      (2, 5, '18:00:00', '23:00:00'),
                                                                                      (2, 6, '18:00:00', '23:00:00'),
                                                                                      (2, 7, '12:00:00', '15:00:00'),
                                                                                      (4, 5, '18:00:00', '23:00:00'),
                                                                                      (4, 6, '12:00:00', '15:00:00'),
                                                                                      (4, 6, '18:00:00', '02:00:00');

INSERT INTO fabbisogno (ruolo, tipo_giorno, servizio, numero_minimo_dipendenti) VALUES
                                                                                    ('CUOCO',       'WEEKEND', 'CENA', 2),
                                                                                    ('AIUTO_CUOCO', 'WEEKEND', 'CENA', 1),
                                                                                    ('CAMERIERE',   'WEEKEND', 'CENA', 5),
                                                                                    ('LAVAPIATTI',  'WEEKEND', 'CENA', 2);
