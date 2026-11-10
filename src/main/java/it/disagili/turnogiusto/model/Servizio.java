package it.disagili.turnogiusto.model;

public enum Servizio {
    PRANZO("Pranzo"),
    CENA("Cena");

    private final String etichetta;

    Servizio(String etichetta) {
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
