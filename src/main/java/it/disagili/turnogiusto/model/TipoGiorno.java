package it.disagili.turnogiusto.model;

public enum TipoGiorno {
    FERIALE("Feriale"),
    FESTIVO("Festivo"),
    WEEKEND("Weekend");

    private final String etichetta;

    TipoGiorno(String etichetta) {
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
