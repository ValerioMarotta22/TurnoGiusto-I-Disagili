// TEST GRAFICO ARCHIVIATO: non fa parte dei test eseguibili finche la UI e sospesa.
// Ripristinare in src/test/java insieme alla UI; iniettare apriConnessione nei service/repository.
// package it.disagili.turnogiusto.ui;
//
// import it.disagili.turnogiusto.model.Dipendente;
// import it.disagili.turnogiusto.model.Disponibilita;
// import it.disagili.turnogiusto.repository.DisponibilitaRepository;
// import it.disagili.turnogiusto.service.DisponibilitaService;
// import it.disagili.turnogiusto.service.RiepilogoDisponibilita;
// import it.disagili.turnogiusto.support.DatabaseDisponibilitaSupport;
// import javafx.application.Platform;
// import javafx.fxml.FXMLLoader;
// import javafx.scene.Parent;
// import javafx.scene.Scene;
// import javafx.scene.control.Button;
// import javafx.scene.control.ComboBox;
// import javafx.scene.control.Label;
// import javafx.scene.control.ListView;
// import javafx.scene.control.TableView;
// import javafx.scene.control.TextField;
// import javafx.scene.image.WritableImage;
// import org.junit.jupiter.api.AfterAll;
// import org.junit.jupiter.api.BeforeAll;
// import org.junit.jupiter.api.Test;
// import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
//
// import javax.imageio.ImageIO;
// import java.awt.image.BufferedImage;
// import java.nio.file.Files;
// import java.nio.file.Path;
// import java.sql.SQLException;
// import java.time.DayOfWeek;
// import java.time.LocalTime;
// import java.util.List;
// import java.util.concurrent.Callable;
// import java.util.concurrent.CompletableFuture;
// import java.util.concurrent.TimeUnit;
//
// import static org.junit.jupiter.api.Assertions.*;
//
// /** Prove dei veri FXML e pulsanti JavaFX, senza aprire finestre sul desktop. */
// @EnabledIfSystemProperty(named = "disponibilita.test.ui", matches = "true")
// // FXMLLoader espone un namespace di Object: i cast corrispondono ai tipi dei nostri FXML.
// @SuppressWarnings("unchecked")
// class DisponibilitaSchermateTest extends DatabaseDisponibilitaSupport {
//
//     private static boolean javaFxAvviato;
//
//     @BeforeAll
//     static void avviaJavaFx() throws Exception {
//         CompletableFuture<Void> pronto = new CompletableFuture<>();
//         Platform.startup(() -> {
//             Platform.setImplicitExit(false);
//             pronto.complete(null);
//         });
//         pronto.get(10, TimeUnit.SECONDS);
//         javaFxAvviato = true;
//     }
//
//     @AfterAll
//     static void chiudiJavaFx() {
//         // @AfterAll puo essere richiamato anche se il controllo del DB ha saltato la classe.
//         if (javaFxAvviato) Platform.exit();
//     }
//
//     // Tutte le azioni sui controlli avvengono sul thread grafico.
//     private static <T> T fx(Callable<T> azione) throws Exception {
//         CompletableFuture<T> risultato = new CompletableFuture<>();
//         Platform.runLater(() -> {
//             try { risultato.complete(azione.call()); }
//             catch (Throwable errore) { risultato.completeExceptionally(errore); }
//         });
//         return risultato.get(10, TimeUnit.SECONDS);
//     }
//
//     private FXMLLoader carica(String nome) throws Exception {
//         return fx(() -> {
//             FXMLLoader loader = new FXMLLoader(getClass().getResource(
//                     "/it/disagili/turnogiusto/ui/" + nome + "-disponibilita-view.fxml"));
//             Parent vista = loader.load();
//             new Scene(vista, 1080, 720);
//             vista.applyCss();
//             vista.layout();
//             return loader;
//         });
//     }
//
//     private void attendi(FXMLLoader loader) throws Exception {
//         long scadenza = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
//         while (fx(() -> ((Parent) loader.getRoot()).isDisabled())) {
//             if (System.nanoTime() > scadenza) fail("Operazione JavaFX non terminata");
//             Thread.sleep(20);
//         }
//     }
//
//     private void premi(FXMLLoader loader, String id) throws Exception {
//         fx(() -> { ((Button) loader.getNamespace().get(id)).fire(); return null; });
//     }
//
//     private void compila(FXMLLoader loader, DayOfWeek giorno, String inizio, String fine) throws Exception {
//         fx(() -> {
//             ComboBox<DayOfWeek> giorni = (ComboBox<DayOfWeek>) loader.getNamespace().get("giornoCombo");
//             giorni.setValue(giorno);
//             ((TextField) loader.getNamespace().get("oraInizioField")).setText(inizio);
//             ((TextField) loader.getNamespace().get("oraFineField")).setText(fine);
//             return null;
//         });
//     }
//
//     private ListView<Disponibilita> elenco(FXMLLoader loader) {
//         return (ListView<Disponibilita>) loader.getNamespace().get("elencoFinestre");
//     }
//
//     private String messaggio(FXMLLoader loader) throws Exception {
//         return fx(() -> ((Label) loader.getNamespace().get("messaggio")).getText());
//     }
//
//     private FXMLLoader gestioneGiulia() throws Exception {
//         FXMLLoader loader = carica("gestione");
//         GestioneDisponibilitaController controller = loader.getController();
//         fx(() -> { controller.caricaDipendenti(); return null; });
//         attendi(loader);
//         fx(() -> {
//             ComboBox<Dipendente> scelta = (ComboBox<Dipendente>) loader.getNamespace().get("dipendenteCombo");
//             assertEquals(3, scelta.getItems().size());
//             scelta.getSelectionModel().selectFirst();
//             return null;
//         });
//         attendi(loader);
//         return loader;
//     }
//
//     @Test
//     void inserisceModificaSalvaERimuoveDallaSchermata() throws Exception {
//         FXMLLoader loader = gestioneGiulia();
//         compila(loader, DayOfWeek.SATURDAY, "18:00", "02:00");
//         premi(loader, "aggiungiButton");
//         // La prima modifica e ancora solo in memoria.
//         assertTrue(new DisponibilitaRepository().trovaPerDipendente(giulia).isEmpty());
//         premi(loader, "salvaButton");
//         attendi(loader);
//         assertTrue(messaggio(loader).contains("salvate"));
//         assertTrue(new DisponibilitaRepository().trovaPerDipendente(giulia).get(0).attraversaMezzanotte());
//
//         fx(() -> { elenco(loader).getSelectionModel().selectFirst(); return null; });
//         compila(loader, DayOfWeek.FRIDAY, "19:00", "23:00");
//         premi(loader, "modificaButton");
//         premi(loader, "salvaButton");
//         attendi(loader);
//         List<Disponibilita> salvate = new DisponibilitaRepository().trovaPerDipendente(giulia);
//         assertEquals(1, salvate.size());
//         assertEquals(LocalTime.of(19, 0), salvate.get(0).getOraInizio());
//         salvaImmagine(loader, "gestione-disponibilita.png");
//
//         fx(() -> { elenco(loader).getSelectionModel().selectFirst(); return null; });
//         premi(loader, "rimuoviButton");
//         premi(loader, "salvaButton");
//         attendi(loader);
//         assertTrue(new DisponibilitaRepository().trovaPerDipendente(giulia).isEmpty());
//         assertFalse(fx(() -> ((GestioneDisponibilitaController) loader.getController()).haModifiche()));
//     }
//
//     @Test
//     void segnalaOrariErratiEDuplicatiESalvataggioDiBozzaNonApplicata() throws Exception {
//         FXMLLoader loader = gestioneGiulia();
//         compila(loader, DayOfWeek.MONDAY, "24:00", "23:00");
//         premi(loader, "aggiungiButton");
//         assertTrue(messaggio(loader).contains("orario valido"));
//         assertEquals(0, fx(() -> elenco(loader).getItems().size()));
//
//         compila(loader, DayOfWeek.MONDAY, "18:00", "23:00");
//         premi(loader, "salvaButton");
//         assertTrue(messaggio(loader).contains("prima di salvare"));
//         premi(loader, "aggiungiButton");
//         compila(loader, DayOfWeek.MONDAY, "18:00", "23:00");
//         premi(loader, "aggiungiButton");
//         assertTrue(messaggio(loader).contains("già presente"));
//         assertEquals(1, fx(() -> elenco(loader).getItems().size()));
//     }
//
//     @Test
//     void erroreDiSalvataggioMantieneLeModificheInMemoria() throws Exception {
//         FXMLLoader loader = gestioneGiulia();
//         compila(loader, DayOfWeek.MONDAY, "18:00", "23:00");
//         premi(loader, "aggiungiButton");
//         esegui("UPDATE contratto SET tipo = 'FULL_TIME' WHERE dipendente_id = " + giulia);
//         premi(loader, "salvaButton");
//         attendi(loader);
//         assertTrue(messaggio(loader).contains("non completata"));
//         assertTrue(fx(() -> ((GestioneDisponibilitaController) loader.getController()).haModifiche()));
//         assertEquals(1, fx(() -> elenco(loader).getItems().size()));
//         assertTrue(new DisponibilitaRepository().trovaPerDipendente(giulia).isEmpty());
//     }
//
//     @Test
//     void consultazioneVisualizzaPaoloEVieneAggiornataDopoIlSalvataggio() throws Exception {
//         FXMLLoader gestione = gestioneGiulia();
//         FXMLLoader consultazione = carica("consultazione");
//         ConsultazioneDisponibilitaController consultazioneController = consultazione.getController();
//         fx(() -> {
//             ((GestioneDisponibilitaController) gestione.getController()).configura(
//                     new DisponibilitaService(), consultazioneController::aggiorna);
//             consultazioneController.aggiorna();
//             return null;
//         });
//         attendi(consultazione);
//         fx(() -> {
//             TableView<RiepilogoDisponibilita> tabella =
//                     (TableView<RiepilogoDisponibilita>) consultazione.getNamespace().get("tabella");
//             assertEquals(3, tabella.getItems().size());
//             assertEquals("senza disponibilità inserite", tabella.getItems().get(1).getTestoDisponibilita());
//             return null;
//         });
//
//         compila(gestione, DayOfWeek.SATURDAY, "18:00", "02:00");
//         premi(gestione, "aggiungiButton");
//         compila(gestione, DayOfWeek.SATURDAY, "12:00", "15:00");
//         premi(gestione, "aggiungiButton");
//         premi(gestione, "salvaButton");
//         attendi(gestione);
//         attendi(consultazione);
//         fx(() -> {
//             TableView<RiepilogoDisponibilita> tabella =
//                     (TableView<RiepilogoDisponibilita>) consultazione.getNamespace().get("tabella");
//             assertEquals(2, tabella.getItems().get(0).getFinestre().size());
//             assertTrue(tabella.getItems().get(0).getTestoDisponibilita().contains("giorno dopo"));
//             return null;
//         });
//         salvaImmagine(consultazione, "consultazione-disponibilita.png");
//     }
//
//     @Test
//     void erroreDiCaricamentoNonDiventaSenzaDisponibilita() throws Exception {
//         FXMLLoader loader = carica("consultazione");
//         fx(() -> {
//             ConsultazioneDisponibilitaController controller = loader.getController();
//             controller.configura(new DisponibilitaService() {
//                 @Override public List<RiepilogoDisponibilita> consulta() throws SQLException {
//                     throw new SQLException("Connessione di prova interrotta");
//                 }
//             });
//             controller.aggiorna();
//             return null;
//         });
//         attendi(loader);
//         assertTrue(messaggio(loader).contains("Caricamento non riuscito"));
//         fx(() -> {
//             TableView<?> tabella = (TableView<?>) loader.getNamespace().get("tabella");
//             assertTrue(tabella.getItems().isEmpty());
//             assertTrue(((Label) tabella.getPlaceholder()).getText().contains("Dati non disponibili"));
//             return null;
//         });
//     }
//
//     private void salvaImmagine(FXMLLoader loader, String nome) throws Exception {
//         fx(() -> {
//             Parent vista = loader.getRoot();
//             vista.applyCss();
//             vista.layout();
//             WritableImage immagine = vista.snapshot(null, null);
//             BufferedImage png = new BufferedImage((int) immagine.getWidth(), (int) immagine.getHeight(),
//                     BufferedImage.TYPE_INT_ARGB);
//             for (int y = 0; y < png.getHeight(); y++) {
//                 for (int x = 0; x < png.getWidth(); x++) {
//                     png.setRGB(x, y, immagine.getPixelReader().getArgb(x, y));
//                 }
//             }
//             Path cartella = Path.of("target", "verifica-disponibilita");
//             Files.createDirectories(cartella);
//             ImageIO.write(png, "png", cartella.resolve(nome).toFile());
//             return null;
//         });
//     }
// }
//
