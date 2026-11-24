package it.disagili.turnogiusto.model;

/**
 * Il tipo di account con cui si entra nell'applicazione. NON e' il ruolo di lavoro (Ruolo: cuoco,
 * cameriere...): decide a quali sezioni si ha accesso.
 *   - TITOLARE: gestisce dipendenti, fabbisogno e turni;
 *   - DIPENDENTE: vede i propri turni e, se e' part-time, gestisce le proprie disponibilita'.
 * Nel database si salva name(), nella schermata si mostra toString().
 */
public enum TipoUtente {
    TITOLARE("Titolare"),
    DIPENDENTE("Dipendente");

    private final String etichetta;

    TipoUtente(String etichetta) {
        this.etichetta = etichetta;
    }

    public String getEtichetta() {
        return etichetta;
    }

    @Override
    public String toString() {
        return etichetta;
    }
}
