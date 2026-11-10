package it.disagili.turnogiusto.model;

import java.util.ArrayList;
import java.util.List;

public class Dipendente {

    private int id;
    private String nome;
    private String cognome;
    private Ruolo ruolo;
    private Contratto contratto; // il contratto attuale

    public Dipendente(int id, String nome, String cognome,
                      Ruolo ruolo, Contratto contratto) {
        this.id = id;
        this.nome = nome;
        this.cognome = cognome;
        this.ruolo = ruolo;
        this.contratto = contratto;
    }

    // Per un dipendente nuovo, non ancora salvato: l'id lo assegna il database.
    public Dipendente(String nome, String cognome,
                      Ruolo ruolo, Contratto contratto) {
        this(0, nome, cognome, ruolo, contratto);
    }

    public String getNomeCompleto() {
        String n = nome == null ? "" : nome.trim();
        String c = cognome == null ? "" : cognome.trim();
        return (n + " " + c).trim();
    }

    public boolean isPartTime() {
        return contratto != null && contratto.getTipoContratto() == TipoContratto.PART_TIME;
    }

    /**
     * Controlla i dati del dipendente e del suo contratto (US-01).
     *
     * @return l'elenco dei problemi trovati, vuoto se i dati sono validi
     */
    public List<String> valida() {
        List<String> errori = new ArrayList<>();
        if (nome == null || nome.isBlank()) {
            errori.add("Il nome è obbligatorio");
        }
        if (cognome == null || cognome.isBlank()) {
            errori.add("Il cognome è obbligatorio");
        }
        if (ruolo == null) {
            errori.add("Il ruolo è obbligatorio");
        }
        if (contratto == null) {
            errori.add("I dati di contratto sono obbligatori");
        } else {
            errori.addAll(contratto.valida());
        }
        return errori;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCognome() {
        return cognome;
    }

    public void setCognome(String cognome) {
        this.cognome = cognome;
    }

    public Ruolo getRuolo() {
        return ruolo;
    }

    public void setRuolo(Ruolo ruolo) {
        this.ruolo = ruolo;
    }

    public Contratto getContratto() {
        return contratto;
    }

    public void setContratto(Contratto contratto) {
        this.contratto = contratto;
    }

    /** Due dipendenti sono lo stesso se hanno lo stesso id (diverso da 0, cioe' gia' salvato). */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Dipendente)) return false;
        Dipendente altro = (Dipendente) o;
        return id != 0 && id == altro.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return getNomeCompleto() + (ruolo == null ? "" : " (" + ruolo + ")");
    }
}
