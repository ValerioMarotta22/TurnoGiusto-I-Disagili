package it.disagili.turnogiusto.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Punto unico per ottenere una connessione al database MySQL.
 *
 * Le impostazioni (MYSQL_PORT, MYSQL_DATABASE, MYSQL_USER, MYSQL_PASSWORD) sono
 * le stesse di docker-compose.yml e vengono cercate in quest'ordine:
 * variabile d'ambiente, file ".env" nella cartella del progetto, valore di
 * default (quello del database avviato con "docker compose up -d").
 * Quindi se la porta 3306 e' occupata basta scrivere MYSQL_PORT=3307 nel
 * proprio ".env": vale sia per Docker sia per l'applicazione.
 *
 * Ogni chiamata apre una connessione nuova: chi la usa deve chiuderla
 * (try-with-resources).
 */
public final class Database {

    private static final Map<String, String> DOT_ENV = loadDotEnv();

    private Database() {
    }

    public static Connection getConnection() throws SQLException {
        // Sviluppo locale: niente SSL e recupero della chiave pubblica consentito.
        // Per un ambiente diverso da localhost vanno riviste queste opzioni.
        String url = "jdbc:mysql://localhost:" + setting("MYSQL_PORT", "3306")
                + "/" + setting("MYSQL_DATABASE", "turnogiusto")
                + "?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=3000";
        return DriverManager.getConnection(url,
                setting("MYSQL_USER", "turnogiusto"),
                setting("MYSQL_PASSWORD", "turnogiusto"));
    }

    private static String setting(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            value = DOT_ENV.get(name);
        }
        return (value == null || value.isBlank()) ? defaultValue : value;
    }

    private static Map<String, String> loadDotEnv() {
        Map<String, String> values = new HashMap<>();
        Path file = Path.of(".env");
        if (!Files.isRegularFile(file)) {
            return values;
        }
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                String text = line.trim();
                int eq = text.indexOf('=');
                if (!text.isEmpty() && !text.startsWith("#") && eq > 0) {
                    values.put(text.substring(0, eq).trim(), text.substring(eq + 1).trim());
                }
            }
        } catch (IOException e) {
            // file illeggibile: si usano i valori di default
        }
        return values;
    }
}
