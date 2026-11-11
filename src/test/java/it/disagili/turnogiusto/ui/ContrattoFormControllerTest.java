package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.Contratto;
import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.TipoContratto;
import it.disagili.turnogiusto.service.ContrattoService;
import it.disagili.turnogiusto.service.VerificaTurniPianificati.Stato;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.math.BigDecimal;
import java.net.URL;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static it.disagili.turnogiusto.ui.JavaFxTestSupport.suJavaFx;
import static org.junit.jupiter.api.Assertions.*;

/**
 * US02: verifica il vero FXML e la precompilazione del modulo di modifica.
 * Gli oggetti con ID sono dati di prova in memoria: non apriamo connessioni MySQL.
 * Per Salva e Annulla usiamo service controllati: verifichiamo le azioni della UI,
 * non una vera transazione MySQL o una ricerca nella pianificazione reale.
 * L'estensione condivide l'avvio di JavaFX con US01 senza modificare le sue verifiche.
 */
@ExtendWith(JavaFxTestSupport.class)
class ContrattoFormControllerTest {

    /** Da chiamare sul thread JavaFX: carica anche il controller e initialize(). */
    private FXMLLoader caricaModulo() throws Exception {
        return caricaModulo(new ContrattoService());
    }

    /** Il FXML resta reale; sostituiamo soltanto il service del controller nei test. */
    private FXMLLoader caricaModulo(ContrattoService service) throws Exception {
        URL risorsa = getClass().getResource("/it/disagili/turnogiusto/ui/contratto-form.fxml");
        assertNotNull(risorsa, "Il FXML US02 deve essere presente nelle risorse");
        FXMLLoader loader = new FXMLLoader(risorsa);
        loader.setControllerFactory(tipo -> new ContrattoFormController(service));
        loader.load();
        return loader;
    }

    private TextField campo(FXMLLoader loader, String id) {
        return (TextField) loader.getNamespace().get(id);
    }

    private Button salva(FXMLLoader loader) {
        return (Button) loader.getNamespace().get("salvaButton");
    }

    /** Ascolta il risultato finale senza pause arbitrarie o accessi alla UI da altri thread. */
    private CompletableFuture<String> attendiEsito(FXMLLoader loader) {
        CompletableFuture<String> esito = new CompletableFuture<>();
        ((Label) loader.getNamespace().get("messaggioLabel")).textProperty()
                .addListener((proprieta, prima, dopo) -> {
                    if (!"Salvataggio in corso…".equals(dopo)) {
                        esito.complete(dopo);
                    }
                });
        return esito;
    }

    /** Simula il risultato del service senza alterare la proposta o accedere al DB. */
    private ContrattoService.Esito successo(Contratto dati, boolean oreRidotte, Stato stato) {
        Contratto salvato = new Contratto(13, dati.getDipendenteId(), dati.getTipoContratto(),
                dati.getOreSettimanali(), dati.getCostoOrario(), dati.getDataInizio(), dati.getDataFine());
        return new ContrattoService.Esito(salvato, oreRidotte, stato, false);
    }

    /** Costruisce un dipendente registrato con contratto valido, senza usare il DB. */
    private Dipendente dipendenteDiProva(LocalDate oggi, LocalDate fine) {
        Contratto contratto = new Contratto(12, 7, TipoContratto.PART_TIME, 20,
                new BigDecimal("12.50"), oggi.minusMonths(1), fine);
        return new Dipendente(7, "Anna", "Rossi", Ruolo.CAMERIERE, contratto);
    }

    @Test
    void caricaFxmlEPrecompilaTuttiICampiDelContratto() throws Exception {
        suJavaFx(() -> {
            LocalDate oggi = LocalDate.now();
            LocalDate fine = oggi.plusMonths(6);
            FXMLLoader loader = caricaModulo();
            ContrattoFormController controller = loader.getController();

            controller.impostaDipendente(dipendenteDiProva(oggi, fine));

            // Il namespace contiene i controlli indicizzati con gli fx:id del FXML.
            assertEquals("Anna Rossi", ((Label) loader.getNamespace().get("dipendenteLabel")).getText());
            ComboBox<?> tipi = (ComboBox<?>) loader.getNamespace().get("tipoContrattoCombo");
            assertEquals(List.of(TipoContratto.values()), tipi.getItems());
            assertEquals(TipoContratto.PART_TIME, tipi.getValue());
            assertEquals("20", ((TextField) loader.getNamespace().get("oreSettimanaliField")).getText());
            assertEquals("12,50", ((TextField) loader.getNamespace().get("costoOrarioField")).getText());
            // La decorrenza è oggi, non la data iniziale del contratto precedente.
            assertEquals(oggi.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                    ((Label) loader.getNamespace().get("decorrenzaLabel")).getText());
            assertEquals(fine, ((DatePicker) loader.getNamespace().get("dataFinePicker")).getValue());

            Label messaggio = (Label) loader.getNamespace().get("messaggioLabel");
            assertFalse(messaggio.isVisible());
            assertFalse(messaggio.isManaged());
            // Verifichiamo la presenza dei pulsanti; non simuliamo azioni ancora assenti.
            assertInstanceOf(Button.class, loader.getNamespace().get("salvaButton"));
            assertInstanceOf(Button.class, loader.getNamespace().get("annullaButton"));
            return null;
        });
    }

    @Test
    void lasciaVuotaLaScadenzaPerUnContrattoSenzaFine() throws Exception {
        suJavaFx(() -> {
            LocalDate oggi = LocalDate.now();
            FXMLLoader loader = caricaModulo();
            ContrattoFormController controller = loader.getController();
            DatePicker fine = (DatePicker) loader.getNamespace().get("dataFinePicker");
            // Prima carichiamo una scadenza, poi un contratto senza: un valore
            // precedente non deve rimanere nel controllo quando il nuovo è null.
            controller.impostaDipendente(dipendenteDiProva(oggi, oggi.plusMonths(6)));
            assertNotNull(fine.getValue());

            controller.impostaDipendente(dipendenteDiProva(oggi, null));

            assertNull(fine.getValue());
            assertTrue(fine.getEditor().getText().isEmpty());
            return null;
        });
    }

    @Test
    @SuppressWarnings("unchecked")
    void modificareICampiNonAlteraIlContrattoOriginale() throws Exception {
        suJavaFx(() -> {
            LocalDate oggi = LocalDate.now();
            LocalDate scadenza = oggi.plusMonths(6);
            Dipendente dipendente = dipendenteDiProva(oggi, scadenza);
            Contratto originale = dipendente.getContratto();
            FXMLLoader loader = caricaModulo();
            ContrattoFormController controller = loader.getController();
            controller.impostaDipendente(dipendente);

            // Cambiamo solo i controlli, come farebbe l'utente prima del salvataggio.
            // Il cast tipizzato corrisponde alla ComboBox dichiarata nel controller.
            ((ComboBox<TipoContratto>) loader.getNamespace().get("tipoContrattoCombo"))
                    .setValue(TipoContratto.FULL_TIME);
            ((TextField) loader.getNamespace().get("oreSettimanaliField")).setText("40");
            ((TextField) loader.getNamespace().get("costoOrarioField")).setText("15,00");
            ((DatePicker) loader.getNamespace().get("dataFinePicker")).setValue(null);

            // È un presupposto per Annulla, non ancora il test del suo pulsante.
            assertSame(originale, dipendente.getContratto());
            assertEquals(7, dipendente.getId());
            assertEquals(12, originale.getId());
            assertEquals(7, originale.getDipendenteId());
            assertEquals(TipoContratto.PART_TIME, originale.getTipoContratto());
            assertEquals(20, originale.getOreSettimanali());
            assertEquals(new BigDecimal("12.50"), originale.getCostoOrario());
            assertEquals(oggi.minusMonths(1), originale.getDataInizio());
            assertEquals(scadenza, originale.getDataFine());
            return null;
        });
    }

    @Test
    void rifiutaDipendentiOContrattiNonSelezionabili() throws Exception {
        suJavaFx(() -> {
            LocalDate oggi = LocalDate.now();
            Dipendente nuovo = dipendenteDiProva(oggi, null);
            nuovo.setId(0);
            Dipendente senzaContratto = dipendenteDiProva(oggi, null);
            senzaContratto.setContratto(null);
            Dipendente contrattoNuovo = dipendenteDiProva(oggi, null);
            contrattoNuovo.getContratto().setId(0);
            Dipendente scaduto = dipendenteDiProva(oggi, oggi.minusDays(1));
            Dipendente futuro = dipendenteDiProva(oggi, null);
            futuro.getContratto().setDataInizio(oggi.plusDays(1));

            // Controlliamo separatamente tutte le selezioni che il modulo rifiuta.
            // assertThrows è sul thread grafico, dove viene eseguito il controller.
            for (Dipendente invalido : new Dipendente[]{
                    null, nuovo, senzaContratto, contrattoNuovo, scaduto, futuro}) {
                FXMLLoader loader = caricaModulo();
                ContrattoFormController controller = loader.getController();
                assertThrows(IllegalArgumentException.class,
                        () -> controller.impostaDipendente(invalido));
                assertEquals("Dipendente da selezionare",
                        ((Label) loader.getNamespace().get("dipendenteLabel")).getText());
                assertTrue(((TextField) loader.getNamespace().get("oreSettimanaliField")).getText().isEmpty());
            }
            return null;
        });
    }

    @Test
    void annullaAncheConCampiErratiSenzaChiamareIlService() throws Exception {
        AtomicInteger salvataggi = new AtomicInteger();
        ContrattoService service = new ContrattoService() {
            @Override
            public Esito aggiorna(int id, int atteso, Contratto dati) {
                salvataggi.incrementAndGet();
                return successo(dati, false, Stato.NON_RICHIESTA);
            }
        };
        suJavaFx(() -> {
            FXMLLoader loader = caricaModulo(service);
            ContrattoFormController controller = loader.getController();
            Dipendente dipendente = dipendenteDiProva(LocalDate.now(), null);
            controller.impostaDipendente(dipendente);
            Button annulla = (Button) loader.getNamespace().get("annullaButton");
            assertTrue(annulla.isDisabled(), "Manca ancora l'azione di chiusura");
            AtomicInteger chiusure = new AtomicInteger();
            controller.setOnAnnulla(chiusure::incrementAndGet);
            campo(loader, "oreSettimanaliField").setText("non valido");

            annulla.fire();

            assertEquals(1, chiusure.get());
            assertEquals(0, salvataggi.get());
            assertEquals(20, dipendente.getContratto().getOreSettimanali());
            controller.setOnAnnulla(null);
            assertTrue(annulla.isDisabled());
            return null;
        });
    }

    @Test
    void rifiutaTestiNonConvertibiliSenzaInviareIlSalvataggio() throws Exception {
        AtomicInteger chiamate = new AtomicInteger();
        ContrattoService service = new ContrattoService() {
            @Override
            public Esito aggiorna(int id, int atteso, Contratto dati) {
                chiamate.incrementAndGet();
                return successo(dati, false, Stato.NON_RICHIESTA);
            }
        };
        suJavaFx(() -> {
            // Ore frazionarie, importo non numerico e data digitata errata sono
            // problemi di conversione della UI, prima delle validazioni del service.
            for (int caso = 0; caso < 3; caso++) {
                FXMLLoader loader = caricaModulo(service);
                ContrattoFormController controller = loader.getController();
                controller.impostaDipendente(dipendenteDiProva(LocalDate.now(), null));
                String testo;
                if (caso == 0) {
                    campo(loader, "oreSettimanaliField").setText("20,5");
                    testo = "Le ore settimanali devono essere un numero intero";
                } else if (caso == 1) {
                    campo(loader, "costoOrarioField").setText("dodici");
                    testo = "Inserisci un costo orario numerico, per esempio 12,50";
                } else {
                    ((DatePicker) loader.getNamespace().get("dataFinePicker"))
                            .getEditor().setText("non-data");
                    testo = "La data di fine non è valida. Selezionala dal calendario.";
                }
                salva(loader).fire();
                assertEquals(testo, ((Label) loader.getNamespace().get("messaggioLabel")).getText());
                assertFalse(salva(loader).isDisabled());
                assertEquals(0, chiamate.get());
                if (caso == 0) { assertEquals("20,5", campo(loader, "oreSettimanaliField").getText()); }
                if (caso == 1) { assertEquals("dodici", campo(loader, "costoOrarioField").getText()); }
                if (caso == 2) {
                    assertEquals("non-data", ((DatePicker) loader.getNamespace().get("dataFinePicker"))
                            .getEditor().getText());
                }
            }
            return null;
        });
    }

    @Test
    void attendeIlServiceBloccaDoppiInviiEComunicaIlSuccessoAllaFine() throws Exception {
        CountDownLatch entrato = new CountDownLatch(1);
        CountDownLatch prosegui = new CountDownLatch(1);
        AtomicInteger chiamate = new AtomicInteger();
        AtomicInteger aggiornamenti = new AtomicInteger();
        AtomicInteger chiusure = new AtomicInteger();
        AtomicBoolean threadGrafico = new AtomicBoolean();
        AtomicReference<Contratto> ricevuto = new AtomicReference<>();
        AtomicReference<FXMLLoader> modulo = new AtomicReference<>();
        LocalDate oggi = LocalDate.now();
        LocalDate fine = oggi.plusMonths(2);
        ContrattoService service = new ContrattoService() {
            @Override
            public Esito aggiorna(int id, int atteso, Contratto dati) throws SQLException {
                chiamate.incrementAndGet();
                threadGrafico.set(Platform.isFxApplicationThread());
                ricevuto.set(dati);
                assertEquals(7, id);
                assertEquals(12, atteso);
                entrato.countDown();
                try {
                    if (!prosegui.await(10, TimeUnit.SECONDS)) { throw new SQLException("Timeout della prova"); }
                } catch (InterruptedException errore) {
                    Thread.currentThread().interrupt();
                    throw new SQLException(errore);
                }
                return successo(dati, false, Stato.NON_RICHIESTA);
            }
        };
        CompletableFuture<String> esito = suJavaFx(() -> {
            FXMLLoader loader = caricaModulo(service);
            modulo.set(loader);
            ContrattoFormController controller = loader.getController();
            controller.impostaDipendente(dipendenteDiProva(oggi, null));
            controller.setOnAnnulla(chiusure::incrementAndGet);
            controller.setOnSalvato(aggiornamenti::incrementAndGet);
            campo(loader, "costoOrarioField").setText("15,75");
            DatePicker data = (DatePicker) loader.getNamespace().get("dataFinePicker");
            // Digitazione senza Invio: il controller deve leggere il testo dell'editor.
            data.getEditor().setText(data.getConverter().toString(fine));
            CompletableFuture<String> finito = attendiEsito(loader);
            salva(loader).fire();
            return finito;
        });
        try {
            assertTrue(entrato.await(10, TimeUnit.SECONDS));
            suJavaFx(() -> {
                FXMLLoader loader = modulo.get();
                ContrattoFormController controller = loader.getController();
                assertFalse(controller.puoChiudere(), "La futura finestra dovrà usare questa verifica per la X");
                assertTrue(salva(loader).isDisabled());
                assertTrue(campo(loader, "oreSettimanaliField").isDisabled());
                assertEquals("Salvataggio in corso…",
                        ((Label) loader.getNamespace().get("messaggioLabel")).getText());
                salva(loader).fire();
                ((Button) loader.getNamespace().get("annullaButton")).fire();
                assertEquals(0, aggiornamenti.get());
                assertEquals(0, chiusure.get());
                return null;
            });
        } finally {
            // Liberiamo il service anche su test fallito: non lasciamo il thread in attesa.
            prosegui.countDown();
        }
        assertEquals("Contratto aggiornato correttamente.", esito.get(10, TimeUnit.SECONDS));
        suJavaFx(() -> {
            FXMLLoader loader = modulo.get();
            ContrattoFormController controller = loader.getController();
            assertTrue(controller.puoChiudere());
            assertTrue(salva(loader).isDisabled(), "Dopo il successo non si può reinviare");
            salva(loader).fire();
            assertEquals(1, chiamate.get());
            assertEquals(1, aggiornamenti.get());
            Button chiudi = (Button) loader.getNamespace().get("annullaButton");
            assertEquals("Chiudi", chiudi.getText());
            chiudi.fire();
            assertEquals(1, chiusure.get());
            return null;
        });
        assertFalse(threadGrafico.get(), "Il service deve lavorare fuori dal thread grafico");
        assertEquals(0, ricevuto.get().getId());
        assertEquals(7, ricevuto.get().getDipendenteId());
        assertEquals(oggi, ricevuto.get().getDataInizio());
        assertEquals(fine, ricevuto.get().getDataFine());
        assertEquals(new BigDecimal("15.75"), ricevuto.get().getCostoOrario());
    }

    @Test
    void conservaICampiSuErroreDelServiceEPermetteDiRiprovare() throws Exception {
        // Due errori distinti: validazione del service e fallimento SQL.
        for (boolean erroreSql : new boolean[]{false, true}) {
            AtomicInteger chiamate = new AtomicInteger();
            AtomicInteger aggiornamenti = new AtomicInteger();
            AtomicReference<FXMLLoader> modulo = new AtomicReference<>();
            ContrattoService service = new ContrattoService() {
                @Override
                public Esito aggiorna(int id, int atteso, Contratto dati) throws SQLException {
                    if (chiamate.incrementAndGet() == 1) {
                        if (erroreSql) { throw new SQLException("Dettaglio SQL da non mostrare"); }
                        throw new IllegalArgumentException("Le ore settimanali non possono superare 40");
                    }
                    return successo(dati, false, Stato.NON_RICHIESTA);
                }
            };
            CompletableFuture<String> esito = suJavaFx(() -> {
                FXMLLoader loader = caricaModulo(service);
                modulo.set(loader);
                ContrattoFormController controller = loader.getController();
                controller.impostaDipendente(dipendenteDiProva(LocalDate.now(), null));
                controller.setOnSalvato(aggiornamenti::incrementAndGet);
                campo(loader, "oreSettimanaliField").setText(erroreSql ? "20" : "41");
                campo(loader, "costoOrarioField").setText("15,75");
                CompletableFuture<String> finito = attendiEsito(loader);
                salva(loader).fire();
                return finito;
            });
            String messaggio = esito.get(10, TimeUnit.SECONDS);
            assertTrue(erroreSql ? messaggio.contains("Verifica la connessione")
                    : messaggio.equals("Le ore settimanali non possono superare 40"));
            assertFalse(messaggio.contains("Dettaglio SQL"));
            CompletableFuture<String> riprova = suJavaFx(() -> {
                FXMLLoader loader = modulo.get();
                assertFalse(salva(loader).isDisabled());
                assertEquals(0, aggiornamenti.get());
                assertEquals(erroreSql ? "20" : "41", campo(loader, "oreSettimanaliField").getText());
                assertEquals("15,75", campo(loader, "costoOrarioField").getText());
                assertNull(((DatePicker) loader.getNamespace().get("dataFinePicker")).getValue());
                campo(loader, "oreSettimanaliField").setText("20");
                CompletableFuture<String> finito = attendiEsito(loader);
                salva(loader).fire();
                return finito;
            });
            assertEquals("Contratto aggiornato correttamente.", riprova.get(10, TimeUnit.SECONDS));
            suJavaFx(() -> {
                assertEquals(2, chiamate.get());
                assertEquals(1, aggiornamenti.get());
                return null;
            });
        }
    }

    @Test
    void distingueAvvisoSuiTurniVerificaNonDisponibileESuccessoSemplice() throws Exception {
        // Gli stati dei turni sono simulati. Il test verifica quale messaggio mostra
        // la UI, non il collegamento reale alla pianificazione ancora da implementare.
        for (Stato stato : new Stato[]{Stato.PRESENTI, Stato.ASSENTI, Stato.NON_DISPONIBILE, Stato.NON_RICHIESTA}) {
            boolean ridotte = stato != Stato.NON_RICHIESTA;
            ContrattoService service = new ContrattoService() {
                @Override
                public Esito aggiorna(int id, int atteso, Contratto dati) {
                    return successo(dati, ridotte, stato);
                }
            };
            CompletableFuture<String> esito = suJavaFx(() -> {
                FXMLLoader loader = caricaModulo(service);
                Dipendente dipendente = dipendenteDiProva(LocalDate.now(), null);
                if (ridotte) { dipendente.getContratto().setOreSettimanali(40); }
                ContrattoFormController controller = loader.getController();
                controller.impostaDipendente(dipendente);
                campo(loader, "oreSettimanaliField").setText("20");
                CompletableFuture<String> finito = attendiEsito(loader);
                salva(loader).fire();
                return finito;
            });
            String messaggio = esito.get(10, TimeUnit.SECONDS);
            if (stato == Stato.PRESENTI) {
                assertEquals("Contratto aggiornato. Il monte ore è diminuito: ricontrolla i turni già pianificati.", messaggio);
            } else if (stato == Stato.NON_DISPONIBILE) {
                assertEquals("Contratto aggiornato. Non è stato possibile verificare se ci sono turni da ricontrollare.", messaggio);
            } else {
                assertEquals("Contratto aggiornato correttamente.", messaggio);
            }
        }
    }
}
