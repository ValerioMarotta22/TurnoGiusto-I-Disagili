package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.*;
import it.disagili.turnogiusto.repository.DipendenteRepository;
import it.disagili.turnogiusto.service.*;
import it.disagili.turnogiusto.service.VerificaTurniPianificati.Stato;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import static it.disagili.turnogiusto.ui.JavaFxTestSupport.suJavaFx;
import static org.junit.jupiter.api.Assertions.*;

/** FXML, pulsante e dialogo reali; repository e service simulati, senza MySQL. */
@ExtendWith(JavaFxTestSupport.class)
class ElencoCessazioneContrattoTest {
    private final AtomicInteger letture = new AtomicInteger();
    private final AtomicInteger salvataggi = new AtomicInteger();
    private final Dipendente anna = new Dipendente(7, "Anna", "Rossi", Ruolo.CAMERIERE,
            new Contratto(12, 7, TipoContratto.FULL_TIME, 40, BigDecimal.TEN,
                    LocalDate.now().minusMonths(1), null));

    private FXMLLoader carica(ContrattoService service) throws Exception {
        var loader = suJavaFx(() -> {
            var fxml = new FXMLLoader(getClass().getResource("/it/disagili/turnogiusto/ui/elenco-dipendenti.fxml"));
            fxml.setControllerFactory(tipo -> new ElencoDipendentiController(new DipendenteRepository() {
                @Override public List<Dipendente> trovaTutti() {
                    letture.incrementAndGet();
                    return List.of(anna);
                }
            }, new DipendenteService(), service));
            fxml.load();
            return fxml;
        });
        attendi(() -> !bottone(loader, "aggiornaButton").isDisabled());
        return loader;
    }

    private Button bottone(FXMLLoader l, String id) { return (Button) l.getNamespace().get(id); }
    @SuppressWarnings("unchecked")
    private TableView<Dipendente> tabella(FXMLLoader l) {
        return (TableView<Dipendente>) l.getNamespace().get("dipendentiTable");
    }
    private String messaggio(FXMLLoader l) {
        return ((Label) l.getNamespace().get("messaggioLabel")).getText();
    }

    /** Aspetta eventi del Task senza bloccare il thread JavaFX. */
    private void attendi(BooleanSupplier condizione) throws Exception {
        long termine = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!suJavaFx(condizione::getAsBoolean)) {
            if (System.nanoTime() > termine) fail("Operazione JavaFX non completata");
            Thread.sleep(20);
        }
    }

    /** Il dialogo apre un ciclo eventi annidato: il runLater preme il vero pulsante. */
    private void premi(FXMLLoader l, ButtonType risposta) throws Exception {
        suJavaFx(() -> {
            tabella(l).getSelectionModel().selectFirst();
            Platform.runLater(() -> {
                for (Window finestra : List.copyOf(Window.getWindows())) {
                    if (finestra.getScene().getRoot() instanceof DialogPane dialogo) {
                        ((Button) dialogo.lookupButton(risposta)).fire();
                    }
                }
            });
            bottone(l, "cessaContrattoButton").fire();
            return null;
        });
    }

    private ContrattoService servizio(Stato stato) {
        return new ContrattoService() {
            @Override public EsitoCessazione cessa(int id, int contrattoId) {
                assertEquals(7, id);
                assertEquals(12, contrattoId);
                salvataggi.incrementAndGet();
                return new EsitoCessazione(12,
                        ContrattoService.calcolaFineCessazione(LocalDate.now(), null), stato);
            }
        };
    }

    @Test
    void richiedeSelezioneEContrattoAttivo() throws Exception {
        var l = carica(servizio(Stato.ASSENTI));
        suJavaFx(() -> {
            assertTrue(bottone(l, "cessaContrattoButton").isDisabled());
            tabella(l).getSelectionModel().selectFirst();
            assertFalse(bottone(l, "cessaContrattoButton").isDisabled());
            tabella(l).getSelectionModel().clearSelection();
            anna.setContratto(null);
            tabella(l).getSelectionModel().selectFirst();
            assertTrue(bottone(l, "cessaContrattoButton").isDisabled());
            return null;
        });
    }

    @Test
    void annullaNonSalvaENonRicarica() throws Exception {
        var l = carica(servizio(Stato.ASSENTI));
        premi(l, ButtonType.CANCEL);
        assertEquals(0, salvataggi.get());
        assertEquals(1, letture.get());
        assertNull(anna.getContratto().getDataFine());
    }

    @Test
    void confermaSalvaRicaricaEMostraGliAvvisiCorretti() throws Exception {
        for (Stato stato : List.of(Stato.PRESENTI, Stato.ASSENTI, Stato.NON_DISPONIBILE)) {
            int prima = letture.get();
            var l = carica(servizio(stato));
            premi(l, ButtonType.OK);
            attendi(() -> letture.get() >= prima + 2
                    && !bottone(l, "aggiornaButton").isDisabled());
            suJavaFx(() -> {
                assertTrue(messaggio(l).contains("Cessazione registrata"));
                assertEquals(stato == Stato.PRESENTI, messaggio(l).contains("turni successivi da ricontrollare"));
                assertEquals(stato == Stato.NON_DISPONIBILE, messaggio(l).contains("Verifica dei turni non disponibile"));
                assertEquals(1, tabella(l).getItems().size());
                return null;
            });
        }
        assertEquals(3, salvataggi.get());
    }

    @Test
    void erroreConservaLaRigaESpiegaIlBlocco() throws Exception {
        var l = carica(new ContrattoService() {
            @Override public EsitoCessazione cessa(int id, int contrattoId) {
                throw new IllegalArgumentException("Esiste un contratto futuro: risolvi prima il cambio programmato.");
            }
        });
        premi(l, ButtonType.OK);
        attendi(() -> !bottone(l, "aggiornaButton").isDisabled());
        suJavaFx(() -> {
            assertTrue(messaggio(l).contains("contratto futuro"));
            assertFalse(messaggio(l).contains("Cessazione registrata"));
            assertSame(anna, tabella(l).getSelectionModel().getSelectedItem());
            return null;
        });
        assertEquals(1, letture.get());
    }

    @Test
    void duranteSalvataggioBloccaDoppiClicENonAnticipaIlSuccesso() throws Exception {
        var entrato = new CountDownLatch(1);
        var sblocca = new CountDownLatch(1);
        var l = carica(new ContrattoService() {
            @Override public EsitoCessazione cessa(int id, int contrattoId) throws SQLException {
                salvataggi.incrementAndGet();
                entrato.countDown();
                try {
                    if (!sblocca.await(10, TimeUnit.SECONDS)) throw new SQLException("Attesa scaduta");
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new SQLException(e); }
                return new EsitoCessazione(12, LocalDate.now(), Stato.ASSENTI);
            }
        });
        try {
            premi(l, ButtonType.OK);
            assertTrue(entrato.await(10, TimeUnit.SECONDS));
            suJavaFx(() -> {
                assertTrue(bottone(l, "cessaContrattoButton").isDisabled());
                bottone(l, "cessaContrattoButton").fire();
                assertFalse(messaggio(l).contains("Cessazione registrata"));
                return null;
            });
            assertEquals(1, salvataggi.get());
        } finally { sblocca.countDown(); }
        attendi(() -> !bottone(l, "aggiornaButton").isDisabled());
    }
}
