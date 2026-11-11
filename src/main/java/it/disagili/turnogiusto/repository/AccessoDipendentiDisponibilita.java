package it.disagili.turnogiusto.repository;

import it.disagili.turnogiusto.model.Dipendente;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Punto di collegamento con US-01/US-02. La lettura ha la firma proposta da Antonio.
 * Un adattatore potra delegare al suo repository senza cambiare le schermate.
 */
public interface AccessoDipendentiDisponibilita {

    List<Dipendente> trovaTutti() throws SQLException;

    /**
     * Rilegge il contratto prima del salvataggio e rifiuta dipendenti non idonei.
     * Usa la connessione della transazione: non deve chiuderla o eseguire commit.
     * Deve segnalare anche contratti validi sovrapposti, senza sceglierne uno.
     */
    void verificaPartTime(Connection connessione, int dipendenteId, LocalDate data)
            throws SQLException;
}
