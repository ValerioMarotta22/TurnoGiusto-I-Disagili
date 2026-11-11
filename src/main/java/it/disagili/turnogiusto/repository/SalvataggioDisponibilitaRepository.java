package it.disagili.turnogiusto.repository;

import it.disagili.turnogiusto.model.Disponibilita;
import it.disagili.turnogiusto.service.ValidazioneDisponibilita;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Time;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Scrittura transazionale delle disponibilita, separata dalla lettura. */
public class SalvataggioDisponibilitaRepository {

    private final AccessoDipendentiDisponibilita dipendenti;
    private final FornitoreConnessioni connessioni;

    public SalvataggioDisponibilitaRepository(AccessoDipendentiDisponibilita dipendenti) {
        this(dipendenti, Database::getConnection);
    }

    public SalvataggioDisponibilitaRepository(AccessoDipendentiDisponibilita dipendenti,
                                             FornitoreConnessioni connessioni) {
        this.dipendenti = Objects.requireNonNull(dipendenti);
        this.connessioni = Objects.requireNonNull(connessioni);
    }

    public void sostituisciPerDipendente(int dipendenteId, List<Disponibilita> finestre)
            throws SQLException {
        ValidazioneDisponibilita.verifica(dipendenteId, finestre);

        try (Connection connessione = connessioni.apri()) {
            connessione.setAutoCommit(false);
            try {
                // Rileggiamo il contratto: quello mostrato dalla UI potrebbe essere cambiato.
                dipendenti.verificaPartTime(connessione, dipendenteId, LocalDate.now());

                try (PreparedStatement cancella = connessione.prepareStatement(
                        "DELETE FROM disponibilita WHERE dipendente_id = ?")) {
                    cancella.setInt(1, dipendenteId);
                    cancella.executeUpdate();
                }

                String sql = """
                        INSERT INTO disponibilita
                            (dipendente_id, giorno_settimana, ora_inizio, ora_fine)
                        VALUES (?, ?, ?, ?)
                        """;
                try (PreparedStatement inserisci = connessione.prepareStatement(sql)) {
                    for (Disponibilita finestra : finestre) {
                        inserisci.setInt(1, dipendenteId);
                        inserisci.setInt(2, finestra.getGiornoSettimana().getValue());
                        inserisci.setTime(3, Time.valueOf(finestra.getOraInizio()));
                        inserisci.setTime(4, Time.valueOf(finestra.getOraFine()));
                        inserisci.executeUpdate();
                    }
                }
                // Una lista vuota arriva qui dopo la sola cancellazione: scelta valida.
                connessione.commit();
            } catch (SQLException | RuntimeException errore) {
                try {
                    connessione.rollback();
                } catch (SQLException erroreRollback) {
                    errore.addSuppressed(erroreRollback);
                }
                throw errore;
            }
        }
    }
}
