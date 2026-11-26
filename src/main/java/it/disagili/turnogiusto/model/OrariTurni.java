package it.disagili.turnogiusto.model;

import java.time.LocalTime;
import java.util.Objects;

/**
 * Definizione condivisa degli orari per ruolo e servizio.
 * Feriali, weekend e festivi usano le stesse fasce: cambia eventualmente
 * il fabbisogno, non l'orario. Non modifica turni o piani già salvati.
 */
public final class OrariTurni {
    private OrariTurni() { }

    /** Restituisce una fascia immutabile; OrarioServizio calcola il giorno finale. */
    public static OrarioServizio per(Ruolo ruolo, Servizio servizio) {
        Objects.requireNonNull(ruolo, "Il ruolo è obbligatorio");
        Objects.requireNonNull(servizio, "Il servizio è obbligatorio");
        return switch (ruolo) {
            case CUOCO, AIUTO_CUOCO -> switch (servizio) {
                case PRANZO -> fascia(10, 0, 15, 30);
                case CENA -> fascia(17, 0, 0, 0);
            };
            case CAMERIERE -> switch (servizio) {
                case PRANZO -> fascia(11, 30, 16, 0);
                case CENA -> fascia(18, 0, 0, 30);
            };
            case LAVAPIATTI -> switch (servizio) {
                case PRANZO -> fascia(12, 0, 16, 0);
                case CENA -> fascia(19, 0, 0, 30);
            };
        };
    }

    private static OrarioServizio fascia(int oraInizio, int minutoInizio, int oraFine, int minutoFine) {
        return new OrarioServizio(LocalTime.of(oraInizio, minutoInizio), LocalTime.of(oraFine, minutoFine));
    }
}
