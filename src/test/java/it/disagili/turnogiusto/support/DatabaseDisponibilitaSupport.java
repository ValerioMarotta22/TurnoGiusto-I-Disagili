package it.disagili.turnogiusto.support;

import it.disagili.turnogiusto.repository.Database;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Prepara dati di prova nel MySQL dell'applicazione e li rimuove per ID. */
@Tag("database")
public abstract class DatabaseDisponibilitaSupport {

    private final List<Integer> dipendentiDiProva = new ArrayList<>();

    protected int giulia;
    protected int sara;
    protected int paolo;
    protected int mario;
    protected int senzaContratto;

    @BeforeEach
    public void preparaDati() throws SQLException {
        giulia = creaDipendente("Giulia", "Bianchi", "PART_TIME");
        sara = creaDipendente("Sara", "Neri", "PART_TIME");
        paolo = creaDipendente("Paolo", "Blu", "PART_TIME");
        mario = creaDipendente("Mario", "Rossi", "FULL_TIME");
        senzaContratto = creaDipendente("Lea", "Gialli", null);
    }

    @AfterEach
    public void pulisciDati() throws SQLException {
        if (dipendentiDiProva.isEmpty()) return;
        // Le chiavi esterne rimuovono in cascata i contratti e le fasce dei soli ID creati qui.
        try (Connection connessione = Database.getConnection();
             PreparedStatement statement = connessione.prepareStatement("DELETE FROM dipendente WHERE id = ?")) {
            for (int id : dipendentiDiProva) {
                statement.setInt(1, id);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    protected boolean dipendenteDiProva(int id) {
        return dipendentiDiProva.contains(id);
    }

    private int creaDipendente(String nome, String cognome, String tipo) throws SQLException {
        int id;
        try (Connection connessione = Database.getConnection();
             PreparedStatement statement = connessione.prepareStatement(
                     "INSERT INTO dipendente(nome, cognome, ruolo) VALUES (?, ?, 'CAMERIERE')",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, "US03-04 " + nome);
            statement.setString(2, cognome);
            statement.executeUpdate();
            try (ResultSet chiavi = statement.getGeneratedKeys()) {
                if (!chiavi.next()) throw new SQLException("ID di test non generato");
                id = chiavi.getInt(1);
                // Lo registriamo subito per poterlo pulire anche se fallisce il contratto.
                dipendentiDiProva.add(id);
            }
        }
        if (tipo != null) aggiungiContratto(id, tipo, LocalDate.now().minusDays(10), null);
        return id;
    }

    protected void aggiungiContratto(int id, String tipo, LocalDate inizio, LocalDate fine)
            throws SQLException {
        try (Connection connessione = Database.getConnection();
             PreparedStatement statement = connessione.prepareStatement("""
                     INSERT INTO contratto
                         (dipendente_id, tipo, ore_settimanali, costo_orario, data_inizio, data_fine)
                     VALUES (?, ?, 20, 10.00, ?, ?)
                     """)) {
            statement.setInt(1, id);
            statement.setString(2, tipo);
            statement.setDate(3, Date.valueOf(inizio));
            statement.setDate(4, fine == null ? null : Date.valueOf(fine));
            statement.executeUpdate();
        }
    }

    protected void esegui(String sql) throws SQLException {
        try (Connection connessione = Database.getConnection();
             Statement statement = connessione.createStatement()) {
            statement.execute(sql);
        }
    }
}
