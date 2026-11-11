package it.disagili.turnogiusto.support;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Prepara MySQL prima dei test JDBC, sia in Maven sia nel runner JUnit dell'IDE.
 * Riutilizza il DB dedicato se disponibile (anche Docker); altrimenti avvia
 * un mysqld locale in una directory nuova sotto target, senza usare i suoi dati
 * o le sue configurazioni di servizio. Lo spegne al termine della suite JUnit.
 */
public class MySqlDisponibilitaExtension implements BeforeAllCallback {

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        ExtensionContext.Store store = context.getRoot().getStore(
                ExtensionContext.Namespace.create(MySqlDisponibilitaExtension.class));
        // Una sola istanza per l'intera suite, anche se DatabaseTest parte per primo.
        synchronized (MySqlDisponibilitaExtension.class) {
            if (store.get("mysql") == null) {
                store.put("mysql", avviaSeNecessario());
            }
        }
    }

    private static Istanza avviaSeNecessario() throws Exception {
        int porta = leggiPorta(System.getProperty("disponibilita.test.port"));
        try (Connection connessione = apriConnessione()) {
            DatabaseDisponibilitaSupport.verificaCatalogo(connessione.getCatalog());
            return new Istanza(null, porta);
        } catch (SQLException errore) {
            // Credenziali errate o schema assente non si risolvono avviando un altro server.
            if (errore.getSQLState() == null || !errore.getSQLState().startsWith("08")) {
                throw new SQLException("Il server risponde ma il database di test non è utilizzabile. "
                        + "Servono lo schema turnogiusto_us03_test e l'utente turnogiusto_test. "
                        + istruzioniAvvio(), errore);
            }
            if (!Boolean.parseBoolean(System.getProperty("disponibilita.test.autostart", "true"))) {
                throw new SQLException(istruzioniAvvio(), errore);
            }
        }
        // Non tentare di sostituire un servizio che sta già occupando questa porta.
        try (ServerSocket socket = new ServerSocket(porta, 1, InetAddress.getByName("127.0.0.1"))) {
            if (!socket.isBound()) throw new IOException("Porta di test non disponibile: " + porta);
        }
        Path server = trovaServer();
        Path base = Path.of("target", "mysql-test").toAbsolutePath();
        Files.createDirectories(base);
        Path lavoro = Files.createTempDirectory(base, "esecuzione-");
        Path dati = Files.createDirectory(lavoro.resolve("data"));
        Path log = lavoro.resolve("mysql.log");
        List<String> comando = List.of(server.toString(), "--no-defaults",
                "--basedir=" + server.getParent().getParent(), "--datadir=" + dati);

        System.out.println("[MySQL test] Inizializzazione istanza temporanea: " + lavoro);
        Process inizializzazione = processo(comando, log, "--initialize-insecure", "--console").start();
        if (!inizializzazione.waitFor(90, TimeUnit.SECONDS)) {
            inizializzazione.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
            throw new IOException("Timeout inizializzazione MySQL. Consulta " + log);
        }
        if (inizializzazione.exitValue() != 0) {
            throw new IOException("Inizializzazione MySQL fallita. Consulta " + log);
        }

        Process processo = processo(comando, log, "--port=" + porta,
                "--bind-address=127.0.0.1", "--mysqlx=OFF", "--skip-log-bin", "--console").start();
        Istanza istanza = new Istanza(processo, porta);
        // Anche un'interruzione del runner deve fermare soltanto il processo creato qui.
        Runtime.getRuntime().addShutdownHook(istanza.arresto);
        try {
            long scadenza = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            SQLException ultimoErrore = null;
            while (processo.isAlive() && System.nanoTime() < scadenza) {
                try (Connection connessione = connessioneAmministratore(porta)) {
                    preparaSchema(connessione);
                    System.out.println("[MySQL test] Pronto su localhost:" + porta);
                    return istanza;
                } catch (SQLException errore) {
                    // Un errore SQL nello schema deve fallire subito, non essere ritentato.
                    if (errore.getSQLState() == null || !errore.getSQLState().startsWith("08")) throw errore;
                    ultimoErrore = errore;
                }
                Thread.sleep(200);
            }
            throw new IOException("MySQL di test non è pronto. Consulta " + log, ultimoErrore);
        } catch (Exception errore) {
            istanza.close();
            throw errore;
        }
    }

    static int leggiPorta(String valore) {
        if (valore == null) return 33317;
        try {
            int porta = Integer.parseInt(valore);
            if (porta >= 1 && porta <= 65535) return porta;
        } catch (NumberFormatException ignored) {
            // Messaggio unico anche quando manca la variabile del profilo.
        }
        throw new IllegalArgumentException("disponibilita.test.port deve essere una porta valida (1-65535).");
    }

    /**
     * Credenziali e catalogo esclusivi dei test: non legge MYSQL_* o il .env.
     * I repository ricevono questo metodo nel costruttore, senza alterare Database.
     */
    public static Connection apriConnessione() throws SQLException {
        int porta = leggiPorta(System.getProperty("disponibilita.test.port"));
        Connection connessione = DriverManager.getConnection("jdbc:mysql://127.0.0.1:" + porta
                + "/turnogiusto_us03_test?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=1000",
                "turnogiusto_test", "turnogiusto_test");
        try {
            DatabaseDisponibilitaSupport.verificaCatalogo(connessione.getCatalog());
            return connessione;
        } catch (SQLException errore) {
            try { connessione.close(); }
            catch (SQLException chiusura) { errore.addSuppressed(chiusura); }
            throw errore;
        }
    }

    private static ProcessBuilder processo(List<String> comando, Path log, String... opzioni) {
        List<String> argomenti = new ArrayList<>(comando);
        argomenti.addAll(List.of(opzioni));
        return new ProcessBuilder(argomenti).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
    }

    private static Path trovaServer() throws IOException {
        String configurato = System.getProperty("disponibilita.test.mysqld");
        if (configurato != null && !configurato.isBlank()) {
            Path server = Path.of(configurato).toAbsolutePath();
            if (Files.isRegularFile(server)) return server;
            throw new IOException("mysqld non trovato: " + server);
        }
        String eseguibile = System.getProperty("os.name").startsWith("Windows") ? "mysqld.exe" : "mysqld";
        String percorso = System.getenv("PATH");
        if (percorso != null) {
            for (String directory : percorso.split(java.io.File.pathSeparator)) {
                if (directory.isBlank()) continue;
                Path server = Path.of(directory).resolve(eseguibile).toAbsolutePath();
                if (Files.isRegularFile(server)) return server;
            }
        }
        // Installazione standard Windows, senza fissare una versione o il nome utente.
        String programmi = System.getenv("ProgramFiles");
        if (programmi != null) {
            Path mysql = Path.of(programmi, "MySQL");
            if (Files.isDirectory(mysql)) {
                try (var percorsi = Files.find(mysql, 3,
                        (file, attributi) -> attributi.isRegularFile()
                                && file.getFileName().toString().equals(eseguibile))) {
                    Path server = percorsi.sorted().findFirst().orElse(null);
                    if (server != null) return server;
                }
            }
        }
        throw new IOException("MySQL Server locale non trovato. " + istruzioniAvvio()
                + " In alternativa indica -Ddisponibilita.test.mysqld=<percorso di mysqld>.");
    }

    private static String istruzioniAvvio() {
        return "Avvia il database dedicato con: docker compose -p turnogiusto-disponibilita-test "
                + "-f docker-compose.disponibilita-test.yml up -d --wait";
    }

    private static Connection connessioneAmministratore(int porta) throws SQLException {
        // Solo l'istanza appena inizializzata: root locale senza password, mai il servizio di sviluppo.
        return DriverManager.getConnection("jdbc:mysql://127.0.0.1:" + porta
                + "/?sslMode=DISABLED&allowPublicKeyRetrieval=true&connectTimeout=1000&socketTimeout=3000",
                "root", "");
    }

    private static void preparaSchema(Connection connessione) throws IOException, SQLException {
        try (Statement statement = connessione.createStatement()) {
            statement.executeUpdate("CREATE DATABASE turnogiusto_us03_test CHARACTER SET utf8mb4");
            statement.executeUpdate("CREATE USER 'turnogiusto_test'@'localhost' IDENTIFIED BY 'turnogiusto_test'");
            // Lo schema e creato a runtime qui sopra, quindi non e nel catalogo statico dell'IDE.
            //noinspection SqlResolve
            statement.executeUpdate("GRANT ALL ON turnogiusto_us03_test.* TO 'turnogiusto_test'@'localhost'");
        }
        connessione.setCatalog("turnogiusto_us03_test");
        // Uno Statement JDBC conserva il catalogo selezionato al momento della creazione.
        try (Statement statement = connessione.createStatement()) {
            // Lo schema condiviso contiene DDL semplici, separati da ; e commenti di riga.
            // Non duplicare qui tabelle o vincoli: devono restare quelli del progetto.
            String schema;
            try (InputStream sorgente = MySqlDisponibilitaExtension.class.getResourceAsStream("/db/01_schema.sql")) {
                if (sorgente == null) throw new IOException("Schema di test assente: ricarica il progetto Maven.");
                schema = String.join("\n", new String(sorgente.readAllBytes(), StandardCharsets.UTF_8)
                        .lines().filter(riga -> !riga.stripLeading().startsWith("--")).toList());
            }
            for (String sql : schema.split(";")) {
                if (!sql.isBlank()) statement.execute(sql);
            }
        }
    }

    private static final class Istanza implements ExtensionContext.Store.CloseableResource {
        private final Process processo;
        private final int porta;
        private final Thread arresto = new Thread(this::ferma, "arresto-mysql-test");

        private Istanza(Process processo, int porta) {
            this.processo = processo;
            this.porta = porta;
        }

        @Override
        public void close() {
            if (processo == null) return; // Il server già attivo appartiene al chiamante.
            ferma();
            Runtime.getRuntime().removeShutdownHook(arresto);
        }

        private synchronized void ferma() {
            if (processo == null || !processo.isAlive()) return;
            try (Connection connessione = connessioneAmministratore(porta);
                 Statement statement = connessione.createStatement()) {
                statement.execute("SHUTDOWN");
            } catch (SQLException ignored) {
                // Durante lo shutdown il server può chiudere la connessione prima della risposta.
            }
            try {
                if (!processo.waitFor(10, TimeUnit.SECONDS)) {
                    processo.destroyForcibly().waitFor(10, TimeUnit.SECONDS);
                }
            } catch (InterruptedException e) {
                processo.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }
}
