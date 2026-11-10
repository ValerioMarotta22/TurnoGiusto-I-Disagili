package it.disagili.turnogiusto.model;

public enum Ruolo {
    CUOCO("Cuoco"),
    AIUTO_CUOCO("Aiuto cuoco"),
    CAMERIERE("Cameriere"),
    CAPOSALA("Caposala"),
    LAVAPIATTI("Lavapiatti");

    private final String etichetta;

    Ruolo(String etichetta) {
        this.etichetta = etichetta;
    }

    public String getEtichetta() {
        return etichetta;
    }

    /**
     * Testo leggibile per l'interfaccia (liste a tendina, tabelle).
     * Per salvare nel database si usa name(), non toString().
     */
    @Override
    public String toString() {
        return etichetta;
    }
}
