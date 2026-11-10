package it.disagili.turnogiusto.model;

/**
 * Ruoli operativi dei dipendenti. Il caposala NON e' un ruolo: e' un incarico
 * che il titolare assegna, turno per turno, a un cameriere full-time
 * (vedi {@link Dipendente#puoEssereCaposala()}).
 */
public enum Ruolo {
    CUOCO("Cuoco"),
    AIUTO_CUOCO("Aiuto cuoco"),
    CAMERIERE("Cameriere"),
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
