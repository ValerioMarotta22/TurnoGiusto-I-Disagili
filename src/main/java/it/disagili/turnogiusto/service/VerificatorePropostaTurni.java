package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.Contratto;

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
}
