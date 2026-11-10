package it.disagili.turnogiusto.model;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Una finestra di disponibilita' settimanale di un dipendente part-time
 * (US-03), che vale ogni settimana: ad esempio "ogni giovedi' dalle 18:00
 * alle 23:00".
 */
public class Disponibilita {

    private int id;
    private int dipendenteId;
    private DayOfWeek giornoSettimana;
    private LocalTime oraInizio;
    private LocalTime oraFine;

    public Disponibilita(int id, int dipendenteId,
                         DayOfWeek giornoSettimana, LocalTime oraInizio,
                         LocalTime oraFine) {
        this.id = id;
        this.dipendenteId = dipendenteId;
        this.giornoSettimana = giornoSettimana;
        this.oraInizio = oraInizio;
        this.oraFine = oraFine;
    }

    // Per una disponibilita' nuova, non ancora salvata: l'id lo assegna il database.
    public Disponibilita(int dipendenteId, DayOfWeek giornoSettimana,
                         LocalTime oraInizio, LocalTime oraFine) {
        this(0, dipendenteId, giornoSettimana, oraInizio, oraFine);
    }

    /**
     * La finestra finisce dopo la mezzanotte (ad esempio dalle 18:00 alle
     * 02:00): l'ora di fine e' quella del giorno dopo.
     */
    public boolean attraversaMezzanotte() {
        return oraInizio != null && oraFine != null && oraFine.isBefore(oraInizio);
    }

    /**
     * Controlla i dati della finestra.
     *
     * @return l'elenco dei problemi trovati, vuoto se la finestra e' valida
     */
    public List<String> valida() {
        List<String> errori = new ArrayList<>();
        if (giornoSettimana == null) {
            errori.add("Il giorno della settimana è obbligatorio");
        }
        if (oraInizio == null) {
            errori.add("L'ora di inizio è obbligatoria");
        }
        if (oraFine == null) {
            errori.add("L'ora di fine è obbligatoria");
        }
        if (oraInizio != null && oraFine != null && oraInizio.equals(oraFine)) {
            errori.add("L'ora di inizio e quella di fine non possono coincidere");
        }
        return errori;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getDipendenteId() {
        return dipendenteId;
    }

    public void setDipendenteId(int dipendenteId) {
        this.dipendenteId = dipendenteId;
    }

    public DayOfWeek getGiornoSettimana() {
        return giornoSettimana;
    }

    public void setGiornoSettimana(DayOfWeek giornoSettimana) {
        this.giornoSettimana = giornoSettimana;
    }

    public LocalTime getOraInizio() {
        return oraInizio;
    }

    public void setOraInizio(LocalTime oraInizio) {
        this.oraInizio = oraInizio;
    }

    public LocalTime getOraFine() {
        return oraFine;
    }

    public void setOraFine(LocalTime oraFine) {
        this.oraFine = oraFine;
    }

    /** Due disponibilita' sono la stessa se hanno lo stesso id (diverso da 0, cioe' gia' salvata). */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Disponibilita)) return false;
        Disponibilita altra = (Disponibilita) o;
        return id != 0 && id == altra.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        String giorno = giornoSettimana == null
                ? "?" : giornoSettimana.getDisplayName(TextStyle.FULL, Locale.ITALIAN);
        return giorno + " " + oraInizio + "-" + oraFine
                + (attraversaMezzanotte() ? " (fino al giorno dopo)" : "");
    }
}
