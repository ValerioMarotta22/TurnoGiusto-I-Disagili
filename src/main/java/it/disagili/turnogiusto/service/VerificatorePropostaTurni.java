package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.PianoSettimanale;

import java.time.LocalDate;
import java.util.List;

/** Punto di ingresso per collegare le regole US07 alla generazione e alle modifiche manuali. */
@FunctionalInterface
public interface VerificatorePropostaTurni {

    /**
     * Restituisce i vincoli violati dallo stato proposto. Durante la generazione lo stato
     * contiene il candidato e tutte le assegnazioni già accettate. Dopo uno spostamento
     * il chiamante passa tutto il piano risultante con la sola assegnazione sostitutiva.
     * I turni confinanti e i contratti del periodo sono consegnati a parte.
     */
    List<String> verifica(LocalDate inizioSettimana, List<AssegnazioneTurno> proposta,
                         List<AssegnazioneTurno> confinanti, List<Contratto> contratti);

    /**
     * Ingresso con le righe complete, necessario per i vincoli sul fabbisogno.
     * Il default conserva i verificatori personalizzati e le lambda esistenti.
     * L'adattatore US07 lo specializza per applicare anche la quota part-time.
     */
    default List<String> verificaPiano(PianoSettimanale piano,
                                      List<AssegnazioneTurno> confinanti, List<Contratto> contratti) {
        return verifica(piano.inizioSettimana(), piano.assegnazioni(), confinanti, contratti);
    }
}
