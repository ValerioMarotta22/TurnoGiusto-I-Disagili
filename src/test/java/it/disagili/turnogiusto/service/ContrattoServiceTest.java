package it.disagili.turnogiusto.service;

import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.TipoContratto;
import it.disagili.turnogiusto.repository.ContrattoRepository;
import it.disagili.turnogiusto.repository.Database;
import it.disagili.turnogiusto.repository.DipendenteRepository;
import it.disagili.turnogiusto.service.VerificaTurniPianificati.Stato;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * US02: le validazioni non richiedono DB; ConDatabase prova le vere transazioni MySQL.
 * I turni sono simulati esclusivamente attraverso il punto di collegamento dichiarato:
 * questi test NON attestano l'integrazione con una pianificazione reale, ancora assente.
 */
class ContrattoServiceTest {
    /** Solo calcolo in memoria: queste prove non aprono connessioni al database. */
    @Test
    void calcolaFineCessazioneSenzaScadenzaAllaFineDelMese() {
        assertEquals(LocalDate.of(2026, 11, 30),
                ContrattoService.calcolaFineCessazione(LocalDate.of(2026, 11, 24), null));
    }

    @Test
    void calcolaFineCessazioneConservaLaScadenzaAnticipata() {
        // La richiesta non deve estendere un contratto che finisce prima della fine del mese.
        assertEquals(LocalDate.of(2026, 11, 27),
                ContrattoService.calcolaFineCessazione(
                        LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 27)));
    }

    @Test
    void calcolaFineCessazioneAnticipaLaScadenzaSuccessiva() {
        assertEquals(LocalDate.of(2026, 11, 30),
                ContrattoService.calcolaFineCessazione(
                        LocalDate.of(2026, 11, 24), LocalDate.of(2026, 12, 31)));
    }

    @Test
    void calcolaFineCessazioneConservaLaScadenzaCoincidente() {
        assertEquals(LocalDate.of(2026, 11, 30),
                ContrattoService.calcolaFineCessazione(
                        LocalDate.of(2026, 11, 24), LocalDate.of(2026, 11, 30)));
    }

    @Test
    void calcolaFineCessazioneQuandoIlMeseIniziaDiLunedi() {
        // Il primo febbraio è escluso: l'ultimo giorno valido è il 31 gennaio.
        assertEquals(LocalDate.of(2027, 1, 31),
                ContrattoService.calcolaFineCessazione(LocalDate.of(2027, 1, 15), null));
    }

    @Test
    void calcolaFineCessazioneAttraversaIlCambioDiAnno() {
        assertEquals(LocalDate.of(2026, 12, 31),
                ContrattoService.calcolaFineCessazione(LocalDate.of(2026, 12, 31), null));
    }

    @Test
    void calcolaFineCessazioneRifiutaLaDataRichiestaMancante() {
        // Anche con una scadenza già nota, la data della richiesta è obbligatoria.
        assertThrows(IllegalArgumentException.class,
                () -> ContrattoService.calcolaFineCessazione(null, null));
        assertThrows(IllegalArgumentException.class,
                () -> ContrattoService.calcolaFineCessazione(null, LocalDate.of(2026, 11, 30)));
    }

    @Test
    void calcolaFineCessazioneGestisceFebbraioComuneEBisestile() {
        assertEquals(LocalDate.of(2027, 2, 28),
                ContrattoService.calcolaFineCessazione(LocalDate.of(2027, 2, 10), null));
        assertEquals(LocalDate.of(2028, 2, 29),
                ContrattoService.calcolaFineCessazione(LocalDate.of(2028, 2, 10), null));
    }

    @Test
    void calcolaDecorrenzaSulPrimoLunediDelMeseSuccessivo() {
        // Novembre 2026 termina di lunedì: la decorrenza è comunque in dicembre.
        assertEquals(LocalDate.of(2026, 12, 7),
                ContrattoService.calcolaDecorrenza(LocalDate.of(2026, 11, 23)));
        assertEquals(LocalDate.of(2026, 12, 7),
                ContrattoService.calcolaDecorrenza(LocalDate.of(2026, 11, 30)));
    }

    @Test
    void calcolaDecorrenzaMantieneIlGiornoUnoQuandoELunedi() {
        // nextOrSame deve accettare il giorno 1, senza spostarsi all'8 febbraio.
        assertEquals(LocalDate.of(2027, 2, 1),
                ContrattoService.calcolaDecorrenza(LocalDate.of(2027, 1, 31)));
    }

    @Test
    void calcolaDecorrenzaAttraversaIlCambioDiAnno() {
        assertEquals(LocalDate.of(2027, 1, 4),
                ContrattoService.calcolaDecorrenza(LocalDate.of(2026, 12, 7)));
        assertEquals(LocalDate.of(2027, 1, 4),
                ContrattoService.calcolaDecorrenza(LocalDate.of(2026, 12, 31)));
    }

    @Test
    void calcolaDecorrenzaGestisceFebbraioBisestile() {
        // Il 29 febbraio è una data valida nel 2028; marzo inizia di mercoledì.
        assertEquals(LocalDate.of(2028, 3, 6),
                ContrattoService.calcolaDecorrenza(LocalDate.of(2028, 2, 29)));
    }

    @Test
    void calcolaDecorrenzaRifiutaLaDataMancante() {
        assertThrows(IllegalArgumentException.class,
                () -> ContrattoService.calcolaDecorrenza(null));
    }

    // Fermiamo l'orologio all'inizio della singola prova: service e dati preparati
    // condividono lo stesso oggi. La lettura ordinaria di US01 usa invece la data reale.
    private final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
    private final LocalDate oggi = LocalDate.now(clock);
    private final LocalDate decorrenza = ContrattoService.calcolaDecorrenza(oggi);

    private Contratto nuoviDati() {
        // Proposta valida: ID zero e decorrenza al primo lunedì del mese successivo.
        return new Contratto(0, TipoContratto.PART_TIME, 20, new BigDecimal("15.75"), decorrenza);
    }

    private ContrattoService service(ContrattoRepository repository, VerificaTurniPianificati verifica) {
        // Sostituiamo le dipendenze nei test tramite il costruttore dello stesso pacchetto.
        return new ContrattoService(repository, verifica, clock);
    }

    private ContrattoService service() {
        // ASSENTI è una risposta simulata per queste prove, non una query sui turni reali.
        return service(new ContrattoRepository(), (connessione, id, data) -> Stato.ASSENTI);
    }

    @Test
    void rifiutaIdentificativiEContrattoMancanti() {
        // Queste chiamate devono fallire in validazione, prima di richiedere MySQL.
        assertThrows(IllegalArgumentException.class, () -> service().aggiorna(0, 1, nuoviDati()));
        assertThrows(IllegalArgumentException.class, () -> service().aggiorna(1, 0, nuoviDati()));
        assertThrows(IllegalArgumentException.class, () -> service().aggiorna(1, 1, null));
    }

    @Test
    void raccoglieErroriDelModelloEDelCostoPrimaDelDatabase() {
        // Inseriamo più errori insieme e controlliamo che il messaggio li riporti tutti.
        Contratto dati = nuoviDati();
        dati.setTipoContratto(null);
        dati.setOreSettimanali(41);
        dati.setCostoOrario(new BigDecimal("10000.001"));
        dati.setDataFine(oggi.minusDays(1));

        IllegalArgumentException errore = assertThrows(IllegalArgumentException.class,
                () -> service().aggiorna(1, 1, dati));

        assertTrue(errore.getMessage().contains("Il tipo di contratto è obbligatorio"));
        assertTrue(errore.getMessage().contains("non possono superare 40"));
        assertTrue(errore.getMessage().contains("non può superare 9999,99"));
        assertTrue(errore.getMessage().contains("al massimo due cifre decimali"));
        assertTrue(errore.getMessage().contains("non può precedere la data di inizio"));
        assertEquals(0, dati.getId());
    }

    @Test
    void rifiutaDecorrenzeDiverseDaQuellaProgrammata() {
        // Accettiamo solo la decorrenza calcolata: anche oggi è una data non ammessa.
        for (LocalDate data : new LocalDate[]{oggi.minusDays(1), oggi, decorrenza.minusDays(1), decorrenza.plusDays(1), null}) {
            Contratto dati = nuoviDati();
            dati.setDataInizio(data);
            IllegalArgumentException errore = assertThrows(IllegalArgumentException.class,
                    () -> service().aggiorna(1, 1, dati));
            assertTrue(errore.getMessage().contains("deve decorrere dal " + decorrenza));
        }
    }

    @Test
    void rifiutaCollegamentiAdAltroDipendenteOAltroContratto() {
        // Un oggetto ricevuto dalla UI non deve cambiare il proprietario del contratto.
        Contratto dati = nuoviDati();
        dati.setDipendenteId(2);
        assertThrows(IllegalArgumentException.class, () -> service().aggiorna(1, 1, dati));
        dati.setDipendenteId(0);
        dati.setId(2);
        assertThrows(IllegalArgumentException.class, () -> service().aggiorna(1, 1, dati));
    }

    @Nested
    class ConDatabase {
        // Questa connessione prepara e rilegge i dati; il service ne apre una distinta.
        private Connection connessione;
        private int dipendenteId;

        @BeforeEach
        void preparaDipendenteDiProva() throws SQLException {
            try {
                connessione = Database.getConnection();
            } catch (SQLException errore) {
                // Come negli altri test del progetto, un errore in apertura fa saltare
                // la prova: skipped non è un successo e non verifica la transazione.
                assumeTrue(false, "MySQL non raggiungibile: " + errore.getMessage());
            }
            // Le righe preparate devono essere visibili alla connessione del service.
            connessione.setAutoCommit(true);
            dipendenteId = new DipendenteRepository().inserisci(connessione,
                    new Dipendente("US02", UUID.randomUUID().toString(), Ruolo.CAMERIERE, null));
        }

        @AfterEach
        void eliminaSoloLaProva() throws SQLException {
            if (connessione == null) { return; }
            // L'ID è assegnato prima del cambio contratto: la pulizia funziona anche
            // dopo un test fallito. ON DELETE CASCADE elimina i contratti della prova.
            try (Connection daChiudere = connessione;
                 PreparedStatement statement = daChiudere.prepareStatement("DELETE FROM dipendente WHERE id = ?")) {
                statement.setInt(1, dipendenteId);
                statement.executeUpdate();
            }
        }

        private int creaContratto(LocalDate inizio, LocalDate fine, int ore, TipoContratto tipo)
                throws SQLException {
            // Preparazione di fixture: usiamo l'INSERT esistente e conserviamo l'ID
            // generato da MySQL, senza supporre ID fissi o database inizialmente vuoto.
            return new ContrattoRepository().inserisci(connessione, dipendenteId,
                    new Contratto(0, dipendenteId, tipo, ore, new BigDecimal("12.50"), inizio, fine));
        }

        private int creaPrecedente() throws SQLException {
            return creaContratto(oggi.minusDays(30), null, 40, TipoContratto.FULL_TIME);
        }

        // Una lettura SQL indipendente, su connessione diversa da quella del service,
        // verifica ciò che è stato realmente confermato o annullato nel database.
        private List<Contratto> rileggi() throws SQLException {
            List<Contratto> righe = new ArrayList<>();
            try (PreparedStatement statement = connessione.prepareStatement(
                    "SELECT * FROM contratto WHERE dipendente_id = ? ORDER BY data_inizio, id")) {
                statement.setInt(1, dipendenteId);
                try (ResultSet risultati = statement.executeQuery()) {
                    while (risultati.next()) {
                        Date fine = risultati.getDate("data_fine");
                        righe.add(new Contratto(risultati.getInt("id"), dipendenteId,
                                TipoContratto.valueOf(risultati.getString("tipo")),
                                risultati.getInt("ore_settimanali"), risultati.getBigDecimal("costo_orario"),
                                risultati.getDate("data_inizio").toLocalDate(),
                                fine == null ? null : fine.toLocalDate()));
                    }
                }
            }
            return righe;
        }

        @Test
        void programmaIlNuovoContrattoConservandoLoStoricoEQuelloAncoraVigente() throws SQLException {
            // Tre fasi: contratto precedente, chiamata al service, lettura SQL indipendente.
            int precedenteId = creaPrecedente();
            Contratto input = nuoviDati();
            input.setDataFine(decorrenza.plusDays(10));

            ContrattoService.Esito esito = service().aggiorna(dipendenteId, precedenteId, input);

            List<Contratto> righe = rileggi();
            assertEquals(2, righe.size());
            Contratto vecchio = righe.getFirst();
            Contratto nuovo = righe.getLast();
            assertEquals(precedenteId, vecchio.getId());
            assertEquals(oggi.minusDays(30), vecchio.getDataInizio());
            assertEquals(decorrenza.minusDays(1), vecchio.getDataFine());
            assertEquals(40, vecchio.getOreSettimanali());
            assertEquals(TipoContratto.FULL_TIME, vecchio.getTipoContratto());
            assertEquals(new BigDecimal("12.50"), vecchio.getCostoOrario());
            assertNotEquals(precedenteId, nuovo.getId());
            assertEquals(esito.contratto().getId(), nuovo.getId());
            assertEquals(dipendenteId, nuovo.getDipendenteId());
            assertEquals(decorrenza, nuovo.getDataInizio());
            assertEquals(input.getDataFine(), nuovo.getDataFine());
            assertEquals(TipoContratto.PART_TIME, nuovo.getTipoContratto());
            assertEquals(20, nuovo.getOreSettimanali());
            assertEquals(new BigDecimal("15.75"), nuovo.getCostoOrario());
            assertTrue(vecchio.isValidoIl(oggi));
            assertFalse(nuovo.isValidoIl(oggi));
            assertTrue(vecchio.isValidoIl(decorrenza.minusDays(1)));
            assertFalse(vecchio.isValidoIl(decorrenza));
            assertTrue(nuovo.isValidoIl(decorrenza));
            assertFalse(esito.correzioneOdierna());
            // L'elenco di US01 continua a mostrare il contratto vigente, non quello futuro.
            Dipendente letto = new DipendenteRepository().trovaTutti().stream()
                    .filter(d -> d.getId() == dipendenteId).findFirst().orElseThrow();
            assertEquals(vecchio.getId(), letto.getContratto().getId());
            assertEquals(40, letto.getContratto().getOreSettimanali());
            assertEquals(0, input.getId(), "Il service non modifica l'oggetto ricevuto");
        }

        @Test
        void conservaAncheIlContrattoIniziatoOggiInUnaRigaDistinta() throws SQLException {
            int precedenteId = creaContratto(oggi, null, 40, TipoContratto.FULL_TIME);
            ContrattoService.Esito esito = service().aggiorna(dipendenteId, precedenteId, nuoviDati());
            List<Contratto> righe = rileggi();
            assertEquals(2, righe.size());
            assertFalse(esito.correzioneOdierna());
            assertNotEquals(precedenteId, esito.contratto().getId());
            assertEquals(precedenteId, righe.getFirst().getId());
            assertEquals(oggi, righe.getFirst().getDataInizio());
            assertEquals(decorrenza.minusDays(1), righe.getFirst().getDataFine());
            assertEquals(40, righe.getFirst().getOreSettimanali());
            assertEquals(TipoContratto.FULL_TIME, righe.getFirst().getTipoContratto());
            assertEquals(new BigDecimal("12.50"), righe.getFirst().getCostoOrario());
            assertEquals(decorrenza, righe.getLast().getDataInizio());
            assertEquals(20, righe.getLast().getOreSettimanali());
        }

        @Test
        void nonEstendeUnContrattoCheScadePrimaDellaDecorrenza() throws SQLException {
            int precedenteId = creaContratto(oggi.minusDays(1), oggi, 40, TipoContratto.FULL_TIME);
            IllegalArgumentException errore = assertThrows(IllegalArgumentException.class,
                    () -> service().aggiorna(dipendenteId, precedenteId, nuoviDati()));
            assertTrue(errore.getMessage().contains("scade prima"));
            assertEquals(1, rileggi().size());
            assertEquals(oggi, rileggi().getFirst().getDataFine());
        }

        @Test
        void secondoCambioNonSovrascriveIlContrattoFuturoGiaSalvato() throws SQLException {
            // Finché non definiamo la modifica di un cambio programmato, il secondo
            // salvataggio viene rifiutato: nessuna versione già registrata va persa.
            int precedenteId = creaPrecedente();
            int nuovoId = service().aggiorna(dipendenteId, precedenteId, nuoviDati()).contratto().getId();
            Contratto altroCambio = nuoviDati();
            altroCambio.setCostoOrario(new BigDecimal("16.00"));
            assertThrows(IllegalArgumentException.class,
                    () -> service().aggiorna(dipendenteId, precedenteId, altroCambio));
            assertEquals(2, rileggi().size());
            assertEquals(precedenteId, rileggi().getFirst().getId());
            assertEquals(decorrenza.minusDays(1), rileggi().getFirst().getDataFine());
            assertEquals(nuovoId, rileggi().getLast().getId());
            assertEquals(new BigDecimal("15.75"), rileggi().getLast().getCostoOrario());
        }

        @Test
        void rifiutaDipendenteInesistenteESenzaContrattoValido() throws SQLException {
            assertThrows(IllegalArgumentException.class,
                    () -> service().aggiorna(Integer.MAX_VALUE, 1, nuoviDati()));
            assertThrows(IllegalArgumentException.class,
                    () -> service().aggiorna(dipendenteId, 1, nuoviDati()));
            int scaduto = creaContratto(oggi.minusDays(10), oggi.minusDays(1), 40, TipoContratto.FULL_TIME);
            assertThrows(IllegalArgumentException.class,
                    () -> service().aggiorna(dipendenteId, scaduto, nuoviDati()));
            assertEquals(1, rileggi().size());
            assertEquals(oggi.minusDays(1), rileggi().getFirst().getDataFine());
        }

        @Test
        void rifiutaContrattiContemporaneamenteValidiDiTipiDiversi() throws SQLException {
            // Un filtro prematuro sui soli part-time nasconderebbe la sovrapposizione.
            int precedenteId = creaPrecedente();
            creaContratto(oggi, null, 20, TipoContratto.PART_TIME);

            IllegalArgumentException errore = assertThrows(IllegalArgumentException.class,
                    () -> service().aggiorna(dipendenteId, precedenteId, nuoviDati()));

            assertTrue(errore.getMessage().contains("più contratti"));
            assertEquals(2, rileggi().size());
            assertNull(rileggi().getFirst().getDataFine());
        }

        @Test
        void rifiutaSovrapposizioneFuturaMaAccettaPeriodiAdiacenti() throws SQLException {
            // Fine a decorrenza +4 compatibile con un contratto da decorrenza +5.
            // La proposta senza scadenza, invece, coprirebbe anche quel periodo futuro.
            int precedenteId = creaPrecedente();
            creaContratto(decorrenza.plusDays(5), null, 20, TipoContratto.PART_TIME);
            assertThrows(IllegalArgumentException.class,
                    () -> service().aggiorna(dipendenteId, precedenteId, nuoviDati()));
            assertNull(rileggi().getFirst().getDataFine());
            Contratto dati = nuoviDati();
            // Finire esattamente quando inizia l'altro contratto significa
            // condividere quel giorno: gli estremi inclusivi impongono il rifiuto.
            dati.setDataFine(decorrenza.plusDays(5));
            assertThrows(IllegalArgumentException.class,
                    () -> service().aggiorna(dipendenteId, precedenteId, dati));
            assertEquals(2, rileggi().size());
            assertNull(rileggi().getFirst().getDataFine());
            dati.setDataFine(decorrenza.plusDays(4));

            service().aggiorna(dipendenteId, precedenteId, dati);

            assertEquals(3, rileggi().size());
            assertEquals(decorrenza.plusDays(4), rileggi().get(1).getDataFine());
            assertEquals(decorrenza.plusDays(5), rileggi().getLast().getDataInizio());
        }

        @Test
        void rifiutaUnaSelezioneNonPiuAttuale() throws SQLException {
            // Simuliamo una schermata rimasta con l'ID del contratto già sostituito.
            int precedenteId = creaPrecedente();
            int futuroId = service().aggiorna(dipendenteId, precedenteId, nuoviDati()).contratto().getId();
            // Il contratto futuro non è ancora quello vigente.
            IllegalArgumentException errore = assertThrows(IllegalArgumentException.class,
                    () -> service().aggiorna(dipendenteId, futuroId, nuoviDati()));
            assertTrue(errore.getMessage().contains("aggiorna l'elenco"));
            assertEquals(2, rileggi().size());
        }

        @Test
        void rollbackRipristinaLaFinePrecedenteEAnnullaIlNuovoInserimento() throws SQLException {
            // Il repository di prova fa prima il vero INSERT, poi genera l'errore.
            // Il test dimostra il rollback sia della chiusura sia del nuovo inserimento.
            LocalDate fineOriginale = decorrenza.plusDays(30);
            int precedenteId = creaContratto(oggi.minusDays(30), fineOriginale, 40, TipoContratto.FULL_TIME);
            SQLException simulato = new SQLException("Errore dopo INSERT");
            ContrattoRepository repositoryConErrore = new ContrattoRepository() {
                @Override
                public int inserisci(Connection connection, int id, Contratto dati) throws SQLException {
                    super.inserisci(connection, id, dati);
                    throw simulato;
                }
            };
            Contratto input = nuoviDati();

            SQLException errore = assertThrows(SQLException.class, () -> service(repositoryConErrore,
                    (c, id, data) -> Stato.ASSENTI).aggiorna(dipendenteId, precedenteId, input));

            assertSame(simulato, errore);
            assertEquals(1, rileggi().size());
            assertEquals(precedenteId, rileggi().getFirst().getId());
            assertEquals(fineOriginale, rileggi().getFirst().getDataFine());
            assertEquals(40, rileggi().getFirst().getOreSettimanali());
            assertEquals(0, input.getId());
        }

        @Test
        void rollbackRipristinaLaChiusuraDiUnContrattoIniziatoOggi() throws SQLException {
            int precedenteId = creaContratto(oggi, null, 40, TipoContratto.FULL_TIME);
            SQLException simulato = new SQLException("Errore dopo chiusura");
            ContrattoRepository repositoryConErrore = new ContrattoRepository() {
                @Override
                public void chiudi(Connection connection, int id, int contrattoId, LocalDate fine)
                        throws SQLException {
                    super.chiudi(connection, id, contrattoId, fine);
                    throw simulato;
                }
            };
            assertSame(simulato, assertThrows(SQLException.class, () -> service(repositoryConErrore,
                    (c, id, data) -> Stato.ASSENTI).aggiorna(dipendenteId, precedenteId, nuoviDati())));
            assertEquals(1, rileggi().size());
            assertNull(rileggi().getFirst().getDataFine());
            assertEquals(40, rileggi().getFirst().getOreSettimanali());
            assertEquals(TipoContratto.FULL_TIME, rileggi().getFirst().getTipoContratto());
            assertEquals(new BigDecimal("12.50"), rileggi().getFirst().getCostoOrario());
        }

        @Test
        void avvisoSoloConRiduzioneETurniPresentiUsandoLaConnessioneRicevuta() throws SQLException {
            // Il verificatore simulato controlla ID, data e connessione transazionale.
            // PRESENTI consente di verificare la decisione del service, non la ricerca
            // dei turni nel sistema di pianificazione, che resta da implementare.
            int precedenteId = creaPrecedente();
            AtomicInteger verifiche = new AtomicInteger();
            VerificaTurniPianificati turniPresenti = (connection, id, data) -> {
                verifiche.incrementAndGet();
                assertFalse(connection.isClosed());
                assertFalse(connection.getAutoCommit());
                assertEquals(dipendenteId, id);
                assertEquals(decorrenza, data);
                // La verifica vede il contratto precedente nella transazione del service.
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT ore_settimanali FROM contratto WHERE id = ?")) {
                    statement.setInt(1, precedenteId);
                    try (ResultSet risultati = statement.executeQuery()) {
                        assertTrue(risultati.next());
                        assertEquals(40, risultati.getInt(1));
                    }
                }
                return Stato.PRESENTI;
            };

            ContrattoService.Esito esito = service(new ContrattoRepository(), turniPresenti)
                    .aggiorna(dipendenteId, precedenteId, nuoviDati());

            assertTrue(esito.oreRidotte());
            assertTrue(esito.richiedeRicontrolloTurni());
            assertFalse(esito.integrazionePianificazioneDaCompletare());
            assertEquals(1, verifiche.get());
            assertEquals(2, rileggi().size(), "L'avviso accompagna un salvataggio confermato");
        }

        @Test
        void nessunAvvisoSeLeOreDiminuisconoMaNonCiSonoTurni() throws SQLException {
            int precedenteId = creaPrecedente();

            ContrattoService.Esito esito = service().aggiorna(dipendenteId, precedenteId, nuoviDati());

            assertTrue(esito.oreRidotte());
            assertEquals(Stato.ASSENTI, esito.statoTurni());
            assertFalse(esito.richiedeRicontrolloTurni());
            assertFalse(esito.integrazionePianificazioneDaCompletare());
        }

        @Test
        void nonInterrogaTurniSeLeOreRestanoUguali() throws SQLException {
            verificaNessunaRicercaTurni(20);
        }

        @Test
        void nonInterrogaTurniSeLeOreAumentano() throws SQLException {
            verificaNessunaRicercaTurni(30);
        }

        // Ogni test ha un dipendente distinto: non tentiamo di sovrascrivere
        // un contratto futuro già programmato nella stessa prova.
        private void verificaNessunaRicercaTurni(int nuoveOre) throws SQLException {
            int precedenteId = creaContratto(oggi.minusDays(30), null, 20, TipoContratto.PART_TIME);
            AtomicInteger verifiche = new AtomicInteger();
            ContrattoService servizio = service(new ContrattoRepository(), (c, id, data) -> {
                verifiche.incrementAndGet();
                return Stato.PRESENTI;
            });
            Contratto dati = nuoviDati();
            dati.setOreSettimanali(nuoveOre);
            ContrattoService.Esito esito = servizio.aggiorna(dipendenteId, precedenteId, dati);
            assertEquals(0, verifiche.get());
            assertFalse(esito.richiedeRicontrolloTurni());
            assertEquals(Stato.NON_RICHIESTA, esito.statoTurni());
        }

        @Test
        void integrazioneAssenteNonEquivaleATurniAssenti() throws SQLException {
            // Il costruttore reale predefinito restituisce NON_DISPONIBILE: segnala
            // un'integrazione ancora mancante, senza inventare un avviso o un esito negativo.
            int precedenteId = creaPrecedente();

            ContrattoService.Esito esito = new ContrattoService()
                    .aggiorna(dipendenteId, precedenteId, nuoviDati());

            assertEquals(Stato.NON_DISPONIBILE, esito.statoTurni());
            assertTrue(esito.integrazionePianificazioneDaCompletare());
            assertFalse(esito.richiedeRicontrolloTurni());
            assertEquals(2, rileggi().size());
        }

        @Test
        void erroreDellaVerificaTurniNonLasciaModificheParziali() throws SQLException {
            // Se la ricerca dei turni fallisce, il service propaga l'errore e non salva.
            int precedenteId = creaPrecedente();
            SQLException simulato = new SQLException("Verifica turni fallita");
            ContrattoService service = service(new ContrattoRepository(), (c, id, data) -> {
                throw simulato;
            });

            assertSame(simulato, assertThrows(SQLException.class,
                    () -> service.aggiorna(dipendenteId, precedenteId, nuoviDati())));

            assertEquals(1, rileggi().size());
            assertNull(rileggi().getFirst().getDataFine());
        }

        @Test
        void rifiutaRisposteNonValideDelVerificatoreSenzaSalvare() throws SQLException {
            // Questo è un controllo del contratto di integrazione, non di turni reali.
            // Una risposta mancante o NON_RICHIESTA non può diventare "nessun turno":
            // il chiamante riceve un errore e il contratto originale rimane intatto.
            int precedenteId = creaPrecedente();
            for (Stato risposta : new Stato[]{null, Stato.NON_RICHIESTA}) {
                Contratto input = nuoviDati();
                ContrattoService conRispostaErrata = service(new ContrattoRepository(),
                        (c, id, data) -> risposta);

                IllegalStateException errore = assertThrows(IllegalStateException.class,
                        () -> conRispostaErrata.aggiorna(dipendenteId, precedenteId, input));

                assertEquals("Esito della verifica dei turni non valido", errore.getMessage());
                List<Contratto> righe = rileggi();
                assertEquals(1, righe.size());
                assertEquals(precedenteId, righe.getFirst().getId());
                assertEquals(40, righe.getFirst().getOreSettimanali());
                assertNull(righe.getFirst().getDataFine());
                assertEquals(0, input.getId());
            }
        }
    }
}
