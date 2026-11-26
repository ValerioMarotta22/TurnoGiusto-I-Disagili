-- Per database gia inizializzati: non occorre ricreare il volume MySQL.
-- Cambia soltanto il fabbisogno della cena del weekend; i festivi restano a 5.
UPDATE fabbisogno
SET numero_minimo_dipendenti = 4
WHERE ruolo = 'CAMERIERE' AND tipo_giorno = 'WEEKEND' AND servizio = 'CENA';
