package it.disagili.turnogiusto.service;

/** Le sezioni dell'applicazione a cui l'accesso e' controllato. Chi puo' usarle lo decide Autorizzazioni. */
public enum Funzione {
    /** Registrazione dei dipendenti e dei loro contratti (US-01, US-02). */
    GESTIONE_DIPENDENTI,
    /** Disponibilita' dei part-time viste dal titolare (US-03, US-04). */
    DISPONIBILITA_PART_TIME,
    /** Quante persone servono per ruolo, giorno e servizio (US-05). */
    FABBISOGNO,
    /** Generazione automatica del piano settimanale (US-06A). */
    GENERA_PIANO,
    /** Modifica del piano settimanale (US-06B). */
    MODIFICA_PIANO,
    /** Le proprie fasce di disponibilita' (solo i dipendenti part-time). */
    LE_MIE_DISPONIBILITA,
    /** I propri turni della settimana. */
    I_MIEI_TURNI
}
