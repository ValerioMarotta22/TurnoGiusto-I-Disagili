package it.disagili.turnogiusto.model;

public enum TipoContratto {
    FULL_TIME("Tempo pieno"),
    PART_TIME("Part-time");

    private final String etichetta;

    TipoContratto(String etichetta) {
        this.etichetta = etichetta;
    }

    public String getEtichetta() {
        return etichetta;
    }

    /** Testo per l'interfaccia. Per il database si usa name(). */
    @Override
    public String toString() {
        return etichetta;
    }
}
