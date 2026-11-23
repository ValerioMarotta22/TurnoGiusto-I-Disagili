package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.Dipendente;
import it.disagili.turnogiusto.model.Disponibilita;
import it.disagili.turnogiusto.repository.DisponibilitaRepository;
import it.disagili.turnogiusto.service.DisponibilitaService;
import it.disagili.turnogiusto.service.RiepilogoDisponibilita;
import it.disagili.turnogiusto.support.DatabaseDisponibilitaSupport;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static it.disagili.turnogiusto.ui.JavaFxTestSupport.suJavaFx;
import static org.junit.jupiter.api.Assertions.*;

/** Prove dei veri FXML e pulsanti JavaFX, senza aprire finestre sul desktop. */
@ExtendWith(JavaFxTestSupport.class)
// FXMLLoader espone un namespace di Object: i cast corrispondono ai tipi dei nostri FXML.
@SuppressWarnings("unchecked")
class DisponibilitaSchermateTest extends DatabaseDisponibilitaSupport {

    private FXMLLoader carica(String nome) throws Exception {
        return suJavaFx(() -> {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(
                    "/it/disagili/turnogiusto/ui/" + nome + "-disponibilita-view.fxml"));
            Parent vista = loader.load();
            new Scene(vista, 1080, 720);
            vista.applyCss();
            vista.layout();
            return loader;
        });
    }

    private void attendi(FXMLLoader loader) throws Exception {
        long scadenza = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (suJavaFx(() -> ((Parent) loader.getRoot()).isDisabled())) {
            if (System.nanoTime() > scadenza) fail("Operazione JavaFX non terminata");
            Thread.sleep(20);
        }
    }

    private void premi(FXMLLoader loader, String id) throws Exception {
        suJavaFx(() -> { ((Button) loader.getNamespace().get(id)).fire(); return null; });
    }

    private void compila(FXMLLoader loader, DayOfWeek giorno, String inizio, String fine) throws Exception {
        suJavaFx(() -> {
            ComboBox<DayOfWeek> giorni = (ComboBox<DayOfWeek>) loader.getNamespace().get("giornoCombo");
            giorni.setValue(giorno);
            ((TextField) loader.getNamespace().get("oraInizioField")).setText(inizio);
            ((TextField) loader.getNamespace().get("oraFineField")).setText(fine);
            return null;
        });
    }

    private ListView<Disponibilita> elenco(FXMLLoader loader) {
        return (ListView<Disponibilita>) loader.getNamespace().get("elencoFinestre");
    }

    private String messaggio(FXMLLoader loader) throws Exception {
        return suJavaFx(() -> ((Label) loader.getNamespace().get("messaggio")).getText());
    }

    private FXMLLoader gestioneGiulia() throws Exception {
        FXMLLoader loader = carica("gestione");
        GestioneDisponibilitaController controller = loader.getController();
        suJavaFx(() -> { controller.caricaDipendenti(); return null; });
        attendi(loader);
        suJavaFx(() -> {
            ComboBox<Dipendente> scelta = (ComboBox<Dipendente>) loader.getNamespace().get("dipendenteCombo");
            Dipendente dipendente = scelta.getItems().stream()
                    .filter(persona -> persona.getId() == giulia)
                    .findFirst().orElseThrow();
            scelta.getSelectionModel().select(dipendente);
            return null;
        });
        attendi(loader);
        return loader;
    }

    @Test
    void inserisceModificaSalvaERimuoveDallaSchermata() throws Exception {
        FXMLLoader loader = gestioneGiulia();
        compila(loader, DayOfWeek.SATURDAY, "18:00", "02:00");
        premi(loader, "aggiungiButton");
        // La prima modifica e ancora solo in memoria.
        assertTrue(new DisponibilitaRepository().trovaPerDipendente(giulia).isEmpty());
        premi(loader, "salvaButton");
        attendi(loader);
        assertTrue(messaggio(loader).contains("salvate"));
        assertTrue(new DisponibilitaRepository().trovaPerDipendente(giulia).get(0).attraversaMezzanotte());

        suJavaFx(() -> { elenco(loader).getSelectionModel().selectFirst(); return null; });
        compila(loader, DayOfWeek.FRIDAY, "19:00", "23:00");
        premi(loader, "modificaButton");
        premi(loader, "salvaButton");
        attendi(loader);
        List<Disponibilita> salvate = new DisponibilitaRepository().trovaPerDipendente(giulia);
        assertEquals(1, salvate.size());
        assertEquals(LocalTime.of(19, 0), salvate.get(0).getOraInizio());

        suJavaFx(() -> { elenco(loader).getSelectionModel().selectFirst(); return null; });
        premi(loader, "rimuoviButton");
        premi(loader, "salvaButton");
        attendi(loader);
        assertTrue(new DisponibilitaRepository().trovaPerDipendente(giulia).isEmpty());
        assertFalse(suJavaFx(() -> ((GestioneDisponibilitaController) loader.getController()).haModifiche()));
    }

    @Test
    void segnalaOrariErratiEDuplicatiESalvataggioDiBozzaNonApplicata() throws Exception {
        FXMLLoader loader = gestioneGiulia();
        compila(loader, DayOfWeek.MONDAY, "24:00", "23:00");
        premi(loader, "aggiungiButton");
        assertTrue(messaggio(loader).contains("orario valido"));
        assertEquals(0, suJavaFx(() -> elenco(loader).getItems().size()));

        compila(loader, DayOfWeek.MONDAY, "18:00", "23:00");
        premi(loader, "salvaButton");
        assertTrue(messaggio(loader).contains("prima di salvare"));
        premi(loader, "aggiungiButton");
        compila(loader, DayOfWeek.MONDAY, "18:00", "23:00");
        premi(loader, "aggiungiButton");
        assertTrue(messaggio(loader).contains("già presente"));
        assertEquals(1, suJavaFx(() -> elenco(loader).getItems().size()));
    }

    @Test
    void erroreDiSalvataggioMantieneLeModificheInMemoria() throws Exception {
        FXMLLoader loader = gestioneGiulia();
        compila(loader, DayOfWeek.MONDAY, "18:00", "23:00");
        premi(loader, "aggiungiButton");
        esegui("UPDATE contratto SET tipo = 'FULL_TIME' WHERE dipendente_id = " + giulia);
        premi(loader, "salvaButton");
        attendi(loader);
        assertTrue(messaggio(loader).contains("non completata"));
        assertTrue(suJavaFx(() -> ((GestioneDisponibilitaController) loader.getController()).haModifiche()));
        assertEquals(1, suJavaFx(() -> elenco(loader).getItems().size()));
        assertTrue(new DisponibilitaRepository().trovaPerDipendente(giulia).isEmpty());
    }

    @Test
    void consultazioneVisualizzaPaoloEVieneAggiornataDopoIlSalvataggio() throws Exception {
        FXMLLoader gestione = gestioneGiulia();
        FXMLLoader consultazione = carica("consultazione");
        ConsultazioneDisponibilitaController consultazioneController = consultazione.getController();
        List<Dipendente> dipendenti = partTimeDiProva();
        suJavaFx(() -> {
            ((GestioneDisponibilitaController) gestione.getController()).configura(
                    new DisponibilitaService(), consultazioneController::aggiorna);
            // Il database contiene anche dipendenti reali. Qui selezioniamo i soli dati del test.
            consultazioneController.mostraDipendenti(dipendenti);
            return null;
        });
        attendi(consultazione);
        suJavaFx(() -> {
            TableView<RiepilogoDisponibilita> tabella =
                    (TableView<RiepilogoDisponibilita>) consultazione.getNamespace().get("tabella");
            assertEquals(3, tabella.getItems().size());
            assertEquals("senza disponibilità inserite", tabella.getItems().get(1).getTestoDisponibilita());
            return null;
        });

        compila(gestione, DayOfWeek.SATURDAY, "18:00", "02:00");
        premi(gestione, "aggiungiButton");
        compila(gestione, DayOfWeek.SATURDAY, "12:00", "15:00");
        premi(gestione, "aggiungiButton");
        premi(gestione, "salvaButton");
        attendi(gestione);
        attendi(consultazione);
        suJavaFx(() -> {
            TableView<RiepilogoDisponibilita> tabella =
                    (TableView<RiepilogoDisponibilita>) consultazione.getNamespace().get("tabella");
            assertEquals(2, tabella.getItems().get(0).getFinestre().size());
            assertTrue(tabella.getItems().get(0).getTestoDisponibilita().contains("giorno dopo"));
            return null;
        });
    }

    /** Una fascia oltre il bordo del riquadro deve restare raggiungibile. */
    @Test
    void scorreFinoAllUltimaFasciaDelRiepilogo() throws Exception {
        DisponibilitaService servizio = new DisponibilitaService();
        servizio.salva(giulia, IntStream.range(8, 20)
                .mapToObj(ora -> new Disponibilita(giulia, DayOfWeek.WEDNESDAY,
                        LocalTime.of(ora, 0), LocalTime.of(ora + 1, 0)))
                .toList());
        List<Dipendente> dipendenti = partTimeDiProva();
        FXMLLoader loader = carica("consultazione");
        ConsultazioneDisponibilitaController controller = loader.getController();
        suJavaFx(() -> {
            controller.mostraDipendenti(dipendenti);
            return null;
        });
        attendi(loader);

        suJavaFx(() -> {
            Parent vista = loader.getRoot();
            vista.applyCss();
            vista.layout();
            ScrollPane fasce = vista.lookupAll(".fasce-disponibilita").stream()
                    .map(ScrollPane.class::cast)
                    .filter(riquadro -> ((Label) riquadro.getContent()).getText().contains("19:00-20:00"))
                    .findFirst().orElseThrow();
            double altezzaVisibile = fasce.getViewportBounds().getHeight();
            assertTrue(altezzaVisibile > 80, "Devono essere leggibili più di tre righe. Altezza visibile: "
                    + altezzaVisibile + ", altezza preferita: " + fasce.getPrefViewportHeight());
            assertTrue(fasce.getContent().getLayoutBounds().getHeight() > altezzaVisibile);
            double inizioPrima = fasce.getContent().localToScene(0, 0).getY();
            fasce.setVvalue(fasce.getVmax());
            fasce.layout();
            assertTrue(fasce.getContent().localToScene(0, 0).getY() < inizioPrima,
                    "Lo scorrimento deve mostrare le fasce più in basso");
            return null;
        });
    }

    private List<Dipendente> partTimeDiProva() throws SQLException {
        return new DisponibilitaService().trovaPartTime().stream()
                .filter(dipendente -> dipendenteDiProva(dipendente.getId()))
                .toList();
    }

    @Test
    void erroreDiCaricamentoNonDiventaSenzaDisponibilita() throws Exception {
        FXMLLoader loader = carica("consultazione");
        suJavaFx(() -> {
            ConsultazioneDisponibilitaController controller = loader.getController();
            controller.configura(new DisponibilitaService() {
                @Override public List<RiepilogoDisponibilita> consulta() throws SQLException {
                    throw new SQLException("Connessione di prova interrotta");
                }
            });
            controller.aggiorna();
            return null;
        });
        attendi(loader);
        assertTrue(messaggio(loader).contains("Caricamento non riuscito"));
        suJavaFx(() -> {
            TableView<?> tabella = (TableView<?>) loader.getNamespace().get("tabella");
            assertTrue(tabella.getItems().isEmpty());
            assertTrue(((Label) tabella.getPlaceholder()).getText().contains("Dati non disponibili"));
            return null;
        });
    }
}
