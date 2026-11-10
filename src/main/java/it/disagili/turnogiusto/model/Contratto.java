package it.disagili.turnogiusto.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Contratto {

    /** Vincolo del dominio: al massimo 40 ore settimanali. */
    public static final int MAX_ORE_SETTIMANALI = 40;

    private int id;
    private int dipendenteId;
    private TipoContratto tipoContratto;
    private int oreSettimanali;
    private BigDecimal costoOrario;
    private LocalDate dataInizio;
    private LocalDate dataFine; // null = contratto senza scadenza

    public Contratto(int id, int dipendenteId, TipoContratto tipoContratto,
                     int oreSettimanali, BigDecimal costoOrario,
                     LocalDate dataInizio, LocalDate dataFine) {
        this.id = id;
        this.dipendenteId = dipendenteId;
        this.tipoContratto = tipoContratto;
        this.oreSettimanali = oreSettimanali;
        this.costoOrario = costoOrario;
        this.dataInizio = dataInizio;
        this.dataFine = dataFine;
    }

    // Per un contratto nuovo, non ancora salvato: l'id lo assegna il database.
    public Contratto(int dipendenteId, TipoContratto tipoContratto,
                     int oreSettimanali, BigDecimal costoOrario,
                     LocalDate dataInizio) {
        this(0, dipendenteId, tipoContratto, oreSettimanali, costoOrario,
                dataInizio, null);
    }

    /** Il contratto vale nel giorno indicato (estremi compresi). */
    public boolean isValidoIl(LocalDate giorno) {
        return dataInizio != null
                && !giorno.isBefore(dataInizio)
                && (dataFine == null || !giorno.isAfter(dataFine));
    }

    /** Il contratto vale oggi. */
    public boolean isInVigore() {
        return isValidoIl(LocalDate.now());
    }

    /**
     * Controlla i dati del contratto.
     *
     * @return l'elenco dei problemi trovati, vuoto se il contratto e' valido
     */
    public List<String> valida() {
        List<String> errori = new ArrayList<>();
        if (tipoContratto == null) {
            errori.add("Il tipo di contratto è obbligatorio");
        }
        if (oreSettimanali <= 0) {
            errori.add("Le ore settimanali devono essere maggiori di zero");
        } else if (oreSettimanali > MAX_ORE_SETTIMANALI) {
            errori.add("Le ore settimanali non possono superare " + MAX_ORE_SETTIMANALI);
        }
        if (costoOrario == null || costoOrario.signum() <= 0) {
            errori.add("Il costo orario deve essere maggiore di zero");
        }
        if (dataInizio == null) {
            errori.add("La data di inizio è obbligatoria");
        } else if (dataFine != null && dataFine.isBefore(dataInizio)) {
            errori.add("La data di fine non può precedere la data di inizio");
        }
        return errori;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public int getDipendenteId() { return dipendenteId; }
    public void setDipendenteId(int dipendenteId) { this.dipendenteId = dipendenteId; }

    public TipoContratto getTipoContratto() { return tipoContratto; }
    public void setTipoContratto(TipoContratto tipoContratto) { this.tipoContratto = tipoContratto; }

    public int getOreSettimanali() { return oreSettimanali; }
    public void setOreSettimanali(int oreSettimanali) { this.oreSettimanali = oreSettimanali; }

    public BigDecimal getCostoOrario() { return costoOrario; }
    public void setCostoOrario(BigDecimal costoOrario) { this.costoOrario = costoOrario; }

    public LocalDate getDataInizio() { return dataInizio; }
    public void setDataInizio(LocalDate dataInizio) { this.dataInizio = dataInizio; }

    public LocalDate getDataFine() { return dataFine; }
    public void setDataFine(LocalDate dataFine) { this.dataFine = dataFine; }

    /** Due contratti sono lo stesso se hanno lo stesso id (diverso da 0, cioe' gia' salvato). */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Contratto)) return false;
        Contratto altro = (Contratto) o;
        return id != 0 && id == altro.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }

    @Override
    public String toString() {
        return tipoContratto + ", " + oreSettimanali + " ore/sett., "
                + costoOrario + " euro/ora, dal " + dataInizio
                + (dataFine == null ? "" : " al " + dataFine);
    }
}
