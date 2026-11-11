package it.disagili.turnogiusto.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;

/** Dati propri dei test: si puo scrivere esclusivamente nello schema dedicato. */
@ExtendWith(MySqlDisponibilitaExtension.class)
@Tag("database")
public abstract class DatabaseDisponibilitaSupport {

    private static final String DATABASE_TEST = "turnogiusto_us03_test";

    // Diventa true dopo il controllo del catalogo e la pulizia iniziale riuscita.
    // Una preparazione bloccata non deve provocare un secondo errore in @AfterEach.
    private boolean puliziaAbilitata;

    protected int giulia;
    protected int sara;
    protected int paolo;
    protected int mario;
    protected int senzaContratto;

    static void verificaCatalogo(String catalogo) throws SQLException {
        if (!DATABASE_TEST.equals(catalogo)) {
            throw new SQLException("Test di scrittura bloccato: catalogo ricevuto '" + catalogo
                    + "', richiesto '" + DATABASE_TEST + "'. Nessuna pulizia eseguita.");
        }
    }

    /** Separato per verificare le protezioni con connessioni simulate nei test unitari. */
    protected Connection apriConnessione() throws SQLException {
        return MySqlDisponibilitaExtension.apriConnessione();
    }

    @BeforeEach
    public void preparaDati() throws SQLException {
        puliziaAbilitata = false;
        try (Connection connessione = apriConnessione()) {
            verificaCatalogo(connessione.getCatalog());
            cancellaDatiDiProva(connessione);
            puliziaAbilitata = true;
        }
        giulia = creaDipendente("Giulia", "Bianchi", "PART_TIME");
        sara = creaDipendente("Sara", "Neri", "PART_TIME");
        paolo = creaDipendente("Paolo", "Blu", "PART_TIME");
        mario = creaDipendente("Mario", "Rossi", "FULL_TIME");
        senzaContratto = creaDipendente("Lea", "Gialli", null);
    }

    @AfterEach
    public void pulisciDati() throws SQLException {
        if (!puliziaAbilitata) return;
        try (Connection connessione = apriConnessione()) {
            verificaCatalogo(connessione.getCatalog());
            cancellaDatiDiProva(connessione);
        }
    }

    private void cancellaDatiDiProva(Connection connessione) throws SQLException {
        try (Statement statement = connessione.createStatement()) {
            statement.execute("DROP TRIGGER IF EXISTS test_errore_disponibilita");
            statement.executeUpdate("DELETE FROM disponibilita");
            statement.executeUpdate("DELETE FROM contratto");
            statement.executeUpdate("DELETE FROM dipendente");
        }
    }

    protected int creaDipendente(String nome, String cognome, String tipo) throws SQLException {
        int id;
        try (Connection connessione = apriConnessione();
             PreparedStatement statement = connessione.prepareStatement(
                     "INSERT INTO dipendente(nome, cognome, ruolo) VALUES (?, ?, 'CAMERIERE')",
                     Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, nome);
            statement.setString(2, cognome);
            statement.executeUpdate();
            try (ResultSet chiavi = statement.getGeneratedKeys()) {
                if (!chiavi.next()) throw new SQLException("ID di test non generato");
                id = chiavi.getInt(1);
            }
        }
        if (tipo != null) aggiungiContratto(id, tipo, LocalDate.now().minusDays(10), null);
        return id;
    }

    protected void aggiungiContratto(int id, String tipo, LocalDate inizio, LocalDate fine)
            throws SQLException {
        try (Connection connessione = apriConnessione();
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
        try (Connection connessione = apriConnessione();
             Statement statement = connessione.createStatement()) {
            statement.execute(sql);
        }
    }
}
