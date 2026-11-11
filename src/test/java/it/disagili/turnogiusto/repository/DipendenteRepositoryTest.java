package it.disagili.turnogiusto.repository;

import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.TipoContratto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifica trovaTutti() usando il vero MySQL di sviluppo: sono test di integrazione,
 * perché controllano insieme query SQL, connessione JDBC e oggetti Java restituiti.
 * Ogni test prepara dati propri, esegue il metodo e confronta il risultato con
 * quello atteso tramite le asserzioni JUnit (assertEquals, assertNull, ecc.).
 *
 * I dati inseriti sono confermati subito, così la connessione separata aperta dal
 * repository può leggerli. Dopo ogni test vengono eliminati usando gli ID registrati.
 * Non vengono cancellate le righe preesistenti; il contatore AUTO_INCREMENT avanza.
 * La pulizia viene eseguita anche se un'asserzione fallisce, ma richiede che il processo
 * continui a funzionare e che la connessione al database sia ancora utilizzabile.
 *
 * I test non dipendono dai nomi o dal numero di dipendenti dei dati iniziali.
 * trovaTutti() legge comunque l'intera tabella: eventuali contratti sovrapposti già
 * presenti nel database possono far fallire anche queste verifiche.
 * Le date sono relative a oggi; evitare di eseguire questi test a cavallo di mezzanotte,
 * perché test e repository calcolano la data odierna in due momenti distinti.
 */
class DipendenteRepositoryTest {

    // Connessione usata solo per preparazione e pulizia; il repository ne apre una propria.
    private Connection connessione;
    // Gli ID consentono di cancellare esattamente i dipendenti inseriti dal singolo test.
    // Con il ciclo di vita JUnit predefinito, ogni test usa una nuova istanza della classe:
    // questa lista riparte quindi vuota per ogni metodo annotato con @Test.
    private final List<Integer> idCreati = new ArrayList<>();
    private final DipendenteRepository repository = new DipendenteRepository();
    private LocalDate oggi;

    /** JUnit esegue questo metodo prima di ciascun test per preparare l'ambiente. */
    @BeforeEach
    void preparaConnessione() throws SQLException {
        try {
            connessione = Database.getConnection();
        } catch (SQLException e) {
            // Un'assunzione falsa interrompe il test marcandolo come saltato (skipped).
            // Si segue la convenzione di DatabaseTest: qualunque SQLException in apertura
            // porta al salto. Un test saltato NON dimostra che il repository funzioni.
            assumeTrue(false, "Database non raggiungibile: avvia Docker e 'docker compose up -d'. "
                    + e.getMessage());
        }
        // Ogni INSERT/DELETE viene confermata subito. Un semplice rollback finale
        // non potrebbe annullarla: per questo @AfterEach esegue DELETE mirate.
        connessione.setAutoCommit(true);
        oggi = LocalDate.now();
    }

    /** JUnit richiama la pulizia anche se il test termina con un'asserzione fallita. */
    @AfterEach
    void eliminaSoloIDatiCreatiDalTest() throws SQLException {
        // Se l'apertura è fallita, non sono stati inseriti dati e non c'è nulla da chiudere.
        if (connessione == null) {
            return;
        }
        // ON DELETE CASCADE elimina anche i contratti dei soli dipendenti creati qui.
        // daChiudere è la stessa connessione, non una nuova: il try la chiuderà
        // automaticamente, anche se una delle operazioni di pulizia genera un errore.
        try (Connection daChiudere = connessione;
             PreparedStatement statement = daChiudere.prepareStatement(
                     "DELETE FROM dipendente WHERE id = ?")) {
            for (int id : idCreati) {
                statement.setInt(1, id);
                // Accodiamo una cancellazione per ciascun ID creato dal test.
                statement.addBatch();
            }
            // Eseguiamo le cancellazioni accodate. Non usiamo DELETE senza WHERE.
            statement.executeBatch();
        }
    }

    /** Controlla la selezione temporale e la conversione di tutti i campi del modello. */
    @Test
    void leggeAnagraficaEContrattoAttualeEscludendoStoricoEFuturo() throws SQLException {
        // PREPARAZIONE: stesso dipendente, tre contratti con periodi diversi.
        // Quello iniziato oggi è valido: questo verifica anche il limite iniziale incluso.
        int id = creaDipendente("Anna", "TestContratto");
        creaContratto(id, oggi.minusDays(20), oggi.minusDays(1));
        int contrattoId = creaContratto(id, oggi, null);
        creaContratto(id, oggi.plusDays(10), null);

        // ESECUZIONE: chiamiamo il vero repository e individuiamo il nostro dipendente.
        Dipendente dipendente = trovaCreato(repository.trovaTutti(), id);

        // VERIFICA: assertEquals riceve prima il valore atteso, poi quello ottenuto.
        // Le asserzioni confrontano i campi letti con i dati inseriti in preparazione.
        assertEquals("Anna", dipendente.getNome());
        assertEquals("TestContratto", dipendente.getCognome());
        assertEquals(Ruolo.CAMERIERE, dipendente.getRuolo());
        Contratto contratto = dipendente.getContratto();
        // Prima di leggere i campi, verifichiamo che l'oggetto contratto esista.
        assertNotNull(contratto);
        assertEquals(contrattoId, contratto.getId());
        assertEquals(id, contratto.getDipendenteId());
        assertEquals(TipoContratto.PART_TIME, contratto.getTipoContratto());
        assertEquals(20, contratto.getOreSettimanali());
        // La stringa costruisce un valore decimale esatto, con due cifre decimali.
        assertEquals(new BigDecimal("12.50"), contratto.getCostoOrario());
        assertEquals(oggi, contratto.getDataInizio());
        assertNull(contratto.getDataFine());
    }

    /** Un contratto che termina oggi deve ancora essere associato al dipendente. */
    @Test
    void includeAncheIlGiornoDiFineDelContratto() throws SQLException {
        int id = creaDipendente("Anna", "TestScadenza");
        creaContratto(id, oggi.minusDays(10), oggi);

        Contratto contratto = trovaCreato(repository.trovaTutti(), id).getContratto();

        assertNotNull(contratto);
        assertEquals(oggi, contratto.getDataFine());
    }

    /** Controlla la LEFT JOIN e tutti e tre i criteri dell'ORDER BY. */
    @Test
    void mantieneDipendentiSenzaContrattoValidoEOrdinaPerCognomeNomeEId() throws SQLException {
        // Un prefisso casuale riduce le collisioni con i cognomi dei dati esistenti.
        // Inseriamo volutamente in ordine diverso da quello alfabetico atteso.
        String prefisso = UUID.randomUUID().toString();
        int ultimo = creaDipendente("Anna", prefisso + "Z");
        int secondo = creaDipendente("Bruno", prefisso + "A");
        int primo = creaDipendente("Anna", prefisso + "A");
        int terzo = creaDipendente("Bruno", prefisso + "A");
        // primo e terzo non hanno contratti; secondo ne ha uno scaduto;
        // ultimo ne ha uno futuro. Tutti devono comparire con contratto null.
        creaContratto(secondo, oggi.minusDays(10), oggi.minusDays(1));
        creaContratto(ultimo, oggi.plusDays(1), null);

        // stream() permette di elaborare la lista; filter conserva solo i nostri ID
        // senza riordinarli; toList raccoglie gli elementi rimasti in una nuova lista.
        List<Dipendente> creati = repository.trovaTutti().stream()
                .filter(d -> idCreati.contains(d.getId()))
                .toList();

        // Cognome A prima di Z; a parità di cognome, Anna prima di Bruno.
        // secondo e terzo sono omonimi: il loro ordine dipende dall'ID crescente.
        // map(Dipendente::getId) estrae gli ID per confrontare l'ordine delle due liste.
        assertEquals(List.of(primo, secondo, terzo, ultimo),
                creati.stream().map(Dipendente::getId).toList());
        for (Dipendente dipendente : creati) {
            assertNull(dipendente.getContratto());
        }
    }

    /** Il repository deve rifiutare due contratti validi oggi per lo stesso dipendente. */
    @Test
    void segnalaContrattiContemporaneamenteValidi() throws SQLException {
        int id = creaDipendente("Anna", "TestSovrapposizione");
        creaContratto(id, oggi.minusDays(10), null);
        creaContratto(id, oggi.minusDays(5), null);

        // assertThrows chiama il metodo e verifica che lanci SQLException.
        // repository::trovaTutti è un riferimento al metodo da eseguire.
        // Se il metodo termina normalmente, l'asserzione fallisce.
        SQLException errore = assertThrows(SQLException.class, repository::trovaTutti);

        // Verifichiamo anche che l'errore riguardi proprio il dipendente creato qui.
        assertEquals("Più contratti validi per il dipendente " + id, errore.getMessage());
    }

    /** Cerca il nostro ID e verifica che il repository lo abbia restituito una sola volta. */
    private Dipendente trovaCreato(List<Dipendente> dipendenti, int id) {
        List<Dipendente> trovati = dipendenti.stream().filter(d -> d.getId() == id).toList();
        assertEquals(1, trovati.size(), "Il dipendente deve apparire una sola volta");
        // L'asserzione precedente garantisce che esista esattamente un elemento.
        return trovati.getFirst();
    }

    /** Inserisce direttamente un dipendente di prova e registra l'ID per la pulizia. */
    private int creaDipendente(String nome, String cognome) throws SQLException {
        // Il SQL di preparazione è indipendente dal repository sotto test.
        // RETURN_GENERATED_KEYS richiede a JDBC l'ID AUTO_INCREMENT scelto da MySQL:
        // non assumiamo che gli ID siano fissi o che il database sia vuoto.
        try (PreparedStatement statement = connessione.prepareStatement(
                "INSERT INTO dipendente (nome, cognome, ruolo) VALUES (?, ?, 'CAMERIERE')",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, nome);
            statement.setString(2, cognome);
            // executeUpdate esegue un comando di modifica dei dati, in questo caso INSERT.
            statement.executeUpdate();
            try (ResultSet chiavi = statement.getGeneratedKeys()) {
                // Anche le chiavi generate sono un ResultSet: next() raggiunge la prima riga.
                assertTrue(chiavi.next());
                int id = chiavi.getInt(1);
                // Registriamo subito l'ID, così @AfterEach potrà rimuovere questo dipendente.
                idCreati.add(id);
                return id;
            }
        }
    }

    /**
     * Inserisce un contratto di prova PART_TIME da 20 ore e 12,50 euro/ora.
     * Le date sono parametri per costruire contratti scaduti, attuali o futuri.
     * Restituisce l'ID generato, utile per verificare quale contratto viene letto.
     */
    private int creaContratto(int dipendenteId, LocalDate inizio, LocalDate fine)
            throws SQLException {
        String sql = """
                INSERT INTO contratto
                    (dipendente_id, tipo, ore_settimanali, costo_orario, data_inizio, data_fine)
                VALUES (?, 'PART_TIME', 20, 12.50, ?, ?)
                """;
        try (PreparedStatement statement = connessione.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, dipendenteId);
            statement.setDate(2, Date.valueOf(inizio));
            // Un valore Java null viene scritto come NULL SQL, specificando il tipo DATE.
            // Il database interpreterà questa assenza come contratto senza scadenza.
            if (fine == null) {
                statement.setNull(3, Types.DATE);
            } else {
                statement.setDate(3, Date.valueOf(fine));
            }
            statement.executeUpdate();
            try (ResultSet chiavi = statement.getGeneratedKeys()) {
                assertTrue(chiavi.next());
                return chiavi.getInt(1);
            }
        }
    }
}
