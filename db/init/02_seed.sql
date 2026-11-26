-- =====================================================================
-- Dati di prova SOLO per lo sviluppo (nomi inventati).
-- La cena del weekend richiede 4 camerieri; i festivi ne richiedono 5.
--
-- 18 dipendenti: 8 camerieri, 4 cuochi, 4 aiuto cuochi, 2 lavapiatti.
-- Le disponibilita sono volutamente incomplete: la generazione puo lasciare
-- scoperture quando ore, riposi e fasce non consentono altre assegnazioni.
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
    (6,  'FULL_TIME', 40,  8.50, '2025-09-01', NULL),  -- Paolo: tempo pieno, come l'altro lavapiatti
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
    (17, 'PART_TIME', 24,  9.00, '2025-10-01', NULL),  -- Nicolo': 24 h
    (18, 'FULL_TIME', 40,  8.50, '2025-01-01', NULL);

-- Disponibilita' dei part-time, con giorno 1 = lunedi ... 7 = domenica.
-- Le fasce coincidono con gli orari dei turni del ruolo (OrariTurni), cosi' coprono il turno intero:
--   cucina 10:00 - 15:30 / 17:00 - 00:00, sala 11:30 - 16:00 / 18:00 - 00:30.
-- La quota part-time (US07) vale solo per i camerieri: cuoca e aiuto cuoca part-time possono coprire
-- anche da soli un turno. I camerieri part-time sono liberi anche a pranzo: con le stesse ore fanno
-- piu' turni (4,5 h invece di 6,5 h). I due lavapiatti sono a tempo pieno: non serve dichiararle.
-- Con queste fasce la bozza copre tutto anche in una settimana con due festivi piu' il weekend.
INSERT INTO disponibilita (dipendente_id, giorno_settimana, ora_inizio, ora_fine) VALUES
    -- Giulia (cuoca, 24 h): pranzo 10:00 - 15:30 e cena 17:00 - 00:00, da giovedi' a domenica
    (2, 4, '10:00:00', '15:30:00'),
    (2, 5, '10:00:00', '15:30:00'),
    (2, 6, '10:00:00', '15:30:00'),
    (2, 7, '10:00:00', '15:30:00'),
    (2, 4, '17:00:00', '00:00:00'),
    (2, 5, '17:00:00', '00:00:00'),
    (2, 6, '17:00:00', '00:00:00'),
    (2, 7, '17:00:00', '00:00:00'),
    -- Marta (aiuto cuoca, 24 h): stesse fasce della cucina, da giovedi' a domenica
    (11, 4, '10:00:00', '15:30:00'),
    (11, 5, '10:00:00', '15:30:00'),
    (11, 6, '10:00:00', '15:30:00'),
    (11, 7, '10:00:00', '15:30:00'),
    (11, 4, '17:00:00', '00:00:00'),
    (11, 5, '17:00:00', '00:00:00'),
    (11, 6, '17:00:00', '00:00:00'),
    (11, 7, '17:00:00', '00:00:00'),
    -- Sara (20 h), Beatrice (20 h) e Nicolo' (24 h), camerieri: pranzo 11:30 - 16:00 e cena 18:00 - 00:30 tutti i giorni
    (4, 1, '11:30:00', '16:00:00'),
    (4, 2, '11:30:00', '16:00:00'),
    (4, 3, '11:30:00', '16:00:00'),
    (4, 4, '11:30:00', '16:00:00'),
    (4, 5, '11:30:00', '16:00:00'),
    (4, 6, '11:30:00', '16:00:00'),
    (4, 7, '11:30:00', '16:00:00'),
    (4, 1, '18:00:00', '00:30:00'),
    (4, 2, '18:00:00', '00:30:00'),
    (4, 3, '18:00:00', '00:30:00'),
    (4, 4, '18:00:00', '00:30:00'),
    (4, 5, '18:00:00', '00:30:00'),
    (4, 6, '18:00:00', '00:30:00'),
    (4, 7, '18:00:00', '00:30:00'),
    (16, 1, '11:30:00', '16:00:00'),
    (16, 2, '11:30:00', '16:00:00'),
    (16, 3, '11:30:00', '16:00:00'),
    (16, 4, '11:30:00', '16:00:00'),
    (16, 5, '11:30:00', '16:00:00'),
    (16, 6, '11:30:00', '16:00:00'),
    (16, 7, '11:30:00', '16:00:00'),
    (16, 1, '18:00:00', '00:30:00'),
    (16, 2, '18:00:00', '00:30:00'),
    (16, 3, '18:00:00', '00:30:00'),
    (16, 4, '18:00:00', '00:30:00'),
    (16, 5, '18:00:00', '00:30:00'),
    (16, 6, '18:00:00', '00:30:00'),
    (16, 7, '18:00:00', '00:30:00'),
    (17, 1, '11:30:00', '16:00:00'),
    (17, 2, '11:30:00', '16:00:00'),
    (17, 3, '11:30:00', '16:00:00'),
    (17, 4, '11:30:00', '16:00:00'),
    (17, 5, '11:30:00', '16:00:00'),
    (17, 6, '11:30:00', '16:00:00'),
    (17, 7, '11:30:00', '16:00:00'),
    (17, 1, '18:00:00', '00:30:00'),
    (17, 2, '18:00:00', '00:30:00'),
    (17, 3, '18:00:00', '00:30:00'),
    (17, 4, '18:00:00', '00:30:00'),
    (17, 5, '18:00:00', '00:30:00'),
    (17, 6, '18:00:00', '00:30:00'),
    (17, 7, '18:00:00', '00:30:00');

-- ---------------------------------------------------------------------
-- FABBISOGNO STANDARD: quante persone servono per tipo di giorno, servizio e ruolo.
-- 24 righe = 3 tipi di giorno (feriale, weekend, festivo) x 2 servizi (pranzo, cena) x 4 ruoli.
-- Le quantita' sono pensate sul personale in elenco (4 cuochi, 4 aiuto cuochi, 8 camerieri, 2 lavapiatti):
--   - la cena del weekend richiede 2 cuochi, 1 aiuto, 4 camerieri, 2 lavapiatti;
--   - i festivi richiedono 5 camerieri a cena, gli altri numeri coincidono col weekend;
--   - a pranzo non servono lavapiatti (0): ce ne sono solo 2 e servono entrambi alla cena;
--   - la stessa persona non puo' fare pranzo e cena di seguito, quindi i numeri tengono conto dei turni
--     che si escludono a vicenda.
-- Una riga con 0 significa "nessuno richiesto", non "dato mancante".
-- ---------------------------------------------------------------------
INSERT INTO fabbisogno (ruolo, tipo_giorno, servizio, numero_minimo_dipendenti) VALUES
    -- giorni FERIALI (lunedi' - venerdi')
    ('CUOCO',       'FERIALE', 'PRANZO', 1),
    ('AIUTO_CUOCO', 'FERIALE', 'PRANZO', 1),
    ('CAMERIERE',   'FERIALE', 'PRANZO', 2),
    ('LAVAPIATTI',  'FERIALE', 'PRANZO', 0),
    ('CUOCO',       'FERIALE', 'CENA',   1),
    ('AIUTO_CUOCO', 'FERIALE', 'CENA',   1),
    ('CAMERIERE',   'FERIALE', 'CENA',   3),
    ('LAVAPIATTI',  'FERIALE', 'CENA',   1),
    -- WEEKEND (sabato e domenica)
    ('CUOCO',       'WEEKEND', 'PRANZO', 1),
    ('AIUTO_CUOCO', 'WEEKEND', 'PRANZO', 1),
    ('CAMERIERE',   'WEEKEND', 'PRANZO', 2),
    ('LAVAPIATTI',  'WEEKEND', 'PRANZO', 0),
    ('CUOCO',       'WEEKEND', 'CENA',   2),
    ('AIUTO_CUOCO', 'WEEKEND', 'CENA',   1),
    ('CAMERIERE',   'WEEKEND', 'CENA',   4),
    ('LAVAPIATTI',  'WEEKEND', 'CENA',   2),
    -- giorni FESTIVI (un cameriere in piu a cena rispetto al weekend)
    ('CUOCO',       'FESTIVO', 'PRANZO', 1),
    ('AIUTO_CUOCO', 'FESTIVO', 'PRANZO', 1),
    ('CAMERIERE',   'FESTIVO', 'PRANZO', 2),
    ('LAVAPIATTI',  'FESTIVO', 'PRANZO', 0),
    ('CUOCO',       'FESTIVO', 'CENA',   2),
    ('AIUTO_CUOCO', 'FESTIVO', 'CENA',   1),
    ('CAMERIERE',   'FESTIVO', 'CENA',   5),
    ('LAVAPIATTI',  'FESTIVO', 'CENA',   2);

-- ---------------------------------------------------------------------
-- ACCOUNT DI PROVA (solo per lo sviluppo, come le altre credenziali di questo file).
-- Le password NON sono scritte qui: sono salvate come hash (vedi PasswordHasher).
--   titolare              ->  nome utente: titolare            password: titolare123
--   i 18 dipendenti       ->  nome utente: nome.cognome        password: dipendente123
--                             (senza accenti, in minuscolo: es. mario.rossi, nicolo.ferri)
-- In un'installazione vera queste righe vanno CANCELLATE e le password cambiate.
-- ---------------------------------------------------------------------
INSERT INTO utente (username, password_hash, tipo, dipendente_id) VALUES
    ('titolare', 'pbkdf2-sha256$210000$ZJ8oHXqcQyPz3t9Y5/YqNg==$gXpSebIXBSSaOA485bQ5BqoGAZSNb8eA3iBDB+6n3v8=', 'TITOLARE', NULL),
    ('mario.rossi', 'pbkdf2-sha256$210000$cA205EOKz5CZa0He+xKWOQ==$8/sUzV8uz5CUlkJjrAsqE5LvF7uDJdBMX0fzK0NPtsI=', 'DIPENDENTE', 1),
    ('giulia.bianchi', 'pbkdf2-sha256$210000$RW53ukgyZG5cnBDnBVaWoQ==$Uf4ugAx68lCQrYLwuFqE1M1ayrdP7eP2KQ+bjzMZYxw=', 'DIPENDENTE', 2),
    ('luca.verdi', 'pbkdf2-sha256$210000$z21mu2NoTmZu0fufFuyiVQ==$OiVBCGvuhfvY2P8AIe7w3lylexkcIJH3Lo5OqBHZkdI=', 'DIPENDENTE', 3),
    ('sara.neri', 'pbkdf2-sha256$210000$JXL8eoXqXuzLUpw2XkIW2w==$XYsL+Pjmg96CcbQQKBZTVR9L/bbFZIgGdvHeo7nWQmQ=', 'DIPENDENTE', 4),
    ('anna.gialli', 'pbkdf2-sha256$210000$BFBzfRkyucAKHSy7+0E/hQ==$v/C/o2/URs2oQLn4EFDnM3WXeZHoHeoL61GiUJDVx4o=', 'DIPENDENTE', 5),
    ('paolo.blu', 'pbkdf2-sha256$210000$Qz16O7qiu3wnwq2hzPhurA==$kZcPkAsogYlqW1XFQsAmvVL3W7PvEW5r6Fctr79jOI4=', 'DIPENDENTE', 6),
    ('chiara.serra', 'pbkdf2-sha256$210000$lgzGMkXmwgionCHaBbtzEA==$2LrvPPAJ6aqJfp5hOMBzQUTEt7bRflXMaNmytXfZYHA=', 'DIPENDENTE', 7),
    ('davide.fabbri', 'pbkdf2-sha256$210000$5RlnB3N/19Yey8cCPvb8Xg==$VMeL+pZ3G/B1j6UXU4FNjpBpbcs2Tt9Ix+pgS78AeA0=', 'DIPENDENTE', 8),
    ('elena.greco', 'pbkdf2-sha256$210000$LFGJwqh5cRmIsvbBbgKcWQ==$znf5AHhoBAJsbMlYMNPIgPSmCzH6RkvFtLWQgmD42P8=', 'DIPENDENTE', 9),
    ('omar.khan', 'pbkdf2-sha256$210000$Vw9NfquQGZjq/gj1Ar3IGw==$wiQ4LOgVaWoc6PpSv9NIsmnmZjfvxEthUsFUqohZwEI=', 'DIPENDENTE', 10),
    ('marta.conti', 'pbkdf2-sha256$210000$cZWjz6LqK/TvnpjAb5ukoA==$vFGR4pgV0+QI/WZ6691uz/7f93XQVHiZfbebjIHFz+o=', 'DIPENDENTE', 11),
    ('piero.villa', 'pbkdf2-sha256$210000$YuSiqScgfOUQlKqKNipWjQ==$bBcOyAH/SppgE9nMkirXgRT7+ls166MoCXdfhHCLXnQ=', 'DIPENDENTE', 12),
    ('tiziano.moro', 'pbkdf2-sha256$210000$Q0SPRWDkRqweXpqWOsjSyA==$0r0S/H31oPIcvTw1/UNX1I4bunfB7G8pjwMKUqcQ4Bg=', 'DIPENDENTE', 13),
    ('francesca.riva', 'pbkdf2-sha256$210000$dhkkDrlGQgdERBMdd/8g/A==$ES7FrZ5HzDDlNrKhh7u0zvU+Px73x19l2QgQfvtJY4g=', 'DIPENDENTE', 14),
    ('matteo.costa', 'pbkdf2-sha256$210000$x9o5s+fVsJd8XFB7TWHASA==$JpRk4GXhhjhLtS2TUR1jBltxfh/idpCdqvEi5nnI4Q4=', 'DIPENDENTE', 15),
    ('beatrice.longo', 'pbkdf2-sha256$210000$jkd/qrypXk5RKHfnAMrFPw==$y0ZqVQa1kAcWh4ZO+rJB1L32CV3XdazN+e68B5IDnmk=', 'DIPENDENTE', 16),
    ('nicolo.ferri', 'pbkdf2-sha256$210000$QLmCTgosoQr7l/oUPZB90g==$h1m/0CIvD+fiEMBuqKMprjucmxryVGqBDeWB0VU2w/Y=', 'DIPENDENTE', 17),
    ('karim.haddad', 'pbkdf2-sha256$210000$vzVGqgUz+JQaCAnGAsLyrQ==$zB+rYrlf7kIq+t2NXv7329QovtdP2VKmuv3RuTmt5CU=', 'DIPENDENTE', 18);
