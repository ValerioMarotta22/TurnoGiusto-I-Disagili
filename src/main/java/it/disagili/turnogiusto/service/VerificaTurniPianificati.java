package it.disagili.turnogiusto.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Punto di collegamento alla futura pianificazione. Deve verificare se il dipendente
 * ha turni già pianificati dalla decorrenza indicata, usando solo la connessione ricevuta.
 * Non deve chiuderla, cambiarne autoCommit o eseguire commit/rollback.
 * Nessuna tabella dei turni è attualmente presente nel progetto locale.
 */
@FunctionalInterface
public interface VerificaTurniPianificati {
    // Una interfaccia funzionale ha un unico metodo astratto: nei test possiamo
    // fornirlo con una lambda; nell'integrazione reale con un componente dedicato.
    enum Stato {
        // PRESENTI: trovati turni; ASSENTI: ricerca eseguita senza risultati.
        // NON_DISPONIBILE: non possiamo sapere se ci siano turni, manca l'integrazione.
        // NON_RICHIESTA: il service non ha interrogato i turni perché le ore non diminuiscono.
        PRESENTI, ASSENTI, NON_DISPONIBILE, NON_RICHIESTA
    }

    /**
     * Il service chiama questo metodo solo quando riduce le ore, dopo aver riletto
     * il contratto e prima di modificarlo. Il componente reale dovrà interrogare
     * la pianificazione del dipendente dalla decorrenza compresa, senza modificarla.
     * Deve solo restituire lo stato: l'avviso si comunica dopo il ritorno del service,
     * quando anche il salvataggio è stato confermato dal commit.
     *
     * @return PRESENTI, ASSENTI oppure NON_DISPONIBILE; mai null o NON_RICHIESTA,
     *         che è assegnato dal service quando non serve interrogare i turni
     * @throws SQLException se la lettura fallisce: il service interrompe il salvataggio;
     *                      un errore di query non va trasformato in ASSENTI
     */
    Stato verifica(Connection connessione, int dipendenteId, LocalDate decorrenza)
            throws SQLException;
}
