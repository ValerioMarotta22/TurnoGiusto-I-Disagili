package it.disagili.turnogiusto.repository;

// --- IMPORT -------------------------------------------------------------
import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.PianoSettimanale;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.Turno;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Salva e rilegge il piano settimanale (US-06B). Qui c'e' SOLO SQL e conversione
 * righe <-> oggetti: le regole (chi puo' sostituire chi) stanno nel service.
 *
 * Il piano si salva INTERO: salvare una settimana gia' presente significa sostituire
 * tutto il piano di quella settimana, in una sola transazione. E' la scelta piu'
 * semplice perche' il modello (Turno, AssegnazioneTurno) non ha identificatori propri.
 */
public class PianoRepository {

    // Quanti giorni prima del lunedi' e dopo la domenica contano come "confinanti"
    // (servono per i controlli sulle ore di riposo tra un turno e l'altro).
    private static final int GIORNI_CONFINANTI = 1;

    // Il "fornitore" apre una connessione nuova ogni volta. Nel programma e' Database::getConnection,
    // nei test si puo' passare un altro database. Chi riceve la connessione deve chiuderla.
    private final FornitoreConnessioni connessioni;

    public PianoRepository() {
        this(Database::getConnection);
    }

    public PianoRepository(FornitoreConnessioni connessioni) {
        this.connessioni = Objects.requireNonNull(connessioni);
    }

    // ======================================================================
    // SALVARE UN PIANO (inserisce, oppure sostituisce quello della stessa settimana)
    // ======================================================================
    // "throws SQLException": se qualcosa va storto l'errore sale al chiamante (service/controller),
    // che decide cosa mostrare all'utente.
    // Salva il piano tenendo i festivi gia' salvati per quella settimana (se ce n'erano).
    public void salva(PianoSettimanale piano) throws SQLException {
        Objects.requireNonNull(piano, "Il piano è obbligatorio");
        salva(piano, trovaFestivi(piano.inizioSettimana()));
    }

    // Salva il piano INSIEME ai giorni segnati come festivi dal titolare (anche nessuno).
    // I festivi devono essere giorni di quella settimana.
    public void salva(PianoSettimanale piano, Set<LocalDate> festivi) throws SQLException {
        Objects.requireNonNull(piano, "Il piano è obbligatorio");
        Objects.requireNonNull(festivi, "I festivi sono obbligatori (anche vuoti)");
        LocalDate lunedi = piano.inizioSettimana();
        for (LocalDate festivo : festivi) {
            if (festivo.isBefore(lunedi) || !festivo.isBefore(lunedi.plusWeeks(1))) {
                throw new IllegalArgumentException("Il festivo " + festivo + " non appartiene alla settimana del piano");
            }
        }

        try (Connection connessione = connessioni.apri()) {
            // PASSO 1 - TRANSAZIONE. Con autoCommit attivo ogni INSERT sarebbe salvato subito:
            // se l'applicazione si fermasse a meta' resterebbe un piano a pezzi. Lo spegniamo
            // e chiudiamo noi con commit() (tutto) oppure rollback() (niente).
            boolean autoCommitPrecedente = connessione.getAutoCommit();
            connessione.setAutoCommit(false);
            try {
                // PASSO 2 - tolgo il piano della stessa settimana, se c'e'. Le chiavi esterne con
                // ON DELETE CASCADE cancellano da sole i suoi turni e le sue assegnazioni.
                try (PreparedStatement elimina = connessione.prepareStatement(
                        "DELETE FROM piano WHERE inizio_settimana = ?")) {
                    elimina.setObject(1, piano.inizioSettimana());
                    elimina.executeUpdate();
                }

                // PASSO 3 - inserisco il piano e leggo l'ID che MySQL gli ha assegnato.
                int pianoId = inserisciPiano(connessione, piano.inizioSettimana());

                // PASSO 4 - per ogni riga di turno: inserisco il turno, poi le sue assegnazioni.
                for (Turno turno : piano.turni()) {
                    int turnoId = inserisciTurno(connessione, pianoId, turno);
                    inserisciAssegnazioni(connessione, turnoId, turno.assegnazioni());
                }

                // PASSO 4b - i giorni festivi della settimana (quelli vecchi sono spariti con il DELETE del piano).
                inserisciFestivi(connessione, pianoId, festivi);

                // PASSO 5 - tutto e' andato bene: rendo definitivo.
                connessione.commit();
            } catch (SQLException | RuntimeException errore) {
                // Qualcosa e' fallito (per esempio un dipendente che non esiste): annullo tutto.
                connessione.rollback();
                throw errore;
            } finally {
                connessione.setAutoCommit(autoCommitPrecedente);
            }
        }
    }

    private int inserisciPiano(Connection connessione, LocalDate inizioSettimana) throws SQLException {
        // RETURN_GENERATED_KEYS = "dimmi l'ID che hai generato per la riga appena inserita".
        try (PreparedStatement inserisci = connessione.prepareStatement(
                "INSERT INTO piano (inizio_settimana) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            inserisci.setObject(1, inizioSettimana);
            inserisci.executeUpdate();
            return idGenerato(inserisci);
        }
    }

    private void inserisciFestivi(Connection connessione, int pianoId, Set<LocalDate> festivi) throws SQLException {
        try (PreparedStatement inserisci = connessione.prepareStatement(
                "INSERT INTO piano_festivo (piano_id, giorno) VALUES (?, ?)")) {
            for (LocalDate festivo : festivi) {
                inserisci.setInt(1, pianoId);
                inserisci.setObject(2, festivo);
                inserisci.addBatch();
            }
            inserisci.executeBatch();
        }
    }

    // ======================================================================
    // RILEGGERE I FESTIVI DI UNA SETTIMANA (vuoto se il piano non c'e' o non ha festivi)
    // ======================================================================
    public Set<LocalDate> trovaFestivi(LocalDate lunedi) throws SQLException {
        Objects.requireNonNull(lunedi, "Il lunedì è obbligatorio");
        Set<LocalDate> festivi = new TreeSet<>(); // TreeSet: in ordine di data
        try (Connection connessione = connessioni.apri();
             PreparedStatement leggi = connessione.prepareStatement("""
                     SELECT f.giorno FROM piano_festivo f
                     JOIN piano p ON p.id = f.piano_id
                     WHERE p.inizio_settimana = ?
                     ORDER BY f.giorno
                     """)) {
            leggi.setObject(1, lunedi);
            try (ResultSet rs = leggi.executeQuery()) {
                while (rs.next()) {
                    festivi.add(rs.getObject("giorno", LocalDate.class));
                }
            }
        }
        return festivi;
    }

    private int inserisciTurno(Connection connessione, int pianoId, Turno turno) throws SQLException {
        try (PreparedStatement inserisci = connessione.prepareStatement("""
                INSERT INTO turno (piano_id, servizio, ruolo, inizio, fine, posti_richiesti)
                VALUES (?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS)) {
            inserisci.setInt(1, pianoId);
            // Le enum si salvano con name() (es. 'CENA'), NON con toString() (che e' l'etichetta "Cena").
            inserisci.setString(2, turno.servizio().name());
            inserisci.setString(3, turno.ruolo().name());
            // setObject con LocalDateTime passa data e ora cosi' come sono, senza conversioni di fuso orario.
            inserisci.setObject(4, turno.inizio());
            inserisci.setObject(5, turno.fine());
            inserisci.setInt(6, turno.postiRichiesti());
            inserisci.executeUpdate();
            return idGenerato(inserisci);
        }
    }

    private void inserisciAssegnazioni(Connection connessione, int turnoId, List<AssegnazioneTurno> assegnazioni)
            throws SQLException {
        // L'orario non si salva: ogni assegnazione ha lo stesso intervallo della sua riga di turno.
        try (PreparedStatement inserisci = connessione.prepareStatement(
                "INSERT INTO assegnazione_turno (turno_id, dipendente_id) VALUES (?, ?)")) {
            for (AssegnazioneTurno assegnazione : assegnazioni) {
                inserisci.setInt(1, turnoId);
                inserisci.setInt(2, assegnazione.dipendenteId());
                inserisci.addBatch(); // le accumulo e le mando insieme
            }
            inserisci.executeBatch();
        }
    }

    private static int idGenerato(PreparedStatement statement) throws SQLException {
        try (ResultSet chiavi = statement.getGeneratedKeys()) {
            if (!chiavi.next()) {
                throw new SQLException("MySQL non ha restituito l'ID della riga inserita");
            }
            return chiavi.getInt(1);
        }
    }

    // ======================================================================
    // RILEGGERE IL PIANO DI UNA SETTIMANA
    // ======================================================================
    // Optional = "forse c'e', forse no". Se per quel lunedi' non e' mai stato salvato
    // un piano si restituisce Optional.empty(), senza inventare un piano vuoto.
    public Optional<PianoSettimanale> trova(LocalDate lunedi) throws SQLException {
        Objects.requireNonNull(lunedi, "Il lunedì è obbligatorio");

        try (Connection connessione = connessioni.apri()) {
            // PASSO 1 - esiste un piano per quella settimana?
            Integer pianoId = null;
            try (PreparedStatement cerca = connessione.prepareStatement(
                    "SELECT id FROM piano WHERE inizio_settimana = ?")) {
                cerca.setObject(1, lunedi);
                try (ResultSet rs = cerca.executeQuery()) {
                    if (rs.next()) {
                        pianoId = rs.getInt(1);
                    }
                }
            }
            if (pianoId == null) {
                return Optional.empty();
            }

            // PASSO 2 - leggo le righe di turno con le loro assegnazioni in una sola query.
            // LEFT JOIN: una riga senza nessun assegnato (tutta scoperta) deve comparire lo stesso,
            // con dipendente_id = NULL. L'ordine e' sempre lo stesso: orario, servizio, ruolo.
            String sql = """
                    SELECT t.id, t.servizio, t.ruolo, t.inizio, t.fine, t.posti_richiesti, a.dipendente_id
                    FROM turno t
                    LEFT JOIN assegnazione_turno a ON a.turno_id = t.id
                    WHERE t.piano_id = ?
                    ORDER BY t.inizio, t.servizio, t.ruolo, t.id, a.dipendente_id
                    """;

            // PASSO 3 - ogni riga SQL e' una coppia (turno, assegnazione): raggruppo per ID del turno.
            // LinkedHashMap mantiene l'ordine di arrivo.
            Map<Integer, RigaTurno> righe = new LinkedHashMap<>();
            try (PreparedStatement leggi = connessione.prepareStatement(sql)) {
                leggi.setInt(1, pianoId);
                try (ResultSet rs = leggi.executeQuery()) {
                    while (rs.next()) {
                        // Se questa riga di turno non l'ho ancora vista, la creo; altrimenti riuso quella
                        // gia' creata (e' il caso del turno con piu' assegnati: una riga SQL per assegnato).
                        int turnoId = rs.getInt("id");
                        RigaTurno riga = righe.get(turnoId);
                        if (riga == null) {
                            riga = new RigaTurno(
                                    Servizio.valueOf(rs.getString("servizio")),
                                    Ruolo.valueOf(rs.getString("ruolo")),
                                    rs.getObject("inizio", LocalDateTime.class),
                                    rs.getObject("fine", LocalDateTime.class),
                                    rs.getInt("posti_richiesti"));
                            righe.put(turnoId, riga);
                        }
                        int dipendenteId = rs.getInt("dipendente_id");
                        if (!rs.wasNull()) { // wasNull: l'ultimo getInt ha letto un NULL (turno senza assegnati)?
                            riga.dipendenti.add(dipendenteId);
                        }
                    }
                }
            }

            // PASSO 4 - trasformo ogni gruppo in un Turno del modello, poi tutto nel PianoSettimanale.
            List<Turno> turni = new ArrayList<>();
            for (RigaTurno riga : righe.values()) {
                List<AssegnazioneTurno> assegnazioni = new ArrayList<>();
                for (int dipendenteId : riga.dipendenti) {
                    assegnazioni.add(new AssegnazioneTurno(dipendenteId, riga.inizio, riga.fine));
                }
                turni.add(new Turno(riga.servizio, riga.ruolo, riga.inizio, riga.fine,
                        riga.postiRichiesti, assegnazioni));
            }
            return Optional.of(new PianoSettimanale(lunedi, turni));
        }
    }

    // Contenitore temporaneo, usato solo mentre si legge: riga di turno + ID di chi e' assegnato.
    private static final class RigaTurno {
        final Servizio servizio;
        final Ruolo ruolo;
        final LocalDateTime inizio;
        final LocalDateTime fine;
        final int postiRichiesti;
        final List<Integer> dipendenti = new ArrayList<>();

        RigaTurno(Servizio servizio, Ruolo ruolo, LocalDateTime inizio, LocalDateTime fine, int postiRichiesti) {
            this.servizio = servizio;
            this.ruolo = ruolo;
            this.inizio = inizio;
            this.fine = fine;
            this.postiRichiesti = postiRichiesti;
        }
    }

    // ======================================================================
    // ASSEGNAZIONI "CONFINANTI"
    // ======================================================================
    // Sono le assegnazioni delle settimane vicine che iniziano nel giorno prima del lunedi' o
    // nel giorno dopo la domenica: servono ai controlli (per esempio le 11 ore di riposo tra la
    // cena della domenica sera e il pranzo del lunedi'). Quelle che iniziano DENTRO la settimana
    // non sono confinanti: fanno parte del piano stesso.
    public List<AssegnazioneTurno> trovaConfinanti(LocalDate lunedi) throws SQLException {
        Objects.requireNonNull(lunedi, "Il lunedì è obbligatorio");

        // Due finestre: [lunedi - 1 giorno, lunedi) e [lunedi + 7 giorni, lunedi + 8 giorni).
        LocalDateTime inizioPrima = lunedi.minusDays(GIORNI_CONFINANTI).atStartOfDay();
        LocalDateTime finePrima = lunedi.atStartOfDay();
        LocalDateTime inizioDopo = lunedi.plusWeeks(1).atStartOfDay();
        LocalDateTime fineDopo = lunedi.plusWeeks(1).plusDays(GIORNI_CONFINANTI).atStartOfDay();

        // "<> ?" esclude il piano della stessa settimana, per non contare due volte le sue assegnazioni.
        String sql = """
                SELECT a.dipendente_id, t.inizio, t.fine
                FROM assegnazione_turno a
                JOIN turno t ON t.id = a.turno_id
                JOIN piano p ON p.id = t.piano_id
                WHERE p.inizio_settimana <> ?
                  AND ((t.inizio >= ? AND t.inizio < ?) OR (t.inizio >= ? AND t.inizio < ?))
                ORDER BY t.inizio, a.dipendente_id
                """;

        List<AssegnazioneTurno> risultato = new ArrayList<>();
        try (Connection connessione = connessioni.apri();
             PreparedStatement leggi = connessione.prepareStatement(sql)) {
            leggi.setObject(1, lunedi);
            leggi.setObject(2, inizioPrima);
            leggi.setObject(3, finePrima);
            leggi.setObject(4, inizioDopo);
            leggi.setObject(5, fineDopo);
            try (ResultSet rs = leggi.executeQuery()) {
                while (rs.next()) {
                    risultato.add(new AssegnazioneTurno(
                            rs.getInt("dipendente_id"),
                            rs.getObject("inizio", LocalDateTime.class),
                            rs.getObject("fine", LocalDateTime.class)));
                }
            }
        }
        return risultato;
    }
}