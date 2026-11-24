package it.disagili.turnogiusto.service;

/**
 * Una modifica del piano che non si puo' fare (per esempio un cameriere al posto di un cuoco).
 * Il messaggio e' scritto per l'utente: la schermata lo mostra cosi' com'e'.
 * E' un'eccezione "controllata" (extends Exception): chi chiama il service e' obbligato a gestirla.
 */
public class ModificaPianoException extends Exception {

    public ModificaPianoException(String messaggio) {
        super(messaggio);
    }
}
