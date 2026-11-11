package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.repository.DipendenteRepository;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.TipoContratto;
import it.disagili.turnogiusto.service.DipendenteService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test JavaFX attivi del modulo di registrazione e dell'elenco dipendenti.
 * Caricano i FXML ripristinati e controllano i controller con repository e service
 * sostituiti nei test: le operazioni grafiche non scrivono sul database reale.
 * I test separati di repository e service verificano la persistenza e le transazioni.
 */
class DipendenteFormControllerTest {

    /** Condivide lo stesso toolkit JavaFX dei test del modulo, senza riavviarlo. */
    @Nested
    class ElencoDipendenti {

        private FXMLLoader caricaElenco(DipendenteRepository repository) throws Exception {
            return caricaElenco(repository, new DipendenteService());
        }

        private FXMLLoader caricaElenco(DipendenteRepository repository,
                                       DipendenteService service) throws Exception {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/it/disagili/turnogiusto/ui/elenco-dipendenti.fxml"));
            loader.setControllerFactory(tipo -> new ElencoDipendentiController(repository, service));
            loader.load();
            return loader;
        }

        private Stage apriModulo(FXMLLoader loader) {
            ((Button) loader.getNamespace().get("nuovoDipendenteButton")).fire();
            return Window.getWindows().stream()
                    .filter(window -> window instanceof Stage stage
                            && "Nuovo dipendente".equals(stage.getTitle()) && stage.isShowing())
                    .map(window -> (Stage) window).findFirst().orElseThrow();
        }

        @SuppressWarnings("unchecked")
        private void compilaModulo(Stage finestra) {
            ((TextField) finestra.getScene().lookup("#nomeField")).setText("Anna");
            ((TextField) finestra.getScene().lookup("#cognomeField")).setText("Rossi");
            ((TextField) finestra.getScene().lookup("#oreSettimanaliField")).setText("20");
            ((TextField) finestra.getScene().lookup("#costoOrarioField")).setText("12,50");
            ((ComboBox<Ruolo>) finestra.getScene().lookup("#ruoloCombo")).setValue(Ruolo.CAMERIERE);
            ((ComboBox<TipoContratto>) finestra.getScene().lookup("#tipoContrattoCombo"))
                    .setValue(TipoContratto.PART_TIME);
            DatePicker data = (DatePicker) finestra.getScene().lookup("#dataInizioPicker");
            data.getEditor().setText(data.getConverter().toString(LocalDate.now()));
        }

        @Test
        void apreModuloEAnnullaSenzaRegistrare() throws Exception {
            AtomicBoolean registrato = new AtomicBoolean();
            DipendenteService service = new DipendenteService() {
                @Override
                public void registra(Dipendente dipendente) {
                    registrato.set(true);
                }
            };
            AtomicReference<FXMLLoader> elenco = new AtomicReference<>();
            CompletableFuture<Void> fine = suJavaFx(() -> {
                FXMLLoader loader = caricaElenco(new DipendenteRepository() {
                    @Override
                    public List<Dipendente> trovaTutti() { return List.of(); }
                }, service);
                elenco.set(loader);
                return fineCaricamento(loader);
            });
            fine.get(10, TimeUnit.SECONDS);
            suJavaFx(() -> {
                Stage modulo = apriModulo(elenco.get());
                try {
                    compilaModulo(modulo);
                    ((Button) modulo.getScene().lookup("#annullaButton")).fire();
                    assertFalse(modulo.isShowing());
                    assertFalse(registrato.get());
                    assertTrue(tabella(elenco.get()).getItems().isEmpty());
                } finally {
                    modulo.hide();
                }
                return null;
            });
        }

        @Test
        void registraDalModuloAggiornaElencoEConsenteChiusuraDopoSuccesso() throws Exception {
            AtomicReference<List<Dipendente>> salvati = new AtomicReference<>(List.of());
            CountDownLatch entrato = new CountDownLatch(1);
            CountDownLatch prosegui = new CountDownLatch(1);
            DipendenteService service = new DipendenteService() {
                @Override
                public void registra(Dipendente dipendente) throws SQLException {
                    entrato.countDown();
                    try {
                        if (!prosegui.await(10, TimeUnit.SECONDS)) {
                            throw new SQLException("Timeout del test");
                        }
                    } catch (InterruptedException errore) {
                        Thread.currentThread().interrupt();
                        throw new SQLException(errore);
                    }
                    dipendente.setId(17);
                    salvati.set(List.of(dipendente));
                }
            };
            AtomicReference<FXMLLoader> elenco = new AtomicReference<>();
            AtomicReference<Stage> modulo = new AtomicReference<>();
            CompletableFuture<Void> aggiornato = new CompletableFuture<>();
            CompletableFuture<Void> primaLettura = suJavaFx(() -> {
                FXMLLoader loader = caricaElenco(new DipendenteRepository() {
                    @Override
                    public List<Dipendente> trovaTutti() { return salvati.get(); }
                }, service);
                elenco.set(loader);
                return fineCaricamento(loader);
            });
            primaLettura.get(10, TimeUnit.SECONDS);
            try {
                suJavaFx(() -> {
                    // Attendiamo l'aggiornamento reale della lista, non solo il ritorno del service.
                    tabella(elenco.get()).getItems().addListener(
                            (javafx.collections.ListChangeListener<Dipendente>) change -> {
                                if (tabella(elenco.get()).getItems().size() == 1) {
                                    aggiornato.complete(null);
                                }
                            });
                    modulo.set(apriModulo(elenco.get()));
                    compilaModulo(modulo.get());
                    ((Button) modulo.get().getScene().lookup("#salvaButton")).fire();
                    return null;
                });
                assertTrue(entrato.await(10, TimeUnit.SECONDS));
                suJavaFx(() -> {
                    // Durante la transazione né Annulla né la X devono chiudere il modulo.
                    Button annulla = (Button) modulo.get().getScene().lookup("#annullaButton");
                    assertTrue(annulla.isDisabled());
                    annulla.fire();
                    modulo.get().fireEvent(new WindowEvent(modulo.get(), WindowEvent.WINDOW_CLOSE_REQUEST));
                    assertTrue(modulo.get().isShowing());
                    return null;
                });
                prosegui.countDown();
                aggiornato.get(10, TimeUnit.SECONDS);
                suJavaFx(() -> {
                    assertEquals("Anna", tabella(elenco.get()).getItems().getFirst().getNome());
                    assertEquals("Dipendente registrato correttamente.",
                            ((Label) modulo.get().getScene().lookup("#messaggioLabel")).getText());
                    Button chiudi = (Button) modulo.get().getScene().lookup("#annullaButton");
                    assertEquals("Chiudi", chiudi.getText());
                    chiudi.fire();
                    assertFalse(modulo.get().isShowing());
                    return null;
                });
            } finally {
                prosegui.countDown();
                suJavaFx(() -> {
                    if (modulo.get() != null) { modulo.get().hide(); }
                    return null;
                });
            }
        }

        @SuppressWarnings("unchecked")
        private TableView<Dipendente> tabella(FXMLLoader loader) {
            return (TableView<Dipendente>) loader.getNamespace().get("dipendentiTable");
        }

        private Button aggiorna(FXMLLoader loader) {
            return (Button) loader.getNamespace().get("aggiornaButton");
        }

        private Label messaggioElenco(FXMLLoader loader) {
            return (Label) loader.getNamespace().get("messaggioLabel");
        }

        // Il controller riabilita Aggiorna quando ha finito di aggiornare l'interfaccia.
        // Aspettiamo quel segnale invece di introdurre pause arbitrarie nei test.
        private CompletableFuture<Void> fineCaricamento(FXMLLoader loader) {
            CompletableFuture<Void> fine = new CompletableFuture<>();
            if (!aggiorna(loader).isDisabled()) {
                fine.complete(null);
            } else {
                aggiorna(loader).disabledProperty().addListener((property, prima, dopo) -> {
                    if (!dopo) {
                        fine.complete(null);
                    }
                });
            }
            return fine;
        }

        @SuppressWarnings("unchecked")
        private String valoreColonna(FXMLLoader loader, String id, Dipendente dipendente) {
            TableColumn<Dipendente, String> colonna =
                    (TableColumn<Dipendente, String>) loader.getNamespace().get(id);
            return colonna.getCellObservableValue(dipendente).getValue();
        }

        @Test
        void caricaColonneEDatiESostituisceLeRigheQuandoAggiorna() throws Exception {
            Contratto contratto = new Contratto(1, TipoContratto.FULL_TIME, 40,
                    new BigDecimal("14.50"), LocalDate.of(2026, 1, 1));
            Dipendente conContratto = new Dipendente(1, "Anna", "Rossi", Ruolo.CAMERIERE, contratto);
            Dipendente senzaContratto = new Dipendente(2, "Luca", "Verdi", Ruolo.CUOCO, null);
            AtomicBoolean threadGrafico = new AtomicBoolean();
            DipendenteRepository repository = new DipendenteRepository() {
                @Override
                public List<Dipendente> trovaTutti() {
                    threadGrafico.set(Platform.isFxApplicationThread());
                    return List.of(conContratto, senzaContratto);
                }
            };
            AtomicReference<FXMLLoader> vista = new AtomicReference<>();
            CompletableFuture<Void> fine = suJavaFx(() -> {
                FXMLLoader loader = caricaElenco(repository);
                vista.set(loader);
                return fineCaricamento(loader);
            });
            fine.get(10, TimeUnit.SECONDS);
            CompletableFuture<Void> aggiornamento = suJavaFx(() -> {
                FXMLLoader loader = vista.get();
                assertEquals(List.of(conContratto, senzaContratto), tabella(loader).getItems());
                assertEquals("Anna", valoreColonna(loader, "nomeColumn", conContratto));
                assertEquals("Rossi", valoreColonna(loader, "cognomeColumn", conContratto));
                assertEquals("Cameriere", valoreColonna(loader, "ruoloColumn", conContratto));
                assertEquals("Tempo pieno", valoreColonna(loader, "tipoContrattoColumn", conContratto));
                assertEquals("40", valoreColonna(loader, "oreColumn", conContratto));
                assertEquals("14,50", valoreColonna(loader, "costoColumn", conContratto));
                assertEquals("Nessun contratto attuale",
                        valoreColonna(loader, "tipoContrattoColumn", senzaContratto));
                assertEquals("—", valoreColonna(loader, "oreColumn", senzaContratto));
                assertEquals("—", valoreColonna(loader, "costoColumn", senzaContratto));
                aggiorna(loader).fire();
                return fineCaricamento(loader);
            });
            aggiornamento.get(10, TimeUnit.SECONDS);
            assertFalse(threadGrafico.get(), "La lettura deve avvenire in background");
            suJavaFx(() -> {
                assertEquals(2, tabella(vista.get()).getItems().size(), "Nessun duplicato dopo Aggiorna");
                assertFalse(messaggioElenco(vista.get()).isVisible());
                return null;
            });
        }

        @Test
        void conservaElencoPrecedenteSeAggiornamentoFallisce() throws Exception {
            Dipendente dipendente = new Dipendente(1, "Anna", "Rossi", Ruolo.CAMERIERE, null);
            AtomicInteger chiamate = new AtomicInteger();
            DipendenteRepository repository = new DipendenteRepository() {
                @Override
                public List<Dipendente> trovaTutti() throws SQLException {
                    if (chiamate.incrementAndGet() == 1) {
                        return List.of(dipendente);
                    }
                    throw new SQLException("Dettagli SQL interni");
                }
            };
            AtomicReference<FXMLLoader> vista = new AtomicReference<>();
            CompletableFuture<Void> fine = suJavaFx(() -> {
                FXMLLoader loader = caricaElenco(repository);
                vista.set(loader);
                return fineCaricamento(loader);
            });
            fine.get(10, TimeUnit.SECONDS);
            CompletableFuture<Void> aggiornamento = suJavaFx(() -> {
                aggiorna(vista.get()).fire();
                return fineCaricamento(vista.get());
            });
            aggiornamento.get(10, TimeUnit.SECONDS);
            suJavaFx(() -> {
                assertEquals(List.of(dipendente), tabella(vista.get()).getItems());
                assertTrue(messaggioElenco(vista.get()).isVisible());
                assertTrue(messaggioElenco(vista.get()).getText().contains("Impossibile aggiornare"));
                assertFalse(messaggioElenco(vista.get()).getText().contains("Dettagli SQL interni"));
                assertFalse(aggiorna(vista.get()).isDisabled());
                return null;
            });
        }
    }

    @BeforeAll
    static void avviaJavaFx() throws Exception {
        // Il toolkit JavaFX si inizializza una volta per questa classe di test.
        // Un latch è un segnale: countDown lo libera, await aspetta che sia pronto.
        // Il limite di 10 secondi evita attese indefinite in caso di problemi.
        CountDownLatch pronto = new CountDownLatch(1);
        Platform.startup(() -> {
            // Alcune prove aprono e chiudono il modulo: la chiusura dell'ultima
            // finestra non deve fermare JavaFX prima delle prove successive.
            Platform.setImplicitExit(false);
            pronto.countDown();
        });
        assertTrue(pronto.await(10, TimeUnit.SECONDS));
    }

    @AfterAll
    static void terminaJavaFx() {
        // Rilasciamo il toolkit dopo l'ultima prova di questa classe.
        Platform.exit();
    }

    // Qualunque accesso ai controlli avviene sul thread grafico, come nell'applicazione.
    private static <T> T suJavaFx(Callable<T> azione) throws Exception {
        // FutureTask conserva risultato o eccezione dell'azione. runLater la accoda
        // sul thread grafico; get attende il risultato dal thread del test.
        FutureTask<T> operazione = new FutureTask<>(azione);
        Platform.runLater(operazione);
        return operazione.get(10, TimeUnit.SECONDS);
    }

    /** Carica il vero modulo, sostituendo soltanto il service usato dal controller. */
    private FXMLLoader carica(DipendenteService service) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource(
                "/it/disagili/turnogiusto/ui/dipendente-form.fxml"));
        // La factory fa usare il costruttore per i test invece di quello ordinario.
        // Verifichiamo comunque fx:controller, fx:id, initialize e onAction del vero FXML.
        loader.setControllerFactory(tipo -> new DipendenteFormController(service));
        loader.load();
        return loader;
    }

    private TextField campo(FXMLLoader loader, String id) {
        // Il namespace contiene i controlli caricati, indicizzati con i loro fx:id.
        return (TextField) loader.getNamespace().get(id);
    }

    private Button salva(FXMLLoader loader) {
        return (Button) loader.getNamespace().get("salvaButton");
    }

    private Label messaggio(FXMLLoader loader) {
        return (Label) loader.getNamespace().get("messaggioLabel");
    }

    // Il namespace restituisce Object: i cast alle ComboBox tipizzate sono verificati
    // qui sulla base dei tipi dichiarati nel nostro controller.
    @SuppressWarnings("unchecked")
    private void compila(FXMLLoader loader) {
        // Dati di partenza validi, riutilizzati dalle prove. Ogni prova può poi
        // cambiare il campo rilevante senza ripetere tutta la preparazione.
        campo(loader, "nomeField").setText("Anna");
        campo(loader, "cognomeField").setText("Rossi");
        campo(loader, "oreSettimanaliField").setText("20");
        campo(loader, "costoOrarioField").setText("12,50");
        ((ComboBox<Ruolo>) loader.getNamespace().get("ruoloCombo")).setValue(Ruolo.CAMERIERE);
        ((ComboBox<TipoContratto>) loader.getNamespace().get("tipoContrattoCombo"))
                .setValue(TipoContratto.PART_TIME);
        DatePicker inizio = (DatePicker) loader.getNamespace().get("dataInizioPicker");
        // Scriviamo nell'editor senza confermare: il controller deve leggere anche
        // una data digitata, non soltanto il valore selezionato nel calendario.
        inizio.getEditor().setText(inizio.getConverter().toString(LocalDate.of(2026, 11, 1)));
    }

    private CompletableFuture<String> attendiMessaggioFinale(FXMLLoader loader) {
        // La future sarà completata dal listener quando cambia il testo della Label.
        // Escludiamo il messaggio intermedio, perché attendiamo successo o errore.
        CompletableFuture<String> esito = new CompletableFuture<>();
        messaggio(loader).textProperty().addListener((observable, prima, dopo) -> {
            if (!"Salvataggio in corso…".equals(dopo)) {
                esito.complete(dopo);
            }
        });
        return esito;
    }

    @Test
    void caricaFxmlEPopolaComboSenzaSelezionareValori() throws Exception {
        // Controlla il collegamento reale FXML-controller e lo stato iniziale del modulo.
        suJavaFx(() -> {
            FXMLLoader loader = carica(new DipendenteService());
            ComboBox<?> ruoli = (ComboBox<?>) loader.getNamespace().get("ruoloCombo");
            ComboBox<?> tipi = (ComboBox<?>) loader.getNamespace().get("tipoContrattoCombo");
            assertEquals(List.of(Ruolo.values()), ruoli.getItems());
            assertEquals(List.of(TipoContratto.values()), tipi.getItems());
            assertNull(ruoli.getValue());
            assertNull(tipi.getValue());
            assertFalse(messaggio(loader).isVisible());
            return null;
        });
    }

    @Test
    void rifiutaOreNonNumericheSenzaChiamareIlService() throws Exception {
        // AtomicBoolean permette di osservare in modo sicuro se il service è stato chiamato.
        // Il service finto registra soltanto la chiamata: non accede al database.
        AtomicBoolean chiamato = new AtomicBoolean();
        DipendenteService service = new DipendenteService() {
            @Override
            public void registra(Dipendente dipendente) {
                chiamato.set(true);
            }
        };
        suJavaFx(() -> {
            FXMLLoader loader = carica(service);
            compila(loader);
            campo(loader, "oreSettimanaliField").setText("venti");
            // fire simula l'azione del pulsante e attraversa il collegamento onAction.
            salva(loader).fire();
            assertTrue(messaggio(loader).getText().contains("numero intero"));
            assertEquals("venti", campo(loader, "oreSettimanaliField").getText());
            assertEquals("Anna", campo(loader, "nomeField").getText());
            assertFalse(salva(loader).isDisabled());
            assertFalse(chiamato.get());
            return null;
        });
    }

    @Test
    void leggeIlTestoDellaDataERifiutaUnaDataNonValida() throws Exception {
        // La data non interpretabile deve fermare l'operazione prima del service,
        // mostrando un errore senza sostituire il testo digitato dall'utente.
        suJavaFx(() -> {
            FXMLLoader loader = carica(new DipendenteService());
            compila(loader);
            DatePicker fine = (DatePicker) loader.getNamespace().get("dataFinePicker");
            fine.getEditor().setText("non-data");
            salva(loader).fire();
            assertTrue(messaggio(loader).getText().contains("data di fine non è valida"));
            assertEquals("non-data", fine.getEditor().getText());
            assertFalse(salva(loader).isDisabled());
            return null;
        });
    }

    @Test
    void mostraSuccessoSoloDopoIlServiceEBloccaUnSecondoInvio() throws Exception {
        // Due segnali controllano l'ordine: entrato conferma che il service è partito;
        // termina lo tiene sospeso finché il test non ha verificato lo stato di attesa.
        CountDownLatch entrato = new CountDownLatch(1);
        CountDownLatch termina = new CountDownLatch(1);
        // AtomicReference permette di passare tra thread l'oggetto ricevuto dal service.
        AtomicReference<Dipendente> ricevuto = new AtomicReference<>();
        AtomicBoolean threadGrafico = new AtomicBoolean();
        DipendenteService service = new DipendenteService() {
            @Override
            public void registra(Dipendente dipendente) throws SQLException {
                threadGrafico.set(Platform.isFxApplicationThread());
                ricevuto.set(dipendente);
                entrato.countDown();
                try {
                    if (!termina.await(10, TimeUnit.SECONDS)) {
                        throw new SQLException("Timeout del test");
                    }
                } catch (InterruptedException errore) {
                    Thread.currentThread().interrupt();
                    throw new SQLException(errore);
                }
            }
        };
        AtomicReference<FXMLLoader> vista = new AtomicReference<>();
        CompletableFuture<String> esito = suJavaFx(() -> {
            FXMLLoader loader = carica(service);
            vista.set(loader);
            compila(loader);
            CompletableFuture<String> risultato = attendiMessaggioFinale(loader);
            salva(loader).fire();
            return risultato;
        });
        try {
            assertTrue(entrato.await(10, TimeUnit.SECONDS));
            suJavaFx(() -> {
                assertTrue(salva(vista.get()).isDisabled());
                assertEquals("Salvataggio in corso…", messaggio(vista.get()).getText());
                return null;
            });
        } finally {
            // Liberiamo il service anche se un'asserzione fallisce, evitando lavoro sospeso.
            termina.countDown();
        }
        assertEquals("Dipendente registrato correttamente.", esito.get(10, TimeUnit.SECONDS));
        assertFalse(threadGrafico.get());
        assertEquals(new BigDecimal("12.50"), ricevuto.get().getContratto().getCostoOrario());
        assertEquals(LocalDate.of(2026, 11, 1), ricevuto.get().getContratto().getDataInizio());
        assertNull(ricevuto.get().getContratto().getDataFine());
        suJavaFx(() -> {
            assertTrue(salva(vista.get()).isDisabled());
            return null;
        });
    }

    @Test
    void conservaCampiERiabilitaSalvaDopoErroreSqlOValidazione() throws Exception {
        // Ripetiamo lo scenario con due errori distinti: SQL e dati non validi.
        // Il primo deve mostrare un testo comprensibile, il secondo il messaggio di validazione.
        for (boolean erroreSql : new boolean[]{true, false}) {
            DipendenteService service = new DipendenteService() {
                @Override
                public void registra(Dipendente dipendente) throws SQLException {
                    if (erroreSql) {
                        throw new SQLException("Dettaglio tecnico riservato");
                    }
                    throw new IllegalArgumentException("Il ruolo è obbligatorio");
                }
            };
            AtomicReference<FXMLLoader> vista = new AtomicReference<>();
            CompletableFuture<String> esito = suJavaFx(() -> {
                FXMLLoader loader = carica(service);
                vista.set(loader);
                compila(loader);
                CompletableFuture<String> risultato = attendiMessaggioFinale(loader);
                salva(loader).fire();
                return risultato;
            });
            String testo = esito.get(10, TimeUnit.SECONDS);
            assertTrue(erroreSql ? testo.contains("Verifica la connessione")
                    : testo.equals("Il ruolo è obbligatorio"));
            assertFalse(testo.contains("Dettaglio tecnico riservato"));
            suJavaFx(() -> {
                assertEquals("Anna", campo(vista.get(), "nomeField").getText());
                assertEquals("12,50", campo(vista.get(), "costoOrarioField").getText());
                assertFalse(salva(vista.get()).isDisabled());
                assertFalse(campo(vista.get(), "nomeField").isDisabled());
                assertTrue(messaggio(vista.get()).isVisible());
                return null;
            });
        }
    }
}
