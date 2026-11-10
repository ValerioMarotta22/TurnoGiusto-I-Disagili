package it.disagili.turnogiusto.repository;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import it.disagili.turnogiusto.model.Ruolo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Set;
import java.util.TreeSet;

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
    void leQuattroTabelleDelloSchemaSonoPresenti() throws SQLException {
        Assumptions.assumeTrue(connection != null,
                "Database non raggiungibile: avvia 'docker compose up -d' per eseguire questo test");

        Set<String> tabelle = new TreeSet<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SHOW TABLES")) {
            while (rs.next()) {
                tabelle.add(rs.getString(1));
            }
        }
        assertEquals(Set.of("contratto", "dipendente", "disponibilita", "fabbisogno"), tabelle,
                "Schema vecchio? Ricrea il database: docker compose down -v, poi docker compose up -d --wait");
    }

    /** Le colonne ENUM del database devono avere gli stessi valori dell'enum Java Ruolo. */
    @Test
    void iRuoliDelDatabaseCoincidonoConLEnumRuolo() throws SQLException {
        Assumptions.assumeTrue(connection != null,
                "Database non raggiungibile: avvia 'docker compose up -d' per eseguire questo test");

        Set<String> attesi = new TreeSet<>();
        for (Ruolo r : Ruolo.values()) {
            attesi.add(r.name());
        }
        assertEquals(attesi, valoriEnum("dipendente", "ruolo"),
                "dipendente.ruolo non coincide con l'enum Ruolo: ricrea il database");
        assertEquals(attesi, valoriEnum("fabbisogno", "ruolo"),
                "fabbisogno.ruolo non coincide con l'enum Ruolo: ricrea il database");
    }

    private static Set<String> valoriEnum(String tabella, String colonna) throws SQLException {
        String sql = "SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, tabella);
            ps.setString(2, colonna);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Colonna non trovata: " + tabella + "." + colonna);
                // formato: enum('CUOCO','AIUTO_CUOCO',...)
                String tipo = rs.getString(1);
                String elenco = tipo.substring(tipo.indexOf('(') + 1, tipo.lastIndexOf(')'));
                Set<String> valori = new TreeSet<>();
                for (String v : elenco.split(",")) {
                    valori.add(v.trim().replace("'", ""));
                }
                return valori;
            }
        }
    }
}
