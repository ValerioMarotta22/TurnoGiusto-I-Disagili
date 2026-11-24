package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.Utente;

/**
 * Chi puo' fare cosa. E' l'UNICO posto con queste regole: il menu le usa per nascondere le voci e
 * la finestra principale le usa di nuovo prima di aprire una schermata (nascondere non basta).
 *
 *   TITOLARE    -> dipendenti, disponibilita' dei part-time, fabbisogno, generazione e modifica dei turni
 *   DIPENDENTE  -> i propri turni; le proprie disponibilita' solo se e' part-time
 *   nessun utente collegato -> niente
 *
 * Regola di sicurezza: se una funzione non e' elencata, e' VIETATA (si permette solo cio' che e' scritto).
 */
public final class Autorizzazioni {

    private Autorizzazioni() {
    }

    public static boolean consentita(Utente utente, Funzione funzione) {
        if (utente == null || funzione == null) {
            return false;
        }
        return switch (utente.tipo()) {
            case TITOLARE -> switch (funzione) {
                case GESTIONE_DIPENDENTI, DISPONIBILITA_PART_TIME, FABBISOGNO, GENERA_PIANO, MODIFICA_PIANO -> true;
                case LE_MIE_DISPONIBILITA, I_MIEI_TURNI -> false;
            };
            case DIPENDENTE -> switch (funzione) {
                case I_MIEI_TURNI -> true;
                case LE_MIE_DISPONIBILITA -> utente.partTime();
                case GESTIONE_DIPENDENTI, DISPONIBILITA_PART_TIME, FABBISOGNO, GENERA_PIANO, MODIFICA_PIANO -> false;
            };
        };
    }
}
