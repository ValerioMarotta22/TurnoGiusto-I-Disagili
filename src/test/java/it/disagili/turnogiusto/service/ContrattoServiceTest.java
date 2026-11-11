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
    // Fermiamo l'orologio all'inizio della singola prova: service e dati preparati
    // condividono lo stesso oggi. La lettura ordinaria di US01 usa invece la data reale.
    private final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
    private final LocalDate oggi = LocalDate.now(clock);

    private Contratto nuoviDati() {
        // Proposta valida di base: ID zero, part-time da 20 ore e decorrenza odierna.
        return new Contratto(0, TipoContratto.PART_TIME, 20, new BigDecimal("15.75"), oggi);
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
    void rifiutaDecorrenzaPassataFuturaOAssente() {
        // La regola scelta per US02 accetta solo oggi, non date libere di decorrenza.
        for (LocalDate data : new LocalDate[]{oggi.minusDays(1), oggi.plusDays(1), null}) {
            Contratto dati = nuoviDati();
            dati.setDataInizio(data);
            IllegalArgumentException errore = assertThrows(IllegalArgumentException.class,
                    () -> service().aggiorna(1, 1, dati));
            assertTrue(errore.getMessage().contains("data odierna"));
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
        void salvaNuoviDatiConservaStoricoERendeCorrenteIlNuovoContratto() throws SQLException {
            // Tre fasi: contratto precedente, chiamata al service, lettura SQL indipendente.
            int precedenteId = creaPrecedente();
            Contratto input = nuoviDati();
            input.setDataFine(oggi.plusDays(10));

            ContrattoService.Esito esito = service().aggiorna(dipendenteId, precedenteId, input);

            List<Contratto> righe = rileggi();
            assertEquals(2, righe.size());
            Contratto vecchio = righe.getFirst();
            Contratto nuovo = righe.getLast();
            assertEquals(precedenteId, vecchio.getId());
            assertEquals(oggi.minusDays(30), vecchio.getDataInizio());
            assertEquals(oggi.minusDays(1), vecchio.getDataFine());
            assertEquals(40, vecchio.getOreSettimanali());
            assertEquals(TipoContratto.FULL_TIME, vecchio.getTipoContratto());
            assertEquals(new BigDecimal("12.50"), vecchio.getCostoOrario());
            assertNotEquals(precedenteId, nuovo.getId());
            assertEquals(esito.contratto().getId(), nuovo.getId());
            assertEquals(dipendenteId, nuovo.getDipendenteId());
            assertEquals(oggi, nuovo.getDataInizio());
            assertEquals(input.getDataFine(), nuovo.getDataFine());
            assertEquals(TipoContratto.PART_TIME, nuovo.getTipoContratto());
            assertEquals(20, nuovo.getOreSettimanali());
            assertEquals(new BigDecimal("15.75"), nuovo.getCostoOrario());
            assertFalse(vecchio.isValidoIl(oggi));
            assertTrue(nuovo.isValidoIl(oggi));
            assertFalse(esito.correzioneOdierna());
            // Anche la lettura già usata da US01 vede il nuovo contratto dopo il commit.
            Dipendente letto = new DipendenteRepository().trovaTutti().stream()
                    .filter(d -> d.getId() == dipendenteId).findFirst().orElseThrow();
            assertEquals(nuovo.getId(), letto.getContratto().getId());
            assertEquals(20, letto.getContratto().getOreSettimanali());
            assertEquals(0, input.getId(), "Il service non modifica l'oggetto ricevuto");
        }

        @Test
        void correggeStessaRigaSeIniziataOggiAncheConRipetutiCambi() throws SQLException {
            // Deve cambiare il contenuto, ma non il numero di righe, l'ID o l'inizio.
            int precedenteId = creaContratto(oggi, null, 40, TipoContratto.FULL_TIME);

            Contratto primaModifica = nuoviDati();
            primaModifica.setDataFine(oggi.plusDays(10));
            ContrattoService.Esito esito = service().aggiorna(dipendenteId, precedenteId, primaModifica);

            assertTrue(esito.correzioneOdierna());
            assertEquals(precedenteId, esito.contratto().getId());
            assertEquals(1, rileggi().size());
            assertEquals(oggi, rileggi().getFirst().getDataInizio());
            assertEquals(oggi.plusDays(10), rileggi().getFirst().getDataFine());
            assertEquals(20, rileggi().getFirst().getOreSettimanali());
            assertEquals(TipoContratto.PART_TIME, rileggi().getFirst().getTipoContratto());
            assertEquals(new BigDecimal("15.75"), rileggi().getFirst().getCostoOrario());

            Contratto secondoCambio = nuoviDati();
            secondoCambio.setOreSettimanali(16);
            service().aggiorna(dipendenteId, precedenteId, secondoCambio);
            assertEquals(1, rileggi().size());
            assertEquals(16, rileggi().getFirst().getOreSettimanali());
            // Il secondo invio toglie la scadenza: verifica anche il ramo SQL SET NULL.
            assertNull(rileggi().getFirst().getDataFine());
            assertEquals(precedenteId, rileggi().getFirst().getId());
        }

        @Test
        void cambiaIlContrattoInScadenzaOggiConUnoValidoPerUnSoloGiorno() throws SQLException {
            // Il giorno finale è ancora valido. Il precedente termina ieri e il
            // nuovo può iniziare e finire oggi: nessun buco e nessuna sovrapposizione.
            int precedenteId = creaContratto(oggi.minusDays(1), oggi, 40, TipoContratto.FULL_TIME);
            Contratto dati = nuoviDati();
            dati.setDataFine(oggi);

            service().aggiorna(dipendenteId, precedenteId, dati);

            List<Contratto> righe = rileggi();
            assertEquals(2, righe.size());
            Contratto precedente = righe.getFirst();
            Contratto nuovo = righe.getLast();
            assertEquals(precedenteId, precedente.getId());
            assertEquals(oggi.minusDays(1), precedente.getDataFine());
            assertTrue(precedente.isValidoIl(oggi.minusDays(1)));
            assertFalse(precedente.isValidoIl(oggi));
            assertEquals(oggi, nuovo.getDataInizio());
            assertEquals(oggi, nuovo.getDataFine());
            assertTrue(nuovo.isValidoIl(oggi));
            assertFalse(nuovo.isValidoIl(oggi.plusDays(1)));
        }

        @Test
        void secondoCambioNellaGiornataConservaLoStoricoPrecedente() throws SQLException {
            // Primo cambio: crea una versione da oggi. Secondo cambio: corregge solo
            // quella versione odierna, lasciando intatta la riga chiusa ieri.
            int precedenteId = creaPrecedente();
            int nuovoId = service().aggiorna(dipendenteId, precedenteId, nuoviDati()).contratto().getId();
            Contratto altroCambio = nuoviDati();
            altroCambio.setCostoOrario(new BigDecimal("16.00"));

            service().aggiorna(dipendenteId, nuovoId, altroCambio);

            assertEquals(2, rileggi().size());
            assertEquals(precedenteId, rileggi().getFirst().getId());
            assertEquals(oggi.minusDays(1), rileggi().getFirst().getDataFine());
            assertEquals(nuovoId, rileggi().getLast().getId());
            assertEquals(new BigDecimal("16.00"), rileggi().getLast().getCostoOrario());
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
            // Una nuova fine al giorno +4 è compatibile con un contratto dal giorno +5.
            // La proposta senza scadenza, invece, coprirebbe anche quel periodo futuro.
            int precedenteId = creaPrecedente();
            creaContratto(oggi.plusDays(5), null, 20, TipoContratto.PART_TIME);
            assertThrows(IllegalArgumentException.class,
                    () -> service().aggiorna(dipendenteId, precedenteId, nuoviDati()));
            assertNull(rileggi().getFirst().getDataFine());
            Contratto dati = nuoviDati();
            // Finire esattamente quando inizia l'altro contratto significa
            // condividere quel giorno: gli estremi inclusivi impongono il rifiuto.
            dati.setDataFine(oggi.plusDays(5));
            assertThrows(IllegalArgumentException.class,
                    () -> service().aggiorna(dipendenteId, precedenteId, dati));
            assertEquals(2, rileggi().size());
            assertNull(rileggi().getFirst().getDataFine());
            dati.setDataFine(oggi.plusDays(4));

            service().aggiorna(dipendenteId, precedenteId, dati);

            assertEquals(3, rileggi().size());
            assertEquals(oggi.plusDays(4), rileggi().get(1).getDataFine());
            assertEquals(oggi.plusDays(5), rileggi().getLast().getDataInizio());
        }

        @Test
        void rifiutaUnaSelezioneNonPiuAttuale() throws SQLException {
            // Simuliamo una schermata rimasta con l'ID del contratto già sostituito.
            int precedenteId = creaPrecedente();
            service().aggiorna(dipendenteId, precedenteId, nuoviDati());

            IllegalArgumentException errore = assertThrows(IllegalArgumentException.class,
                    () -> service().aggiorna(dipendenteId, precedenteId, nuoviDati()));

            assertTrue(errore.getMessage().contains("aggiorna l'elenco"));
            assertEquals(2, rileggi().size());
        }

        @Test
        void rollbackRipristinaLaFinePrecedenteEAnnullaIlNuovoInserimento() throws SQLException {
            // Il repository di prova fa prima il vero INSERT, poi genera l'errore.
            // Il test dimostra il rollback sia della chiusura sia del nuovo inserimento.
            LocalDate fineOriginale = oggi.plusDays(30);
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
        void rollbackRipristinaAncheUnaCorrezioneOdierna() throws SQLException {
            // Anche l'UPDATE sulla stessa riga deve essere annullabile: il test causa
            // un errore dopo la modifica e confronta i valori originali riletti da MySQL.
            int precedenteId = creaContratto(oggi, null, 40, TipoContratto.FULL_TIME);
            SQLException simulato = new SQLException("Errore dopo UPDATE");
            ContrattoRepository repositoryConErrore = new ContrattoRepository() {
                @Override
                public void correggiOdierno(Connection connection, int id, int contrattoId, Contratto dati)
                        throws SQLException {
                    super.correggiOdierno(connection, id, contrattoId, dati);
                    throw simulato;
                }
            };

            assertSame(simulato, assertThrows(SQLException.class, () -> service(repositoryConErrore,
                    (c, id, data) -> Stato.ASSENTI).aggiorna(dipendenteId, precedenteId, nuoviDati())));

            assertEquals(1, rileggi().size());
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
                assertEquals(oggi, data);
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
        void nonInterrogaTurniSeLeOreRestanoUgualiOAumentano() throws SQLException {
            // Il contatore deve restare a zero anche se il verificatore risponderebbe PRESENTI.
            int precedenteId = creaContratto(oggi.minusDays(30), null, 20, TipoContratto.PART_TIME);
            AtomicInteger verifiche = new AtomicInteger();
            ContrattoService service = service(new ContrattoRepository(), (c, id, data) -> {
                verifiche.incrementAndGet();
                return Stato.PRESENTI;
            });

            ContrattoService.Esito uguali = service.aggiorna(dipendenteId, precedenteId, nuoviDati());
            Contratto aumento = nuoviDati();
            aumento.setOreSettimanali(30);
            ContrattoService.Esito aumentate = service.aggiorna(dipendenteId, uguali.contratto().getId(), aumento);

            assertEquals(0, verifiche.get());
            assertFalse(uguali.richiedeRicontrolloTurni());
            assertFalse(aumentate.richiedeRicontrolloTurni());
            assertEquals(Stato.NON_RICHIESTA, aumentate.statoTurni());
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
