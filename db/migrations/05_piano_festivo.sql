-- Per database gia' inizializzati: aggiunge la tabella dei festivi del piano (US06B)
-- senza ricreare il volume MySQL. I piani gia' salvati restano senza festivi.
CREATE TABLE IF NOT EXISTS piano_festivo (
    piano_id INT UNSIGNED NOT NULL,
    giorno   DATE NOT NULL,
    PRIMARY KEY (piano_id, giorno),
    CONSTRAINT fk_piano_festivo_piano FOREIGN KEY (piano_id) REFERENCES piano (id) ON DELETE CASCADE
) ENGINE=InnoDB;
