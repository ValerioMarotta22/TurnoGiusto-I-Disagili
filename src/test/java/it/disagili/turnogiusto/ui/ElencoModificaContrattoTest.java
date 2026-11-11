package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.TipoContratto;
import it.disagili.turnogiusto.repository.DipendenteRepository;
import it.disagili.turnogiusto.service.ContrattoService;
import it.disagili.turnogiusto.service.DipendenteService;
import it.disagili.turnogiusto.service.VerificaTurniPianificati.Stato;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static it.disagili.turnogiusto.ui.JavaFxTestSupport.suJavaFx;
import static org.junit.jupiter.api.Assertions.*;

/**
 * US02: percorre i FXML e i controller reali dall'elenco alla modifica del contratto.
 * Repository e service sono simulati: questi test non scrivono su MySQL e non
 * attestano l'integrazione con la pianificazione. La persistenza ha test separati.
 * L'avvio di JavaFX è condiviso con US01, senza cambiare le sue verifiche.
 */
@ExtendWith(JavaFxTestSupport.class)
class ElencoModificaContrattoTest {

    private Dipendente dipendenteDiProva() {
        return dipendenteDiProva(7, 12);
    }

    /** Consente di creare persone distinte mantenendo il contratto collegato al relativo ID. */
    private Dipendente dipendenteDiProva(int dipendenteId, int contrattoId) {
        Contratto contratto = new Contratto(contrattoId, dipendenteId, TipoContratto.FULL_TIME, 40,
                new BigDecimal("12.50"), LocalDate.now().minusMonths(1), null);
        return new Dipendente(dipendenteId, "Anna", "Rossi", Ruolo.CAMERIERE, contratto);
    }

    /** Caricamento e attesa si svolgono su thread distinti: non blocchiamo JavaFX. */
    private FXMLLoader elencoPronto(DipendenteRepository repository, ContrattoService service)
            throws Exception {
        AtomicReference<FXMLLoader> riferimento = new AtomicReference<>();
        CompletableFuture<Void> fine = suJavaFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/it/disagili/turnogiusto/ui/elenco-dipendenti.fxml"));
            loader.setControllerFactory(tipo -> new ElencoDipendentiController(
                    repository, new DipendenteService(), service));
            loader.load();
            riferimento.set(loader);
            return prossimaFineCaricamento(loader);
        });
        fine.get(10, TimeUnit.SECONDS);
        return riferimento.get();
    }

    /** Segnale del controller a fine lettura: vale anche per un aggiornamento fallito. */
    private CompletableFuture<Void> prossimaFineCaricamento(FXMLLoader loader) {
        CompletableFuture<Void> fine = new CompletableFuture<>();
        ((Button) loader.getNamespace().get("aggiornaButton")).disabledProperty()
                .addListener((proprieta, prima, dopo) -> {
                    if (!dopo) { fine.complete(null); }
                });
        return fine;
    }

    @SuppressWarnings("unchecked")
    private TableView<Dipendente> tabella(FXMLLoader loader) {
        return (TableView<Dipendente>) loader.getNamespace().get("dipendentiTable");
    }

    private Button modifica(FXMLLoader loader) {
        return (Button) loader.getNamespace().get("modificaContrattoButton");
    }

    private List<Stage> moduliAperti() {
        // Copiamo la lista prima di chiudere finestre: Window.getWindows() è osservabile.
        return Window.getWindows().stream()
                .filter(f -> f instanceof Stage stage && stage.isShowing()
                        && "Modifica contratto".equals(stage.getTitle()))
                .map(f -> (Stage) f).toList();
    }

    private Stage apriModulo(FXMLLoader loader) {
        tabella(loader).getSelectionModel().selectFirst();
        modifica(loader).fire();
        List<Stage> aperti = moduliAperti();
        assertEquals(1, aperti.size(), "Il pulsante deve aprire esattamente un modulo US02");
        return aperti.getFirst();
    }

    private TextField campo(Stage modulo, String id) {
        return (TextField) modulo.getScene().lookup("#" + id);
    }

    private Button pulsante(Stage modulo, String id) {
        return (Button) modulo.getScene().lookup("#" + id);
    }

    /** Ogni prova chiude le finestre create anche se un'asserzione fallisce. */
    private void chiudiModuli() throws Exception {
        suJavaFx(() -> {
            moduliAperti().forEach(Stage::hide);
            return null;
        });
    }

    @Test
    void abilitaModificaSoloConUnaSelezioneEUnContrattoValido() throws Exception {
        Dipendente valido = dipendenteDiProva();
        Dipendente senzaContratto = new Dipendente(8, "Luca", "Verdi", Ruolo.CUOCO, null);
        // Ogni riga rappresenta una persona diversa, quindi deve avere un ID distinto.
        // Dipendente.equals confronta gli ID: riutilizzare il 7 farebbe risultare uguali
        // queste righe e il listener JavaFX della selezione potrebbe non rilevare il cambio.
        Dipendente scaduto = dipendenteDiProva(9, 13);
        scaduto.getContratto().setDataFine(LocalDate.now().minusDays(1));
        Dipendente futuro = dipendenteDiProva(10, 14);
        futuro.getContratto().setDataInizio(LocalDate.now().plusDays(1));
        DipendenteRepository repository = new DipendenteRepository() {
            @Override
            public List<Dipendente> trovaTutti() {
                return List.of(valido, senzaContratto, scaduto, futuro);
            }
        };
        FXMLLoader loader = elencoPronto(repository, new ContrattoService());
        suJavaFx(() -> {
            assertTrue(modifica(loader).isDisabled());
            modifica(loader).fire();
            assertTrue(moduliAperti().isEmpty());
            for (int indice = 1; indice <= 3; indice++) {
                tabella(loader).getSelectionModel().select(indice);
                assertTrue(modifica(loader).isDisabled());
            }
            tabella(loader).getSelectionModel().select(0);
            assertFalse(modifica(loader).isDisabled());
            tabella(loader).getSelectionModel().clearSelection();
            assertTrue(modifica(loader).isDisabled());
            return null;
        });
    }

    @Test
    void aprePrecompilatoUnSoloModuloEAnnullaSenzaSalvare() throws Exception {
        Dipendente originale = dipendenteDiProva();
        AtomicInteger letture = new AtomicInteger();
        AtomicInteger salvataggi = new AtomicInteger();
        DipendenteRepository repository = new DipendenteRepository() {
            @Override
            public List<Dipendente> trovaTutti() {
                letture.incrementAndGet();
                return List.of(originale);
            }
        };
        ContrattoService service = new ContrattoService() {
            @Override
            public Esito aggiorna(int id, int atteso, Contratto dati) throws SQLException {
                salvataggi.incrementAndGet();
                throw new SQLException("Annulla non deve chiamare il service");
            }
        };
        FXMLLoader loader = elencoPronto(repository, service);
        AtomicReference<Stage> proprietario = new AtomicReference<>();
        try {
            suJavaFx(() -> {
                Stage elenco = new Stage();
                proprietario.set(elenco);
                elenco.setScene(new Scene(loader.getRoot()));
                elenco.show();
                Stage modulo = apriModulo(loader);
                assertSame(elenco, modulo.getOwner());
                assertEquals(Modality.WINDOW_MODAL, modulo.getModality());
                assertEquals("Anna Rossi", ((Label) modulo.getScene().lookup("#dipendenteLabel")).getText());
                assertEquals("40", campo(modulo, "oreSettimanaliField").getText());
                assertEquals("12,50", campo(modulo, "costoOrarioField").getText());
                assertEquals(TipoContratto.FULL_TIME,
                        ((ComboBox<?>) modulo.getScene().lookup("#tipoContrattoCombo")).getValue());
                assertNull(((DatePicker) modulo.getScene().lookup("#dataFinePicker")).getValue());

                modifica(loader).fire();
                assertEquals(List.of(modulo), moduliAperti(), "Un secondo clic non deve duplicare la finestra");
                campo(modulo, "oreSettimanaliField").setText("non valido");
                pulsante(modulo, "annullaButton").fire();
                assertFalse(modulo.isShowing());
                assertEquals(0, salvataggi.get());
                assertEquals(1, letture.get(), "Annulla non richiede di rileggere i dati salvati");
                assertEquals(40, originale.getContratto().getOreSettimanali());

                // La chiusura libera il riferimento al modulo: si può riaprire normalmente.
                Stage riaperto = apriModulo(loader);
                assertNotSame(modulo, riaperto);
                assertEquals("40", campo(riaperto, "oreSettimanaliField").getText());
                pulsante(riaperto, "annullaButton").fire();
                return null;
            });
        } finally {
            chiudiModuli();
            suJavaFx(() -> {
                if (proprietario.get() != null) { proprietario.get().hide(); }
                return null;
            });
        }
    }

    @Test
    void salvaAggiornaElencoEBloccaAnnullaEXDuranteIlSalvataggio() throws Exception {
        Dipendente originale = dipendenteDiProva();
        AtomicReference<List<Dipendente>> datiSalvati = new AtomicReference<>(List.of(originale));
        AtomicInteger letture = new AtomicInteger();
        CountDownLatch entrato = new CountDownLatch(1);
        CountDownLatch prosegui = new CountDownLatch(1);
        DipendenteRepository repository = new DipendenteRepository() {
            @Override
            public List<Dipendente> trovaTutti() {
                letture.incrementAndGet();
                return datiSalvati.get();
            }
        };
        ContrattoService service = new ContrattoService() {
            @Override
            public Esito aggiorna(int id, int atteso, Contratto dati) throws SQLException {
                assertEquals(7, id);
                assertEquals(12, atteso);
                entrato.countDown();
                try {
                    if (!prosegui.await(10, TimeUnit.SECONDS)) { throw new SQLException("Timeout della prova"); }
                } catch (InterruptedException errore) {
                    Thread.currentThread().interrupt();
                    throw new SQLException(errore);
                }
                Contratto nuovo = new Contratto(13, id, dati.getTipoContratto(), dati.getOreSettimanali(),
                        dati.getCostoOrario(), dati.getDataInizio(), dati.getDataFine());
                datiSalvati.set(List.of(new Dipendente(id, "Anna", "Rossi", Ruolo.CAMERIERE, nuovo)));
                return new Esito(nuovo, true, Stato.ASSENTI, false);
            }
        };
        FXMLLoader loader = elencoPronto(repository, service);
        AtomicReference<Stage> modulo = new AtomicReference<>();
        try {
            CompletableFuture<Void> aggiornato = suJavaFx(() -> {
                modulo.set(apriModulo(loader));
                campo(modulo.get(), "oreSettimanaliField").setText("16");
                campo(modulo.get(), "costoOrarioField").setText("15,75");
                CompletableFuture<Void> fine = prossimaFineCaricamento(loader);
                pulsante(modulo.get(), "salvaButton").fire();
                return fine;
            });
            assertTrue(entrato.await(10, TimeUnit.SECONDS));
            suJavaFx(() -> {
                assertEquals(1, letture.get(), "L'elenco non deve aggiornarsi prima del successo");
                assertEquals(40, originale.getContratto().getOreSettimanali());
                assertTrue(pulsante(modulo.get(), "annullaButton").isDisabled());
                pulsante(modulo.get(), "annullaButton").fire();
                modulo.get().fireEvent(new WindowEvent(modulo.get(), WindowEvent.WINDOW_CLOSE_REQUEST));
                assertTrue(modulo.get().isShowing(), "La X non può interrompere il salvataggio");
                return null;
            });
            prosegui.countDown();
            aggiornato.get(10, TimeUnit.SECONDS);
            suJavaFx(() -> {
                assertEquals(2, letture.get());
                assertEquals(1, tabella(loader).getItems().size());
                Dipendente letto = tabella(loader).getItems().getFirst();
                assertEquals(13, letto.getContratto().getId());
                assertEquals(16, letto.getContratto().getOreSettimanali());
                // Verifichiamo anche i testi delle colonne, non soltanto il modello della riga.
                assertEquals("16", valoreColonna(loader, "oreColumn", letto));
                assertEquals("15,75", valoreColonna(loader, "costoColumn", letto));
                assertTrue(modulo.get().isShowing(), "La conferma deve rimanere leggibile");
                assertEquals("Contratto aggiornato correttamente.",
                        ((Label) modulo.get().getScene().lookup("#messaggioLabel")).getText());
                pulsante(modulo.get(), "annullaButton").fire();
                assertFalse(modulo.get().isShowing());

                Stage riaperto = apriModulo(loader);
                assertEquals("16", campo(riaperto, "oreSettimanaliField").getText());
                riaperto.fireEvent(new WindowEvent(riaperto, WindowEvent.WINDOW_CLOSE_REQUEST));
                assertFalse(riaperto.isShowing(), "La X torna utilizzabile fuori dal salvataggio");
                return null;
            });
        } finally {
            prosegui.countDown();
            chiudiModuli();
        }
    }

    @SuppressWarnings("unchecked")
    private String valoreColonna(FXMLLoader loader, String id, Dipendente dipendente) {
        TableColumn<Dipendente, String> colonna =
                (TableColumn<Dipendente, String>) loader.getNamespace().get(id);
        return colonna.getCellObservableValue(dipendente).getValue();
    }

    @Test
    void erroreDiSalvataggioConservaModuloEListaSenzaAggiornareElenco() throws Exception {
        Dipendente originale = dipendenteDiProva();
        AtomicInteger letture = new AtomicInteger();
        DipendenteRepository repository = new DipendenteRepository() {
            @Override
            public List<Dipendente> trovaTutti() {
                letture.incrementAndGet();
                return List.of(originale);
            }
        };
        ContrattoService service = new ContrattoService() {
            @Override
            public Esito aggiorna(int id, int atteso, Contratto dati) throws SQLException {
                throw new SQLException("Errore controllato di salvataggio");
            }
        };
        FXMLLoader loader = elencoPronto(repository, service);
        AtomicReference<Stage> modulo = new AtomicReference<>();
        try {
            CompletableFuture<String> esito = suJavaFx(() -> {
                modulo.set(apriModulo(loader));
                campo(modulo.get(), "oreSettimanaliField").setText("16");
                CompletableFuture<String> fine = new CompletableFuture<>();
                ((Label) modulo.get().getScene().lookup("#messaggioLabel")).textProperty()
                        .addListener((p, prima, dopo) -> {
                            if (!"Salvataggio in corso…".equals(dopo)) { fine.complete(dopo); }
                        });
                pulsante(modulo.get(), "salvaButton").fire();
                return fine;
            });
            assertTrue(esito.get(10, TimeUnit.SECONDS).contains("Impossibile salvare il contratto"));
            suJavaFx(() -> {
                assertTrue(modulo.get().isShowing());
                assertEquals("16", campo(modulo.get(), "oreSettimanaliField").getText());
                assertFalse(pulsante(modulo.get(), "salvaButton").isDisabled());
                assertEquals(1, letture.get());
                assertSame(originale, tabella(loader).getItems().getFirst());
                assertEquals(40, originale.getContratto().getOreSettimanali());
                pulsante(modulo.get(), "annullaButton").fire();
                assertFalse(modulo.get().isShowing());
                return null;
            });
        } finally {
            chiudiModuli();
        }
    }

    @Test
    void segnalaErroreDiRiletturaSenzaConfonderloConIlSalvataggioRiuscito() throws Exception {
        Dipendente originale = dipendenteDiProva();
        AtomicInteger letture = new AtomicInteger();
        DipendenteRepository repository = new DipendenteRepository() {
            @Override
            public List<Dipendente> trovaTutti() throws SQLException {
                if (letture.incrementAndGet() > 1) { throw new SQLException("Rilettura non riuscita"); }
                return List.of(originale);
            }
        };
        ContrattoService service = new ContrattoService() {
            @Override
            public Esito aggiorna(int id, int atteso, Contratto dati) {
                Contratto nuovo = new Contratto(13, id, dati.getTipoContratto(), dati.getOreSettimanali(),
                        dati.getCostoOrario(), dati.getDataInizio(), dati.getDataFine());
                return new Esito(nuovo, false, Stato.NON_RICHIESTA, false);
            }
        };
        FXMLLoader loader = elencoPronto(repository, service);
        AtomicReference<Stage> modulo = new AtomicReference<>();
        try {
            CompletableFuture<Void> fine = suJavaFx(() -> {
                modulo.set(apriModulo(loader));
                CompletableFuture<Void> aggiornato = prossimaFineCaricamento(loader);
                pulsante(modulo.get(), "salvaButton").fire();
                return aggiornato;
            });
            fine.get(10, TimeUnit.SECONDS);
            suJavaFx(() -> {
                assertEquals(2, letture.get());
                assertSame(originale, tabella(loader).getItems().getFirst());
                assertTrue(((Label) loader.getNamespace().get("messaggioLabel")).getText()
                        .contains("Impossibile aggiornare l'elenco"));
                assertEquals("Contratto aggiornato correttamente.",
                        ((Label) modulo.get().getScene().lookup("#messaggioLabel")).getText());
                assertTrue(pulsante(modulo.get(), "salvaButton").isDisabled());
                pulsante(modulo.get(), "annullaButton").fire();
                assertFalse(modulo.get().isShowing());
                return null;
            });
        } finally {
            chiudiModuli();
        }
    }
}
