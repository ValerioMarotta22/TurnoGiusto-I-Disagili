package it.disagili.turnogiusto.repository;

import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.TipoContratto;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;

/**
 * Legge e scrive i contratti usando esclusivamente la connessione del chiamante.
 * I metodi chiudono i propri statement e risultati, mai la connessione ricevuta.
 * Validazione, scelta delle date e commit/rollback competono ai service.
 */
public class ContrattoRepository {

    /**
     * Rilegge e blocca anagrafica e contratti nella transazione del chiamante.
     * Il blocco della riga dipendente serializza i cambi fatti attraverso questo service.
     * Leggiamo tutti i tipi e tutte le date per non nascondere sovrapposizioni.
     * La connessione rimane aperta e il suo autoCommit non viene modificato.
     */
    public List<Contratto> trovaPerAggiornamento(Connection connessione, int dipendenteId)
            throws SQLException {
        // Con autoCommit attivo i blocchi sarebbero rilasciati dopo ogni comando.
        // Il repository segnala l'uso scorretto, senza cambiare la configurazione ricevuta.
        if (connessione.getAutoCommit()) {
            throw new IllegalArgumentException("La rilettura richiede una transazione attiva");
        }
        // FOR UPDATE è una lettura con blocco. Le altre operazioni che seguono
        // lo stesso protocollo sul dipendente devono attendere la fine della transazione.
        try (PreparedStatement statement = connessione.prepareStatement(
                "SELECT id FROM dipendente WHERE id = ? FOR UPDATE")) {
            statement.setInt(1, dipendenteId);
            try (ResultSet risultati = statement.executeQuery()) {
                if (!risultati.next()) {
                    throw new IllegalArgumentException("Il dipendente non esiste");
                }
            }
        }
        // Un elenco vuoto significa dipendente esistente ma senza contratti.
        // Il service distinguerà questo caso da quello del dipendente inesistente.
        List<Contratto> contratti = new ArrayList<>();
        try (PreparedStatement statement = connessione.prepareStatement(
                "SELECT * FROM contratto WHERE dipendente_id = ? ORDER BY data_inizio, id FOR UPDATE")) {
            statement.setInt(1, dipendenteId);
            try (ResultSet risultati = statement.executeQuery()) {
                while (risultati.next()) {
                    Date fine = risultati.getDate("data_fine");
                    contratti.add(new Contratto(risultati.getInt("id"), dipendenteId,
                            TipoContratto.valueOf(risultati.getString("tipo")),
                            risultati.getInt("ore_settimanali"), risultati.getBigDecimal("costo_orario"),
                            risultati.getDate("data_inizio").toLocalDate(),
                            fine == null ? null : fine.toLocalDate()));
                }
            }
        }
        return contratti;
    }

    /** Chiude soltanto il contratto indicato, senza confermare la transazione. */
    public void chiudi(Connection connessione, int dipendenteId, int contrattoId,
                       LocalDate fine) throws SQLException {
        // Oltre agli ID, la WHERE verifica che la nuova fine non preceda l'inizio
        // né prolunghi un contratto già scaduto prima di quella data.
        try (PreparedStatement statement = connessione.prepareStatement("""
                UPDATE contratto SET data_fine = ?
                WHERE id = ? AND dipendente_id = ? AND data_inizio <= ?
                  AND (data_fine IS NULL OR data_fine >= ?)
                """)) {
            statement.setDate(1, Date.valueOf(fine));
            statement.setInt(2, contrattoId);
            statement.setInt(3, dipendenteId);
            statement.setDate(4, Date.valueOf(fine));
            statement.setDate(5, Date.valueOf(fine));
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Impossibile chiudere il contratto precedente");
            }
        }
    }

    /**
     * Eccezione concordata per i contratti iniziati oggi: corregge la stessa riga,
     * mantenendo ID, dipendente e data iniziale. Non crea uno storico infragiornaliero.
     */
    public void correggiOdierno(Connection connessione, int dipendenteId, int contrattoId,
                                Contratto dati) throws SQLException {
        // La data iniziale non compare nella SET: rimane invariata. Nella WHERE
        // verifichiamo che coincida con la decorrenza già controllata dal service.
        try (PreparedStatement statement = connessione.prepareStatement("""
                UPDATE contratto SET tipo = ?, ore_settimanali = ?, costo_orario = ?, data_fine = ?
                WHERE id = ? AND dipendente_id = ? AND data_inizio = ?
                """)) {
            statement.setString(1, dati.getTipoContratto().name());
            statement.setInt(2, dati.getOreSettimanali());
            statement.setBigDecimal(3, dati.getCostoOrario());
            if (dati.getDataFine() == null) {
                statement.setNull(4, Types.DATE);
            } else {
                statement.setDate(4, Date.valueOf(dati.getDataFine()));
            }
            statement.setInt(5, contrattoId);
            statement.setInt(6, dipendenteId);
            statement.setDate(7, Date.valueOf(dati.getDataInizio()));
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Impossibile correggere il contratto odierno");
            }
        }
    }

    /**
     * Inserisce un contratto già validato e restituisce l'ID generato.
     * Il chiamante gestisce transazione e chiusura della connessione.
     */
    public int inserisci(Connection connessione, int dipendenteId,
                         Contratto contratto) throws SQLException {

        // I sei parametri corrispondono alle colonne nell'ordine elencato.
        // L'ID del contratto è omesso perché lo genera MySQL con AUTO_INCREMENT.
        String sql = """
                INSERT INTO contratto
                    (dipendente_id, tipo, ore_settimanali,
                     costo_orario, data_inizio, data_fine)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        // Chiudiamo lo statement, non la connessione ricevuta dal service.
        // RETURN_GENERATED_KEYS rende disponibile l'ID assegnato al nuovo contratto.
        try (PreparedStatement statement = connessione.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {

            // In US01 il dipendenteId arriva dal primo INSERT nella stessa transazione;
            // in US02 identifica il dipendente già registrato, che rimane lo stesso.
            // name() scrive il nome dell'enum SQL, non l'etichetta dell'interfaccia.
            // BigDecimal mantiene il costo esatto; Date.valueOf adatta LocalDate a JDBC.
            statement.setInt(1, dipendenteId);
            statement.setString(2, contratto.getTipoContratto().name());
            statement.setInt(3, contratto.getOreSettimanali());
            statement.setBigDecimal(4, contratto.getCostoOrario());
            statement.setDate(5, Date.valueOf(contratto.getDataInizio()));

            // La data di fine può mancare: in quel caso scriviamo NULL.
            if (contratto.getDataFine() == null) {
                statement.setNull(6, Types.DATE);
            } else {
                statement.setDate(
                        6, Date.valueOf(contratto.getDataFine())
                );
            }

            // A differenza di executeQuery, executeUpdate restituisce il numero di
            // righe modificate: un inserimento singolo deve produrre una sola riga.
            int righeInserite = statement.executeUpdate();

            if (righeInserite != 1) {
                throw new SQLException(
                        "Inserimento del contratto non riuscito"
                );
            }

            // Le chiavi generate si leggono come un risultato SQL: next raggiunge
            // la prima riga e getInt(1) legge la prima colonna, cioè l'ID generato.
            try (ResultSet chiavi = statement.getGeneratedKeys()) {
                if (!chiavi.next()) {
                    throw new SQLException(
                            "ID del nuovo contratto non disponibile"
                    );
                }

                return chiavi.getInt(1);
            }
        }
    }
}
