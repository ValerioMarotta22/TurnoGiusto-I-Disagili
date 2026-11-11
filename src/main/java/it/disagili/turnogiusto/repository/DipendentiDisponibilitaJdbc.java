package it.disagili.turnogiusto.repository;

import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.TipoContratto;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Accesso di sola lettura a dipendenti e contratti per rendere autonome US-03/04.
 * Nome distinto da DipendenteRepository, che appartiene al lavoro di Antonio.
 * Non modifica mai dipendenti o contratti.
 */
public class DipendentiDisponibilitaJdbc implements AccessoDipendentiDisponibilita {

    private final FornitoreConnessioni connessioni;

    public DipendentiDisponibilitaJdbc() {
        this(Database::getConnection);
    }

    public DipendentiDisponibilitaJdbc(FornitoreConnessioni connessioni) {
        this.connessioni = Objects.requireNonNull(connessioni);
    }

    @Override
    public List<Dipendente> trovaTutti() throws SQLException {
        String sql = """
                SELECT d.id, d.nome, d.cognome, d.ruolo,
                       c.id AS contratto_id, c.tipo, c.ore_settimanali,
                       c.costo_orario, c.data_inizio, c.data_fine
                FROM dipendente d
                LEFT JOIN contratto c ON c.dipendente_id = d.id
                    AND c.data_inizio <= ? AND (c.data_fine IS NULL OR c.data_fine >= ?)
                ORDER BY d.cognome, d.nome, d.id, c.id
                """;
        List<Dipendente> dipendenti = new ArrayList<>();
        Set<Integer> idLetti = new HashSet<>();
        Date oggi = Date.valueOf(LocalDate.now());

        try (Connection connessione = connessioni.apri();
             PreparedStatement statement = connessione.prepareStatement(sql)) {
            statement.setDate(1, oggi);
            statement.setDate(2, oggi);
            try (ResultSet risultati = statement.executeQuery()) {
                while (risultati.next()) {
                    int id = risultati.getInt("id");
                    if (!idLetti.add(id)) {
                        throw new SQLException("Contratti validi sovrapposti per il dipendente " + id);
                    }
                    Contratto contratto = null;
                    int contrattoId = risultati.getInt("contratto_id");
                    if (!risultati.wasNull()) {
                        Date dataFine = risultati.getDate("data_fine");
                        contratto = new Contratto(contrattoId, id,
                                TipoContratto.valueOf(risultati.getString("tipo")),
                                risultati.getInt("ore_settimanali"),
                                risultati.getBigDecimal("costo_orario"),
                                risultati.getDate("data_inizio").toLocalDate(),
                                dataFine == null ? null : dataFine.toLocalDate());
                    }
                    dipendenti.add(new Dipendente(id, risultati.getString("nome"),
                            risultati.getString("cognome"),
                            Ruolo.valueOf(risultati.getString("ruolo")), contratto));
                }
            }
        }
        return dipendenti;
    }

    @Override
    public void verificaPartTime(Connection connessione, int dipendenteId, LocalDate data)
            throws SQLException {
        // Il blocco della riga del dipendente serializza i salvataggi delle sue fasce.
        // Per coordinare anche US-02, l'aggiornamento contratti deve usare lo stesso blocco.
        try (PreparedStatement statement = connessione.prepareStatement(
                "SELECT id FROM dipendente WHERE id = ? FOR UPDATE")) {
            statement.setInt(1, dipendenteId);
            try (ResultSet risultati = statement.executeQuery()) {
                if (!risultati.next()) {
                    throw new IllegalArgumentException("Il dipendente selezionato non esiste più.");
                }
            }
        }

        String sql = """
                SELECT tipo FROM contratto
                WHERE dipendente_id = ? AND data_inizio <= ?
                  AND (data_fine IS NULL OR data_fine >= ?)
                FOR UPDATE
                """;
        try (PreparedStatement statement = connessione.prepareStatement(sql)) {
            statement.setInt(1, dipendenteId);
            statement.setDate(2, Date.valueOf(data));
            statement.setDate(3, Date.valueOf(data));
            try (ResultSet risultati = statement.executeQuery()) {
                if (!risultati.next()) {
                    throw new IllegalArgumentException("Il dipendente non ha un contratto valido oggi.");
                }
                String tipo = risultati.getString("tipo");
                if (risultati.next()) {
                    throw new IllegalArgumentException(
                            "Contratti validi sovrapposti per il dipendente " + dipendenteId);
                }
                if (!TipoContratto.PART_TIME.name().equals(tipo)) {
                    throw new IllegalArgumentException("Le disponibilità sono riservate ai dipendenti part-time.");
                }
            }
        }
    }
}
