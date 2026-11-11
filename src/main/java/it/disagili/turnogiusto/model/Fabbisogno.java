package it.disagili.turnogiusto.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Quante persone servono per un ruolo, in un tipo di giorno e in un servizio
 * (US-05). Esempio: sabato sera servono 5 camerieri.
 */
public class Fabbisogno {

    /** Limite della colonna numero_minimo_dipendenti del database (TINYINT UNSIGNED). */
    public static final int MAX_NUMERO_DIPENDENTI = 255;

    private int id;
    private Ruolo ruolo;
    private TipoGiorno tipoGiorno;
    private Servizio servizio;
    private int numeroMinimoDipendenti;

    public Fabbisogno(int id, Ruolo ruolo,
                      TipoGiorno tipoGiorno, Servizio servizio,
                      int numeroMinimoDipendenti) {
        this.id = id;
        this.ruolo = ruolo;
        this.tipoGiorno = tipoGiorno;
        this.servizio = servizio;
        this.numeroMinimoDipendenti = numeroMinimoDipendenti;
    }

    // Per un fabbisogno nuovo, non ancora salvato: l'id lo assegna il database.
    public Fabbisogno(Ruolo ruolo, TipoGiorno tipoGiorno,
                      Servizio servizio, int numeroMinimoDipendenti) {
        this(0, ruolo, tipoGiorno, servizio,
                numeroMinimoDipendenti);
    }

    /**
     * Controlla i dati del fabbisogno. Zero persone e' un valore ammesso
     * (ad esempio nessun lavapiatti a pranzo), un numero negativo no.
     *
     * @return l'elenco dei problemi trovati, vuoto se i dati sono validi
     */
    public List<String> valida() {
        List<String> errori = new ArrayList<>();
        if (ruolo == null) {
            errori.add("Il ruolo è obbligatorio");
        }
        if (tipoGiorno == null) {
            errori.add("Il tipo di giorno è obbligatorio");
        }
        if (servizio == null) {
            errori.add("Il servizio è obbligatorio");
        }
        if (numeroMinimoDipendenti < 0) {
            errori.add("Il numero di dipendenti non può essere negativo");
        } else if (numeroMinimoDipendenti > MAX_NUMERO_DIPENDENTI) {
            errori.add("Il numero di dipendenti non può superare " + MAX_NUMERO_DIPENDENTI);
        }
        return errori;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public Ruolo getRuolo() {
        return ruolo;
    }

    public void setRuolo(Ruolo ruolo) {
        this.ruolo = ruolo;
    }

    public TipoGiorno getTipoGiorno() {
        return tipoGiorno;
    }

    public void setTipoGiorno(TipoGiorno tipoGiorno) {
        this.tipoGiorno = tipoGiorno;
    }

    public Servizio getServizio() {
        return servizio;
    }

    public void setServizio(Servizio servizio) {
        this.servizio = servizio;
    }

    public int getNumeroMinimoDipendenti() {
        return numeroMinimoDipendenti;
    }

    public void setNumeroMinimoDipendenti(int numeroMinimoDipendenti) {
        this.numeroMinimoDipendenti = numeroMinimoDipendenti;
    }

    /** Due fabbisogni sono lo stesso se hanno lo stesso id (diverso da 0, cioe' gia' salvato). */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Fabbisogno)) return false;
        Fabbisogno altro = (Fabbisogno) o;
        return id != 0 && id == altro.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return ruolo + " - " + tipoGiorno + " - " + servizio + ": " + numeroMinimoDipendenti;
    }
}
