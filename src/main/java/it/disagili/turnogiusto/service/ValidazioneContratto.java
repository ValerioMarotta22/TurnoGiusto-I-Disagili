package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.Contratto;
import java.math.BigDecimal;
import java.util.List;

/** Limiti SQL condivisi da registrazione e aggiornamento, senza modificare i model. */
final class ValidazioneContratto {
    // Classe di utilità: tutti i metodi sono statici, quindi non serve istanziarla.
    private ValidazioneContratto() { }

    /** Aggiunge gli errori alla lista ricevuta, senza modificare importi o contratto. */
    static void aggiungiLimitiPersistenza(Contratto contratto, List<String> errori) {
        // I campi assenti sono già segnalati dalle validazioni dei model.
        if (contratto == null || contratto.getCostoOrario() == null) {
            return;
        }
        BigDecimal costo = contratto.getCostoOrario();
        // DECIMAL(6,2) lascia quattro cifre intere e due decimali.
        // Il limite è costruito da testo per evitare approssimazioni di tipo double.
        if (costo.compareTo(new BigDecimal("9999.99")) > 0) {
            errori.add("Il costo orario non può superare 9999,99 euro");
        }
        // Accettiamo zeri finali (12.500), ma non arrotondiamo importi come 12.501.
        if (costo.stripTrailingZeros().scale() > 2) {
            errori.add("Il costo orario può avere al massimo due cifre decimali");
        }
    }
}
