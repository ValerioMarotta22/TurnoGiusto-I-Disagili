package it.disagili.turnogiusto.service.vincoli;

/**
 * Identifica la regola violata senza dover interpretare il messaggio per il titolare.
 * I dettagli del dipendente e dei turni saranno contenuti nella violazione.
 */
public enum TipoViolazione {

    // Tra i due turni trascorrono meno di 11 ore.
    RIPOSO_INSUFFICIENTE,

    // Lo stesso dipendente occupa due fasce adiacenti:
    // pranzo e cena dello stesso giorno, oppure
    // cena e pranzo del giorno successivo.
    TURNI_CONSECUTIVI,

    // Il dipendente ha assegnazioni in tutti i sette giorni del piano.
    RIPOSO_SETTIMANALE_ASSENTE,

    // Le ore assegnate superano il limite settimanale applicabile.
    MONTE_ORE_SUPERATO

}