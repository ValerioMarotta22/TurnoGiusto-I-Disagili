package it.disagili.turnogiusto.ui;

import it.disagili.turnogiusto.model.AssegnazioneTurno;
import it.disagili.turnogiusto.model.Fabbisogno;
import it.disagili.turnogiusto.model.OrarioServizio;
import it.disagili.turnogiusto.model.PianoSettimanale;
import it.disagili.turnogiusto.model.Ruolo;
import it.disagili.turnogiusto.model.Servizio;
import it.disagili.turnogiusto.model.TipoGiorno;
import it.disagili.turnogiusto.model.Turno;
import it.disagili.turnogiusto.service.GenerazioneTurniService;
import it.disagili.turnogiusto.repository.FabbisognoRepository;
import it.disagili.turnogiusto.ui.GenerazioneTurniController.RigaPiano;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableCell;
import javafx.scene.control.TreeTableColumn;
import javafx.scene.control.TreeTableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static it.disagili.turnogiusto.ui.JavaFxTestSupport.suJavaFx;
import static org.junit.jupiter.api.Assertions.*;

/** Controlla il percorso JavaFX dal menu al piano e i messaggi della schermata US06A. */
@ExtendWith(JavaFxTestSupport.class)
class GenerazioneTurniControllerTest {
    private static final LocalDate LUNEDI = LocalDate.of(2026, 11, 23);

    // La finestra principale mostra le voci in base a chi ha fatto l'accesso: questi test partono dal menu
    // del titolare, quindi si simula l'accesso del titolare e poi lo si toglie, per non influenzare altri test.
    @org.junit.jupiter.api.BeforeEach
    void accediComeTitolare() {
        it.disagili.turnogiusto.service.SessioneUtente.accedi(new it.disagili.turnogiusto.model.Utente(
                1, "titolare", it.disagili.turnogiusto.model.TipoUtente.TITOLARE, null, "Titolare", false));
    }

    @org.junit.jupiter.api.AfterEach
    void esci() {
        it.disagili.turnogiusto.service.SessioneUtente.esci();
    }

    /** Parte dal menu reale, usa i controlli in una finestra e legge il MySQL configurato per i test. */
    @Test
    @Tag("database")
    void generaEVisualizzaIlPianoDalMenuConIlDatabaseReale() throws Exception {
        List<Fabbisogno> configurati = new FabbisognoRepository().trovaTutti();
        CountDownLatch completato = new CountDownLatch(1);
        Stage finestra = suJavaFx(() -> {
            BorderPane radice = FXMLLoader.load(getClass().getResource("/it/disagili/turnogiusto/main-view.fxml"));
            Stage stage = new Stage();
            stage.setScene(new Scene(radice, 900, 600));
            stage.show();
            return stage;
        });

        try {
            suJavaFx(() -> {
                BorderPane radice = (BorderPane) finestra.getScene().getRoot();
                MenuBar barra = (MenuBar) radice.getTop();
                barra.getMenus().stream().filter(menu -> menu.getText().equals("Turni")).findFirst().orElseThrow()
                        .getItems().getFirst().fire();
                radice.applyCss();
                radice.layout();

                DatePicker data = (DatePicker) radice.lookup("#settimana");
                data.show();
                assertTrue(data.isShowing());
                data.setValue(LUNEDI);
                data.hide();

                scegliOrario(radice, "inizioPranzo", "11:30");
                scegliOrario(radice, "finePranzo", "16:00");
                scegliOrario(radice, "inizioCena", "18:00");
                scegliOrario(radice, "fineCena", "00:30");

                FlowPane giorni = (FlowPane) radice.lookup("#festivi");
                CheckBox domenica = (CheckBox) giorni.getChildren().getLast();
                domenica.fire();
                assertTrue(domenica.isSelected());
                Label stato = (Label) radice.lookup("#messaggio");
                stato.textProperty().addListener((osservabile, prima, dopo) -> {
                    if (!dopo.equals("Generazione in corso…")) completato.countDown();
                });
                ((Button) radice.lookup("#genera")).fire();
                return null;
            });

            assertTrue(completato.await(10, TimeUnit.SECONDS), "La generazione sul database deve terminare");
            suJavaFx(() -> {
                Parent radice = finestra.getScene().getRoot();
                Label stato = (Label) radice.lookup("#messaggio");
                TreeTableView<RigaPiano> tabella = tabella(radice);
                assertTrue(stato.getText().startsWith("Bozza dal 23/11/2026 al 29/11/2026"), stato.getText());
                assertFalse(radice.lookup("#genera").isDisabled());
                verificaRigheMostrate(tabella, configurati);
                return null;
            });
        } finally {
            suJavaFx(() -> {
                finestra.close();
                return null;
            });
        }
    }

    /** Apre ogni elenco nella finestra reale e sceglie un orario disponibile. */
    private void scegliOrario(Parent radice, String id, String orario) {
        ComboBox<?> campo = (ComboBox<?>) radice.lookup("#" + id);
        campo.show();
        assertTrue(campo.isShowing());
        int indice = campo.getItems().indexOf(orario);
        assertTrue(indice >= 0);
        campo.getSelectionModel().select(indice);
        campo.hide();
        assertEquals(orario, campo.getEditor().getText());
    }

    /** Confronta le righe del piano con i fabbisogni effettivi per feriali, sabato e domenica festiva. */
    private void verificaRigheMostrate(TreeTableView<RigaPiano> tabella, List<Fabbisogno> configurati) {
        assertEquals(7, tabella.getRoot().getChildren().size());
        for (int indice = 0; indice < 7; indice++) {
            LocalDate giorno = LUNEDI.plusDays(indice);
            TipoGiorno tipo = switch (indice) {
                case 5 -> TipoGiorno.WEEKEND;
                case 6 -> TipoGiorno.FESTIVO;
                default -> TipoGiorno.FERIALE;
            };
            List<Fabbisogno> attesi = configurati.stream()
                    .filter(fabbisogno -> fabbisogno.getTipoGiorno() == tipo && fabbisogno.getNumeroMinimoDipendenti() > 0)
                    .toList();
            TreeItem<RigaPiano> gruppo = tabella.getRoot().getChildren().get(indice);
            assertEquals(giorno, gruppo.getValue().giorno());
            List<Turno> righe = new ArrayList<>();
            List<TreeItem<RigaPiano>> servizi = gruppo.getChildren().stream().filter(riga -> !riga.isLeaf()).toList();
            assertEquals(List.of("Pranzo", "Cena"), servizi.stream().map(riga -> riga.getValue().testo()).toList());
            for (TreeItem<RigaPiano> servizio : servizi) {
                for (TreeItem<RigaPiano> riga : servizio.getChildren()) {
                    Turno turno = riga.getValue().turno();
                    if (turno != null) {
                        assertEquals(servizio.getValue().testo(), turno.servizio().toString());
                        righe.add(turno);
                    }
                }
            }
            assertEquals(attesi.size(), righe.size(), "Righe effettive per " + giorno);
            assertEquals(!attesi.isEmpty(), gruppo.isExpanded());
            for (Turno turno : righe) {
                assertEquals(giorno, turno.inizio().toLocalDate());
                Fabbisogno fabbisogno = attesi.stream().filter(riga -> riga.getServizio() == turno.servizio()
                        && riga.getRuolo() == turno.ruolo()).findFirst().orElseThrow();
                assertEquals(fabbisogno.getNumeroMinimoDipendenti(), turno.postiRichiesti());
                assertEquals(turno.postiRichiesti(), turno.assegnazioni().size() + turno.postiScoperti());
                assertEquals(turno.servizio() == Servizio.CENA ? giorno.atTime(18, 0) : giorno.atTime(11, 30),
                        turno.inizio());
                assertEquals(turno.servizio() == Servizio.CENA ? giorno.plusDays(1).atTime(0, 30) : giorno.atTime(16, 0),
                        turno.fine());
            }
        }
    }

    @Test
    void apreLaGenerazioneDalMenuPrincipale() throws Exception {
        suJavaFx(() -> {
            BorderPane finestra = FXMLLoader.load(getClass().getResource("/it/disagili/turnogiusto/main-view.fxml"));
            MenuBar menu = (MenuBar) finestra.getTop();
            MenuItem voce = menu.getMenus().stream().filter(m -> m.getText().equals("Turni")).findFirst().orElseThrow()
                    .getItems().getFirst();
            voce.fire();
            StackPane centro = (StackPane) finestra.getCenter();
            assertInstanceOf(Button.class, centro.getChildren().getFirst().lookup("#genera"));
            return null;
        });
    }

    @Test
    void inviaDateOrariEFestiviEMostraAssegnatiScopertureEStatoDiBozza() throws Exception {
        GenerazioneTurniService servizio = new GenerazioneTurniService() {
            @Override
            public Esito genera(LocalDate lunedi, Map<Servizio, OrarioServizio> orari, Set<LocalDate> festivi,
                               List<AssegnazioneTurno> confinanti) {
                assertEquals(LUNEDI, lunedi);
                assertEquals(Set.of(LUNEDI.plusDays(6)), festivi);
                assertEquals(LocalTime.of(2, 0), orari.get(Servizio.CENA).fine());
                assertEquals(LocalTime.of(12, 5, 30), orari.get(Servizio.PRANZO).inizio());
                assertEquals(List.of(), confinanti);
                AssegnazioneTurno assegnata = new AssegnazioneTurno(8, LUNEDI.plusDays(6).atTime(18, 0),
                        LUNEDI.plusWeeks(1).atTime(2, 0));
                Turno turno = new Turno(Servizio.CENA, Ruolo.CAMERIERE, assegnata.inizio(), assegnata.fine(), 2, List.of(assegnata));
                Turno scoperto = new Turno(Servizio.CENA, Ruolo.CUOCO, assegnata.inizio(), assegnata.fine(), 1, List.of());
                AssegnazioneTurno seconda = new AssegnazioneTurno(9, assegnata.inizio().minusDays(1), assegnata.fine().minusDays(1));
                Turno coperto = new Turno(Servizio.CENA, Ruolo.AIUTO_CUOCO, seconda.inizio(), seconda.fine(), 1, List.of(seconda));
                AssegnazioneTurno terza = new AssegnazioneTurno(10, lunedi.plusDays(6).atTime(12, 5, 30),
                        lunedi.plusDays(6).atTime(16, 0));
                Turno pranzo = new Turno(Servizio.PRANZO, Ruolo.LAVAPIATTI, terza.inizio(), terza.fine(), 1, List.of(terza));
                return new Esito(new PianoSettimanale(lunedi, List.of(turno, scoperto, coperto, pranzo)),
                        Map.of(8, "Giulia Rossi", 9, "Luca Verdi", 10, "Ada Neri"), false, List.of());
            }
        };
        CountDownLatch mostrato = new CountDownLatch(1);
        FXMLLoader loader = suJavaFx(() -> {
            FXMLLoader schermata = carica(servizio);
            campoData(schermata).setValue(LUNEDI);
            // Prova sia la selezione da elenco sia un orario preciso digitato prima di generare.
            campoOra(schermata, "fineCena").getSelectionModel().select("02:00");
            campoOra(schermata, "inizioPranzo").getEditor().setText("12:05:30");
            FlowPane festivi = (FlowPane) schermata.getNamespace().get("festivi");
            ((CheckBox) festivi.getChildren().getLast()).setSelected(true);
            messaggio(schermata).textProperty().addListener((osservabile, prima, dopo) -> {
                if (!dopo.equals("Generazione in corso…")) mostrato.countDown();
            });
            genera(schermata).fire();
            return schermata;
        });

        assertTrue(mostrato.await(5, TimeUnit.SECONDS), "La bozza deve apparire nella tabella");
        suJavaFx(() -> {
            Parent radice = loader.getRoot();
            radice.applyCss();
            radice.layout();
            TreeTableView<RigaPiano> tabella = tabella(radice);
            assertEquals(7, tabella.getRoot().getChildren().size());
            assertEquals(15, tabella.getExpandedItemCount());
            TreeItem<RigaPiano> lunedi = tabella.getRoot().getChildren().getFirst();
            TreeItem<RigaPiano> sabato = tabella.getRoot().getChildren().get(5);
            TreeItem<RigaPiano> domenica = tabella.getRoot().getChildren().get(6);
            TreeItem<RigaPiano> pranzoSabato = sabato.getChildren().getFirst();
            TreeItem<RigaPiano> pranzoDomenica = domenica.getChildren().getFirst();
            TreeItem<RigaPiano> cenaDomenica = domenica.getChildren().getLast();
            assertTrue(sabato.isExpanded());
            assertTrue(domenica.isExpanded());
            assertFalse(pranzoSabato.isExpanded());
            assertTrue(pranzoDomenica.isExpanded());
            assertTrue(cenaDomenica.isExpanded());
            assertEquals(tabella.getTreeItemLevel(domenica) + 1, tabella.getTreeItemLevel(cenaDomenica));
            int primaRigaCena = tabella.getRow(cenaDomenica.getChildren().getFirst());
            TreeTableColumn<?, ?> assegnati = (TreeTableColumn<?, ?>) loader.getNamespace().get("assegnatiColonna");
            TreeTableColumn<?, ?> orario = (TreeTableColumn<?, ?>) loader.getNamespace().get("orarioColonna");
            TreeTableColumn<?, ?> scoperti = (TreeTableColumn<?, ?>) loader.getNamespace().get("scopertiColonna");
            assertEquals("Giulia Rossi", assegnati.getCellData(primaRigaCena));
            assertEquals("Nessuno", assegnati.getCellData(primaRigaCena + 1));
            assertTrue(orario.getCellData(primaRigaCena).toString().contains("30/11 02:00"));
            assertTrue(messaggio(loader).getText().contains("23/11/2026 al 29/11/2026"));
            assertTrue(messaggio(loader).getText().contains("Posti assegnati: 3. Posti scoperti: 2"));
            assertTrue(messaggio(loader).getText().contains("Monte ore e riposi da verificare"));
            verificaColoreDellaCella(tabella, scoperti, 1, Color.web("#FBE7E6"));
            verificaColoreDellaCella(tabella, scoperti, 0, Color.web("#E7F3E8"));
            // La cena si chiude da sola, poi il giorno può nascondere entrambi i servizi.
            cenaDomenica.setExpanded(false);
            assertEquals(13, tabella.getExpandedItemCount());
            assertTrue(pranzoDomenica.isExpanded());
            cenaDomenica.setExpanded(true);
            domenica.setExpanded(false);
            assertEquals(10, tabella.getExpandedItemCount());
            assertTrue(sabato.isExpanded());
            domenica.setExpanded(true);
            assertEquals(15, tabella.getExpandedItemCount());

            // Si possono aprire anche un singolo servizio e un'intera giornata senza turni.
            pranzoSabato.setExpanded(true);
            assertEquals("Servizio non previsto.", orario.getCellData(tabella.getRow(pranzoSabato.getChildren().getFirst())));
            assertFalse(lunedi.isExpanded());
            lunedi.setExpanded(true);
            assertEquals("Nessun servizio previsto.", orario.getCellData(tabella.getRow(lunedi.getChildren().getFirst())));
            assertEquals(List.of("Pranzo", "Cena"), lunedi.getChildren().stream()
                    .filter(riga -> !riga.isLeaf()).map(riga -> riga.getValue().testo()).toList());
            return null;
        });
    }

    /** Controlla il colore applicato da JavaFX alla cella che mostra il numero. */
    private void verificaColoreDellaCella(TreeTableView<?> tabella, TreeTableColumn<?, ?> colonna, int numero, Color sfondo) {
        for (Node nodo : tabella.lookupAll(".tree-table-cell")) {
            if (nodo instanceof TreeTableCell<?, ?> cella && cella.getTableColumn() == colonna
                    && !cella.isEmpty() && Integer.valueOf(numero).equals(cella.getItem())) {
                assertEquals(Integer.toString(numero), cella.getText());
                assertEquals(sfondo, cella.getBackground().getFills().getFirst().getFill());
                return;
            }
        }
        fail("Cella scoperture non trovata per il valore " + numero);
    }

    @SuppressWarnings("unchecked")
    private TreeTableView<RigaPiano> tabella(Parent radice) {
        return (TreeTableView<RigaPiano>) radice.lookup("#tabella");
    }

    @Test
    void ilCalendarioPermetteSoloIlLunediEAzzeraIFestiviQuandoLaSettimanaCambia() throws Exception {
        suJavaFx(() -> {
            FXMLLoader loader = carica(new GenerazioneTurniService());
            DatePicker data = campoData(loader);
            DateCell giorno = data.getDayCellFactory().call(data);
            giorno.updateItem(LUNEDI.plusDays(1), false);
            assertTrue(giorno.isDisabled());
            giorno.updateItem(LUNEDI, false);
            assertFalse(giorno.isDisabled());

            data.setValue(LUNEDI);
            FlowPane festivi = (FlowPane) loader.getNamespace().get("festivi");
            ((CheckBox) festivi.getChildren().getFirst()).setSelected(true);
            data.setValue(LUNEDI.plusWeeks(1));
            assertEquals(7, festivi.getChildren().size());
            CheckBox primoFestivo = (CheckBox) festivi.getChildren().getFirst();
            assertEquals(LUNEDI.plusWeeks(1), primoFestivo.getUserData());
            assertFalse(primoFestivo.isSelected());
            return null;
        });
    }

    @Test
    void mostraQualeOrarioCorreggereERifiutaGliOrariIdentici() throws Exception {
        suJavaFx(() -> {
            FXMLLoader loader = carica(new GenerazioneTurniService());
            ComboBox<String> finePranzo = campoOra(loader, "finePranzo");
            assertTrue(finePranzo.getItems().contains("15:15"));
            assertEquals("11:30", campoOra(loader, "inizioPranzo").getEditor().getText());
            assertEquals("16:00", finePranzo.getEditor().getText());
            assertEquals("18:00", campoOra(loader, "inizioCena").getEditor().getText());
            assertEquals("00:30", campoOra(loader, "fineCena").getEditor().getText());
            finePranzo.getEditor().setText("25:10");
            genera(loader).fire();
            assertTrue(messaggio(loader).getText().startsWith("Fine pranzo"));

            finePranzo.getSelectionModel().select("11:30");
            genera(loader).fire();
            assertTrue(messaggio(loader).getText().contains("devono essere diversi"));
            assertFalse(genera(loader).isDisabled());
            return null;
        });
    }

    @Test
    void rifiutaUnaDataDiversaDaLunediPrimaDiInvocareIlService() throws Exception {
        suJavaFx(() -> {
            FXMLLoader loader = carica(new GenerazioneTurniService());
            campoData(loader).setValue(LUNEDI.plusDays(1));
            genera(loader).fire();
            assertTrue(messaggio(loader).getText().contains("Seleziona il lunedì"));
            return null;
        });
    }

    @Test
    void segnalaUnErroreDiLetturaEPermetteDiRiprovare() throws Exception {
        CountDownLatch mostrato = new CountDownLatch(1);
        FXMLLoader loader = suJavaFx(() -> {
            FXMLLoader schermata = carica(new GenerazioneTurniService() {
                @Override
                public Esito genera(LocalDate lunedi, Map<Servizio, OrarioServizio> orari, Set<LocalDate> festivi,
                                   List<AssegnazioneTurno> confinanti) throws SQLException {
                    throw new SQLException("Database di prova spento");
                }
            });
            messaggio(schermata).textProperty().addListener((osservabile, prima, dopo) -> {
                if (dopo.startsWith("Lettura del database non riuscita")) mostrato.countDown();
            });
            genera(schermata).fire();
            return schermata;
        });
        assertTrue(mostrato.await(5, TimeUnit.SECONDS));
        assertFalse(suJavaFx(() -> genera(loader).isDisabled()));
    }

    /** Carica la schermata reale con un service controllato dal test. */
    private FXMLLoader carica(GenerazioneTurniService servizio) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("generazione-turni-view.fxml"));
        loader.setControllerFactory(classe -> new GenerazioneTurniController(servizio));
        Parent radice = loader.load();
        // Con una Scene i controlli editabili si comportano come nella finestra reale.
        new Scene(radice, 900, 720);
        radice.applyCss();
        return loader;
    }

    @SuppressWarnings("unchecked")
    private ComboBox<String> campoOra(FXMLLoader loader, String id) {
        return (ComboBox<String>) loader.getNamespace().get(id);
    }

    private DatePicker campoData(FXMLLoader loader) {
        return (DatePicker) loader.getNamespace().get("settimana");
    }

    private Button genera(FXMLLoader loader) {
        return (Button) loader.getNamespace().get("genera");
    }

    private Label messaggio(FXMLLoader loader) {
        return (Label) loader.getNamespace().get("messaggio");
    }
}
