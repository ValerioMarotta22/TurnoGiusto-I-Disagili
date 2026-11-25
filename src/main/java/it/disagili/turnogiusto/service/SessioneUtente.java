package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.Utente;

import java.util.Objects;
import java.util.Optional;

/**
 * Chi sta usando il programma in questo momento. E' unico per tutta l'applicazione: la finestra
 * e' una sola e c'e' un solo utente collegato alla volta.
 * Nei test va svuotata con esci() alla fine, altrimenti il test dopo trova l'utente del test prima.
 */
public final class SessioneUtente {

    // volatile: letto da piu' punti (schermate, test); il valore cambia solo all'accesso e all'uscita.
    private static volatile Utente corrente;

    private SessioneUtente() {
    }

    public static void accedi(Utente utente) {
        corrente = Objects.requireNonNull(utente);
    }

    public static void esci() {
        corrente = null;
    }

    /** L'utente collegato, oppure Optional.empty() se nessuno ha fatto l'accesso. */
    public static Optional<Utente> corrente() {
        return Optional.ofNullable(corrente);
    }
}
