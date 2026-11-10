-- =====================================================================
-- TurnoGiusto - schema database (Sprint 1: US-01 ... US-05)
--
-- Allineato alle classi del pacchetto model: Dipendente, Contratto,
-- Disponibilita, Fabbisogno e le enum Ruolo, Servizio, TipoContratto,
-- TipoGiorno.
-- I valori delle colonne ENUM sono esattamente i nomi delle enum Java
-- (name()), quindi nel codice basta Enum.valueOf(...) e name(), senza
-- tabelle di conversione.
--
-- Gli script in db/init partono solo alla prima creazione del database
-- (per rifarli: docker compose down -v, poi docker compose up -d --wait).
-- =====================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- DIPENDENTE (US-01). Il ruolo e' una colonna ENUM, come l'enum Ruolo.
-- La tabella "ruolo" non serve piu'.
-- Il CAPOSALA non e' un ruolo: e' un incarico che il titolare assegna, turno
-- per turno, a un cameriere full-time (Dipendente.puoEssereCaposala()).
-- Per questo qui non compare: vivra' nella tabella dei turni assegnati
-- (Sprint 2, US-06A / US-22), non in "dipendente".
-- ---------------------------------------------------------------------
CREATE TABLE dipendente (
                            id      INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                            nome    VARCHAR(50) NOT NULL,
                            cognome VARCHAR(50) NOT NULL,
                            ruolo   ENUM('CUOCO', 'AIUTO_CUOCO', 'CAMERIERE', 'LAVAPIATTI') NOT NULL
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- CONTRATTO (US-01, US-02). Un dipendente puo' avere piu' contratti
-- nel tempo (storico). Il contratto in vigore e' quello con data_fine
-- NULL oppure con data_fine nel futuro (vedi Contratto.isValidoIl).
-- Modificare un contratto (US-02) significa chiudere il vecchio
-- (data_fine) e inserirne uno nuovo, in una sola transazione.
-- ---------------------------------------------------------------------
CREATE TABLE contratto (
                           id              INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                           dipendente_id   INT UNSIGNED NOT NULL,
                           tipo            ENUM('FULL_TIME', 'PART_TIME') NOT NULL,
                           ore_settimanali TINYINT UNSIGNED NOT NULL,
                           costo_orario    DECIMAL(6,2) NOT NULL,
                           data_inizio     DATE NOT NULL,
                           data_fine       DATE NULL,
                           CONSTRAINT fk_contratto_dipendente
                               FOREIGN KEY (dipendente_id) REFERENCES dipendente (id) ON DELETE CASCADE,
    -- stesso limite di Contratto.MAX_ORE_SETTIMANALI
                           CONSTRAINT ck_contratto_ore
                               CHECK (ore_settimanali BETWEEN 1 AND 40),
                           CONSTRAINT ck_contratto_costo
                               CHECK (costo_orario > 0),
                           CONSTRAINT ck_contratto_date
                               CHECK (data_fine IS NULL OR data_fine >= data_inizio)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- DISPONIBILITA (US-03, US-04): finestre settimanali dei part-time.
-- giorno_settimana usa gli stessi numeri di DayOfWeek.getValue()
-- (1 = lunedi ... 7 = domenica).
-- Se ora_fine e' prima di ora_inizio la finestra finisce dopo la
-- mezzanotte (es. 18:00 - 02:00): vedi Disponibilita.attraversaMezzanotte.
-- Un part-time senza righe = "senza disponibilita' inserite" (criterio
-- di US-04), diverso da "sempre disponibile".
-- La UNIQUE evita le finestre identiche doppie (US-03).
-- ---------------------------------------------------------------------
CREATE TABLE disponibilita (
                               id               INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                               dipendente_id    INT UNSIGNED NOT NULL,
                               giorno_settimana TINYINT UNSIGNED NOT NULL COMMENT '1 = lunedi ... 7 = domenica',
                               ora_inizio       TIME NOT NULL,
                               ora_fine         TIME NOT NULL,
                               CONSTRAINT fk_disponibilita_dipendente
                                   FOREIGN KEY (dipendente_id) REFERENCES dipendente (id) ON DELETE CASCADE,
                               CONSTRAINT ck_disponibilita_giorno
                                   CHECK (giorno_settimana BETWEEN 1 AND 7),
                               CONSTRAINT ck_disponibilita_orari
                                   CHECK (ora_inizio <> ora_fine),
                               CONSTRAINT uq_disponibilita
                                   UNIQUE (dipendente_id, giorno_settimana, ora_inizio, ora_fine)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- FABBISOGNO (US-05): quante persone servono per ruolo, tipo di giorno
-- e servizio. Una sola riga per combinazione.
-- Come si definisce un giorno festivo e' una domanda per il Product Owner.
-- Il caposala di turno e' uno dei camerieri e conta nel loro numero (non
-- ha una riga a parte): ipotesi da confermare con il Product Owner.
-- ---------------------------------------------------------------------
CREATE TABLE fabbisogno (
                            id                       INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                            ruolo                    ENUM('CUOCO', 'AIUTO_CUOCO', 'CAMERIERE', 'LAVAPIATTI') NOT NULL,
                            tipo_giorno              ENUM('FERIALE', 'FESTIVO', 'WEEKEND') NOT NULL,
                            servizio                 ENUM('PRANZO', 'CENA') NOT NULL,
                            numero_minimo_dipendenti TINYINT UNSIGNED NOT NULL,
                            CONSTRAINT uq_fabbisogno
                                UNIQUE (ruolo, tipo_giorno, servizio)
) ENGINE=InnoDB;
