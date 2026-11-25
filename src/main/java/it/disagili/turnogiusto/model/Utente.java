package it.disagili.turnogiusto.model;

import java.util.Objects;

/**
 * Chi ha fatto l'accesso. Non contiene mai la password.
 *
 * id           = ID dell'account (tabella utente)
 * username     = nome con cui si entra
 * tipo         = TITOLARE o DIPENDENTE
 * dipendenteId = per un DIPENDENTE l'ID della sua anagrafica (serve a mostrargli solo i suoi dati);
 *                per il TITOLARE e' null
 * nome         = nome da mostrare nella finestra (es. "Mario Rossi")
 * partTime     = vero se e' un dipendente con contratto part-time valido oggi
 */
public record Utente(int id, String username, TipoUtente tipo, Integer dipendenteId, String nome, boolean partTime) {

    public Utente {
        if (id <= 0) {
            throw new IllegalArgumentException("L'ID dell'utente deve essere positivo");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Il nome utente è obbligatorio");
        }
        Objects.requireNonNull(tipo, "Il tipo di utente è obbligatorio");
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Il nome da mostrare è obbligatorio");
        }
        // Un titolare non e' un dipendente; un dipendente deve essere collegato alla sua anagrafica.
        if (tipo == TipoUtente.TITOLARE && dipendenteId != null) {
            throw new IllegalArgumentException("Il titolare non è collegato a un dipendente");
        }
        if (tipo == TipoUtente.DIPENDENTE && (dipendenteId == null || dipendenteId <= 0)) {
            throw new IllegalArgumentException("Un dipendente deve essere collegato alla sua anagrafica");
        }
        if (partTime && tipo != TipoUtente.DIPENDENTE) {
            throw new IllegalArgumentException("Solo un dipendente può essere part-time");
        }
    }

    public boolean isTitolare() {
        return tipo == TipoUtente.TITOLARE;
    }

    /** Lo stesso utente, con il dato "part-time" aggiornato (lo calcola il service all'accesso). */
    public Utente conPartTime(boolean nuovoPartTime) {
        return new Utente(id, username, tipo, dipendenteId, nome, nuovoPartTime);
    }
}
