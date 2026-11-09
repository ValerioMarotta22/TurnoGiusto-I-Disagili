package it.disagili.turnogiusto.repository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova di collegamento al database. Se il database non e' acceso
 * (docker compose up -d) il test viene SALTATO, non fallisce: cosi'
 * "mvnw test" funziona anche su un PC senza Docker.
 */
class DatabaseTest {

    private static Connection connection;

    @BeforeAll
    static void connect() {
        try {
            connection = Database.getConnection();
        } catch (SQLException e) {
            connection = null;
        }
    }

    @AfterAll
    static void disconnect() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    @Test
    void iQuattroRuoliSonoPresentiNelDatabase() throws SQLException {
        Assumptions.assumeTrue(connection != null,
                "Database non raggiungibile: avvia 'docker compose up -d' per eseguire questo test");

        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM ruolo")) {
            assertTrue(rs.next());
            assertEquals(4, rs.getInt(1));
        }
    }
}
