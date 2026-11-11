package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.TipoContratto;
import it.disagili.turnogiusto.repository.Database;
import it.disagili.turnogiusto.repository.DipendenteRepository;
import it.disagili.turnogiusto.repository.ContrattoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * US01: verifica la registrazione completa attraverso il service.
 * I primi test controllano la validazione senza richiedere MySQL.
 * Il gruppo ConDatabase verifica invece commit, collegamenti e rollback sul vero DB.
 */
class DipendenteServiceTest {

    private final DipendenteService service = new DipendenteService();
    // Identifica soltanto i dati di questa prova, anche se il service non assegna gli ID.
    // 5 + 36 + 9 = 50 caratteri: ogni salvataggio verifica anche questo limite ammesso.
    private final String cognomeTest = "US01-" + UUID.randomUUID() + "X".repeat(9);

    private Dipendente nuovoDipendente() {
        // Oggetti nuovi con ID zero e dati validi: ciascun test cambia solo ciò
        // che serve al proprio scenario. Non viene ancora eseguito alcun INSERT.
        Contratto contratto = new Contratto(0, TipoContratto.PART_TIME, 20,
                new BigDecimal("12.50"), LocalDate.now());
        return new Dipendente("Test", cognomeTest, Ruolo.CAMERIERE, contratto);
    }

    @Test
    void rifiutaDipendenteNull() {
        assertThrows(IllegalArgumentException.class, () -> service.registra(null));
    }

    @Test
    void comunicaInsiemeGliErroriDiAnagraficaEContratto() {
        Dipendente dipendente = nuovoDipendente();
        dipendente.setNome(" ");
        dipendente.getContratto().setOreSettimanali(0);

        IllegalArgumentException errore = assertThrows(IllegalArgumentException.class,
                () -> service.registra(dipendente));

        assertTrue(errore.getMessage().contains("Il nome è obbligatorio"));
        assertTrue(errore.getMessage().contains("Le ore settimanali devono essere maggiori di zero"));
        verificaIdNonAssegnati(dipendente);
    }

    @Test
    void rifiutaIlContrattoMancante() {
        Dipendente dipendente = nuovoDipendente();
        dipendente.setContratto(null);

        IllegalArgumentException errore = assertThrows(IllegalArgumentException.class,
                () -> service.registra(dipendente));

        assertTrue(errore.getMessage().contains("I dati di contratto sono obbligatori"));
        assertEquals(0, dipendente.getId());
    }

    @Test
    void rifiutaOggettiConIdGiaAssegnati() {
        // Proviamo separatamente ciascuno dei tre controlli sugli ID.
        Dipendente dipendente = nuovoDipendente();
        dipendente.setId(42);
        assertThrows(IllegalArgumentException.class, () -> service.registra(dipendente));
        assertEquals(42, dipendente.getId());

        Dipendente conContrattoSalvato = nuovoDipendente();
        conContrattoSalvato.getContratto().setId(43);
        assertThrows(IllegalArgumentException.class, () -> service.registra(conContrattoSalvato));
        assertEquals(43, conContrattoSalvato.getContratto().getId());

        Dipendente conCollegamentoEsistente = nuovoDipendente();
        conCollegamentoEsistente.getContratto().setDipendenteId(44);
        assertThrows(IllegalArgumentException.class,
                () -> service.registra(conCollegamentoEsistente));
        assertEquals(44, conCollegamentoEsistente.getContratto().getDipendenteId());
    }

    @Test
    void rifiutaNomeECognomeDi51Caratteri() {
        // Verifica il primo valore oltre VARCHAR(50) e la raccolta di entrambi gli errori.
        Dipendente dipendente = nuovoDipendente();
        dipendente.setNome("A".repeat(51));
        dipendente.setCognome("B".repeat(51));

        IllegalArgumentException errore = assertThrows(IllegalArgumentException.class,
                () -> service.registra(dipendente));

        assertTrue(errore.getMessage().contains("Il nome non può superare 50 caratteri"));
        assertTrue(errore.getMessage().contains("Il cognome non può superare 50 caratteri"));
        verificaIdNonAssegnati(dipendente);
    }

    @Test
    void rifiutaCostoSuperioreAlMassimo() {
        // Il limite è controllato prima del database: attendiamo IllegalArgumentException,
        // non più l'errore SQL usato nella prima versione del test di rollback.
        Dipendente dipendente = nuovoDipendente();
        dipendente.getContratto().setCostoOrario(new BigDecimal("10000.00"));

        IllegalArgumentException errore = assertThrows(IllegalArgumentException.class,
                () -> service.registra(dipendente));

        assertTrue(errore.getMessage().contains("non può superare 9999,99"));
        verificaIdNonAssegnati(dipendente);
    }

    @Test
    void rifiutaCifreDecimaliCheRichiederebberoArrotondamento() {
        // 0.001 è positivo, ma MySQL lo arrotonderebbe a zero: va rifiutato prima.
        for (String importo : new String[]{"12.501", "0.001"}) {
            Dipendente dipendente = nuovoDipendente();
            dipendente.getContratto().setCostoOrario(new BigDecimal(importo));

            IllegalArgumentException errore = assertThrows(IllegalArgumentException.class,
                    () -> service.registra(dipendente));

            assertTrue(errore.getMessage().contains("al massimo due cifre decimali"));
            verificaIdNonAssegnati(dipendente);
        }
    }

    @Test
    void gestisceNomeCognomeECostoNullSenzaNullPointerException() {
        // I controlli aggiunti dal service devono tollerare i null già segnalati dal modello.
        Dipendente dipendente = nuovoDipendente();
        dipendente.setNome(null);
        dipendente.setCognome(null);
        dipendente.getContratto().setCostoOrario(null);

        IllegalArgumentException errore = assertThrows(IllegalArgumentException.class,
                () -> service.registra(dipendente));

        assertTrue(errore.getMessage().contains("Il nome è obbligatorio"));
        assertTrue(errore.getMessage().contains("Il cognome è obbligatorio"));
        assertTrue(errore.getMessage().contains("Il costo orario deve essere maggiore di zero"));
    }

    private void verificaIdNonAssegnati(Dipendente dipendente) {
        // Un salvataggio rifiutato o annullato non deve far sembrare persistiti gli oggetti.
        assertEquals(0, dipendente.getId());
        assertEquals(0, dipendente.getContratto().getId());
        assertEquals(0, dipendente.getContratto().getDipendenteId());
    }

    /**
     * JUnit raggruppa qui i test che richiedono MySQL. La connessione di verifica
     * è diversa da quella del service: può vedere i dati solo dopo il commit.
     * Come negli altri test del progetto, se la connessione non si apre il test è saltato.
     */
    @Nested
    class ConDatabase {
        private Connection connessione;

        @BeforeEach
        void apriConnessione() throws SQLException {
            try {
                connessione = Database.getConnection();
            } catch (SQLException errore) {
                assumeTrue(false, "Database non raggiungibile: " + errore.getMessage());
            }
            connessione.setAutoCommit(true);
        }

        @AfterEach
        void eliminaDatiDellaProva() throws SQLException {
            if (connessione == null) {
                return;
            }
            // Usiamo il cognome casuale anche se un errore lascia l'ID Java a zero.
            // La cancellazione a cascata rimuove i relativi contratti.
            // La pulizia non modifica le righe preesistenti e non azzera AUTO_INCREMENT.
            try (Connection daChiudere = connessione;
                 PreparedStatement statement = daChiudere.prepareStatement(
                         "DELETE FROM dipendente WHERE cognome = ?")) {
                statement.setString(1, cognomeTest);
                statement.executeUpdate();
            }
        }

        @Test
        void confermaDipendenteEContrattoSenzaScadenza() throws SQLException {
            // Il controllo SQL successivo usa una connessione diversa: vedere le righe
            // dimostra che il service ha confermato il commit, non solo eseguito gli INSERT.
            Dipendente dipendente = nuovoDipendente();

            service.registra(dipendente);

            verificaSalvataggio(dipendente);
        }

        @Test
        void salvaAncheUnContrattoConScadenza() throws SQLException {
            Dipendente dipendente = nuovoDipendente();
            dipendente.getContratto().setDataFine(LocalDate.now().plusMonths(6));

            service.registra(dipendente);

            verificaSalvataggio(dipendente);
        }

        @Test
        void registraERileggeTuttiIRuoliConEntrambiITipiDiContratto() throws SQLException {
            // Attraversiamo l'intero percorso US01: service, scritture MySQL e trovaTutti.
            // Verifichiamo anche i nomi ENUM del DB, incluso AIUTO_CUOCO, e che
            // l'elenco condiviso con i compagni non escluda i dipendenti full-time.
            List<Dipendente> registrati = new ArrayList<>();
            for (Ruolo ruolo : Ruolo.values()) {
                for (TipoContratto tipo : TipoContratto.values()) {
                    Dipendente dipendente = nuovoDipendente();
                    dipendente.setNome(ruolo.name() + "-" + tipo.name());
                    dipendente.setRuolo(ruolo);
                    dipendente.getContratto().setTipoContratto(tipo);
                    dipendente.getContratto().setOreSettimanali(
                            tipo == TipoContratto.FULL_TIME ? 40 : 20);
                    service.registra(dipendente);
                    registrati.add(dipendente);
                }
            }

            // Il cognome casuale è comune solo ai dati di questa prova. La pulizia
            // già presente li elimina tutti, anche se un'asserzione successiva fallisce.
            List<Dipendente> riletti = new DipendenteRepository().trovaTutti().stream()
                    .filter(d -> cognomeTest.equals(d.getCognome())).toList();
            assertEquals(registrati.size(), riletti.size());
            for (Dipendente atteso : registrati) {
                List<Dipendente> corrispondenti = riletti.stream()
                        .filter(d -> d.getId() == atteso.getId()).toList();
                assertEquals(1, corrispondenti.size(), "Ogni dipendente deve comparire una sola volta");
                Dipendente letto = corrispondenti.getFirst();
                assertEquals(atteso.getNome(), letto.getNome());
                assertEquals(atteso.getRuolo(), letto.getRuolo());
                assertNotNull(letto.getContratto());
                assertEquals(atteso.getContratto().getId(), letto.getContratto().getId());
                assertEquals(atteso.getId(), letto.getContratto().getDipendenteId());
                assertEquals(atteso.getContratto().getTipoContratto(), letto.getContratto().getTipoContratto());
                assertEquals(atteso.getContratto().getOreSettimanali(), letto.getContratto().getOreSettimanali());
            }
        }

        @Test
        void nonInserisceDatiNonValidi() throws SQLException {
            Dipendente dipendente = nuovoDipendente();
            dipendente.getContratto().setOreSettimanali(41);

            assertThrows(IllegalArgumentException.class, () -> service.registra(dipendente));

            assertEquals(0, contaDipendentiDellaProva());
            verificaIdNonAssegnati(dipendente);
        }

        @Test
        void nonRegistraDueVolteLoStessoOggetto() throws SQLException {
            // Dopo il primo successo gli ID sono assegnati; il secondo invio deve
            // essere rifiutato senza cambiare gli ID o aggiungere altre righe.
            Dipendente dipendente = nuovoDipendente();
            service.registra(dipendente);
            int idOriginale = dipendente.getId();

            assertThrows(IllegalArgumentException.class, () -> service.registra(dipendente));

            assertEquals(idOriginale, dipendente.getId());
            assertEquals(1, contaDipendentiDellaProva());
            verificaSalvataggio(dipendente);
        }

        @Test
        void accetta50CaratteriUnicodeECostoMassimo() throws SQLException {
            Dipendente dipendente = nuovoDipendente();
            // Un carattere Unicode supplementare occupa due char Java:
            // length() sarebbe 100, ma i caratteri da memorizzare sono 50.
            dipendente.setNome(new String(Character.toChars(0x10400)).repeat(50));
            dipendente.getContratto().setCostoOrario(new BigDecimal("9999.99"));

            service.registra(dipendente);

            verificaSalvataggio(dipendente);
        }

        @Test
        void accettaZeriDecimaliFinaliSenzaCambiareImporto() throws SQLException {
            Dipendente dipendente = nuovoDipendente();
            dipendente.getContratto().setCostoOrario(new BigDecimal("12.500"));

            service.registra(dipendente);

            verificaSalvataggio(dipendente);
        }

        @Test
        void annullaEntrambeLeRigheSeIlRepositoryContrattoSegnalaUnErrore() throws SQLException {
            Dipendente dipendente = nuovoDipendente();
            SQLException erroreSimulato = new SQLException("Errore controllato dopo INSERT contratto");
            // L'array consente al repository anonimo di comunicare l'ID al test.
            // La variabile catturata resta la stessa, ma il suo elemento è modificabile.
            int[] idContrattoInserito = {0};
            // Questa variante esiste solo nel test. Esegue il vero INSERT e poi fallisce,
            // prima del commit: dimostriamo che il rollback annulla entrambe le righe.
            ContrattoRepository repositoryConErrore = new ContrattoRepository() {
                @Override
                public int inserisci(Connection connection, int dipendenteId, Contratto contratto)
                        throws SQLException {
                    idContrattoInserito[0] = super.inserisci(connection, dipendenteId, contratto);
                    throw erroreSimulato;
                }
            };
            DipendenteService serviceConErrore = new DipendenteService(
                    new DipendenteRepository(), repositoryConErrore);

            SQLException errore = assertThrows(SQLException.class,
                    () -> serviceConErrore.registra(dipendente));

            // assertSame controlla l'identità: deve riemergere esattamente l'errore
            // simulato, non un errore diverso che farebbe passare il test per caso.
            assertSame(erroreSimulato, errore);
            assertTrue(idContrattoInserito[0] > 0, "Il secondo INSERT deve essere stato eseguito");
            assertEquals(0, contaDipendentiDellaProva(),
                    "Il rollback deve annullare anche il primo INSERT");
            try (PreparedStatement statement = connessione.prepareStatement(
                    "SELECT COUNT(*) FROM contratto WHERE id = ?")) {
                statement.setInt(1, idContrattoInserito[0]);
                try (ResultSet risultati = statement.executeQuery()) {
                    assertTrue(risultati.next());
                    assertEquals(0, risultati.getInt(1), "Anche il contratto deve essere annullato");
                }
            }
            verificaIdNonAssegnati(dipendente);
        }

        private int contaDipendentiDellaProva() throws SQLException {
            try (PreparedStatement statement = connessione.prepareStatement(
                    "SELECT COUNT(*) FROM dipendente WHERE cognome = ?")) {
                statement.setString(1, cognomeTest);
                try (ResultSet risultati = statement.executeQuery()) {
                    assertTrue(risultati.next());
                    return risultati.getInt(1);
                }
            }
        }

        /** Confronta gli oggetti Java con le righe confermate nel database. */
        private void verificaSalvataggio(Dipendente dipendente) throws SQLException {
            Contratto contratto = dipendente.getContratto();
            assertTrue(dipendente.getId() > 0);
            assertTrue(contratto.getId() > 0);
            assertEquals(dipendente.getId(), contratto.getDipendenteId());
            assertEquals(1, contaDipendentiDellaProva());

            String sql = """
                    SELECT d.nome, d.cognome, d.ruolo, c.id AS contratto_id,
                           c.tipo, c.ore_settimanali, c.costo_orario,
                           c.data_inizio, c.data_fine
                    FROM dipendente d
                    JOIN contratto c ON c.dipendente_id = d.id
                    WHERE d.id = ?
                    """;
            try (PreparedStatement statement = connessione.prepareStatement(sql)) {
                statement.setInt(1, dipendente.getId());
                try (ResultSet risultati = statement.executeQuery()) {
                    assertTrue(risultati.next(), "Devono esistere entrambe le righe collegate");
                    assertEquals(dipendente.getNome(), risultati.getString("nome"));
                    assertEquals(cognomeTest, risultati.getString("cognome"));
                    assertEquals("CAMERIERE", risultati.getString("ruolo"));
                    assertEquals(contratto.getId(), risultati.getInt("contratto_id"));
                    assertEquals("PART_TIME", risultati.getString("tipo"));
                    assertEquals(20, risultati.getInt("ore_settimanali"));
                    // Confrontiamo il valore numerico: 12.500 Java e 12.50 SQL sono uguali.
                    assertEquals(0, contratto.getCostoOrario().compareTo(
                            risultati.getBigDecimal("costo_orario")));
                    assertEquals(contratto.getDataInizio(),
                            risultati.getDate("data_inizio").toLocalDate());
                    assertEquals(contratto.getDataFine(),
                            risultati.getObject("data_fine", LocalDate.class));
                    assertFalse(risultati.next(), "Deve esserci un solo contratto iniziale");
                }
            }
        }
    }
}
