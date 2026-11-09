-- =====================================================================
-- TurnoGiusto - schema database (BOZZA per lo Sprint 1: US-01 ... US-05)
--
-- Questa e' una proposta di partenza, NON una decisione presa: va letta,
-- discussa e corretta dal team. Le scelte da confermare sono marcate
-- con "DA DECIDERE".
-- Gli script in db/init partono solo alla prima creazione del database
-- (per rilanciarli: docker compose down -v).
-- =====================================================================
SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- RUOLO: cuoco, aiuto cuoco, cameriere, lavapiatti (dati fissi dal testo
-- del progetto).
-- DA DECIDERE: tabella (come qui, permette di aggiungere ruoli) oppure
-- elenco fisso nel codice.
-- ---------------------------------------------------------------------
CREATE TABLE ruolo (
    id   TINYINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nome VARCHAR(30) NOT NULL UNIQUE
) ENGINE=InnoDB;

INSERT INTO ruolo (id, nome) VALUES
    (1, 'Cuoco'),
    (2, 'Aiuto cuoco'),
    (3, 'Cameriere'),
    (4, 'Lavapiatti');

-- ---------------------------------------------------------------------
-- DIPENDENTE (US-01): nome e ruolo. I dati di contratto stanno a parte.
-- ---------------------------------------------------------------------
CREATE TABLE dipendente (
    id       INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    nome     VARCHAR(50) NOT NULL,
    cognome  VARCHAR(50) NOT NULL,
    ruolo_id TINYINT UNSIGNED NOT NULL,
    CONSTRAINT fk_dipendente_ruolo
        FOREIGN KEY (ruolo_id) REFERENCES ruolo (id)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- CONTRATTO (US-01, US-02): tempo pieno o part-time, monte ore settimanale
-- e costo orario.
-- DA DECIDERE: tabella separata con date di inizio/fine (come qui) cosi'
-- si conserva lo storico dei contratti, oppure un solo contratto per
-- dipendente che si sovrascrive a ogni modifica (US-02).
-- Il contratto in vigore e' quello con data_fine NULL.
-- ---------------------------------------------------------------------
CREATE TABLE contratto (
    id                    INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    dipendente_id         INT UNSIGNED NOT NULL,
    tipo                  ENUM('TEMPO_PIENO', 'PART_TIME') NOT NULL,
    monte_ore_settimanali DECIMAL(4,1) NOT NULL,
    costo_orario          DECIMAL(6,2) NOT NULL,
    data_inizio           DATE NOT NULL,
    data_fine             DATE NULL,
    CONSTRAINT fk_contratto_dipendente
        FOREIGN KEY (dipendente_id) REFERENCES dipendente (id) ON DELETE CASCADE,
    CONSTRAINT ck_contratto_monte_ore
        CHECK (monte_ore_settimanali > 0 AND monte_ore_settimanali <= 40),
    CONSTRAINT ck_contratto_costo
        CHECK (costo_orario > 0),
    CONSTRAINT ck_contratto_date
        CHECK (data_fine IS NULL OR data_fine >= data_inizio)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- DISPONIBILITA (US-03, US-04): finestre settimanali dei part-time.
-- Un part-time senza righe = "senza disponibilita' inserite" (criterio
-- di US-04), che e' diverso da "sempre disponibile".
-- La UNIQUE evita i doppioni quando si modificano le finestre (US-03).
-- DA DECIDERE: la disponibilita' e' per giorno + servizio (pranzo/cena,
-- come il fabbisogno) oppure per fasce orarie precise (ora inizio/fine)?
-- Con le fasce orarie bisogna gestire i turni che finiscono dopo
-- mezzanotte.
-- ---------------------------------------------------------------------
CREATE TABLE disponibilita (
    id               INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    dipendente_id    INT UNSIGNED NOT NULL,
    giorno_settimana TINYINT UNSIGNED NOT NULL COMMENT '1 = lunedi ... 7 = domenica',
    servizio         ENUM('PRANZO', 'CENA') NOT NULL,
    CONSTRAINT fk_disponibilita_dipendente
        FOREIGN KEY (dipendente_id) REFERENCES dipendente (id) ON DELETE CASCADE,
    CONSTRAINT ck_disponibilita_giorno
        CHECK (giorno_settimana BETWEEN 1 AND 7),
    CONSTRAINT uq_disponibilita
        UNIQUE (dipendente_id, giorno_settimana, servizio)
) ENGINE=InnoDB;

-- ---------------------------------------------------------------------
-- FABBISOGNO (US-05): quante persone servono per ruolo, tipo di giorno e
-- servizio. Una sola riga per combinazione (UNIQUE).
-- DA DECIDERE: il fabbisogno e' per tipo di giorno (feriale / weekend /
-- festivo, come qui) oppure per ogni giorno della settimana?
-- Come si definisce un giorno festivo e' una domanda per il Product Owner.
-- ---------------------------------------------------------------------
CREATE TABLE fabbisogno (
    id             INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    ruolo_id       TINYINT UNSIGNED NOT NULL,
    tipo_giorno    ENUM('FERIALE', 'WEEKEND', 'FESTIVO') NOT NULL,
    servizio       ENUM('PRANZO', 'CENA') NOT NULL,
    numero_persone TINYINT UNSIGNED NOT NULL,
    CONSTRAINT fk_fabbisogno_ruolo
        FOREIGN KEY (ruolo_id) REFERENCES ruolo (id),
    CONSTRAINT uq_fabbisogno
        UNIQUE (ruolo_id, tipo_giorno, servizio)
) ENGINE=InnoDB;
