package it.disagili.turnogiusto.service.vincoli;

import java.util.List;

/**
 * Descrive una regola non rispettata e conserva i dati necessari a spiegarla.
 *
 * @param tipo regola violata, riconoscibile senza interpretare il testo
 * @param dipendenteId identificativo del dipendente interessato
 * @param turniCoinvolti assegnazioni che permettono di individuare il problema
 * @param motivo spiegazione con il valore rilevato e il limite richiesto
 */
public record ViolazioneTurno(
        TipoViolazione tipo,
        int dipendenteId,
        List<AssegnazioneDaVerificare> turniCoinvolti,
        String motivo) {

    /**
     * Il costruttore compatto viene eseguito alla creazione del record.
     * La copia impedisce che modifiche successive alla lista originale cambino
     * una violazione già restituita; anche la lista esposta non è modificabile.
     */
    public ViolazioneTurno {
        turniCoinvolti = List.copyOf(turniCoinvolti);
    }
}

