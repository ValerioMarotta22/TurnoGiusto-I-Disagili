package it.disagili.turnogiusto.service.vincoli;

import it.disagili.turnogiusto.model.Servizio;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Dati di un turno assegnato a un dipendente, necessari ai controlli US07.
 *
 * Il giorno del piano resta distinto dalla data di fine:
 * la cena della domenica appartiene alla domenica,
 * anche se termina il lunedì alle 00:30.
 *
 * Questo oggetto conserva i dati ricevuti.
 * Non accede al database e non calcola gli orari standard.
 *
 * @param dipendenteId identificativo del dipendente assegnato
 * @param giornoPiano giorno a cui appartiene il turno nel piano
 * @param servizio PRANZO oppure CENA, usando l'enum già esistente
 * @param inizio data e ora effettive di inizio del turno
 * @param fine data e ora effettive di fine del turno
 */
public record AssegnazioneDaVerificare(
        int dipendenteId,
        LocalDate giornoPiano,
        Servizio servizio,
        LocalDateTime inizio,
        LocalDateTime fine){

}